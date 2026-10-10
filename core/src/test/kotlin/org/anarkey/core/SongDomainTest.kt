package org.anarkey.core

import org.anarkey.core.music.ChordAnchors
import org.anarkey.core.music.ChordSymbolParser
import org.anarkey.core.music.SongMetadata
import org.junit.Assert.*
import org.junit.Test

class SongDomainTest {
    @Test fun chordParserPreservesSpellingSlashBassAndUnknownSymbols() {
        val parsed = ChordSymbolParser.parse("F#m7/C#")
        assertEquals("F", parsed.rootLetter); assertEquals(1, parsed.rootAccidental)
        assertEquals("m", parsed.quality); assertEquals("7", parsed.extension)
        assertEquals("C", parsed.bassLetter); assertEquals(1, parsed.bassAccidental)
        assertTrue(ChordSymbolParser.parse("Cmaj7").interpretable)
        assertTrue(ChordSymbolParser.parse("Dsus4").interpretable)
        assertTrue(ChordSymbolParser.parse("A/G#").interpretable)
        val unknown = ChordSymbolParser.parse("Cfoo/G")
        assertFalse(unknown.interpretable); assertEquals("Cfoo/G", unknown.original)
    }

    @Test fun anchorsUseCodePointsAndRetainOrRemapOnReplacement() {
        val old = "A😀bc"
        assertEquals(4, ChordAnchors.codePointCount(old))
        assertEquals(2, ChordAnchors.remap(2, old, "A😀Xbc"))
        assertEquals(4, ChordAnchors.remap(3, old, "A😀Xbc"))
    }

    @Test fun validatesOnlyProvidedMetadataAndCatalogPair() {
        assertNull(SongMetadata().validationError())
        assertNull(SongMetadata(bpm = 60, timeNumerator = 6, timeDenominator = 8, instrumentId = "guitar", tuningId = "guitar.drop_d", capo = 4).validationError())
        assertEquals("bpm_range", SongMetadata(bpm = 401).validationError())
        assertEquals("instrument_tuning_unknown", SongMetadata(instrumentId = "guitar", tuningId = "violin.standard").validationError())
        assertEquals("instrument_tuning_pair", SongMetadata(instrumentId = "guitar").validationError())
    }

    @Test fun pianoIsAnInstrumentWithoutTuning() {
        assertNull(SongMetadata(instrumentId = "piano").validationError())
        assertEquals("instrument_tuning_unknown", SongMetadata(instrumentId = "piano", tuningId = "guitar.standard").validationError())
    }
}
