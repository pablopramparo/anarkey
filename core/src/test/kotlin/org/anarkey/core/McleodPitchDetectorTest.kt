package org.anarkey.core

import org.anarkey.core.music.PitchMath
import org.anarkey.core.pitch.McleodPitchDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class McleodPitchDetectorTest {
    @Test fun tracksGuitarRangeWithCleanAndBalancedHarmonicSignals() {
        val detector = McleodPitchDetector(48000)
        for (hz in listOf(82.4069, 110.0, 146.8324, 195.9977, 246.9417, 329.6276)) {
            for (harmonics in listOf(doubleArrayOf(1.0), doubleArrayOf(1.0, 0.7, 0.3))) {
                val result = detector.detect(SyntheticAudio.signal(hz, 48000, 0.4, harmonics, phase = 0.7))
                assertNotNull("No MPM result for $hz Hz", result.frequencyHz)
                assertEquals("Octave/frequency error at $hz Hz", 0.0, PitchMath.cents(result.frequencyHz!!, hz), 5.0)
            }
        }
    }
}
