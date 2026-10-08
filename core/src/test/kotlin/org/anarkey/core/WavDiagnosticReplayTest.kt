package org.anarkey.core

import org.anarkey.core.music.PitchMath
import org.anarkey.core.pitch.McleodPitchDetector
import org.anarkey.core.pitch.YinPitchDetector
import org.anarkey.core.tuner.TunerEngine
import org.junit.Assume.assumeNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Optional real-capture replay. Set ANARKEY_DIAGNOSTIC_WAV to a mono PCM16 WAV path. */
class WavDiagnosticReplayTest {
    @Test fun replayWavAndPrintSparseDetectorComparison() {
        val path = System.getenv("ANARKEY_DIAGNOSTIC_WAV")
        assumeNotNull("Set ANARKEY_DIAGNOSTIC_WAV to replay a recorded capture", path)
        val targetHz = System.getenv("ANARKEY_TARGET_HZ")?.toDoubleOrNull()
        val wav = File(path!!).readBytes()
        require(String(wav, 0, 4, Charsets.US_ASCII) == "RIFF" && String(wav, 8, 4, Charsets.US_ASCII) == "WAVE")
        require(u16(wav, 22) == 1 && u16(wav, 34) == 16) { "Expected mono PCM16 WAV" }
        val rate = i32(wav, 24)
        val dataAt = findData(wav)
        val count = i32(wav, dataAt + 4) / 2
        val pcm = ShortArray(count) { i -> (u16(wav, dataAt + 8 + i * 2)).toShort() }
        val windowSize = 4096
        val hop = 1024
        require(pcm.size >= windowSize)
        val yin = YinPitchDetector(rate, windowSize)
        val mpm = McleodPitchDetector(rate, windowSize)
        val engine = TunerEngine(YinPitchDetector(rate, windowSize), rate, highPassCutoffHz = 60.0, hopSamples = 1024)
        val samples = FloatArray(windowSize)
        println("WAV_REPLAY rate=$rate samples=$count seconds=${"%.2f".format(java.util.Locale.US, count.toDouble()/rate)} frames=${1+(count-windowSize)/hop} target_hz=$targetHz")
        println("frame,time_ms,status,stable_note,stable_hz,yin_hz,yin_candidates,mpm_hz,clarity,input_dbfs")
        var frameIndex = 0
        var start = 0
        var targetFrames = 0
        var d3Frames = 0
        var g1Frames = 0
        while (start + windowSize <= pcm.size) {
            for (i in 0 until windowSize) samples[i] = pcm[start + i] / 32768f
            val timeMs = start * 1000L / rate
            val state = engine.analyze(samples, timeMs, targetHz)
            if (timeMs in 6900L..7700L) {
                targetFrames++
                val stableHz = state.frequencyHz
                if (state.status in setOf(org.anarkey.core.tuner.SignalStatus.STABLE, org.anarkey.core.tuner.SignalStatus.HOLDING) && stableHz != null) {
                    if (kotlin.math.abs(PitchMath.cents(stableHz, 146.8324)) <= 30.0) d3Frames++
                    if (kotlin.math.abs(PitchMath.cents(stableHz, 48.0)) <= 100.0) g1Frames++
                }
            }
            if (frameIndex % 12 == 0 || frameIndex == 0 || timeMs in 6900L..7700L) {
                val y = yin.detect(samples)
                val m = mpm.detect(samples)
                val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
                val note = state.note?.let { "${noteNames[it.pitchClass]}${it.octave}" } ?: "none"
                fun fmt(value: Double?) = value?.let { "%.2f".format(java.util.Locale.US, it) } ?: "none"
                val candidates = y.candidates.joinToString("|") { "${fmt(it.frequencyHz)}:${"%.2f".format(java.util.Locale.US, it.confidence)}" }
                println("$frameIndex,$timeMs,${state.status},$note,${fmt(state.frequencyHz)},${fmt(y.frequencyHz)},$candidates,${fmt(m.frequencyHz)},${"%.3f".format(java.util.Locale.US, m.confidence)},${"%.1f".format(java.util.Locale.US, state.inputDbfs)}")
            }
            start += hop
            frameIndex++
        }
        if (File(path).name == "anarkey-last-audio.wav") {
            assertTrue("Capture is too short to include the known D3 passage", targetFrames >= 20)
            assertTrue("Expected the D3 passage to remain stable in most windows ($d3Frames/$targetFrames)", d3Frames >= targetFrames * 9 / 10)
            assertEquals("D3 passage must not stabilize as G1", 0, g1Frames)

            val coldEngine = TunerEngine(YinPitchDetector(rate, windowSize), rate, highPassCutoffHz = 60.0, hopSamples = 1024)
            var coldD3 = 0
            var coldG1 = 0
            var coldTargetFrames = 0
            start = rate * 6 / 1
            while (start + windowSize <= rate * 77 / 10) {
                for (i in 0 until windowSize) samples[i] = pcm[start + i] / 32768f
                val coldState = coldEngine.analyze(samples, (start - rate * 6 / 1) * 1000L / rate)
                val hz = coldState.frequencyHz
                val relativeMs = (start - rate * 6 / 1) * 1000L / rate
                if (relativeMs in 900L..1700L) {
                    coldTargetFrames++
                    if (coldState.status in setOf(org.anarkey.core.tuner.SignalStatus.STABLE, org.anarkey.core.tuner.SignalStatus.HOLDING) && hz != null) {
                        if (kotlin.math.abs(PitchMath.cents(hz, 146.8324)) <= 30.0) coldD3++
                        if (kotlin.math.abs(PitchMath.cents(hz, 48.0)) <= 100.0) coldG1++
                    }
                }
                start += hop
            }
            assertTrue("Cold replay should contain the sustained target passage", coldTargetFrames >= 20)
            assertTrue("Cold acquisition should resolve D3 across the sustained part ($coldD3/$coldTargetFrames)", coldD3 >= coldTargetFrames * 9 / 10)
            assertEquals("Cold acquisition must not stabilize as G1", 0, coldG1)
        }
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
    private fun u16(bytes: ByteArray, at: Int) = (bytes[at].toInt() and 0xff) or ((bytes[at+1].toInt() and 0xff) shl 8)
    private fun i32(bytes: ByteArray, at: Int) = u16(bytes, at) or (u16(bytes, at+2) shl 16)
}
