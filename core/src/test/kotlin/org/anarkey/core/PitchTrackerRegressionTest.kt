package org.anarkey.core

import org.anarkey.core.music.Note
import org.anarkey.core.pitch.YinPitchDetector
import org.anarkey.core.tuner.TunerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class PitchTrackerRegressionTest {
    private var frameClock = 0

    @Test fun changesBothDirectionsWhenTheFundamentalEvidenceChanges() {
        val engine = TunerEngine(YinPitchDetector(RATE))
        var state = stream(engine, G1_HZ, guitarLikeG1())
        assertEquals(Note(G1_MIDI), state.note)

        state = stream(engine, D3_HZ, doubleArrayOf(1.0, 0.55, 0.3, 0.18))
        assertEquals("A confirmed higher root must replace its subharmonic", Note(D3_MIDI), state.note)

        state = stream(engine, G1_HZ, guitarLikeG1())
        assertEquals("A real lower fundamental must replace the D3 harmonic", Note(G1_MIDI), state.note)
    }

    @Test fun sweepExclusiveG1HarmonicEnergyAndReportAmbiguityBoundary() {
        for (exclusiveEnergy in listOf(0.20, 0.10, 0.05, 0.02)) {
            val engine = TunerEngine(YinPitchDetector(RATE))
            val harmonics = g1WithExclusiveEnergy(exclusiveEnergy)
            frameClock = 0
            val state = stream(engine, G1_HZ, harmonics, frames = 24)
            println("G1 harmonic sweep exclusive_energy=$exclusiveEnergy harmonics=${harmonics.joinToString { "%.3f".format(java.util.Locale.US, it) }} resolved=${state.note} hz=${state.frequencyHz}")
            if (exclusiveEnergy >= 0.10) assertEquals("Should retain G1 with $exclusiveEnergy exclusive energy", Note(G1_MIDI), state.note)
            if (exclusiveEnergy <= 0.05) assertEquals("Should resolve the third-harmonic root when exclusive energy is ambiguous at $exclusiveEnergy", Note(D3_MIDI), state.note)
        }
    }

    @Test fun mainsLikeLowFrequencyHumDoesNotTurnAnInitialD3IntoG1() {
        for (humAmplitude in listOf(0.02, 0.04, 0.06)) {
            val engine = TunerEngine(YinPitchDetector(RATE))
            var state = org.anarkey.core.tuner.TunerState()
            repeat(24) { frame ->
                val guitar = signal(D3_HZ, doubleArrayOf(1.0, 0.55, 0.3, 0.18), frame * 1024)
                val mixed = FloatArray(guitar.size) { i ->
                    guitar[i] + (humAmplitude * sin(2.0 * PI * 50.0 * (frame * 1024 + i) / RATE)).toFloat()
                }
                state = engine.analyze(mixed, frame * 1024_000L / RATE)
            }
            assertEquals("50 Hz hum amplitude=$humAmplitude", Note(D3_MIDI), state.note)
        }
    }

    @Test fun selectedGuitarStringKeepsItsFundamentalWhenYinAlsoFindsTheHalfPeriod() {
        val targetHz = 82.4069
        val engine = TunerEngine(YinPitchDetector(RATE), RATE, highPassCutoffHz = 60.0, hopSamples = 1024)
        var state = org.anarkey.core.tuner.TunerState()
        repeat(12) { frame ->
            val samples = SyntheticAudio.signal(
                79.8,
                RATE,
                0.35,
                doubleArrayOf(0.15, 1.0),
                phase = 0.37,
                offset = frame * 1024,
            )
            state = engine.analyze(samples, frame * 1024_000L / RATE, targetHz)
        }
        assertEquals("A selected E2 string should acquire despite its half-period candidate", org.anarkey.core.tuner.SignalStatus.STABLE, state.status)
        assertTrue("The selected target should keep the detected frequency near the E2 string", kotlin.math.abs(state.frequencyHz!! - 79.8) < 2.0)
    }

    private fun stream(engine: TunerEngine, hz: Double, harmonics: DoubleArray, frames: Int = 18): org.anarkey.core.tuner.TunerState {
        var state = org.anarkey.core.tuner.TunerState()
        repeat(frames) {
            val offset = frameClock * 1024
            state = engine.analyze(signal(hz, harmonics, offset), offset * 1000L / RATE)
            frameClock++
        }
        return state
    }

    private fun signal(hz: Double, harmonics: DoubleArray, offset: Int) =
        SyntheticAudio.signal(hz, RATE, 0.35, harmonics, phase = 0.37, offset = offset)

    private fun guitarLikeG1() = doubleArrayOf(1.0, 0.48, 0.28, 0.18, 0.12, 0.09, 0.06, 0.04, 0.03)

    private fun g1WithExclusiveEnergy(fraction: Double): DoubleArray {
        val exclusiveCount = 8.0
        val thirdMultipleCount = 4.0
        val exclusiveAmplitude = sqrt(fraction * thirdMultipleCount / (exclusiveCount * (1.0 - fraction)))
        return DoubleArray(12) { index -> if ((index + 1) % 3 == 0) 1.0 else exclusiveAmplitude }
    }

    private companion object {
        const val RATE = 48000
        const val G1_HZ = 49.0
        const val G1_MIDI = 31
        const val D3_HZ = 146.8324
        const val D3_MIDI = 50
    }
}
