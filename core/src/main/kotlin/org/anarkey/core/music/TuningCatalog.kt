package org.anarkey.core.music

import kotlin.math.abs

/** Physical string order, not pitch order (ukulele can be reentrant). */
data class OpenString(val number: Int, val midi: Int)
data class Instrument(val id: String, val stringCount: Int)
data class Tuning(val id: String, val instrumentId: String, val strings: List<OpenString>) {
    fun nearestStringIndex(hz: Double, a4Hz: Double): Int? {
        val midi = PitchMath.nearestNote(hz, a4Hz).midi
        val index = strings.indices.minByOrNull { abs(strings[it].midi - midi) } ?: return null
        return index.takeIf { abs(strings[it].midi - midi) <= 1 }
    }
}

/** Packaged reference data. No Android dependencies, custom tuning persistence or DSP policy. */
object TuningCatalog {
    val instruments = listOf(Instrument("guitar", 6), Instrument("ukulele", 4), Instrument("violin", 4),
        Instrument("bass", 4), Instrument("mandolin", 4), Instrument("banjo", 5))
    val tunings = listOf(
        Tuning("guitar.standard", "guitar", strings(40, 45, 50, 55, 59, 64)),
        Tuning("guitar.drop_d", "guitar", strings(38, 45, 50, 55, 59, 64)),
        Tuning("guitar.dadgad", "guitar", strings(38, 45, 50, 55, 57, 62)),
        Tuning("ukulele.high_g", "ukulele", strings(67, 60, 64, 69)),
        Tuning("ukulele.low_g", "ukulele", strings(55, 60, 64, 69)),
        Tuning("violin.standard", "violin", strings(55, 62, 69, 76)),
        Tuning("bass.standard", "bass", strings(28, 33, 38, 43)),
        Tuning("mandolin.standard", "mandolin", strings(55, 62, 69, 76)),
        // Open G: the short fifth string comes first because it is physically the highest-numbered one.
        Tuning("banjo.open_g", "banjo", strings(67, 50, 55, 59, 62)),
    )
    private fun strings(vararg midi: Int) = midi.mapIndexed { index, note -> OpenString(midi.size - index, note) }
    fun instrument(id: String) = instruments.singleOrNull { it.id == id }
    fun tuning(id: String) = tunings.singleOrNull { it.id == id }
    fun forInstrument(id: String) = tunings.filter { it.instrumentId == id }
}

data class TunerSelection(val instrumentId: String?, val tuningId: String?) {
    init {
        require((instrumentId == null) == (tuningId == null))
        require(instrumentId == null || (TuningCatalog.instrument(instrumentId) != null &&
            TuningCatalog.tuning(requireNotNull(tuningId))?.instrumentId == instrumentId))
    }
    val tuning: Tuning? get() = tuningId?.let(TuningCatalog::tuning)
    companion object {
        val Default = TunerSelection("guitar", "guitar.standard")
        val Chromatic = TunerSelection(null, null)
    }
}

data class TunerConfiguration(val selection: TunerSelection = TunerSelection.Default, val a4Hz: Double = 440.0) {
    init { require(a4Hz.isFinite() && a4Hz in 400.0..480.0) }
}

enum class NoteNaming { LETTERS_SHARPS, LETTERS_FLATS, SOLFEGE_SHARPS, SOLFEGE_FLATS }
enum class ChordPresentationMode { BEGINNER, ADVANCED }

/** Instrument shown by default in chord views; null is "theory only". Independent from the tuner's selection. */
data class ChordInstrumentChoice(val instrumentId: String? = "guitar", val tuningId: String? = "guitar.standard")

data class TunerPreferences(
    val configuration: TunerConfiguration = TunerConfiguration(),
    val noteNaming: NoteNaming = NoteNaming.LETTERS_SHARPS,
    val chordPresentationMode: ChordPresentationMode = ChordPresentationMode.BEGINNER,
    val chordInstrument: ChordInstrumentChoice = ChordInstrumentChoice(),
)
