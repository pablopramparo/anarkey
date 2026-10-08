package org.anarkey.core

import org.anarkey.core.music.*
import org.junit.Assert.*
import org.junit.Test

class ChordTranspositionTest {
    @Test fun movesRootAndBassPreservingFormula() {
        assertEquals("G#m7/D#", ChordTransposition.transpose("F#m7/C#", 2))
        assertEquals("Bbm/Db", ChordTransposition.transpose("Lam/Do", 1, true))
        assertEquals("Bbmaj7/F", ChordTransposition.transpose("Cmaj7/G", -2, true))
    }
    @Test fun unknownAndZeroIntervalsPreserveExactText() {
        for (symbol in listOf("Cfoo/G", "N.C.", "", " F#m7/C# ", "Lam/Do")) {
            assertEquals(symbol, ChordTransposition.transpose(symbol, 0))
            assertEquals(symbol, ChordTransposition.transpose(symbol, 12))
        }
        assertEquals("Cfoo/G", ChordTransposition.transpose("Cfoo/G", 3))
    }
    @Test fun allIntervalsRoundTripPitchClassesAndPreserveQualities() {
        for (symbol in listOf("Cbmaj7/Gb", "B#dim7/Fb", "Rem/La", "F#sus4/C#", "Bb7b5/F")) {
            val original = ChordSymbolParser.parse(symbol)
            for (shift in -11..11) for (flats in listOf(false, true)) {
                val moved = ChordSymbolParser.parse(ChordTransposition.transpose(symbol, shift, flats))
                val back = ChordSymbolParser.parse(ChordTransposition.transpose(moved.original, -shift, flats))
                assertEquals(original.quality, moved.quality)
                assertEquals(original.extension, moved.extension)
                assertEquals(SpelledNote.parse(original.rootLetter, original.rootAccidental)!!.pitchClass,
                    SpelledNote.parse(back.rootLetter, back.rootAccidental)!!.pitchClass)
                assertEquals(SpelledNote.parse(original.bassLetter, original.bassAccidental)!!.pitchClass,
                    SpelledNote.parse(back.bassLetter, back.bassAccidental)!!.pitchClass)
            }
        }
    }
}
