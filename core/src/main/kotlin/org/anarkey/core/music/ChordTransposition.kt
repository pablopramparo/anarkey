package org.anarkey.core.music

/** Transpose written shapes and sounding key by the same interval, keeping capo fixed. */
object ChordTransposition {
    fun transpose(symbol: String, semitones: Int, flats: Boolean = false): String {
        if (semitones.mod(12) == 0) return symbol
        (SongMarks.parse(symbol) as? SongMark.Note)?.let { return SongMarks.transposeNote(it, semitones, flats) }
        val chord = ChordSymbolParser.parse(symbol)
        if (!chord.interpretable) return symbol
        fun moved(letter: String?, accidental: Int?): String {
            val note = requireNotNull(SpelledNote.parse(letter, accidental))
            return SpelledNote.fromMidi(note.pitchClass + semitones.mod(12), flats).name()
        }
        return moved(chord.rootLetter, chord.rootAccidental) +
            (if (chord.quality == "major") "" else chord.quality.orEmpty()) + chord.extension.orEmpty() +
            (chord.bassLetter?.let { "/" + moved(it, chord.bassAccidental) } ?: "")
    }
}
