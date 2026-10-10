package org.anarkey.core.exchange

import org.junit.Assert.*
import org.junit.Test

class JsonTest {
    @Test fun roundTripsNestedValuesAndEscapes() {
        val value = linkedMapOf<String, Any?>(
            "text" to "línea \"con\" comillas\n\ty 🎵",
            "list" to listOf(1L, 2.5, true, null, linkedMapOf("k" to "v")),
            "empty" to emptyMap<String, Any?>(),
        )
        assertEquals(value, Json.parse(Json.write(value)))
        assertEquals(value, Json.parse(Json.write(value, pretty = true)))
    }

    @Test fun readsUnicodeEscapesAndNumbers() {
        assertEquals("é🎵", Json.parse("\"\\u00e9\\ud83c\\udfb5\""))
        assertEquals(-12L, Json.parse("-12"))
        assertEquals(1.5e3, Json.parse("1.5e3"))
    }

    @Test fun rejectsMalformedInput() {
        listOf("{", "[1,]", "{\"a\" 1}", "\"abc", "tru", "[1] x", "{a:1}").forEach { bad ->
            assertThrows(bad, Json.ParseException::class.java) { Json.parse(bad) }
        }
        assertThrows(Json.ParseException::class.java) { Json.parse("[".repeat(200)) }
    }
}

class ChordProFormatTest {
    private val sample = """
        {title: Amazing Grace}
        {artist: Traditional}
        {key: Am}
        {tempo: 84}
        {time: 3/4}
        {capo: 2}
        {start_of_verse: Verso 1}
        [Am]Amazing [G7]grace! How [C]sweet
        That saved a wretch
        {end_of_verse}
        {start_of_chorus}
        [F]Chorus line
        {end_of_chorus}
    """.trimIndent()

    @Test fun readsMetadataSectionsAndChordPositions() {
        val song = ChordProFormat.parse(sample).songs.single().song
        assertEquals("Amazing Grace", song.title)
        assertEquals("Traditional", song.artist)
        assertEquals("A" to "minor", song.keyRoot to song.keyMode)
        assertEquals(84, song.bpm)
        assertEquals(3 to 4, song.timeNumerator!! to song.timeDenominator!!)
        assertEquals(2, song.capo)
        assertEquals(listOf("verse", "chorus"), song.sections.map { it.kind })
        assertEquals("Verso 1", song.sections[0].title)
        val first = song.sections[0].lines[0]
        assertEquals("Amazing grace! How sweet", first.text)
        assertEquals(listOf(ExchangeMark(0, "Am"), ExchangeMark(8, "G7"), ExchangeMark(19, "C")), first.marks)
        assertEquals("That saved a wretch", song.sections[0].lines[1].text)
    }

    @Test fun positionsCountCodePointsNotUtf16Units() {
        val line = ChordProFormat.parseLine("🎵 [C]ño").first
        assertEquals("🎵 ño", line.text)
        assertEquals(2, line.marks.single().position)
    }

    @Test fun reportsUnknownDirectivesWithoutFailing() {
        val result = ChordProFormat.parse("{title: X}\n{frobnicate: 3}\n{textfont: Arial}\n# comment\n[C]hello").songs.single()
        assertEquals(listOf("frobnicate"), result.notices.filter { it.code == "unknown_directive" }.map { it.detail })
        assertTrue(result.notices.any { it.code == "formatting_ignored" })
        assertEquals("hello", result.song.unsectionedLines.single().text)
    }

    @Test fun subtitleBecomesArtistAndExtrasGoToNotes() {
        val song = ChordProFormat.parse("{title: T}\n{subtitle: Someone}\n{composer: Bach}\n{comment_italic: ensayar lento}\nline").songs.single().song
        assertEquals("Someone", song.artist)
        assertTrue(song.notes.contains("composer: Bach"))
        assertTrue(song.notes.contains("ensayar lento"))
    }

    @Test fun commentOutsideAnEnvironmentOpensASection() {
        val song = ChordProFormat.parse("{title: T}\n{comment: Verse 1}\n[C]one\n\n{comment: Chorus}\n[G]two").songs.single().song
        assertEquals(listOf("verse", "chorus"), song.sections.map { it.kind })
        assertEquals(listOf("Verse 1", "Chorus"), song.sections.map { it.title })
    }

    @Test fun chorusDirectiveRepeatsTheLastChorus() {
        val song = ChordProFormat.parse("{title: T}\n{soc}\n[C]la la\n{eoc}\n{start_of_verse}\nverse\n{end_of_verse}\n{chorus}").songs.single().song
        assertEquals(listOf("chorus", "verse", "chorus"), song.sections.map { it.kind })
        assertEquals("la la", song.sections[2].lines.single().text)
    }

    @Test fun readsAnarkeyAnnotationsAndDropsOthers() {
        val result = ChordProFormat.parse("{title: T}\nHello [*♪A4:1/4]world [*_:1/8.]x [*cresc.]y [C]z").songs.single()
        val marks = result.song.unsectionedLines.single().marks
        assertEquals(listOf(ExchangeMark(6, "♪A4", "1/4"), ExchangeMark(12, "_", "1/8."), ExchangeMark(16, "C")), marks)
        assertEquals("1", result.notices.single { it.code == "annotations_dropped" }.detail)
    }

