package org.anarkey.core

import org.anarkey.core.music.*
import org.anarkey.core.pitch.*
import org.anarkey.core.tuner.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class EngineTest {
    @Test fun pitchMathRoundTripsAndReference() {
        for (midi in 0..127) {
            val note = Note(midi)
            assertEquals(note, PitchMath.nearestNote(PitchMath.frequency(note)))
        }
        assertEquals(440.0, PitchMath.frequency(Note(69)), 1e-9)
        assertEquals(442.0, PitchMath.frequency(Note(69), 442.0), 1e-9)
        assertEquals(1200.0, PitchMath.cents(220.0, 110.0), 1e-9)
        assertEquals(-1200.0, PitchMath.cents(110.0, 220.0), 1e-9)
        assertEquals(4, Note(60).octave)
    }
    @Test fun logarithmicNoteBoundary() {
        assertEquals(Note(69), PitchMath.nearestNote(440.0 * 2.0.pow(49.9 / 1200)))
        assertEquals(Note(70), PitchMath.nearestNote(440.0 * 2.0.pow(50.1 / 1200)))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidFrequency() { PitchMath.nearestNote(0.0) }
    @Test fun inputLevelUsesDigitalFullScale() {
        assertEquals(-120.0, Levels.measure(FloatArray(100)).dbfs, 0.0)
        assertEquals(-6.0206, Levels.measure(FloatArray(100) { 0.5f }).dbfs, 0.0001)
        assertTrue(Levels.measure(FloatArray(100) { -1f }).clipped)
        assertFalse(Levels.measure(FloatArray(100) { 0.5f }).clipped)
    }
    @Test fun medianAndNoteChangeRequireCoherentEvidence() {
        val stabilizer = PitchStabilizer()
        assertNull(stabilizer.update(110.0))
        assertNull(stabilizer.update(110.3))
        assertEquals(110.0, stabilizer.update(109.8)!!, 0.0)
        assertNull(stabilizer.update(220.0))
        assertNull(stabilizer.update(220.0))
        assertEquals(220.0, stabilizer.update(220.0)!!, 0.0)
        assertNull(stabilizer.update(null))
        assertNull(stabilizer.update(220.0))
    }
    @Test fun confidenceDropSilenceAndClippingClearDisplayedPitch() {
        var result = PitchResult(110.0, 0.99)
        val engine = TunerEngine(PitchDetector { result })
        val input = FloatArray(4096) { 0.2f }
        repeat(2) { assertNull(engine.analyze(input).frequencyHz) }
        assertEquals(Note(45), engine.analyze(input).note)
        result = PitchResult(329.0, 0.3)
        assertEquals(SignalStatus.HOLDING, engine.analyze(input).status)
        assertEquals(Note(45), engine.analyze(input).note)
        result = PitchResult(110.0, 0.99)
        repeat(3) { engine.analyze(input) }
        assertEquals(SignalStatus.LISTENING, engine.analyze(FloatArray(4096), System.nanoTime() / 1_000_000 + 200).status)
        assertEquals(SignalStatus.CLIPPING, engine.analyze(FloatArray(4096) { 1f }).status)
        assertEquals(SignalStatus.WEAK, engine.analyze(FloatArray(4096) { 0.0001f }).status)
    }
    @Test fun noteBoundaryHysteresis() {
        var hz = 440.0 * 2.0.pow(49.0 / 1200)
        val engine = TunerEngine(PitchDetector { PitchResult(hz, 0.99) })
        val input = FloatArray(4096) { 0.2f }
        repeat(3) { engine.analyze(input) }
        hz = 440.0 * 2.0.pow(52.0 / 1200)
        repeat(3) { assertEquals(Note(69), engine.analyze(input).note) }
        hz = 440.0 * 2.0.pow(57.0 / 1200)
        repeat(3) { engine.analyze(input) }
        assertEquals(Note(70), engine.analyze(input).note)
    }
    @Test fun endToEndStreamingWithChangingPhaseAndSilence() {
        val engine = TunerEngine(YinPitchDetector(48000))
        for ((hz, midi) in listOf(82.4069 to 40, 110.0 to 45, 329.6276 to 64)) {
            var state = TunerState()
            repeat(6) { hop ->
                state = engine.analyze(SyntheticAudio.signal(hz, 48000, 0.4, doubleArrayOf(0.15, 1.0), offset = hop * 1024))
            }
            assertEquals(Note(midi), state.note)
            assertEquals(0.0, state.cents!!, 2.0)
            assertNull(engine.analyze(FloatArray(4096), System.nanoTime() / 1_000_000 + TunerEngine.HOLD_DURATION_MS + 1).note)
        }
    }
    @Test fun tuningThresholdBoundaries() {
        assertEquals(TuningDirection.IN_TUNE, TuningThresholds.direction(3.0))
        assertEquals(TuningDirection.IN_TUNE, TuningThresholds.direction(-3.0))
        assertEquals(TuningDirection.FLAT, TuningThresholds.direction(-3.01))
        assertEquals(TuningDirection.SHARP, TuningThresholds.direction(3.01))
    }
}
