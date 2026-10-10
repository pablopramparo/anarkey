package org.anarkey.app.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import org.anarkey.core.music.*

private val Context.tunerDataStore by preferencesDataStore(name = "tuner_preferences")

/** One DataStore per application; only preferences, no musical user database. */
class PreferencesRepository(context: Context) {
    private val store = context.applicationContext.tunerDataStore
    val preferences = store.data.map { values ->
        val selection = when (val instrument = values[INSTRUMENT]) {
            null -> TunerSelection.Default
            "chromatic" -> TunerSelection.Chromatic
            else -> runCatching { TunerSelection(instrument, values[TUNING]) }.getOrDefault(TunerSelection.Default)
        }
        val a4 = values[A4]?.takeIf { it.isFinite() && it in 400.0..480.0 } ?: 440.0
        val naming = NoteNaming.entries.firstOrNull { it.name == values[NAMING] } ?: NoteNaming.LETTERS_SHARPS
        val chordMode = ChordPresentationMode.entries.firstOrNull { it.name == values[CHORD_MODE] } ?: ChordPresentationMode.BEGINNER
        // Stored apart from the tuner's choice; "theory" is the explicit no-instrument option.
        val chordInstrument = when (val stored = values[CHORD_INSTRUMENT]) {
            null -> ChordInstrumentChoice()
            "theory" -> ChordInstrumentChoice(null, null)
            else -> ChordInstrumentChoice(stored, values[CHORD_TUNING])
        }
        TunerPreferences(TunerConfiguration(selection, a4), naming, chordMode, chordInstrument)
    }
    suspend fun setSelection(selection: TunerSelection) {
        val tuningId = selection.tuningId
        store.edit {
            it[INSTRUMENT] = selection.instrumentId ?: "chromatic"
            if (tuningId == null) it.remove(TUNING) else it[TUNING] = tuningId
        }
    }
    suspend fun setA4(value: Double) {
        require(value.isFinite() && value in 400.0..480.0)
        store.edit { it[A4] = value }
    }
    suspend fun setNaming(value: NoteNaming) { store.edit { it[NAMING] = value.name } }
    suspend fun setChordPresentationMode(value: ChordPresentationMode) { store.edit { it[CHORD_MODE] = value.name } }
    suspend fun setChordInstrument(choice: ChordInstrumentChoice) {
        val tuningId = choice.tuningId
        store.edit {
            it[CHORD_INSTRUMENT] = choice.instrumentId ?: "theory"
            if (tuningId == null) it.remove(CHORD_TUNING) else it[CHORD_TUNING] = tuningId
        }
    }
    private companion object {
        val INSTRUMENT = stringPreferencesKey("instrument")
        val TUNING = stringPreferencesKey("tuning")
        val A4 = doublePreferencesKey("a4_hz")
        val NAMING = stringPreferencesKey("note_naming")
        val CHORD_MODE = stringPreferencesKey("chord_presentation_mode")
        val CHORD_INSTRUMENT = stringPreferencesKey("chord_instrument")
        val CHORD_TUNING = stringPreferencesKey("chord_tuning")
    }
}
