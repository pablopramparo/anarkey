package org.anarkey.app.chords

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.anarkey.core.music.ChordVoicing
import org.anarkey.core.music.TuningCatalog
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Plays a chord shape as a strummed, plucked-string sound synthesized on the device (no audio assets).
 * Pitches come from the tuning's open strings plus the fret and the capo, so any instrument/tuning works.
 */
object ChordPlayer {
    private const val SAMPLE_RATE = 44_100
    private const val DURATION_S = 2.4
    private const val STRUM_GAP_S = 0.035
    private val lock = Any()
    private var current: AudioTrack? = null

    suspend fun play(voicing: ChordVoicing, capo: Int, a4Hz: Double = 440.0) {
        val tuning = TuningCatalog.tuning(voicing.tuningId) ?: return
        val notes = voicing.frets.mapIndexedNotNull { index, fret ->
            val open = tuning.strings.getOrNull(index)?.midi ?: return@mapIndexedNotNull null
            fret?.let { open + it + capo }
        }
        if (notes.isEmpty()) return
        val pcm = withContext(Dispatchers.Default) { render(notes, a4Hz) }
        val track = withContext(Dispatchers.IO) { start(pcm) } ?: return
        delay((DURATION_S * 1000).toLong() + 300)
        release(track)
    }

    fun stop() = synchronized(lock) { current?.let(::releaseLocked); current = null }

    private fun start(pcm: ShortArray): AudioTrack? = synchronized(lock) {
        current?.let(::releaseLocked)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
        }.getOrNull() ?: return null
        track.write(pcm, 0, pcm.size)
        track.play()
        current = track
        track
    }

    private fun release(track: AudioTrack) = synchronized(lock) {
        if (current === track) current = null
        releaseLocked(track)
    }

    private fun releaseLocked(track: AudioTrack) {
        runCatching { track.stop() }
        runCatching { track.release() }
    }

    private fun render(midiNotes: List<Int>, a4Hz: Double): ShortArray {
        val total = ((DURATION_S + STRUM_GAP_S * midiNotes.size) * SAMPLE_RATE).toInt()
        val mix = FloatArray(total)
        midiNotes.forEachIndexed { order, midi ->
            val hz = a4Hz * 2.0.pow((midi - 69) / 12.0)
            val start = (order * STRUM_GAP_S * SAMPLE_RATE).toInt()
            addString(mix, start, hz)
        }
        val gain = 0.55f / kotlin.math.sqrt(midiNotes.size.toFloat())
        return ShortArray(total) { i ->
            // Short fade at the end avoids a click when the buffer stops.
            val fade = ((total - i) / (SAMPLE_RATE * 0.05f)).coerceIn(0f, 1f)
            (tanh(mix[i] * gain * 1.6f) * fade * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** One plucked string: decaying harmonics, higher ones dying faster, with a pluck-position comb. */
    private fun addString(mix: FloatArray, start: Int, hz: Double) {
        val harmonics = minOf(14, ((SAMPLE_RATE / 2 - 1000) / hz).toInt())
        val lowBoost = (110.0 / hz).coerceIn(0.5, 1.6)
        for (h in 1..harmonics) {
            val amplitude = (1.0 / h.toDouble().pow(1.15) * kotlin.math.abs(sin(PI * h * 0.16))).toFloat()
            if (amplitude < 0.004f) continue
            val decay = (1.6 + 0.55 * h) / lowBoost
            val step = 2.0 * PI * hz * h / SAMPLE_RATE
            val phase = h * 0.37
            for (n in 0 until mix.size - start) {
                val t = n.toDouble() / SAMPLE_RATE
                val envelope = exp(-t * decay)
                if (envelope < 0.0008) break
                val attack = (n / (SAMPLE_RATE * 0.003)).coerceAtMost(1.0)
                mix[start + n] += (amplitude * envelope * attack * sin(step * n + phase)).toFloat()
            }
        }
    }
}
