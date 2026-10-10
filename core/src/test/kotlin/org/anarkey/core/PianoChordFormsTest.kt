package org.anarkey.core

import org.anarkey.core.music.*
import org.junit.Assert.*
import org.junit.Test

class PianoChordFormsTest {
    @Test fun triadHasRootPositionAndTwoInversionsWithOnlyChordTones() {
        val forms = PianoChordForms.forChord("C")
        assertEquals(listOf(listOf(48, 52, 55), listOf(52, 55, 60), listOf(55, 60, 64)), forms.map { it.midi })
        assertEquals(listOf(0, 1, 2), forms.map { it.inversion })
    }

    @Test fun everyFormIsAscendingAndKeepsEveryChordPitchClass() {
        listOf("F#m7b5", "Bbmaj7", "D9", "G13", "Esus4", "A5").forEach { symbol ->
            val expected = ChordTheory.resolve(symbol)!!.pitchClasses
            PianoChordForms.forChord(symbol).forEach { form ->
                assertEquals(form.midi.sorted(), form.midi)
                assertEquals("$symbol ${form.inversion}", expected, form.midi.map { it.mod(12) }.toSet())
            }
        }
    }

    @Test fun slashBassIsTheLowestKey() {
        PianoChordForms.forChord("C/E").forEach { form ->
            assertEquals(4, form.midi.first().mod(12))
            assertTrue(form.midi.first() < form.midi[1])
        }
    }

    @Test fun unknownSymbolHasNoForms() {
        assertTrue(PianoChordForms.forChord("H#").isEmpty())
    }
}
