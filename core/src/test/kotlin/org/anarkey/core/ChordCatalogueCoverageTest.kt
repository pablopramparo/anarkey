package org.anarkey.core

import org.anarkey.core.music.*
import org.junit.Assert.*
import org.junit.Test

class ChordCatalogueCoverageTest {
    @Test fun everyPickerQualityHasAllTwelveRootsInStandardGuitar() {
        val missing = mutableListOf<String>()
        ChordQuality.entries.forEach { quality ->
            (0..11).forEach { root ->
                val symbol = SpelledNote.fromMidi(root).name() + quality.suffix
                val forms = ChordVoicingCatalog.forChord(symbol, "guitar", "guitar.standard")
                if (forms.isEmpty()) missing += symbol
                forms.forEach { assertTrue("$symbol: ${it.id}", ChordVoicingValidator.validate(it.copy(chordSymbol = symbol)).valid) }
                for (naming in NoteNaming.entries) {
                    assertEquals(symbol, forms.map { it.id }, ChordVoicingCatalog.forChord(
                        ChordSymbolFormatter.format(symbol, naming), "guitar", "guitar.standard").map { it.id })
                }
            }
        }
        assertEquals("Missing picker entries", emptyList<String>(), missing)
        println("STANDARD_GUITAR_COVERAGE=${ChordQuality.entries.size * 12}/228; catalogue=${ChordVoicingCatalog.entries.size}")
    }

    @Test fun fDominantSeventhHasTheExpectedLowBarreAndExactNotes() {
        val forms = ChordVoicingCatalog.forChord("F7", "guitar", "guitar.standard")
        val low = forms.single { it.frets == listOf(1, 3, 1, 2, 1, 1) }
        assertEquals(1, low.barreFret)
        assertEquals(listOf(41, 48, 51, 57, 60, 65), ChordVoicingValidator.validate(low).producedMidi)
        assertTrue(forms.size >= 2)
        assertEquals(forms.map { it.id }, ChordVoicingCatalog.forChord("Fa7", "guitar", "guitar.standard").map { it.id })
        assertTrue(ChordVoicingCatalog.forChord("F7", "guitar", "guitar.drop_d").isEmpty())
        assertTrue(ChordVoicingCatalog.forChord("F7", "ukulele", "ukulele.high_g").isNotEmpty())
    }

    @Test fun shapesHaveCompleteFingerMapsAndPhysicallyConsistentBarres() {
        val entries = ChordVoicingCatalog.entries
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        entries.forEach { shape ->
            val fretted = shape.frets.indices.filter { (shape.frets[it] ?: 0) > 0 }
            val assigned = shape.fingers.map { it.stringIndex }.toSet()
            // Earlier shapes may document the barre with a single finger label.
            val barreStrings = shape.frets.indices.filter { shape.barreFret != null && shape.frets[it] == shape.barreFret }
            assertTrue("${shape.id}: missing finger", fretted.all { it in assigned || it in barreStrings })
            assertEquals("${shape.id}: duplicate string finger", shape.fingers.size, assigned.size)
            shape.fingers.groupBy { it.finger }.values.forEach { group ->
                if (group.size > 1) {
                    assertEquals("${shape.id}: finger on different frets", 1, group.map { it.fret }.toSet().size)
                    assertEquals("${shape.id}: repeated finger without barre", shape.barreFret, group.first().fret)
                }
            }
            if (barreStrings.size > 1) {
                (barreStrings.min()..barreStrings.max()).forEach { index ->
                    shape.frets[index]?.let { assertTrue("${shape.id}: barre blocks lower note", it >= shape.barreFret!!) }
                }
            }
            val pressed = shape.frets.filterNotNull().filter { it > 0 }
            // Fully open shapes (banjo open G) have nothing pressed.
            if (pressed.isNotEmpty()) {
                assertTrue("${shape.id}: excessive stretch", pressed.max() - pressed.min() <= 3)
                assertTrue("${shape.id}: out of diagram", pressed.max() - shape.baseFret < 5)
            }
        }
    }

