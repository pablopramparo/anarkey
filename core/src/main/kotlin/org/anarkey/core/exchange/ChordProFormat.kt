package org.anarkey.core.exchange

import org.anarkey.core.music.NoteDuration
import org.anarkey.core.music.SongMark
import org.anarkey.core.music.SongMarks

data class ImportedSong(val song: ExchangeSong, val notices: List<Notice>)
data class ImportResult(val songs: List<ImportedSong>)
data class WriteResult(val text: String, val notices: List<Notice>)

/**
 * ChordPro for interoperability. Reading is tolerant: unknown directives are reported, never fatal.
 * ChordPro cannot hold Anarkey's notes, rests or note values, so those are written as `[*…]` annotations
 * (which other programs show as plain text and Anarkey reads back) and the export says so.
 */
object ChordProFormat {
    val FILE_EXTENSIONS = setOf("cho", "chordpro", "chopro", "pro", "crd")

    private val directivePattern = Regex("^\\{\\s*([A-Za-z_][A-Za-z0-9_\\-]*)\\s*(?::\\s*|\\s+)?(.*?)\\s*\\}$")
    private val keyPattern = Regex("^([A-Ga-g])([#b♯♭]?)\\s*(minor|min|major|maj|m)?$", RegexOption.IGNORE_CASE)
    private val aliases = mapOf(
        "t" to "title", "st" to "subtitle", "su" to "subtitle", "c" to "comment", "ci" to "comment_italic", "cb" to "comment_box",
        "sov" to "start_of_verse", "eov" to "end_of_verse", "soc" to "start_of_chorus", "eoc" to "end_of_chorus",
        "sob" to "start_of_bridge", "eob" to "end_of_bridge", "sot" to "start_of_tab", "eot" to "end_of_tab",
        "sog" to "start_of_grid", "eog" to "end_of_grid", "ns" to "new_song", "np" to "new_page", "npp" to "new_physical_page",
        "colb" to "column_break", "col" to "columns", "g" to "grid", "ng" to "no_grid",
    )
    private val ignoredExact = setOf("new_page", "new_physical_page", "column_break", "columns", "pagetype", "pagesize", "titles",
        "diagrams", "grid", "no_grid", "define", "chord", "sorttitle", "sortartist", "duration", "highlight", "image")
    private val kindWords = mapOf(
        "verse" to "verse", "verso" to "verse", "estrofa" to "verse", "strophe" to "verse",
        "chorus" to "chorus", "estribillo" to "chorus", "coro" to "chorus", "refrain" to "chorus",
        "bridge" to "bridge", "puente" to "bridge", "prechorus" to "prechorus", "pre-chorus" to "prechorus", "preestribillo" to "prechorus",
        "intro" to "intro", "outro" to "outro", "solo" to "solo", "coda" to "outro",
    )

    private class MutableSection(val kind: String, var title: String, val lines: MutableList<ExchangeLine> = mutableListOf(), val raw: Boolean = false)

    private class Builder {
        var title: String? = null
        var artist: String? = null
        val subtitles = mutableListOf<String>()
        val extraNotes = mutableListOf<String>()
        var keyRoot: String? = null
        var keyMode: String? = null
        var bpm: Int? = null
        var numerator: Int? = null
        var denominator: Int? = null
        var capo = 0
        var instrument: String? = null
        var tuning: String? = null
        var offset = 0
        val tags = mutableListOf<String>()
        val unsectioned = mutableListOf<ExchangeLine>()
        val sections = mutableListOf<MutableSection>()
        var current: MutableSection? = null
        var environmentOpen = false
        val notices = mutableListOf<Notice>()
        var anything = false
        fun target(): MutableList<ExchangeLine> = current?.lines ?: unsectioned
    }

