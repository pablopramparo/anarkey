package org.anarkey.core.music

/** Root spelling stays distinct from pitch class so later transposition can preserve musical spelling. */
data class ParsedChord(
    val original: String,
    val rootLetter: String?,
    val rootAccidental: Int?,
    val quality: String?,
    val extension: String?,
    val bassLetter: String?,
    val bassAccidental: Int?,
) {
    val interpretable: Boolean get() = rootLetter != null
}

object ChordSymbolParser {
    // Solfege roots are longer than their initial letter (notably Sol), so match them first.
    private val pattern = Regex("^(Sol|Do|Re|Mi|Fa|La|Si|[A-G])([#b]?)(min|m|maj|dim|aug|sus2|sus4|sus)?(7b5|maj7|m7|7|6|9|11|13|add9|add11|5)?(?:/(Sol|Do|Re|Mi|Fa|La|Si|[A-G])([#b]?))?$")
    private val roots = mapOf("Do" to "C", "Re" to "D", "Mi" to "E", "Fa" to "F", "Sol" to "G", "La" to "A", "Si" to "B")
    fun parse(symbol: String): ParsedChord {
        val source = symbol.trim()
        val match = pattern.matchEntire(source)
        if (match == null) return ParsedChord(source, null, null, null, null, null, null)
        fun accidental(value: String?): Int? = when (value) { "#" -> 1; "b" -> -1; else -> 0 }
        fun letter(value: String): String = roots[value] ?: value
        val quality = match.groupValues[3].ifEmpty { "major" }
        val bass = match.groupValues[5]
        return ParsedChord(source, letter(match.groupValues[1]), accidental(match.groupValues[2]), quality,
            match.groupValues[4].ifEmpty { null }, bass.takeIf { it.isNotEmpty() }?.let(::letter),
            bass.takeIf { it.isNotEmpty() }?.let { accidental(match.groupValues[6]) })
    }
}

/** Language and enharmonic spelling are presentation choices; parsing always yields canonical pitch roots. */
object ChordSymbolFormatter {
    private fun noteName(note: SpelledNote, naming: NoteNaming): String {
        val flat = naming == NoteNaming.LETTERS_FLATS || naming == NoteNaming.SOLFEGE_FLATS
        val canonical = SpelledNote.fromMidi(note.pitchClass, flat)
        val name = if (naming == NoteNaming.SOLFEGE_SHARPS || naming == NoteNaming.SOLFEGE_FLATS)
            mapOf('C' to "Do", 'D' to "Re", 'E' to "Mi", 'F' to "Fa", 'G' to "Sol", 'A' to "La", 'B' to "Si").getValue(canonical.letter)
        else canonical.letter.toString()
        return name + canonical.name().drop(1)
    }

    fun format(symbol: String, naming: NoteNaming): String {
        val parsed = ChordSymbolParser.parse(symbol)
        if (!parsed.interpretable) return symbol
        fun root(letter: String?, accidental: Int?): String {
            val note = SpelledNote.parse(letter, accidental) ?: return ""
            return noteName(note, naming)
        }
        val quality = if (parsed.quality == "major") "" else parsed.quality.orEmpty()
        return root(parsed.rootLetter, parsed.rootAccidental) + quality + parsed.extension.orEmpty() +
            (parsed.bassLetter?.let { "/" + root(it, parsed.bassAccidental) } ?: "")
    }

    fun format(note: SpelledNote, naming: NoteNaming): String = noteName(note, naming)
}

data class SongMetadata(
    val bpm: Int? = null,
    val timeNumerator: Int? = null,
    val timeDenominator: Int? = null,
    val capo: Int = 0,
    val instrumentId: String? = null,
    val tuningId: String? = null,
) {
    fun validationError(): String? {
        // The keyboard has no tuning; every other instrument needs a tuning from its own catalog.
        if (instrumentId == PianoChordForms.INSTRUMENT_ID) {
            if (tuningId != null) return "instrument_tuning_unknown"
        } else {
            if ((instrumentId == null) != (tuningId == null)) return "instrument_tuning_pair"
            if (instrumentId != null && (TuningCatalog.instrument(instrumentId) == null ||
                    TuningCatalog.tuning(tuningId.orEmpty())?.instrumentId != instrumentId)) return "instrument_tuning_unknown"
        }
        if (bpm != null && bpm !in 1..400) return "bpm_range"
        if ((timeNumerator == null) != (timeDenominator == null)) return "time_signature_pair"
        if (timeNumerator != null && timeNumerator !in 1..16) return "time_numerator_range"
        if (timeDenominator != null && timeDenominator !in setOf(1, 2, 4, 8, 16)) return "time_denominator_range"
        if (capo !in 0..24) return "capo_range"
        return null
    }
}

/** Character offsets are Unicode code points, matching what a musician sees rather than UTF-16 units. */
object ChordAnchors {
    fun codePointCount(text: String): Int = text.codePointCount(0, text.length)
    fun clamp(position: Int, text: String): Int = position.coerceIn(0, codePointCount(text))

    /** Common prefix/suffix are retained; anchors inside replaced text move to its start. */
    fun remap(position: Int, oldText: String, newText: String): Int {
        val old = oldText.codePoints().toArray()
        val new = newText.codePoints().toArray()
        var prefix = 0
        while (prefix < old.size && prefix < new.size && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < old.size - prefix && suffix < new.size - prefix &&
            old[old.lastIndex - suffix] == new[new.lastIndex - suffix]) suffix++
        val at = clamp(position, oldText)
        return when {
            at <= prefix -> at
            at >= old.size - suffix -> (new.size - (old.size - at)).coerceIn(0, new.size)
            else -> prefix
        }
    }
}
