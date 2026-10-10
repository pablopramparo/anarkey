package org.anarkey.core.music

/** Spelled pitch, kept separate from its enharmonic pitch class. */
data class SpelledNote(val letter: Char, val accidental: Int = 0) {
    init { require(letter in 'A'..'G' && accidental in -2..2) }
    val pitchClass: Int get() = (NATURAL_PC.getValue(letter) + accidental).mod(12)
    fun name(): String = letter + when (accidental) { -2 -> "bb"; -1 -> "b"; 1 -> "#"; 2 -> "##"; else -> "" }

    companion object {
        private val NATURAL_PC = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)
        fun parse(letter: String?, accidental: Int?): SpelledNote? = letter?.singleOrNull()?.let { SpelledNote(it, accidental ?: 0) }
        fun fromMidi(midi: Int, flats: Boolean = false): SpelledNote {
            val names = if (flats) listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
            else listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
            val name = names[midi.mod(12)]
            return SpelledNote(name[0], when (name.length) { 1 -> 0; else -> if (name[1] == '#') 1 else -1 })
        }
    }
}

data class ChordTone(val note: SpelledNote, val intervalSemitones: Int, val intervalName: String)
data class ChordDefinition(val symbol: String, val root: SpelledNote, val tones: List<ChordTone>, val bass: SpelledNote?) {
    val pitchClasses: Set<Int> get() = tones.mapTo(mutableSetOf()) { it.note.pitchClass }
}

private fun defaultChordDegree(semitones: Int): Int = when (semitones % 12) {
    0 -> 0; 1, 2 -> 1; 3, 4 -> 2; 5 -> 3; 6, 7 -> 4; 8, 9 -> 5; else -> 6
} + (semitones / 12) * 7

private data class Formula(val intervals: List<Int>, val essential: Set<Int>, val degrees: List<Int> = intervals.map(::defaultChordDegree)) {
    init { require(intervals.size == degrees.size) }
}

/** Chord theory is independent from any instrument or fingering. */
object ChordTheory {
    private val formulas = mapOf(
        "major" to Formula(listOf(0, 4, 7), setOf(0, 4)),
        "minor" to Formula(listOf(0, 3, 7), setOf(0, 3)),
        "diminished" to Formula(listOf(0, 3, 6), setOf(0, 3, 6)),
        "augmented" to Formula(listOf(0, 4, 8), setOf(0, 4, 8), listOf(0, 2, 4)),
        "sus2" to Formula(listOf(0, 2, 7), setOf(0, 2, 7)),
        "sus4" to Formula(listOf(0, 5, 7), setOf(0, 5, 7)),
        "power" to Formula(listOf(0, 7), setOf(0, 7)),
        "dominant7" to Formula(listOf(0, 4, 7, 10), setOf(0, 4, 10)),
        "major7" to Formula(listOf(0, 4, 7, 11), setOf(0, 4, 11)),
        "minor7" to Formula(listOf(0, 3, 7, 10), setOf(0, 3, 10)),
        "halfDiminished7" to Formula(listOf(0, 3, 6, 10), setOf(0, 3, 6, 10)),
        "diminished7" to Formula(listOf(0, 3, 6, 9), setOf(0, 3, 6, 9), listOf(0, 2, 4, 6)),
        "sixth" to Formula(listOf(0, 4, 7, 9), setOf(0, 4, 9)),
        "minorSixth" to Formula(listOf(0, 3, 7, 9), setOf(0, 3, 9)),
        "add9" to Formula(listOf(0, 4, 7, 14), setOf(0, 4, 14)),
        "minorAdd9" to Formula(listOf(0, 3, 7, 14), setOf(0, 3, 14)),
        "ninth" to Formula(listOf(0, 4, 7, 10, 14), setOf(0, 4, 10, 14)),
        "eleventh" to Formula(listOf(0, 4, 7, 10, 14, 17), setOf(0, 4, 10, 17)),
        "thirteenth" to Formula(listOf(0, 4, 7, 10, 14, 17, 21), setOf(0, 4, 10, 21)),
    )

