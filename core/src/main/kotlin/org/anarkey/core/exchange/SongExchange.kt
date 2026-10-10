package org.anarkey.core.exchange

import org.anarkey.core.music.ChordAnchors
import org.anarkey.core.music.NoteDuration
import org.anarkey.core.music.SongMetadata

/** A mark (chord, note or rest) anchored to a lyric character; positions are Unicode code points. */
data class ExchangeMark(val position: Int, val symbol: String, val figure: String? = null)

data class ExchangeLine(val text: String, val marks: List<ExchangeMark> = emptyList())

data class ExchangeSection(val kind: String, val title: String, val notes: String = "", val lines: List<ExchangeLine> = emptyList())

/**
 * A song as it travels between files and the app: complete, free of database ids and of anything that only
 * makes sense on one device (no file paths, no recording references).
 */
data class ExchangeSong(
    val title: String,
    val artist: String? = null,
    val keyRoot: String? = null,
    val keyMode: String? = null,
    val bpm: Int? = null,
    val timeNumerator: Int? = null,
    val timeDenominator: Int? = null,
    val capo: Int = 0,
    val instrumentId: String? = null,
    val tuningId: String? = null,
    val notes: String = "",
    val favorite: Boolean = false,
    val transposeOffset: Int = 0,
    val tags: List<String> = emptyList(),
    val unsectionedLines: List<ExchangeLine> = emptyList(),
    val sections: List<ExchangeSection> = emptyList(),
) {
    val lineCount: Int get() = unsectionedLines.size + sections.sumOf { it.lines.size }
    private val allMarks: List<ExchangeMark> get() = (unsectionedLines + sections.flatMap { it.lines }).flatMap { it.marks }
    val chordCount: Int get() = allMarks.count { isChord(it.symbol) }
    val noteCount: Int get() = allMarks.count { it.symbol.startsWith("♪") }
    val restCount: Int get() = allMarks.count { it.symbol == "_" }
    val chordsWithFigure: Int get() = allMarks.count { isChord(it.symbol) && it.figure != null }

    private fun isChord(symbol: String) = !symbol.startsWith("♪") && symbol != "_"
}

/** Something worth telling the person about an import or export; [code] is language-free, [detail] optional. */
data class Notice(val code: String, val detail: String? = null)

data class Sanitized(val song: ExchangeSong, val notices: List<Notice>)

/** One place for the limits and rules every incoming song must satisfy, whatever file it came from. */
object ExchangeRules {
    const val MAX_FILE_CHARS = 5_000_000
    const val MAX_SONGS = 1_000
    const val MAX_SECTIONS = 200
    const val MAX_LINES = 5_000
    const val MAX_TEXT = 2_000
    const val MAX_TITLE = 160
    const val MAX_SECTION_TITLE = 80
    const val MAX_NOTES = 8_000
    const val MAX_TAGS = 50
    const val MAX_TAG = 40
    const val MAX_SYMBOL = 40
    val SECTION_KINDS = setOf("intro", "verse", "prechorus", "chorus", "bridge", "solo", "outro", "custom")
    val KEY_ROOTS = setOf("A", "B", "C", "D", "E", "F", "G", "A#", "Bb", "B#", "Cb", "C#", "Db", "D#", "Eb", "E#", "Fb", "F#", "Gb", "G#", "Ab")

    /** Clamps and cleans a song so it can be stored; anything dropped or repaired is reported, never silent. */
    fun sanitize(input: ExchangeSong): Sanitized {
        val notices = mutableListOf<Notice>()
        val title = input.title.trim().take(MAX_TITLE).ifEmpty { notices += Notice("untitled"); "" }
        val artist = input.artist?.trim()?.take(MAX_TITLE)?.ifBlank { null }
        var keyRoot = input.keyRoot
        var keyMode = input.keyMode
        if (keyRoot != null && keyRoot !in KEY_ROOTS) { notices += Notice("key_invalid", keyRoot); keyRoot = null; keyMode = null }
        if (keyMode != null && keyMode != "major" && keyMode != "minor") { notices += Notice("key_mode_invalid", keyMode); keyMode = null }
        if (keyRoot == null) keyMode = null
        // Each field is checked on its own so one bad value never hides or drags down another.
        var bpm = input.bpm
        if (bpm != null && bpm !in 1..400) { notices += Notice("bpm_invalid", bpm.toString()); bpm = null }
        var numerator = input.timeNumerator
        var denominator = input.timeDenominator
        val meterOk = (numerator == null && denominator == null) ||
            (numerator != null && denominator != null && numerator in 1..16 && denominator in setOf(1, 2, 4, 8, 16))
        if (!meterOk) { notices += Notice("meter_invalid", "$numerator/$denominator"); numerator = null; denominator = null }
        var instrument = input.instrumentId
        var tuning = input.tuningId
        if (SongMetadata(instrumentId = instrument, tuningId = tuning).validationError() != null) {
            notices += Notice("instrument_invalid", instrument); instrument = null; tuning = null
        }
        if (input.capo !in 0..24) notices += Notice("capo_invalid", input.capo.toString())
        val offset = input.transposeOffset.mod(12)

        var linesLeft = MAX_LINES
        fun cleanLine(line: ExchangeLine): ExchangeLine {
            val text = line.text.take(MAX_TEXT)
            val length = ChordAnchors.codePointCount(text)
            val marks = line.marks.mapNotNull { mark ->
                val symbol = mark.symbol.trim().take(MAX_SYMBOL)
                if (symbol.isEmpty()) return@mapNotNull null
                val figure = mark.figure?.takeIf { NoteDuration.parse(it) != null }
                if (mark.figure != null && figure == null) notices += Notice("figure_invalid", mark.figure)
                ExchangeMark(mark.position.coerceIn(0, length), symbol, figure)
            }
            return ExchangeLine(text, marks)
        }
        fun cleanLines(lines: List<ExchangeLine>): List<ExchangeLine> {
            val take = lines.take(linesLeft)
            if (take.size < lines.size) notices += Notice("lines_truncated", MAX_LINES.toString())
            linesLeft -= take.size
            return take.map(::cleanLine)
        }
        val unsectioned = cleanLines(input.unsectionedLines)
        val sections = input.sections.take(MAX_SECTIONS).map { section ->
            val kind = section.kind.takeIf { it in SECTION_KINDS } ?: "custom"
            ExchangeSection(kind, section.title.trim().take(MAX_SECTION_TITLE), section.notes.take(MAX_NOTES), cleanLines(section.lines))
        }
        if (input.sections.size > MAX_SECTIONS) notices += Notice("sections_truncated", MAX_SECTIONS.toString())
        val tags = input.tags.map { it.trim().take(MAX_TAG) }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.take(MAX_TAGS)
        return Sanitized(ExchangeSong(
            title = title, artist = artist, keyRoot = keyRoot, keyMode = keyMode, bpm = bpm,
            timeNumerator = numerator, timeDenominator = denominator, capo = input.capo.coerceIn(0, 24),
            instrumentId = instrument, tuningId = tuning, notes = input.notes.take(MAX_NOTES), favorite = input.favorite,
            transposeOffset = offset, tags = tags, unsectionedLines = unsectioned, sections = sections,
        ), notices)
    }
}
