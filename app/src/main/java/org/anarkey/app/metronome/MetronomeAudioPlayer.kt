package org.anarkey.app.metronome

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import org.anarkey.core.metronome.MetronomePcmGenerator
import org.anarkey.core.metronome.MetronomeSettings

data class MetronomePlaybackState(
    val isPlaying: Boolean = false,
    val beatInBar: Int = -1,
    val error: String? = null,
)

/** AudioTrack is the clock: clicks are placed by sample frame and UI progress reads played frames. */
class MetronomeAudioPlayer(
    private val onState: (MetronomePlaybackState) -> Unit,
) {
    private var activeRun: AudioRun? = null

    @Synchronized
    fun start(settings: MetronomeSettings) {
        stopActive(wait = true)
        val run = AudioRun(settings, onState)
        activeRun = run
        run.start()
    }

    @Synchronized
    fun update(settings: MetronomeSettings) {
        activeRun?.update(settings)
    }

    @Synchronized
    fun stop() {
        activeRun?.requestStop()
    }

    @Synchronized
    private fun stopActive(wait: Boolean) {
        val run = activeRun ?: return
        run.requestStop()
        activeRun = null
        if (wait) run.awaitStopped()
    }

    private class AudioRun(
        settings: MetronomeSettings,
        private val onState: (MetronomePlaybackState) -> Unit,
    ) {
        private val running = java.util.concurrent.atomic.AtomicBoolean(true)
        private val generator = MetronomePcmGenerator(SAMPLE_RATE, settings)
        private val track = createTrack()
        private val thread = Thread(::render, "Anarkey-Metronome-Audio")

        fun start() {
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                running.set(false)
                track.release()
                error("audio_track_uninitialized")
            }
            thread.start()
        }

        fun update(value: MetronomeSettings) = generator.updateAtNextPulse(value)

        fun requestStop() {
            if (!running.getAndSet(false)) return
            runCatching { track.pause() }
            runCatching { track.flush() }
            thread.interrupt()
        }

        fun awaitStopped() {
            if (Thread.currentThread() !== thread) runCatching { thread.join(STOP_JOIN_MS) }
        }

        private fun render() {
            val pcm = ShortArray(BLOCK_FRAMES)
            var failureMessage: String? = null
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                track.play()
                onState(MetronomePlaybackState(isPlaying = true, beatInBar = -1))
                while (running.get()) {
                    generator.render(pcm)
                    var offset = 0
                    while (offset < pcm.size && running.get()) {
                        val written = track.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
                        if (written < 0) error("audio_track_write_$written")
                        if (written == 0) Thread.yield() else offset += written
                    }
                    val playedFrame = track.playbackHeadPosition.toLong() and 0xFFFF_FFFFL
                    val beat = generator.beatAtPlayedFrame(playedFrame)
                    if (running.get()) onState(MetronomePlaybackState(isPlaying = true, beatInBar = beat))
                }
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (failure: Throwable) {
                if (running.get()) {
                    failureMessage = failure.message ?: "audio_track_error"
                    onState(MetronomePlaybackState(error = failureMessage))
                }
            } finally {
                running.set(false)
                runCatching { track.pause() }
                runCatching { track.flush() }
                runCatching { track.stop() }
                track.release()
                onState(MetronomePlaybackState(error = failureMessage))
            }
        }

        private fun createTrack(): AudioTrack {
            val minimum = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0) { "audio_track_min_buffer_$minimum" }
            val bufferBytes = maxOf(minimum, BLOCK_FRAMES * BYTES_PER_SAMPLE * 4)
            return AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val BLOCK_FRAMES = 240
        const val BYTES_PER_SAMPLE = 2
        const val STOP_JOIN_MS = 500L
    }
}