    fun resolve(symbol: String): ChordDefinition? {
        val parsed = ChordSymbolParser.parse(symbol)
        if (!parsed.interpretable) return null
        val root = SpelledNote.parse(parsed.rootLetter, parsed.rootAccidental) ?: return null
        val quality = when (parsed.quality) {
            "minor", "m", "min" -> "minor"
            "dim" -> "diminished"
            "aug" -> "augmented"
            "sus2" -> "sus2"
            "sus4", "sus" -> "sus4"
            else -> "major"
        }
        val key = when (parsed.extension) {
            null -> quality
            "5" -> "power"
            "7" -> when { quality == "minor" -> "minor7"; quality == "diminished" -> "diminished7"; parsed.quality == "maj" -> "major7"; else -> "dominant7" }
            "maj7" -> "major7"
            "m7" -> "minor7"
            "7b5" -> "halfDiminished7"
            "6" -> if (quality == "minor") "minorSixth" else "sixth"
            "add9" -> if (quality == "minor") "minorAdd9" else "add9"
            "9" -> "ninth"
            "11", "add11" -> "eleventh"
            "13" -> "thirteenth"
            else -> return null
        }
        val formula = formulas[key] ?: return null
        val bass = SpelledNote.parse(parsed.bassLetter, parsed.bassAccidental)
        val tones = formula.intervals.mapIndexed { index, semitones ->
            val degree = formula.degrees[index]
            val letterIndex = ("CDEFGAB".indexOf(root.letter) + degree) % 7
            val letter = "CDEFGAB"[letterIndex]
            val natural = SpelledNote(letter).pitchClass
            val accidental = ((root.pitchClass + semitones).mod(12) - natural + 12).mod(12).let { if (it > 6) it - 12 else it }
            ChordTone(SpelledNote(letter, accidental), semitones, intervalLabel(semitones, degree))
        }
        return ChordDefinition(parsed.original, root, tones, bass)
    }

    private fun intervalLabel(semitones: Int, degree: Int): String = when (degree % 7) {
        0 -> if (semitones == 12) "8" else "1"
        1 -> when (semitones % 12) { 1 -> "b2"; else -> "2" }
        2 -> if (semitones % 12 == 3) "b3" else "3"
        3 -> if (semitones % 12 == 6) "#4" else "4"
        4 -> when (semitones % 12) { 6 -> "b5"; 8 -> "#5"; else -> "5" }
        5 -> if (semitones % 12 == 8) "b6" else "6"
        else -> when (semitones % 12) { 9 -> "bb7"; 10 -> "b7"; else -> "7" }
    }.let { label -> when (degree) { 8 -> "9"; 10 -> "11"; 12 -> "13"; else -> label } }

    internal fun essentialIntervals(symbol: String): Set<Int>? {
        val parsed = ChordSymbolParser.parse(symbol)
        val q = when (parsed.quality) { "m", "min", "minor" -> "minor"; "dim" -> "diminished"; "aug" -> "augmented"; "sus2" -> "sus2"; "sus4", "sus" -> "sus4"; else -> "major" }
        val key = when (parsed.extension) { null -> q; "5" -> "power"; "7" -> when { q == "minor" -> "minor7"; q == "diminished" -> "diminished7"; parsed.quality == "maj" -> "major7"; else -> "dominant7" }; "maj7" -> "major7"; "m7" -> "minor7"; "7b5" -> "halfDiminished7"; "6" -> if (q == "minor") "minorSixth" else "sixth"; "add9" -> if (q == "minor") "minorAdd9" else "add9"; "9" -> "ninth"; "11", "add11" -> "eleventh"; "13" -> "thirteenth"; else -> return null }
        return formulas[key]?.essential
    }

}

data class FingerPosition(val stringIndex: Int, val fret: Int, val finger: Int)
data class ChordVoicing(
    val id: String, val instrumentId: String, val tuningId: String, val chordSymbol: String,
    /** One fret per physical string, in the exact order of Tuning.strings; null means muted, zero means open. */
    val frets: List<Int?>, val fingers: List<FingerPosition> = emptyList(), val baseFret: Int = 1,
    val barreFret: Int? = null, val verification: String,
)
data class VoicingValidation(val valid: Boolean, val producedMidi: List<Int>, val reasons: List<String>)

