package org.anarkey.core.exchange

/** Raised when a file cannot be used at all; [code] is language-free and [detail] gives the offending value. */
class ExchangeException(val code: String, val detail: String? = null) : Exception(code)

/**
 * Anarkey's own song file: complete and versioned, for backups and moving songs between devices.
 *
 * Positions are Unicode code points into each line's text. Linked recordings are deliberately not part of
 * the file: audio lives in the device's private storage and is never exported with the songs.
 * Readers ignore unknown fields, so later versions can add data without breaking this one.
 */
object AnarkeyFormat {
    const val FORMAT = "anarkey-song"
    const val VERSION = 1
    const val EXTENSION = "anarkeysong.json"

    fun encode(songs: List<ExchangeSong>, exportedAt: String, pretty: Boolean = true): String {
        val root = linkedMapOf<String, Any?>(
            "format" to FORMAT,
            "version" to VERSION,
            "exportedAt" to exportedAt,
            "positionUnit" to "unicode-code-point",
            "songs" to songs.map(::songToJson),
        )
        return Json.write(root, pretty)
    }

    private fun songToJson(song: ExchangeSong): Map<String, Any?> = linkedMapOf(
        "title" to song.title,
        "artist" to song.artist,
        "key" to song.keyRoot?.let { linkedMapOf("root" to it, "mode" to song.keyMode) },
        "tempo" to song.bpm,
        "meter" to song.timeNumerator?.let { linkedMapOf("numerator" to it, "denominator" to song.timeDenominator) },
        "capo" to song.capo,
        "instrument" to song.instrumentId?.let { linkedMapOf("id" to it, "tuning" to song.tuningId) },
        "notes" to song.notes,
        "favorite" to song.favorite,
        "transposeOffset" to song.transposeOffset,
        "tags" to song.tags,
        "lyrics" to song.unsectionedLines.map(::lineToJson),
        "sections" to song.sections.map { section ->
            linkedMapOf("kind" to section.kind, "title" to section.title, "notes" to section.notes, "lines" to section.lines.map(::lineToJson))
        },
    )

    private fun lineToJson(line: ExchangeLine): Map<String, Any?> = linkedMapOf(
        "text" to line.text,
        "marks" to line.marks.map { linkedMapOf("at" to it.position, "symbol" to it.symbol, "figure" to it.figure) },
    )

    fun decode(text: String): ImportResult {
        if (text.length > ExchangeRules.MAX_FILE_CHARS) throw ExchangeException("file_too_large")
        val root = try { Json.parse(text.removePrefix("﻿")) } catch (e: Json.ParseException) { throw ExchangeException("invalid_json", e.message) }
        val map = root as? Map<*, *> ?: throw ExchangeException("not_anarkey_file")
        if (map["format"] != FORMAT) throw ExchangeException("not_anarkey_file")
        val version = (map["version"] as? Long)?.toInt() ?: throw ExchangeException("missing_version")
        if (version > VERSION || version < 1) throw ExchangeException("unsupported_version", version.toString())
        val songs = map["songs"] as? List<*> ?: throw ExchangeException("missing_songs")
        if (songs.size > ExchangeRules.MAX_SONGS) throw ExchangeException("too_many_songs", songs.size.toString())
        val imported = songs.mapIndexedNotNull { index, entry ->
            val song = entry as? Map<*, *> ?: return@mapIndexedNotNull null
            val title = (song["title"] as? String).orEmpty()
            val key = song["key"] as? Map<*, *>
            val meter = song["meter"] as? Map<*, *>
            val instrument = song["instrument"] as? Map<*, *>
            val raw = ExchangeSong(
                title = title, artist = song["artist"] as? String, keyRoot = key?.get("root") as? String, keyMode = key?.get("mode") as? String,
                bpm = song.int("tempo"), timeNumerator = meter?.int("numerator"), timeDenominator = meter?.int("denominator"),
                capo = song.int("capo") ?: 0, instrumentId = instrument?.get("id") as? String, tuningId = instrument?.get("tuning") as? String,
                notes = song["notes"] as? String ?: "", favorite = song["favorite"] as? Boolean ?: false,
                transposeOffset = song.int("transposeOffset") ?: 0,
                tags = (song["tags"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                unsectionedLines = linesFrom(song["lyrics"]),
                sections = (song["sections"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map { section ->
                    ExchangeSection(section["kind"] as? String ?: "custom", section["title"] as? String ?: "",
                        section["notes"] as? String ?: "", linesFrom(section["lines"]))
                },
            )
            val clean = ExchangeRules.sanitize(raw)
            val extra = if (title.isBlank()) listOf(Notice("song_untitled_index", (index + 1).toString())) else emptyList()
            ImportedSong(clean.song, clean.notices + extra)
        }
        if (imported.isEmpty()) throw ExchangeException("no_songs")
        return ImportResult(imported)
    }

    private fun linesFrom(value: Any?): List<ExchangeLine> = (value as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map { line ->
        ExchangeLine(
            text = line["text"] as? String ?: "",
            marks = (line["marks"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().mapNotNull { mark ->
                val symbol = mark["symbol"] as? String ?: return@mapNotNull null
                ExchangeMark(mark.int("at") ?: 0, symbol, mark["figure"] as? String)
            },
        )
    }

    private fun Map<*, *>.int(key: String): Int? = when (val v = this[key]) {
        is Long -> v.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        is Double -> v.takeIf { it == Math.floor(it) && it.isFinite() && it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() }?.toInt()
        else -> null
    }
}

/** Picks the reader for a file's content, so the person never has to say which format it is. */
object SongFiles {
    enum class Kind { ANARKEY, CHORDPRO }

    fun detect(text: String): Kind {
        val head = text.removePrefix("﻿").trimStart()
        // A ChordPro directive also starts with a brace, so the JSON marker must be present too.
        return if (head.startsWith("{") && head.take(512).contains("\"format\"")) Kind.ANARKEY else Kind.CHORDPRO
    }

    fun read(text: String, fallbackTitle: String): ImportResult = when (detect(text)) {
        Kind.ANARKEY -> AnarkeyFormat.decode(text)
        Kind.CHORDPRO -> {
            if (text.length > ExchangeRules.MAX_FILE_CHARS) throw ExchangeException("file_too_large")
            ChordProFormat.parse(text, fallbackTitle).also { if (it.songs.isEmpty()) throw ExchangeException("no_songs") }
        }
    }
}
