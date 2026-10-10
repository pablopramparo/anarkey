package org.anarkey.core.music

/** Note values as in sheet music; a chord without a value lasts one bar. */
enum class NoteFigure(val code: String, val quarters: Double) {
    WHOLE("1/1", 4.0), HALF("1/2", 2.0), QUARTER("1/4", 1.0), EIGHTH("1/8", 0.5), SIXTEENTH("1/16", 0.25),
}

data class NoteDuration(val figure: NoteFigure, val dotted: Boolean = false) {
    val quarters: Double get() = figure.quarters * if (dotted) 1.5 else 1.0
    /** Stored form: the fraction, plus a trailing dot when dotted ("1/4", "1/8."). */
    val code: String get() = figure.code + if (dotted) "." else ""

    companion object {
        fun parse(code: String?): NoteDuration? {
            if (code.isNullOrEmpty()) return null
            val dotted = code.endsWith(".")
            val figure = NoteFigure.entries.firstOrNull { it.code == code.removeSuffix(".") } ?: return null
            return NoteDuration(figure, dotted)
        }
    }
}

/** What a mark on a lyric line means: a chord, a single pitched note, or a rest. */
sealed interface SongMark {
    data class Chord(val symbol: String) : SongMark
    data class Note(val note: SpelledNote, val octave: Int) : SongMark {
        /** Scientific pitch notation: C4 is MIDI 60. */
        val midi: Int get() = (octave + 1) * 12 + SpelledNote(note.letter).pitchClass + note.accidental
    }
    data object Rest : SongMark
}

/**
 * Marks share one stored string. Chords keep their symbol; a note is `♪` plus its name and octave (`♪La4`, `♪C#3`);
 * a rest is `_`. The prefix is needed because `C7` is already a chord, not "C in octave 7".
 */
object SongMarks {
    const val NOTE_PREFIX = "♪"
    const val REST = "_"
    const val DEFAULT_OCTAVE = 4
    private val notePattern = Regex("^♪(Sol|Do|Re|Mi|Fa|La|Si|[A-G])([#b]?)([0-8])?$")
    private val roots = mapOf("Do" to "C", "Re" to "D", "Mi" to "E", "Fa" to "F", "Sol" to "G", "La" to "A", "Si" to "B")

    fun parse(symbol: String): SongMark {
        val text = symbol.trim()
        if (text == REST) return SongMark.Rest
        val match = notePattern.matchEntire(text) ?: return SongMark.Chord(text)
        val letter = match.groupValues[1].let { roots[it] ?: it }
        val accidental = when (match.groupValues[2]) { "#" -> 1; "b" -> -1; else -> 0 }
        val octave = match.groupValues[3].ifEmpty { null }?.toInt() ?: DEFAULT_OCTAVE
        return SongMark.Note(SpelledNote(letter.single(), accidental), octave)
    }

    fun noteSymbol(note: SpelledNote, octave: Int): String = NOTE_PREFIX + note.name() + octave

    /** Text for the user's chosen note naming; rests have none. */
    fun display(symbol: String, naming: NoteNaming): String = when (val mark = parse(symbol)) {
        is SongMark.Chord -> ChordSymbolFormatter.format(mark.symbol, naming)
        is SongMark.Note -> ChordSymbolFormatter.format(mark.note, naming) + mark.octave
        SongMark.Rest -> ""
    }

    /** Moves a note by whole semitones across octaves; out-of-range results stay as written. */
    fun transposeNote(note: SongMark.Note, semitones: Int, flats: Boolean): String {
        val midi = note.midi + semitones
        val octave = Math.floorDiv(midi, 12) - 1
        if (octave !in 0..8) return noteSymbol(note.note, note.octave)
        return noteSymbol(SpelledNote.fromMidi(midi.mod(12), flats), octave)
    }
}