object ChordVoicingValidator {
    fun validate(voicing: ChordVoicing): VoicingValidation {
        val reasons = mutableListOf<String>()
        val tuning = TuningCatalog.tuning(voicing.tuningId)
        val chord = ChordTheory.resolve(voicing.chordSymbol)
        if (tuning == null || tuning.instrumentId != voicing.instrumentId) reasons += "tuning_instrument_mismatch"
        if (chord == null) reasons += "unknown_chord"
        if (tuning == null || voicing.frets.size != tuning.strings.size) reasons += "string_count_mismatch"
        if (voicing.baseFret < 1 || voicing.baseFret > 24) reasons += "invalid_base_fret"
        voicing.frets.forEachIndexed { i, fret -> if (fret != null && fret !in 0..24) reasons += "invalid_fret_$i" }
        if (voicing.frets.all { it == null }) reasons += "all_strings_muted"
        if (voicing.fingers.any { it.stringIndex !in voicing.frets.indices || it.fret !in 1..24 || it.finger !in 1..4 || voicing.frets.getOrNull(it.stringIndex) != it.fret }) reasons += "invalid_finger_map"
        val midi = if (tuning == null || tuning.strings.size != voicing.frets.size) emptyList() else tuning.strings.indices.mapNotNull { i -> voicing.frets[i]?.let { tuning.strings[i].midi + it } }
        val highestFret = voicing.frets.filterNotNull().maxOrNull() ?: 0
        val expectedBase = if (highestFret > 5) voicing.frets.filterNotNull().filter { it > 0 }.minOrNull() ?: 1 else 1
        if (voicing.baseFret != expectedBase) reasons += "invalid_base_fret_value"
        if (voicing.barreFret != null && (voicing.barreFret !in 1..24 || voicing.barreFret !in voicing.frets.filterNotNull())) reasons += "invalid_barre"
        if (chord != null && midi.isNotEmpty()) {
            val allowedPitchClasses = chord.pitchClasses + listOfNotNull(chord.bass?.pitchClass)
            if (midi.any { it.mod(12) !in allowedPitchClasses }) reasons += "non_chord_tone"
            val pcs = midi.map { it.mod(12) }.toSet()
            val essential = ChordTheory.essentialIntervals(voicing.chordSymbol).orEmpty()
            val root = chord.root.pitchClass
            essential.forEach { interval -> if ((root + interval).mod(12) !in pcs) reasons += "missing_essential_$interval" }
            chord.bass?.let { target ->
                val bassPc = midi.minOrNull()!!.mod(12)
                if (bassPc != target.pitchClass) reasons += "wrong_slash_bass"
            }
        }
        return VoicingValidation(reasons.isEmpty(), midi, reasons)
    }
}

