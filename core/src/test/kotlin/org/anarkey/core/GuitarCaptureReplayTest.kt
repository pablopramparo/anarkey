package org.anarkey.core

import org.anarkey.core.music.PitchMath
import org.anarkey.core.pitch.YinPitchDetector
import org.anarkey.core.tuner.SignalStatus
import org.anarkey.core.tuner.TunerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.pow

/** Kotlin engine replay against the independently played open strings in the saved phone capture. */
class GuitarCaptureReplayTest {
    @Test fun isolatedOpenStringsResolveWithoutOctaveErrors() {
        val wav = javaClass.getResourceAsStream("/audio/guitar-open-strings-capture.wav")!!.use { it.readBytes() }
        require(String(wav, 0, 4, Charsets.US_ASCII) == "RIFF" && String(wav, 8, 4, Charsets.US_ASCII) == "WAVE")
        require(u16(wav, 22) == 1 && u16(wav, 34) == 16) { "Expected mono PCM16 WAV" }
        val rate = i32(wav, 24)
        val dataAt = findData(wav)
        val count = i32(wav, dataAt + 4) / 2
        val pcm = ShortArray(count) { i -> u16(wav, dataAt + 8 + i * 2).toShort() }
        val windowSize = 4096
        val hop = 1024
        val engine = TunerEngine(YinPitchDetector(rate, windowSize), rate, highPassCutoffHz = 60.0, hopSamples = 1024)
        val samples = FloatArray(windowSize)
        // Use this guitar's measured fundamentals. Its open E2 is about 79.8 Hz,
        // roughly 55 cents below standard E2, so equal-tempered labels are not ground truth.
        val e2 = 79.8
        val a2 = 108.5
        val d3 = 144.6
        val g3 = 193.8
        val b3 = 245.3
        val e4 = 326.9
        val segments = listOf(
            Segment("E2-tail", 0.0, 2.0, e2, assertMinimum = false),
            Segment("A2", 2.09, 4.0, a2),
            Segment("D3", 5.0, 7.6, d3),
            Segment("G3", 7.6, 10.12, g3),
            Segment("B3", 10.12, 11.64, b3),
            Segment("E4-quiet", 12.5, 17.76, e4, assertMinimum = false),
            // Overlapped strings: any string within ~12 dB of the loudest one (measured offline from
            // the spectrum) counts as audible. The tuner may show any of them or nothing, never a
            // frequency that belongs to no sounding string.
            Segment("overlap-E2", 17.76, 19.5, e2, assertMinimum = false, assertNoPhantom = true),
            Segment("overlap-A2+E2", 19.5, 21.53, a2, listOf(e2), assertMinimum = false, assertNoPhantom = true),
            Segment("overlap-D3+A2+E2", 21.53, 23.63, d3, listOf(a2, e2), assertMinimum = false, assertNoPhantom = true),
            Segment("overlap-G3", 23.63, 24.3, g3, assertMinimum = false, assertNoPhantom = true),
            Segment("overlap-G3+D3+E2", 24.3, 25.74, g3, listOf(d3, e2), assertMinimum = false, assertNoPhantom = true),
            Segment("overlap-B3+D3+G3+E2+A2", 25.74, 28.0, b3, listOf(d3, g3, e2, a2), assertMinimum = false, assertNoPhantom = true),
            Segment("overlap-E4+D3+E2", 28.0, 30.0, e4, listOf(d3, e2), assertMinimum = false, assertNoPhantom = true),
        )
        val metrics = segments.associateWith { SegmentMetrics() }

        var start = 0
        while (start + windowSize <= pcm.size) {
            for (i in samples.indices) samples[i] = pcm[start + i] / 32768f
            val timeMs = start * 1000L / rate
            val state = engine.analyze(samples, timeMs)
            val seconds = start.toDouble() / rate
            val segment = segments.firstOrNull { seconds >= it.startSeconds && seconds < it.endSeconds }
            if (segment != null) {
                val result = metrics.getValue(segment)
                val hz = state.frequencyHz.takeIf { state.status == SignalStatus.STABLE || state.status == SignalStatus.HOLDING }
                if (hz != null) {
                    result.stable++
                    val expectedHz = segment.expectedFrequenciesHz
                    val cents = expectedHz.minOf { abs(PitchMath.cents(hz, it)) }
                    if (cents <= 50.0) {
                        result.correct++
                        if (result.firstCorrectMs == null) result.firstCorrectMs = (seconds - segment.startSeconds) * 1000.0
                    } else if (expectedHz.any { isOctaveError(hz, it) }) {
                        result.octave++
                    } else {
                        result.ghost++
                    }
                    if (cents > 50.0) result.phantomHz.merge(hz.roundToInt(), 1, Int::plus)
                    if (seconds >= segment.startSeconds + 0.25) {
                        result.settledFrames++
                        if (cents <= 50.0) result.settledCorrect++
                        else if (expectedHz.any { isOctaveError(hz, it) }) result.settledOctave++
                        else result.settledGhost++
                    }
                }
                result.frames++
            }
            start += hop
        }

        for (segment in segments) {
            val result = metrics.getValue(segment)
            println("GUITAR_REPLAY segment=${segment.name} stable=${result.stable}/${result.frames} audible_string=${result.correct} octave=${result.octave} ghost=${result.ghost} settled_audible=${result.settledCorrect}/${result.settledFrames} settled_octave=${result.settledOctave} settled_ghost=${result.settledGhost} first_audible_ms=${result.firstCorrectMs} phantom_hz=${result.phantomHz.toSortedMap()}")
            if (segment.assertNoPhantom) {
                assertEquals("${segment.name} must not settle on a frequency that no audible string has: ${result.phantomHz.toSortedMap()}", 0, result.settledOctave + result.settledGhost)
            }
            if (!segment.assertMinimum) continue
            assertTrue("${segment.name} had too few analysis frames", result.frames >= 20)
            assertEquals("${segment.name} should have no settled octave errors", 0, result.settledOctave)
            assertTrue("${segment.name} should settle correctly in at least half its windows", result.settledCorrect >= result.settledFrames / 2)
            assertTrue("${segment.name} should acquire the right note within 250 ms: ${result.firstCorrectMs}", result.firstCorrectMs != null && result.firstCorrectMs!! <= 250.0)
        }
    }

