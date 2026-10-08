package org.anarkey.app.navigation

import org.anarkey.core.music.*
import org.junit.Assert.*
import org.junit.Test

class TunerRouteTest {
    @Test fun everyCatalogContextRoundTripsWithoutUsingGlobalDefaults() {
        val configurations = TuningCatalog.tunings.map {
            TunerConfiguration(TunerSelection(it.instrumentId, it.id), 432.5)
        } + TunerConfiguration(TunerSelection.Chromatic, 480.0)
        for (configuration in configurations) {
            val parts = TunerRoute.contextual(configuration).split('/')
            assertEquals(configuration, TunerRoute.decode(parts[1], parts[2], parts[3]).getOrThrow())
        }
    }

    @Test fun invalidContextIsRejectedRatherThanOpeningStandardGuitar() {
        for ((instrument, tuning, reference) in listOf(
            Triple("guitar", "missing", "440"), Triple("ukulele", "guitar.standard", "440"),
            Triple("guitar", "guitar.standard", "NaN"), Triple("guitar", "guitar.standard", "900"),
            Triple(null, null, null), Triple("chromatic", "guitar.standard", "440"),
        )) assertTrue(TunerRoute.decode(instrument, tuning, reference).isFailure)
    }
}
