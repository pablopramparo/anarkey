package org.anarkey.core

import org.anarkey.core.music.*
import org.anarkey.core.pitch.*
import org.anarkey.core.tuner.*
import org.junit.Assert.*
import org.junit.Test

class ReferencePitchTest {
    @Test fun calibratedPitchAffectsBothDetectedNoteAndCents() {
        for (reference in listOf(400.0, 432.0, 442.5, 480.0)) {
            val engine = TunerEngine(PitchDetector { PitchResult(reference, 0.99) }, a4Hz = reference)
            val input = FloatArray(4096) { 0.2f }
            repeat(3) { engine.analyze(input) }
            val state = engine.analyze(input)
            assertEquals(Note(69), state.note)
            assertEquals(0.0, state.cents!!, 1e-8)
            assertEquals(reference, state.frequencyHz!!, 1e-8)
        }
    }

    @Test fun noteBoundaryUsesConfiguredReferenceIncludingHysteresis() {
        val frequency = 440.0
        val engine = TunerEngine(PitchDetector { PitchResult(frequency, 0.99) }, a4Hz = 400.0)
        val input = FloatArray(4096) { 0.2f }
        repeat(3) { engine.analyze(input) }
        val state = engine.analyze(input)
        assertEquals(Note(71), state.note)
        assertEquals(PitchMath.cents(frequency, PitchMath.frequency(Note(71), 400.0)), state.cents!!, 1e-8)
    }

    @Test fun defaultAndExplicit440HaveIdenticalStateAcrossSignalChanges() {
        var pitch = PitchResult(110.0, 0.99)
        val default = TunerEngine(PitchDetector { pitch })
        val explicit = TunerEngine(PitchDetector { pitch }, a4Hz = 440.0)
        val input = FloatArray(4096) { 0.2f }
        var timestamp = 0L
        for (frequency in listOf(110.0, 110.4, 109.8, 220.0, 329.63)) {
            pitch = PitchResult(frequency, 0.99)
            repeat(8) {
                timestamp += 22
                assertEquals(default.analyze(input, timestamp), explicit.analyze(input, timestamp))
            }
        }
        repeat(10) {
            timestamp += 22
            assertEquals(default.analyze(FloatArray(4096), timestamp), explicit.analyze(FloatArray(4096), timestamp))
        }
    }
}