    @Test fun anUnclosedBracketIsJustText() {
        val line = ChordProFormat.parseLine("a [b c").first
        assertEquals("a [b c", line.text)
        assertTrue(line.marks.isEmpty())
    }

    @Test fun toleratesCrLfBomAndSeveralSongs() {
        val text = "﻿{title: One}\r\n[C]a\r\n{new_song}\r\n{title: Two}\r\n[G]b\r\n"
        val songs = ChordProFormat.parse(text).songs.map { it.song }
        assertEquals(listOf("One", "Two"), songs.map { it.title })
        assertEquals("a", songs[0].unsectionedLines.single().text)
    }

    @Test fun usesTheFallbackTitleAndRepairsInvalidValues() {
        val song = ChordProFormat.parse("{tempo: 900}\n{time: 3/7}\n[C]x", fallbackTitle = "mi-archivo").songs.single()
        assertEquals("mi-archivo", song.song.title)
        assertNull(song.song.bpm)
        assertNull(song.song.timeNumerator)
        assertTrue(song.notices.any { it.code == "bpm_invalid" })
        assertTrue(song.notices.any { it.code == "meter_invalid" })
    }

    @Test fun anEmptyFileHasNoSongs() {
        assertTrue(ChordProFormat.parse("").songs.isEmpty())
        assertTrue(ChordProFormat.parse("# only a comment\n\n").songs.isEmpty())
        val error = assertThrows(ExchangeException::class.java) { SongFiles.read("  \n", "x") }
        assertEquals("no_songs", error.code)
    }

    @Test fun tabsAreKeptAsPlainTextWithANotice() {
        val result = ChordProFormat.parse("{title: T}\n{sot}\ne|--0--[x]--|\n{eot}").songs.single()
        assertEquals("e|--0--[x]--|", result.song.sections.single().lines.single().text)
        assertTrue(result.notices.any { it.code == "tab_as_text" })
    }
}

class ChordProWriteTest {
    private val song = ExchangeSong(
        title = "Prueba", artist = "Yo", keyRoot = "F#", keyMode = "minor", bpm = 100, timeNumerator = 6, timeDenominator = 8, capo = 3,
        instrumentId = "guitar", tuningId = "guitar.standard", notes = "primera línea\nsegunda", tags = listOf("folk", "lento"),
        unsectionedLines = listOf(ExchangeLine("intro libre", listOf(ExchangeMark(0, "C")))),
        sections = listOf(
            ExchangeSection("verse", "Verso 1", lines = listOf(
                ExchangeLine("La casa 🎵 azul", listOf(ExchangeMark(0, "Am"), ExchangeMark(3, "♪La4", "1/4"), ExchangeMark(8, "_", "1/2."), ExchangeMark(14, "G7")))),
            ),
            ExchangeSection("custom", "Mi parte", lines = listOf(ExchangeLine("# parece comentario"))),
        ),
    )

    @Test fun writesStandardDirectivesAndInlineChords() {
        val written = ChordProFormat.write(song)
        assertTrue(written.text.contains("{title: Prueba}"))
        assertTrue(written.text.contains("{key: F#m}"))
        assertTrue(written.text.contains("{time: 6/8}"))
        assertTrue(written.text.contains("{start_of_verse: Verso 1}"))
        assertTrue(written.text.contains("[Am]La [*♪La4:1/4]casa"))
    }

    @Test fun warnsAboutWhatChordProCannotHold() {
        val withDuration = song.copy(sections = listOf(ExchangeSection("verse", "V", lines = listOf(ExchangeLine("x", listOf(ExchangeMark(0, "C", "1/2")))))))
        val codes = ChordProFormat.write(song).notices.map { it.code }
        assertTrue("notes_as_annotations" in codes && "rests_as_annotations" in codes)
        assertEquals("1", ChordProFormat.write(withDuration).notices.single { it.code == "chord_durations_lost" }.detail)
    }

    @Test fun roundTripKeepsEverythingChordProCanCarry() {
        val back = ChordProFormat.parse(ChordProFormat.write(song).text).songs.single().song
        assertEquals(song.title, back.title)
        assertEquals(song.artist, back.artist)
        assertEquals(song.keyRoot to song.keyMode, back.keyRoot to back.keyMode)
        assertEquals(song.bpm, back.bpm)
        assertEquals(song.capo, back.capo)
        assertEquals(song.instrumentId to song.tuningId, back.instrumentId to back.tuningId)
        assertEquals(song.tags, back.tags)
        assertEquals(song.unsectionedLines, back.unsectionedLines)
        assertEquals(song.sections[0].lines, back.sections[0].lines)
        assertEquals(listOf("verse", "custom"), back.sections.map { it.kind })
        assertEquals("Mi parte", back.sections[1].title)
        assertEquals(" # parece comentario", back.sections[1].lines.single().text)
    }
}