    fun parse(text: String, fallbackTitle: String = ""): ImportResult {
        val songs = mutableListOf<ImportedSong>()
        var builder = Builder()
        val formatting = mutableSetOf<String>()
        val unknown = linkedSetOf<String>()
        var droppedAnnotations = 0

        fun finish() {
            val b = builder
            builder = Builder()
            if (!b.anything) return
            val subtitles = b.subtitles.toMutableList()
            val artist = b.artist ?: subtitles.firstOrNull()?.also { subtitles.removeAt(0) }
            val notes = (b.extraNotes + subtitles).joinToString("\n")
            if (subtitles.isNotEmpty() || b.extraNotes.any { it.contains(": ") }) b.notices += Notice("metadata_in_notes")
            fun trim(lines: MutableList<ExchangeLine>): List<ExchangeLine> {
                val out = mutableListOf<ExchangeLine>()
                for (line in lines) {
                    val blank = line.text.isBlank() && line.marks.isEmpty()
                    if (blank && (out.isEmpty() || out.last().text.isBlank() && out.last().marks.isEmpty())) continue
                    out += line
                }
                while (out.isNotEmpty() && out.last().text.isBlank() && out.last().marks.isEmpty()) out.removeAt(out.lastIndex)
                return out
            }
            val raw = ExchangeSong(
                title = b.title?.takeIf { it.isNotBlank() } ?: fallbackTitle, artist = artist, keyRoot = b.keyRoot, keyMode = b.keyMode,
                bpm = b.bpm, timeNumerator = b.numerator, timeDenominator = b.denominator, capo = b.capo,
                instrumentId = b.instrument, tuningId = b.tuning, notes = notes, transposeOffset = b.offset, tags = b.tags,
                unsectionedLines = trim(b.unsectioned),
                sections = b.sections.map { ExchangeSection(it.kind, it.title, "", trim(it.lines)) }.filter { it.lines.isNotEmpty() || it.title.isNotBlank() },
            )
            val clean = ExchangeRules.sanitize(raw)
            songs += ImportedSong(clean.song, b.notices + clean.notices)
        }

        fun openSection(b: Builder, kind: String, title: String, raw: Boolean = false) {
            b.current = MutableSection(kind, title, raw = raw).also { b.sections += it }
        }

        val source = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        for (rawLine in source.split('\n')) {
            val trimmed = rawLine.trim()
            val b = builder
            // A comment is a line that starts with #; an indented one is lyric text (the writer relies on this).
            if (rawLine.startsWith("#")) continue
            val directive = if (trimmed.startsWith("{") && trimmed.endsWith("}")) directivePattern.matchEntire(trimmed) else null
            if (directive == null) {
                if (trimmed.isEmpty() && !b.anything) continue
                b.anything = true
                if (b.current?.raw == true) { b.target() += ExchangeLine(rawLine.trimEnd()); continue }
                val (line, dropped) = parseLine(rawLine.trimEnd())
                droppedAnnotations += dropped
                b.target() += line
                continue
            }
            var name = directive.groupValues[1].lowercase().replace('-', '_')
            name = aliases[name] ?: name
            val arg = directive.groupValues[2].trim().trim('"')
            when {
                name == "new_song" -> { finish(); continue }
                name == "title" -> { b.title = arg; b.anything = true }
                name == "subtitle" -> { if (arg.isNotBlank()) b.subtitles += arg; b.anything = true }
                name == "artist" -> { b.artist = arg.ifBlank { null }; b.anything = true }
                name in setOf("composer", "lyricist", "album", "year", "copyright", "arranger") -> {
                    if (arg.isNotBlank()) b.extraNotes += "$name: $arg"; b.anything = true
                }
                name == "key" -> {
                    val match = keyPattern.matchEntire(arg)
                    if (match == null) b.notices += Notice("key_invalid", arg) else {
                        val accidental = match.groupValues[2].replace('♯', '#').replace('♭', 'b')
                        b.keyRoot = match.groupValues[1].uppercase() + accidental
                        b.keyMode = if (match.groupValues[3].lowercase().startsWith("m") && match.groupValues[3].lowercase() != "maj" &&
                            match.groupValues[3].lowercase() != "major") "minor" else "major"
                    }
                    b.anything = true
                }
                name == "tempo" -> { b.bpm = Regex("\\d+").find(arg)?.value?.toIntOrNull(); if (b.bpm == null) b.notices += Notice("bpm_invalid", arg); b.anything = true }
                name == "time" -> {
                    val match = Regex("(\\d+)\\s*/\\s*(\\d+)").find(arg)
                    if (match == null) b.notices += Notice("meter_invalid", arg) else {
                        b.numerator = match.groupValues[1].toIntOrNull(); b.denominator = match.groupValues[2].toIntOrNull()
                    }
                    b.anything = true
                }
                name == "capo" -> { b.capo = arg.toIntOrNull() ?: 0; b.anything = true }
                name == "x_anarkey_instrument" -> {
                    val parts = arg.split(Regex("\\s+"))
                    b.instrument = parts.getOrNull(0)?.ifBlank { null }; b.tuning = parts.getOrNull(1)?.ifBlank { null }
                }
                name == "x_anarkey_tag" -> if (arg.isNotBlank()) b.tags += arg
                name == "x_anarkey_transpose" -> b.offset = arg.toIntOrNull() ?: 0
                name == "meta" -> {
                    val parts = arg.split(Regex("\\s+"), limit = 2)
                    if (parts[0].equals("tag", true) && parts.size > 1) b.tags += parts[1] else unknown += "meta"
                }
                name == "comment_italic" -> { if (arg.isNotBlank()) b.extraNotes += arg; b.anything = true }
                name == "comment" || name == "comment_box" -> {
                    b.anything = true
                    if (b.environmentOpen) b.target() += ExchangeLine(arg)
                    else openSection(b, kindWords[arg.lowercase().substringBefore(' ').trimEnd(':', '.')] ?: "custom", arg)
                }
                name == "chorus" -> {
                    b.anything = true
                    val last = b.sections.lastOrNull { it.kind == "chorus" && !it.raw }
                    if (last == null) b.notices += Notice("chorus_recall_without_chorus")
                    else openSection(b, "chorus", arg.ifBlank { last.title }).also { b.current!!.lines += last.lines }
                    b.current = null
                }
                name.startsWith("start_of_") -> {
                    b.anything = true
                    val env = name.removePrefix("start_of_")
                    val label = Regex("label\\s*=\\s*\"([^\"]*)\"").find(directive.groupValues[2])?.groupValues?.get(1)
                        ?: arg.takeUnless { it.contains("=") }.orEmpty()
                    if (env == "tab" || env == "grid") {
                        b.notices += Notice("tab_as_text")
                        openSection(b, "custom", label.ifBlank { env.replaceFirstChar { it.uppercase() } }, raw = true)
                    } else {
                        val kind = if (env in setOf("verse", "chorus", "bridge", "intro", "outro", "solo", "prechorus")) env else "custom"
                        openSection(b, kind, label.ifBlank { if (kind == "custom" && env != "part") env.replaceFirstChar { it.uppercase() } else "" })
                    }
                    b.environmentOpen = true
                }
                name.startsWith("end_of_") -> { b.current = null; b.environmentOpen = false }
                name in ignoredExact || name.endsWith("font") || name.endsWith("size") || name.endsWith("colour") || name.endsWith("color") ->
                    formatting += name
                else -> unknown += name
            }
        }
        finish()
        val global = buildList {
            if (formatting.isNotEmpty()) add(Notice("formatting_ignored", formatting.size.toString()))
            unknown.forEach { add(Notice("unknown_directive", it)) }
            if (droppedAnnotations > 0) add(Notice("annotations_dropped", droppedAnnotations.toString()))
        }
        // Whole-file notices apply to every song in it, so each preview shows what was left out.
        return ImportResult(songs.map { it.copy(notices = it.notices + global) })
    }

