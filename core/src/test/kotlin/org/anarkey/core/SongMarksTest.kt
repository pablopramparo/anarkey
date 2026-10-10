package org.anarkey.core

import org.anarkey.core.music.*
import org.junit.Assert.*
import org.junit.Test

class SongMarksTest {
    @Test fun parsesChordsNotesAndRests() {
        assertEquals(SongMark.Chord("C7"), SongMarks.parse("C7"))
        assertEquals(SongMark.Rest, SongMarks.parse("_"))
        val note = SongMarks.parse("♪C#3") as SongMark.Note
        assertEquals(3, note.octave)
        assertEquals(49, note.midi)
        assertEquals(60, (SongMarks.parse("♪Do") as SongMark.Note).midi) // octave defaults to 4
        assertEquals(69, (SongMarks.parse("♪La4") as SongMark.Note).midi)
        assertEquals(SongMark.Chord("♪H4"), SongMarks.parse("♪H4"))
    }

    @Test fun displayFollowsTheChosenNaming() {
        assertEquals("La4", SongMarks.display("♪A4", NoteNaming.SOLFEGE_SHARPS))
        assertEquals("Bb3", SongMarks.display("♪A#3", NoteNaming.LETTERS_FLATS))
        assertEquals("Dom7".replace("Dom", "Do").replace("7", "7"), SongMarks.display("C7", NoteNaming.SOLFEGE_SHARPS))
        assertEquals("", SongMarks.display("_", NoteNaming.LETTERS_SHARPS))
    }

    @Test fun notesTransposeAcrossOctavesAndKeepRests() {
        assertEquals("♪C5", ChordTransposition.transpose("♪B4", 1))
        assertEquals("♪A3", ChordTransposition.transpose("♪C4", -3))
        assertEquals("♪Bb4", ChordTransposition.transpose("♪A4", 1, flats = true))
        assertEquals("_", ChordTransposition.transpose("_", 5))
        assertEquals("♪C8", ChordTransposition.transpose("♪C8", 12)) // out of range stays as written
    }

    @Test fun durationsRoundTrip() {
        assertEquals(NoteDuration(NoteFigure.EIGHTH, dotted = true), NoteDuration.parse("1/8."))
        assertEquals("1/2", NoteDuration(NoteFigure.HALF).code)
        assertNull(NoteDuration.parse(null))
        assertNull(NoteDuration.parse("3/7"))
    }
}