class AnarkeyFormatTest {
    private val song = ExchangeSong(
        title = "Canción ñandú", artist = "A", keyRoot = "Bb", keyMode = "major", bpm = 90, timeNumerator = 4, timeDenominator = 4,
        capo = 2, instrumentId = "piano", tuningId = null, notes = "n\nm", favorite = true, transposeOffset = 5,
        tags = listOf("uno", "dos"),
        unsectionedLines = listOf(ExchangeLine("suelta", listOf(ExchangeMark(0, "C", "1/2")))),
        sections = listOf(ExchangeSection("chorus", "Estribillo", "nota", listOf(
            ExchangeLine("la 🎵 ño", listOf(ExchangeMark(0, "♪C#5", "1/8."), ExchangeMark(2, "_", "1/1"), ExchangeMark(5, "Dm7/G"))),
            ExchangeLine(""),
        ))),
    )

    @Test fun roundTripIsLossless() {
        val text = AnarkeyFormat.encode(listOf(song, song.copy(title = "Otra")), "2026-10-10T00:00:00Z")
        val back = AnarkeyFormat.decode(text).songs.map { it.song }
        assertEquals(listOf(song, song.copy(title = "Otra")), back)
        assertTrue(AnarkeyFormat.decode(text).songs.all { it.notices.isEmpty() })
    }

    @Test fun neverContainsLocalPathsOrRecordings() {
        val text = AnarkeyFormat.encode(listOf(song), "2026-10-10T00:00:00Z")
        assertFalse(text.contains("recording", ignoreCase = true))
        assertFalse(text.contains("/data/"))
        assertTrue(text.contains("\"version\": 1"))
    }

    @Test fun ignoresUnknownFieldsFromNewerWriters() {
        val text = """{"format":"anarkey-song","version":1,"extra":{"x":1},"songs":[{"title":"T","future":true,
            "lyrics":[{"text":"hola","marks":[{"at":2,"symbol":"C","flag":1}],"layout":"x"}]}]}"""
        val result = AnarkeyFormat.decode(text).songs.single().song
        assertEquals("T", result.title)
        assertEquals(ExchangeMark(2, "C"), result.unsectionedLines.single().marks.single())
    }

    @Test fun rejectsUnsupportedOrForeignFiles() {
        assertEquals("unsupported_version", assertThrows(ExchangeException::class.java) {
            AnarkeyFormat.decode("""{"format":"anarkey-song","version":2,"songs":[]}""") }.code)
        assertEquals("not_anarkey_file", assertThrows(ExchangeException::class.java) { AnarkeyFormat.decode("""{"format":"other","version":1}""") }.code)
        assertEquals("invalid_json", assertThrows(ExchangeException::class.java) { AnarkeyFormat.decode("{\"format\":") }.code)
        assertEquals("missing_songs", assertThrows(ExchangeException::class.java) { AnarkeyFormat.decode("""{"format":"anarkey-song","version":1}""") }.code)
        assertEquals("no_songs", assertThrows(ExchangeException::class.java) { AnarkeyFormat.decode("""{"format":"anarkey-song","version":1,"songs":[]}""") }.code)
    }

    @Test fun validatesAndRepairsIncomingValues() {
        val text = """{"format":"anarkey-song","version":1,"songs":[{"title":"  ","tempo":9999,"capo":99,
            "key":{"root":"H","mode":"major"},"instrument":{"id":"guitar","tuning":"violin.standard"},
            "transposeOffset":-3,"tags":["a","A"," ",""],
            "lyrics":[{"text":"abc","marks":[{"at":99,"symbol":"C","figure":"7/9"},{"at":-4,"symbol":"  "}]}]}]}"""
        val imported = AnarkeyFormat.decode(text).songs.single()
        val clean = imported.song
        assertNull(clean.bpm)
        assertEquals(24, clean.capo)
        assertNull(clean.keyRoot)
        assertNull(clean.instrumentId)
        assertEquals(9, clean.transposeOffset)
        assertEquals(listOf("a"), clean.tags)
        assertEquals(listOf(ExchangeMark(3, "C", null)), clean.unsectionedLines.single().marks)
        val codes = imported.notices.map { it.code }
        assertTrue(listOf("bpm_invalid", "key_invalid", "instrument_invalid", "figure_invalid", "capo_invalid").all { it in codes })
    }

    @Test fun detectsTheFormatFromContent() {
        assertEquals(SongFiles.Kind.CHORDPRO, SongFiles.detect("{title: Hola}\n[C]x"))
        assertEquals(SongFiles.Kind.ANARKEY, SongFiles.detect("{\n  \"format\": \"anarkey-song\"\n}"))
        assertEquals(SongFiles.Kind.CHORDPRO, SongFiles.detect("Solo texto"))
    }

    @Test fun capsHugeInput() {
        val many = (1..ExchangeRules.MAX_SONGS + 1).joinToString(",") { "{\"title\":\"s$it\"}" }
        val error = assertThrows(ExchangeException::class.java) {
            AnarkeyFormat.decode("""{"format":"anarkey-song","version":1,"songs":[$many]}""")
        }
        assertEquals("too_many_songs", error.code)
    }
}