    /** One lyric line with inline `[chord]` marks; `[*…]` carries Anarkey notes and rests, other annotations are dropped. */
    internal fun parseLine(source: String): Pair<ExchangeLine, Int> {
        val text = StringBuilder()
        val marks = mutableListOf<ExchangeMark>()
        var count = 0
        var dropped = 0
        var index = 0
        while (index < source.length) {
            val char = source[index]
            val end = if (char == '[') source.indexOf(']', index) else -1
            if (char == '[' && end > index) {
                val content = source.substring(index + 1, end).trim()
                index = end + 1
                if (content.startsWith("*")) {
                    val payload = content.drop(1).trim()
                    val split = payload.lastIndexOf(':')
                    val symbol = if (split > 0) payload.substring(0, split).trim() else payload
                    val figure = if (split > 0) payload.substring(split + 1).trim().takeIf { NoteDuration.parse(it) != null } else null
                    val isMark = symbol == SongMarks.REST || (symbol.startsWith(SongMarks.NOTE_PREFIX) && SongMarks.parse(symbol) is SongMark.Note)
                    if (isMark) marks += ExchangeMark(count, symbol, figure) else dropped++
                } else if (content.isNotEmpty()) marks += ExchangeMark(count, content)
            } else {
                val codePoint = source.codePointAt(index)
                text.appendCodePoint(codePoint)
                count++
                index += Character.charCount(codePoint)
            }
        }
        return ExchangeLine(text.toString(), marks) to dropped
    }

