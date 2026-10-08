package org.anarkey.app.navigation

import org.anarkey.core.music.TunerConfiguration
import org.anarkey.core.music.TunerSelection

/** Only stable catalogue IDs and a reference frequency cross the navigation boundary. */
object TunerRoute {
    const val Home = "tuner"
    const val Context = "tuner-context/{instrument}/{tuning}/{a4}"

    fun contextual(configuration: TunerConfiguration): String = with(configuration) {
        "tuner-context/${selection.instrumentId ?: "chromatic"}/${selection.tuningId ?: "chromatic"}/$a4Hz"
    }

    fun decode(instrument: String?, tuning: String?, a4: String?): Result<TunerConfiguration> = runCatching {
        require(instrument != null && tuning != null && a4 != null)
        val selection = if (instrument == "chromatic" && tuning == "chromatic") TunerSelection.Chromatic
            else TunerSelection(instrument, tuning)
        TunerConfiguration(selection, a4.toDouble())
    }
}