/** Curated manual forms only; every entry is checked against the tuning-derived MIDI notes in tests. */
object ChordVoicingCatalog {
    val entries: List<ChordVoicing> = (listOf(
        g("gtr.c.open", "C", listOf(null,3,2,0,1,0), listOf(FingerPosition(1,3,3),FingerPosition(2,2,2),FingerPosition(4,1,1))),
        g("gtr.c.barre", "C", listOf(null,3,5,5,5,3), listOf(FingerPosition(1,3,1),FingerPosition(2,5,3),FingerPosition(3,5,4),FingerPosition(4,5,2)), barre=3),
        g("gtr.g.open", "G", listOf(3,2,0,0,0,3), listOf(FingerPosition(0,3,2),FingerPosition(1,2,1),FingerPosition(5,3,3))),
        g("gtr.d.open", "D", listOf(null,null,0,2,3,2), listOf(FingerPosition(3,2,1),FingerPosition(4,3,3),FingerPosition(5,2,2))),
        g("gtr.am.open", "Am", listOf(null,0,2,2,1,0), listOf(FingerPosition(2,2,2),FingerPosition(3,2,3),FingerPosition(4,1,1))),
        g("gtr.em.open", "Em", listOf(0,2,2,0,0,0), listOf(FingerPosition(1,2,1),FingerPosition(2,2,2))),
        g("gtr.a.open", "A", listOf(null,0,2,2,2,0), listOf(FingerPosition(2,2,1),FingerPosition(3,2,2),FingerPosition(4,2,3))),
        g("gtr.e.open", "E", listOf(0,2,2,1,0,0), listOf(FingerPosition(1,2,2),FingerPosition(2,2,3),FingerPosition(3,1,1))),
        g("gtr.dm.open", "Dm", listOf(null,null,0,2,3,1), listOf(FingerPosition(3,2,2),FingerPosition(4,3,3),FingerPosition(5,1,1))),
        g("gtr.f.barre", "F", listOf(1,3,3,2,1,1), listOf(FingerPosition(0,1,1),FingerPosition(1,3,3),FingerPosition(2,3,4),FingerPosition(3,2,2)), barre=1),
        g("gtr.dropd.d", "D", listOf(0,0,0,2,3,2), listOf(FingerPosition(3,2,1),FingerPosition(4,3,3),FingerPosition(5,2,2)), tuning="guitar.drop_d"),
        g("gtr.dadgad.d", "D", listOf(0,0,0,null,null,4), listOf(FingerPosition(5,4,1)), tuning="guitar.dadgad"),
        g("gtr.dadgad.c", "C", listOf(null,3,2,0,3,null), listOf(FingerPosition(1,3,2),FingerPosition(2,2,1),FingerPosition(4,3,3)), tuning="guitar.dadgad"),
        u("uke.high.c", "C", listOf(0,0,0,3), listOf(FingerPosition(3,3,3))),
        u("uke.high.g", "G", listOf(0,2,3,2), listOf(FingerPosition(1,2,1),FingerPosition(2,3,3),FingerPosition(3,2,2))),
        u("uke.high.d", "D", listOf(2,2,2,0), listOf(FingerPosition(0,2,1),FingerPosition(1,2,2),FingerPosition(2,2,3))),
        u("uke.high.am", "Am", listOf(2,0,0,0), listOf(FingerPosition(0,2,2))),
        u("uke.high.f", "F", listOf(2,0,1,0), listOf(FingerPosition(0,2,2),FingerPosition(2,1,1))),
        u("uke.low.c", "C", listOf(0,0,0,3), listOf(FingerPosition(3,3,3)), tuning="ukulele.low_g"),
        u("uke.low.g", "G", listOf(0,2,3,2), listOf(FingerPosition(1,2,1),FingerPosition(2,3,3),FingerPosition(3,2,2)), tuning="ukulele.low_g"),
        u("uke.low.d", "D", listOf(2,2,2,0), listOf(FingerPosition(0,2,1),FingerPosition(1,2,2),FingerPosition(2,2,3)), tuning="ukulele.low_g"),
        u("uke.low.am", "Am", listOf(2,0,0,0), listOf(FingerPosition(0,2,2)), tuning="ukulele.low_g"),
        u("uke.low.f", "F", listOf(2,0,1,0), listOf(FingerPosition(0,2,2),FingerPosition(2,1,1)), tuning="ukulele.low_g"),
        u("uke.high.em", "Em", listOf(0,4,3,2), listOf(FingerPosition(1,4,3),FingerPosition(2,3,2),FingerPosition(3,2,1))),
        u("uke.low.em", "Em", listOf(0,4,3,2), listOf(FingerPosition(1,4,3),FingerPosition(2,3,2),FingerPosition(3,2,1)), tuning="ukulele.low_g"),
        u("uke.high.e7", "E7", listOf(1,2,0,2), listOf(FingerPosition(0,1,1),FingerPosition(1,2,2),FingerPosition(3,2,3))),
        u("uke.low.e7", "E7", listOf(1,2,0,2), listOf(FingerPosition(0,1,1),FingerPosition(1,2,2),FingerPosition(3,2,3)), tuning="ukulele.low_g"),
    ) + StandardGuitarVoicings.entries + OtherInstrumentVoicings.entries).distinctBy { Triple(it.tuningId, it.chordSymbol, it.frets) }
    /** Instruments that have at least one curated form, in catalog order; the chord selectors list only these. */
    val instruments: List<Instrument> = TuningCatalog.instruments.filter { instrument -> entries.any { it.instrumentId == instrument.id } }
    private val byPitchClasses = entries.groupBy { ChordTheory.resolve(it.chordSymbol)?.pitchClasses }
    fun forChord(symbol: String, instrumentId: String?, tuningId: String?): List<ChordVoicing> {
        if (instrumentId == null) return emptyList()
        val chord = ChordTheory.resolve(symbol) ?: return emptyList()
        return byPitchClasses[chord.pitchClasses].orEmpty().filter {
            it.instrumentId == instrumentId && (tuningId == null || it.tuningId == tuningId) &&
                ChordVoicingValidator.validate(it.copy(chordSymbol = symbol)).valid
        }.sortedBy { it.frets.filterNotNull().maxOrNull() ?: 0 }
    }
    fun find(id: String): ChordVoicing? = entries.singleOrNull { it.id == id }
    /** Presentation-only ordering: keeps every validated form and gently surfaces less demanding shapes. */
    fun prioritizeForBeginner(voicings: List<ChordVoicing>): List<ChordVoicing> = voicings.sortedWith(
        compareBy<ChordVoicing> { if (it.barreFret == null) 0 else 1 }
            .thenBy { it.fingers.map { finger -> finger.finger }.distinct().size }
            .thenBy { it.frets.filterNotNull().maxOrNull() ?: 0 }
            .thenBy { it.frets.count { fret -> fret == null } }
            .thenBy { it.id },
    )

    private fun g(id: String, chord: String, frets: List<Int?>, fingers: List<FingerPosition>, tuning: String = "guitar.standard", barre: Int? = null) =
        ChordVoicing(id, "guitar", tuning, chord, frets, fingers, fretBase(frets), barre, "manually curated; pitch-set validated; playability not independently certified")
    private fun u(id: String, chord: String, frets: List<Int?>, fingers: List<FingerPosition>, tuning: String = "ukulele.high_g") =
        ChordVoicing(id, "ukulele", tuning, chord, frets, fingers, fretBase(frets), null, "manually curated; pitch-set validated; playability not independently certified")
    private fun fretBase(frets: List<Int?>): Int {
        val highest = frets.filterNotNull().maxOrNull() ?: 0
        return if (highest > 5) frets.filterNotNull().filter { it > 0 }.minOrNull() ?: 1 else 1
    }
}
