package org.anarkey.app.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.os.SystemClock
import android.util.Log
import org.anarkey.app.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.anarkey.core.pitch.YinPitchDetector
import org.anarkey.core.pitch.McleodPitchDetector
import org.anarkey.core.pitch.PitchResult
import org.anarkey.core.tuner.TunerEngine
import org.anarkey.core.tuner.TunerState

data class AudioConfiguration(val sampleRate: Int, val source: Int, val bufferBytes: Int) {
    val windowSamples = 4096
    val hopSamples = 1024
}
data class AudioFrame(
    val tuner: TunerState,
    val configuration: AudioConfiguration,
    val processingMs: Double,
    val comparisonPitch: PitchResult? = null,
    val spectrumLevels: FloatArray = floatArrayOf(),
)
class UnsupportedAudioException : Exception()
class MicrophoneUnavailableException : Exception()

/** One worker owns creation, reads, stop and release. No cross-thread release race. */
class AudioCapture(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val appContext = context.applicationContext
    private val ownership = Mutex()
    private val diagnosticAudioLock = Any()
    private var diagnosticAudio: PcmRingBuffer? = null
    private var diagnosticAudioRate: Int? = null
    private val diagnosticScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableHasRecentAudio = MutableStateFlow(false)
    val hasRecentAudio = mutableHasRecentAudio.asStateFlow()
    private val mutableCaptureRunning = MutableStateFlow(false)
    val captureRunning = mutableCaptureRunning.asStateFlow()
    private val mutableExportedAudio = MutableStateFlow<String?>(null)
    val exportedAudio = mutableExportedAudio.asStateFlow()

    fun saveRecentAudio(customName: String, eventLog: (Long) -> String) {
        if (!BuildConfig.DEBUG) return
        val saved = synchronized(diagnosticAudioLock) {
            if (mutableCaptureRunning.value) return
            val audio = diagnosticAudio ?: return
            val rate = diagnosticAudioRate ?: return
            val samples = audio.snapshot()
            if (samples.isEmpty()) return
            val durationMs = samples.size * 1000L / rate
            val log = eventLog(durationMs)
            mutableHasRecentAudio.value = false
            Triple(audio, rate, Pair(samples, log))
        }
        diagnosticScope.launch {
            try {
                val directory = java.io.File(appContext.filesDir, "diagnostics/captures")
                if (!directory.exists() && !directory.mkdirs()) error("Cannot create diagnostic directory")
                val safeName = java.text.Normalizer.normalize(customName.trim(), java.text.Normalizer.Form.NFD)
                    .replace(Regex("\\p{M}+"), "")
                    .replace(Regex("[^A-Za-z0-9._-]+"), "-")
                    .trim('-', '.', '_')
                    .take(48)
                    .ifBlank { "prueba" }
                val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss-SSS", java.util.Locale.US)
                    .format(java.util.Date())
                val suffix = java.util.UUID.randomUUID().toString().take(6)
                val baseName = "anarkey-$safeName-$stamp-$suffix"
                val wavFile = java.io.File(directory, "$baseName.wav")
                val logFile = java.io.File(directory, "$baseName.log")
                val audioDurationMs = saved.third.first.size * 1000L / saved.second
                saved.first.writeWav(wavFile, saved.second, saved.third.first)
                logFile.writeText(saved.third.second, Charsets.UTF_8)
                mutableExportedAudio.value = baseName
                Log.i("AnarkeyTuner", "DIAGNOSTIC_EXPORTED wav=${wavFile.absolutePath} log=${logFile.absolutePath} seconds=${"%.2f".format(java.util.Locale.US, audioDurationMs / 1000.0)}")
            } catch (failure: Exception) {
                mutableHasRecentAudio.value = true
                Log.w("AnarkeyTuner", "AUDIO_EXPORT_FAILED type=${failure.javaClass.simpleName}")
            }
        }
    }

    @SuppressLint("MissingPermission") // Activity checks permission; revocation is caught by caller.
    private fun open(): Pair<AudioRecord, AudioConfiguration> {
        val unprocessed = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val sources = if (unprocessed) listOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.VOICE_RECOGNITION)
            else listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        // Output native rate is not a reliable input-rate capability query. Probe both.
        for (rate in listOf(48000, 44100)) {
            val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minimum <= 0) continue
            for (source in sources) {
                val bytes = maxOf(minimum * 2, 4096 * 2)
                val recorder = try {
                    AudioRecord.Builder().setAudioSource(source).setAudioFormat(
                        AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
                    ).setBufferSizeInBytes(bytes).build()
                } catch (_: IllegalArgumentException) { continue }
                  catch (_: UnsupportedOperationException) { continue }
                if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                    return recorder to AudioConfiguration(recorder.sampleRate, source, bytes)
                }
                recorder.release()
            }
        }
        throw UnsupportedAudioException()
    }

    fun close() { diagnosticScope.cancel() }

    suspend fun run(a4Hz: Double = 440.0, highPassHz: Double = GUITAR_HIGH_PASS_HZ, targetFrequencyHz: () -> Double? = { null }, onFrame: (AudioFrame) -> Unit) = ownership.withLock {
        withContext(Dispatchers.IO) {
            val (recorder, configuration) = open()
            try {
                // Standard guitar tuning starts at E2 (~82 Hz); reject the persistent
                // sub-55 Hz room hum before pitch detection without changing the raw WAV.
                val engine = TunerEngine(
                    YinPitchDetector(configuration.sampleRate, configuration.windowSamples),
                    configuration.sampleRate,
                    highPassCutoffHz = highPassHz,
                    hopSamples = configuration.hopSamples,
                    a4Hz = a4Hz,
                )
                val comparisonDetector = if (BuildConfig.DEBUG) McleodPitchDetector(configuration.sampleRate, configuration.windowSamples) else null
                val pcm = ShortArray(configuration.hopSamples)
                if (BuildConfig.DEBUG) synchronized(diagnosticAudioLock) {
                    diagnosticAudio = PcmRingBuffer(configuration.sampleRate * DEBUG_AUDIO_SECONDS)
                    diagnosticAudioRate = configuration.sampleRate
                    mutableHasRecentAudio.value = false
                }
                val window = FloatArray(configuration.windowSamples)
                val spectrumAnalyzer = SpectrumAnalyzer(configuration.sampleRate, configuration.windowSamples)
                var filled = 0
                var hopFilled = 0
                var lastAudio = SystemClock.elapsedRealtime()
                recorder.startRecording()
                if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw MicrophoneUnavailableException()
                if (BuildConfig.DEBUG) mutableCaptureRunning.value = true
                while (currentCoroutineContext().isActive) {
                    val count = recorder.read(pcm, hopFilled, pcm.size - hopFilled, AudioRecord.READ_NON_BLOCKING)
                    if (count < 0) throw MicrophoneUnavailableException()
                    if (count == 0) {
                        if (SystemClock.elapsedRealtime() - lastAudio > 1500) throw MicrophoneUnavailableException()
                        delay(4)
                        continue
                    }
                    lastAudio = SystemClock.elapsedRealtime()
                    hopFilled += count
                    if (hopFilled < pcm.size) continue
                    hopFilled = 0
                    if (BuildConfig.DEBUG) synchronized(diagnosticAudioLock) {
                        diagnosticAudio?.append(pcm)
                        mutableHasRecentAudio.value = true
                    }
                    if (filled == window.size) {
                        window.copyInto(window, 0, pcm.size, window.size)
                        filled -= pcm.size
                    }
                    for (i in pcm.indices) window[filled + i] = pcm[i] / 32768f
                    filled += pcm.size
                    if (filled == window.size) {
                        val start = SystemClock.elapsedRealtimeNanos()
                        val result = engine.analyze(window, targetFrequencyHz = targetFrequencyHz())
                        val comparison = comparisonDetector?.detect(window)
                        val spectrum = spectrumAnalyzer.analyze(window)
                        val ms = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
                        currentCoroutineContext().ensureActive()
                        onFrame(AudioFrame(result, configuration, ms, comparison, spectrum))
                    }
                }
            } finally {
                try {
                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
                } finally { recorder.release() }
                if (BuildConfig.DEBUG) mutableCaptureRunning.value = false
            }
        }
    }

    companion object {
        private const val DEBUG_AUDIO_SECONDS = 30
        const val GUITAR_HIGH_PASS_HZ = 60.0
        /** Bass E1 is ~41 Hz, so the guitar cutoff would remove its fundamental. */
        const val BASS_HIGH_PASS_HZ = 25.0
    }
}
