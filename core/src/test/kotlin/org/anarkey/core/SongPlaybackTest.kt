package org.anarkey.core

import org.anarkey.core.music.*
import org.junit.Assert.*
import org.junit.Test

class SongPlaybackTest {
    private fun line(id: String, text: String, vararg chords: Pair<Int, String>) =
        PlanLine(id, text, chords.mapIndexed { i, (position, symbol) -> PlanChord("$id-$i", position, 0, symbol) })

    @Test fun chordsRingUntilTheNextOneOrTheEndOfTheLine() {
        val steps = SongPlaybackPlan.steps(listOf(
            line("a", "Amazing grace", 0 to "G", 8 to "C"),
            line("b", "no chords here"),
            line("c", "", 0 to "Am"),
        ))
        assertEquals(listOf("G", "C", "Am"), steps.map { it.symbol })
        assertEquals(0 to 8, steps[0].spanStart to steps[0].spanEnd)
        assertEquals(8 to 13, steps[1].spanStart to steps[1].spanEnd)
        assertEquals(0 to 0, steps[2].spanStart to steps[2].spanEnd)
    }

    @Test fun chordsAtTheSamePositionShareTheLyricAfterThem() {
        val steps = SongPlaybackPlan.steps(listOf(PlanLine("a", "hello", listOf(
            PlanChord("1", 0, 0, "C"), PlanChord("2", 0, 1, "G"), PlanChord("3", 3, 0, "Am")))))
        assertEquals(listOf(3, 3, 5), steps.map { it.spanEnd })
    }

    @Test fun barLengthFollowsTempoAndMeter() {
        assertEquals(2.0, SongPlaybackPlan.barSeconds(120, 4, 4), 1e-9)
        assertEquals(3.0, SongPlaybackPlan.barSeconds(60, 3, 4), 1e-9)
        assertEquals(2.0, SongPlaybackPlan.barSeconds(60, 6, 8), 1e-9) // dotted-quarter beats
        assertEquals(2.4, SongPlaybackPlan.barSeconds(null, null, null), 1e-9)
    }

    @Test fun soundingUsesGuitarShapesWithCapoAndFallsBackToKeyboard() {
        val guitar = ChordSounding.of("C", "guitar", "guitar.standard", 2)!!
        assertEquals(5, guitar.midi.size)
        assertTrue(guitar.midi.all { it.mod(12) in setOf(2, 6, 9) }) // C shape sounds as D with capo 2
        val keyboard = ChordSounding.of("C", null, null, 0)!!
        assertEquals(listOf(48, 52, 55), keyboard.midi)
        assertNull(ChordSounding.of("H#", "guitar", "guitar.standard", 0))
    }

    @Test fun pianoIgnoresTheCapo() {
        assertEquals(listOf(48, 52, 55), ChordSounding.of("C", "piano", null, 5)!!.midi)
    }

    @Test fun markValuesSetTheirLength() {
        assertEquals(0.5, SongPlaybackPlan.stepSeconds("1/4", 120, 4, 4), 1e-9)
        assertEquals(1.5, SongPlaybackPlan.stepSeconds("1/4.", 60, 4, 4) , 1e-9)
        assertEquals(2.0, SongPlaybackPlan.stepSeconds("1/2", 60, 4, 4), 1e-9)
        assertEquals(1.0, SongPlaybackPlan.stepSeconds("1/4.", 60, 6, 8), 1e-9) // dotted quarter = one beat in 6/8
        assertEquals(SongPlaybackPlan.barSeconds(100, 3, 4), SongPlaybackPlan.stepSeconds(null, 100, 3, 4), 1e-9)
    }

    @Test fun notesSoundAtTheirPitchAndRestsAreSilent() {
        assertEquals(listOf(69), ChordSounding.of("♪A4", "guitar", "guitar.standard", 3)!!.midi)
        assertNull(ChordSounding.of(SongMarks.REST, "guitar", "guitar.standard", 0))
    }
}
