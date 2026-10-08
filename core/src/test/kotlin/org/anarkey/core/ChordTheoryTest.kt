package org.anarkey.core

import org.anarkey.core.music.*
import org.junit.Assert.*
import org.junit.Test

class ChordTheoryTest {
    @Test fun formulasCoverRequestedQualitiesAndSlashBass() {
        val cases = mapOf(
            "C" to listOf("C", "E", "G"), "Cm" to listOf("C", "Eb", "G"),
            "Cdim" to listOf("C", "Eb", "Gb"), "Caug" to listOf("C", "E", "G#"),
            "Csus2" to listOf("C", "D", "G"), "Csus4" to listOf("C", "F", "G"),
            "C7" to listOf("C", "E", "G", "Bb"), "Cmaj7" to listOf("C", "E", "G", "B"),
            "Cm7" to listOf("C", "Eb", "G", "Bb"), "Cm7b5" to listOf("C", "Eb", "Gb", "Bb"),
            "Cdim7" to listOf("C", "Eb", "Gb", "Bbb"), "C6" to listOf("C", "E", "G", "A"),
            "Cadd9" to listOf("C", "E", "G", "D"), "C/E" to listOf("C", "E", "G"),
        )
        cases.forEach { (symbol, notes) -> assertEquals(symbol, notes, ChordTheory.resolve(symbol)?.tones?.map { it.note.name() }) }
        assertEquals("E", ChordTheory.resolve("C/E")?.bass?.name())
        assertEquals("F#", ChordTheory.resolve("C/F#")?.bass?.name())
    }

    @Test fun parserIsSharedWithSongAndKeepsUnknownSymbols() {
        assertEquals("7b5", ChordSymbolParser.parse("F#m7b5/C#").extension)
        assertEquals("C#", ChordTheory.resolve("F#m7b5/C#")?.bass?.name())
        assertNull(ChordTheory.resolve("Cfoo"))
        assertEquals("F#", ChordTheory.resolve("F#")?.root?.name())
        assertEquals("Eb", ChordTheory.resolve("Eb")?.root?.name())
    }

    @Test fun sixthAddedNinthAndExtendedDominantFormulasAreDefined() {
        val cases = mapOf(
            "Cm6" to listOf("C", "Eb", "G", "A"),
            "Cmadd9" to listOf("C", "Eb", "G", "D"),
            "C5" to listOf("C", "G"),
            "C9" to listOf("C", "E", "G", "Bb", "D"),
            "C11" to listOf("C", "E", "G", "Bb", "D", "F"),
            "C13" to listOf("C", "E", "G", "Bb", "D", "F", "A"),
        )
        cases.forEach { (symbol, notes) -> assertEquals(symbol, notes, ChordTheory.resolve(symbol)?.tones?.map { it.note.name() }) }
    }

    @Test fun latinAndAngloSymbolsShareCanonicalTheoryAndFormatting() {
        val pairs = mapOf("Do" to "C", "Rem" to "Dm", "Fa#7" to "F#7", "Sibmaj7" to "Bbmaj7", "Lam/Do" to "Am/C")
        pairs.forEach { (latin, anglo) ->
            assertEquals(ChordTheory.resolve(anglo)?.pitchClasses, ChordTheory.resolve(latin)?.pitchClasses)
            assertEquals(ChordTheory.resolve(anglo)?.pitchClasses, ChordTheory.resolve(ChordSymbolFormatter.format(latin, NoteNaming.LETTERS_SHARPS))?.pitchClasses)
            assertEquals(ChordTheory.resolve(latin)?.pitchClasses, ChordTheory.resolve(ChordSymbolFormatter.format(anglo, NoteNaming.SOLFEGE_SHARPS))?.pitchClasses)
        }
        assertEquals("Sibmaj7", ChordSymbolFormatter.format("Bbmaj7", NoteNaming.SOLFEGE_FLATS))
        assertEquals("A#maj7", ChordSymbolFormatter.format("Bbmaj7", NoteNaming.LETTERS_SHARPS))
        assertEquals("Db", ChordSymbolFormatter.format("C#", NoteNaming.LETTERS_FLATS))
        assertEquals("Cfoo/G", ChordSymbolFormatter.format("Cfoo/G", NoteNaming.SOLFEGE_FLATS))
        assertFalse(ChordSymbolParser.parse("Domic").interpretable)
        assertTrue(ChordVoicingCatalog.forChord("Do", "guitar", "guitar.standard").isNotEmpty())
        assertEquals(ChordVoicingCatalog.forChord("C", "guitar", "guitar.standard").map { it.id },
            ChordVoicingCatalog.forChord("Do", "guitar", "guitar.standard").map { it.id })
    }

    @Test fun beginnerOrderingOnlyChangesPresentationAndKeepsEveryShape() {
        val forms = ChordVoicingCatalog.forChord("C", "guitar", "guitar.standard")
        val ordered = ChordVoicingCatalog.prioritizeForBeginner(forms)
        assertEquals(forms.map { it.id }.toSet(), ordered.map { it.id }.toSet())
        assertEquals(forms.size, ordered.size)
        assertFalse(ordered.first().barreFret != null)
        assertEquals(ordered, ChordVoicingCatalog.prioritizeForBeginner(forms))
        assertEquals(ChordPresentationMode.BEGINNER, TunerPreferences().chordPresentationMode)
    }

    @Test fun everyPackagedShapeValidatesFromItsRealTuning() {
        val failures = ChordVoicingCatalog.entries.map { it to ChordVoicingValidator.validate(it) }.filterNot { it.second.valid }
        assertTrue(failures.joinToString("\n") { "${it.first.id}: ${it.second.reasons}, notes=${it.second.producedMidi}" }, failures.isEmpty())
        assertTrue(ChordVoicingCatalog.forChord("C", "guitar", "guitar.standard").size >= 2)
        assertTrue(ChordVoicingCatalog.forChord("C", "guitar", "guitar.drop_d").isEmpty())
        assertEquals(listOf(67,60,64,69), TuningCatalog.tuning("ukulele.high_g")!!.strings.map { it.midi })
        assertEquals(listOf(55,60,64,69), TuningCatalog.tuning("ukulele.low_g")!!.strings.map { it.midi })
        assertTrue(ChordVoicingCatalog.forChord("C", "ukulele", "ukulele.low_g").isNotEmpty())
        assertTrue(ChordVoicingCatalog.forChord("C", "ukulele", "ukulele.high_g").isNotEmpty())
        assertTrue(ChordVoicingCatalog.forChord("C", "guitar", "guitar.dadgad").isNotEmpty())
    }

    @Test fun validatorRejectsWrongTuningAndOutOfRangeFret() {
        val good = ChordVoicingCatalog.find("gtr.c.open")!!
        val standardG = ChordVoicingCatalog.find("gtr.g.open")!!
        assertTrue(ChordVoicingValidator.validate(standardG.copy(tuningId = "guitar.drop_d")).reasons.contains("non_chord_tone"))
        assertTrue(ChordVoicingValidator.validate(good.copy(chordSymbol = "C/E")).reasons.contains("wrong_slash_bass"))
        assertTrue(ChordVoicingValidator.validate(good.copy(frets = listOf(null, 3, 2, 0, 1, 25))).reasons.contains("invalid_fret_5"))
        assertTrue(ChordVoicingValidator.validate(good.copy(frets = listOf(null, 3, 2, 0, 1))).reasons.contains("string_count_mismatch"))
    }
}