    /** D3, G3 and B3 left ringing: their periods share a multiple near 48 Hz that is no string. */
    @Test fun threeRingingStringsDoNotSettleOnTheirCommonPeriod() {
        val wav = javaClass.getResourceAsStream("/audio/guitar-three-strings-capture.wav")!!.use { it.readBytes() }
        require(u16(wav, 22) == 1 && u16(wav, 34) == 16) { "Expected mono PCM16 WAV" }
        val rate = i32(wav, 24)
        val dataAt = findData(wav)
        val pcm = ShortArray(i32(wav, dataAt + 4) / 2) { i -> u16(wav, dataAt + 8 + i * 2).toShort() }
        val windowSize = 4096
        val engine = TunerEngine(YinPitchDetector(rate, windowSize), rate, highPassCutoffHz = 60.0, hopSamples = 1024)
        val samples = FloatArray(windowSize)
        // Strings within ~12 dB of the loudest while the B3 pluck (2.13 s into the clip) decays.
        val audible = listOf(245.0, 193.4, 144.4, 108.3, 79.8)
        val shown = ArrayList<Double>()
        val phantoms = ArrayList<Double>()
        var frames = 0
        var start = 0
        while (start + windowSize <= pcm.size) {
            for (i in samples.indices) samples[i] = pcm[start + i] / 32768f
            val state = engine.analyze(samples, start * 1000L / rate)
            val seconds = start.toDouble() / rate
            if (seconds >= 2.13 + 0.25 && seconds < 3.82) {
                frames++
                val hz = state.frequencyHz.takeIf { state.status == SignalStatus.STABLE || state.status == SignalStatus.HOLDING }
                if (hz != null) {
                    shown += hz
                    if (audible.none { abs(PitchMath.cents(hz, it)) <= 50.0 }) phantoms += hz
                }
            }
            start += 1024
        }
        val histogram = phantoms.groupingBy { it.roundToInt() }.eachCount().toSortedMap()
        println("GUITAR_REPLAY segment=three-strings-B3+G3+D3 stable=${shown.size}/$frames audible_string=${shown.size - phantoms.size} phantom_hz=$histogram")
        assertEquals("Three ringing strings must not settle on a frequency no string has: $histogram", 0, phantoms.size)
    }

    private fun isOctaveError(hz: Double, targetHz: Double): Boolean {
        val octave = (ln(hz / targetHz) / ln(2.0)).roundToInt()
        return abs(octave) >= 1 && abs(PitchMath.cents(hz, targetHz * 2.0.pow(octave))) <= 50.0
    }

    private fun findData(bytes: ByteArray): Int {
        var at = 12
        while (at + 8 <= bytes.size) {
            val size = i32(bytes, at + 4)
            if (String(bytes, at, 4, Charsets.US_ASCII) == "data") return at
            at += 8 + size + (size and 1)
        }
        error("WAV has no data chunk")
    }
    private fun u16(bytes: ByteArray, at: Int) = (bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8)
    private fun i32(bytes: ByteArray, at: Int) = u16(bytes, at) or (u16(bytes, at + 2) shl 16)
    private data class Segment(
        val name: String,
        val startSeconds: Double,
        val endSeconds: Double,
        val targetHz: Double,
        val additionalTargetsHz: List<Double> = emptyList(),
        val assertMinimum: Boolean = true,
        val assertNoPhantom: Boolean = false,
    ) {
        val expectedFrequenciesHz get() = listOf(targetHz) + additionalTargetsHz
    }
    private data class SegmentMetrics(
        var frames: Int = 0,
        var stable: Int = 0,
        var correct: Int = 0,
        var octave: Int = 0,
        var ghost: Int = 0,
        var settledFrames: Int = 0,
        var settledCorrect: Int = 0,
        var settledOctave: Int = 0,
        var settledGhost: Int = 0,
        var firstCorrectMs: Double? = null,
        val phantomHz: MutableMap<Int, Int> = HashMap(),
    )
}