    @Test fun everyShapeIsValidAgainstItsOwnTuning() {
        val invalid = ChordVoicingCatalog.entries.filter { !ChordVoicingValidator.validate(it).valid }
            .map { "${it.id}: ${ChordVoicingValidator.validate(it).reasons}" }
        assertEquals(emptyList<String>(), invalid)
    }

    @Test fun otherInstrumentsCoverTheCommonChordsWithKnownShapes() {
        fun frets(symbol: String, instrument: String, tuning: String) =
            ChordVoicingCatalog.forChord(symbol, instrument, tuning).map { it.frets }
        for (tuning in listOf("ukulele.high_g", "ukulele.low_g")) {
            assertTrue(listOf(0, 0, 0, 3) in frets("C", "ukulele", tuning))
            assertTrue(listOf(0, 2, 3, 2) in frets("G", "ukulele", tuning))
            assertTrue(listOf(2, 0, 1, 0) in frets("F", "ukulele", tuning))
            assertTrue(listOf(2, 0, 0, 0) in frets("Am", "ukulele", tuning))
            assertTrue(listOf(1, 0, 1, 3) in frets("Fm", "ukulele", tuning))
        }
        assertTrue(listOf(0, 0, 2, 3) in frets("G", "mandolin", "mandolin.standard"))
        assertTrue(listOf(2, 0, 0, 2) in frets("D", "mandolin", "mandolin.standard"))
        assertTrue(listOf(0, 0, 0, 0, 0) in frets("G", "banjo", "banjo.open_g"))
        assertTrue(listOf(0, 3, 2, 0) in frets("C", "bass", "bass.standard").map { it.drop(0).let { f -> listOf(f[0] ?: 0, f[1] ?: 0, f[2] ?: 0, f[3] ?: 0) } })
        for ((instrument, tuning) in listOf(
            "ukulele" to "ukulele.high_g", "ukulele" to "ukulele.low_g", "mandolin" to "mandolin.standard", "banjo" to "banjo.open_g",
        )) {
            val missing = ChordQuality.entries.flatMap { quality ->
                (0..11).map { SpelledNote.fromMidi(it).name() + quality.suffix }
            }.filter { ChordVoicingCatalog.forChord(it, instrument, tuning).isEmpty() }
            assertTrue("$tuning is missing too many chords: $missing", missing.size <= 3)
        }
        // Bass voicings need root in the bass and a chord third, so a few extended chords have no shape.
        val bassMissing = ChordQuality.entries.flatMap { quality ->
            (0..11).map { SpelledNote.fromMidi(it).name() + quality.suffix }
        }.filter { ChordVoicingCatalog.forChord(it, "bass", "bass.standard").isEmpty() }
        assertTrue("bass is missing too many chords: $bassMissing", bassMissing.size <= 16)
    }

    @Test fun slashBassIsFilteredAndOriginalFavoritesStillResolve() {
        val forms = ChordVoicingCatalog.forChord("C/E", "guitar", "guitar.standard")
        forms.forEach { assertEquals(4, ChordVoicingValidator.validate(it).producedMidi.min().mod(12)) }
        assertTrue(ChordVoicingCatalog.forChord("F7/B", "guitar", "guitar.standard").isEmpty())
        for (id in listOf("gtr.c.open", "gtr.c.barre", "gtr.f.barre", "uke.high.c", "uke.low.f")) {
            assertNotNull(id, ChordVoicingCatalog.find(id))
        }
        val c = ChordVoicingCatalog.prioritizeForBeginner(ChordVoicingCatalog.forChord("C", "guitar", "guitar.standard"))
        assertEquals("gtr.c.open", c.first().id)
        val e7 = ChordVoicingCatalog.prioritizeForBeginner(ChordVoicingCatalog.forChord("E7", "guitar", "guitar.standard"))
        assertEquals("gtr.e7.open", e7.first().id)
    }
}
