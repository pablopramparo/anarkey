package org.anarkey.core

import org.anarkey.core.music.*
import org.junit.Assert.*
import org.junit.Test

class TuningCatalogTest {
    @Test fun catalogHasUniqueIdsAndCompatiblePhysicalStrings() {
        assertEquals(TuningCatalog.instruments.size, TuningCatalog.instruments.map { it.id }.toSet().size)
        assertEquals(TuningCatalog.tunings.size, TuningCatalog.tunings.map { it.id }.toSet().size)
        for (instrument in TuningCatalog.instruments) {
            val tunings = TuningCatalog.forInstrument(instrument.id)
            assertTrue(tunings.isNotEmpty())
            for (tuning in tunings) {
                assertEquals(instrument.stringCount, tuning.strings.size)
                assertEquals((1..instrument.stringCount).toSet(), tuning.strings.map { it.number }.toSet())
                for (string in tuning.strings) {
                    // Bass reaches E1 (~41 Hz); the pitch detectors accept down to 35 Hz.
                    assertTrue(PitchMath.frequency(Note(string.midi), 400.0) > 35.0)
                    assertTrue(PitchMath.frequency(Note(string.midi), 480.0) < 1000.0)
                }
            }
        }
    }

    @Test fun dropDChangesOnlyTheSixthString() {
        val standard = TuningCatalog.tuning("guitar.standard")!!
        val drop = TuningCatalog.tuning("guitar.drop_d")!!
        assertEquals(OpenString(6, 38), drop.strings.first())
        assertEquals(standard.strings.drop(1), drop.strings.drop(1))
    }

    @Test fun reentrantTuningPreservesPhysicalOrderAndUsesReferencePitch() {
        val tuning = TuningCatalog.tuning("ukulele.high_g")!!
        assertTrue(tuning.strings[0].midi > tuning.strings[1].midi)
        for ((index, string) in tuning.strings.withIndex()) {
            assertEquals(index, tuning.nearestStringIndex(PitchMath.frequency(Note(string.midi), 480.0), 480.0))
        }
        assertNull(tuning.nearestStringIndex(80.0, 440.0))
    }

    @Test fun chromaticHasNoImplicitInstrumentOrStrings() {
        assertNull(TunerSelection.Chromatic.instrumentId)
        assertNull(TunerSelection.Chromatic.tuning)
    }

    @Test fun rejectsIncompatibleOrMissingReferences() {
        for ((instrument, tuning) in listOf("ukulele" to "guitar.standard", "missing" to "guitar.standard", "guitar" to "missing", "guitar" to null, null to "guitar.standard")) {
            assertTrue(runCatching { TunerSelection(instrument, tuning) }.isFailure)
        }
    }

    @Test fun validatesReferenceBeforePersistingOrNavigating() {
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, 399.9, 480.1, 0.0)) {
            assertTrue(runCatching { TunerConfiguration(a4Hz = invalid) }.isFailure)
        }
        for (valid in listOf(400.0, 432.0, 440.0, 442.5, 480.0)) assertEquals(valid, TunerConfiguration(a4Hz = valid).a4Hz, 0.0)
    }
}
