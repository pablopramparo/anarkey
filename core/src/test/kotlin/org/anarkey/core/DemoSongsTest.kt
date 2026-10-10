package org.anarkey.core

import org.anarkey.core.music.*
import org.junit.Assert.*
import org.junit.Test

class DemoSongsTest {
    @Test fun chordProSeparatesLyricsFromAnchors() {
        val line = ChordPro.parse("[Am]Alas, my [C]love")
        assertEquals("Alas, my love", line.text)
        assertEquals(listOf(ChordPro.Anchor(0, "Am"), ChordPro.Anchor(9, "C")), line.chords)
        assertEquals(ChordPro.Line("plain", emptyList()), ChordPro.parse("plain"))
    }

    @Test fun everyDemoHasValidMetadataAndOnlyKnownChords() {
        assertEquals(DemoSongs.all.size, DemoSongs.all.map { it.title }.toSet().size)
        DemoSongs.all.forEach { song ->
            assertNull(song.title, SongMetadata(song.bpm, song.numerator, song.denominator, 0, "guitar", "guitar.standard").validationError())
            assertTrue(song.title, song.keyMode in setOf("major", "minor"))
            val lines = song.sections.flatMap { it.lines }.map(ChordPro::parse)
            assertTrue(song.title, lines.any { it.chords.isNotEmpty() })
            lines.flatMap { it.chords }.forEach { anchor ->
                assertNotNull("${song.title}: ${anchor.symbol}", ChordTheory.resolve(anchor.symbol))
                assertNotNull("${song.title}: ${anchor.symbol}", ChordSounding.of(anchor.symbol, "guitar", "guitar.standard", 0))
            }
        }
    }
}