    fun write(song: ExchangeSong): WriteResult {
        val out = StringBuilder()
        fun directive(name: String, value: String?) { if (!value.isNullOrBlank()) out.append('{').append(name).append(": ").append(value.replace("\n", " ")).append("}\n") }
        directive("title", song.title)
        directive("artist", song.artist)
        song.keyRoot?.let { directive("key", it + if (song.keyMode == "minor") "m" else "") }
        song.bpm?.let { directive("tempo", it.toString()) }
        if (song.timeNumerator != null) directive("time", "${song.timeNumerator}/${song.timeDenominator}")
        if (song.capo > 0) directive("capo", song.capo.toString())
        if (song.instrumentId != null) directive("x_anarkey_instrument", listOfNotNull(song.instrumentId, song.tuningId).joinToString(" "))
        if (song.transposeOffset != 0) directive("x_anarkey_transpose", song.transposeOffset.toString())
        song.tags.forEach { directive("x_anarkey_tag", it) }
        song.notes.lines().filter { it.isNotBlank() }.forEach { directive("comment_italic", it.trim()) }
        var notes = 0
        var rests = 0
        var chordsWithFigure = 0
        fun lineText(line: ExchangeLine): String {
            val points = line.text.codePoints().toArray()
            val sb = StringBuilder()
            val marks = line.marks.sortedBy { it.position }
            var next = 0
            fun mark(m: ExchangeMark) {
                val symbol = m.symbol.replace("[", "").replace("]", "")
                when {
                    symbol == SongMarks.REST -> { rests++; sb.append("[*").append(symbol).append(m.figure?.let { ":$it" } ?: "").append(']') }
                    symbol.startsWith(SongMarks.NOTE_PREFIX) -> { notes++; sb.append("[*").append(symbol).append(m.figure?.let { ":$it" } ?: "").append(']') }
                    else -> { if (m.figure != null) chordsWithFigure++; sb.append('[').append(symbol).append(']') }
                }
            }
            for (point in points.indices) {
                while (next < marks.size && marks[next].position <= point) mark(marks[next++])
                sb.appendCodePoint(points[point])
            }
            while (next < marks.size) mark(marks[next++])
            val result = sb.toString()
            // A lyric that starts like a directive or comment would be misread; a leading space keeps it text.
            return if (result.startsWith("#") || result.startsWith("{")) " $result" else result
        }
        if (song.unsectionedLines.isNotEmpty()) {
            out.append('\n')
            song.unsectionedLines.forEach { out.append(lineText(it)).append('\n') }
        }
        song.sections.forEach { section ->
            out.append('\n')
            val env = when (section.kind) { "verse", "chorus", "bridge", "intro", "outro", "solo", "prechorus" -> section.kind; else -> "part" }
            out.append("{start_of_").append(env)
            if (section.title.isNotBlank()) out.append(": ").append(section.title.replace("\n", " "))
            out.append("}\n")
            section.lines.forEach { out.append(lineText(it)).append('\n') }
            out.append("{end_of_").append(env).append("}\n")
        }
        val notices = buildList {
            if (notes > 0) add(Notice("notes_as_annotations", notes.toString()))
            if (rests > 0) add(Notice("rests_as_annotations", rests.toString()))
            if (chordsWithFigure > 0) add(Notice("chord_durations_lost", chordsWithFigure.toString()))
            if (song.transposeOffset != 0 || song.instrumentId != null) add(Notice("anarkey_extensions"))
        }
        return WriteResult(out.toString(), notices)
    }
}
