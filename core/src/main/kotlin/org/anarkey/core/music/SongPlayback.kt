package org.anarkey.core.music

data class PlanChord(val id: String, val position: Int, val order: Int, val symbol: String, val figure: String? = null)
data class PlanLine(val id: String, val text: String, val chords: List<PlanChord>)

/** One chord of the song in playing order; [spanStart] until [spanEnd] (code points) is the lyric it sounds over. */
data class PlaybackStep(
    val lineId: String, val chordId: String, val symbol: String, val spanStart: Int, val spanEnd: Int, val figure: String? = null,
)

/** Turns a song, already in reading order, into the chord sequence to play and how long each chord lasts. */
object SongPlaybackPlan {
    const val DEFAULT_BPM = 100

    /** Chords sound in the order they are written; lines without chords add no step. */
    fun steps(lines: List<PlanLine>): List<PlaybackStep> = lines.flatMap { line ->
        val length = ChordAnchors.codePointCount(line.text)
        val ordered = line.chords.sortedWith(compareBy<PlanChord> { it.position }.thenBy { it.order })
        ordered.map { chord ->
            val start = chord.position.coerceIn(0, length)
            // A chord rings until the next chord that sits further along the line, or until the line ends.
            val end = ordered.firstOrNull { it.position > chord.position }?.position?.coerceIn(start, length) ?: length
            PlaybackStep(line.id, chord.id, chord.symbol, start, end, chord.figure)
        }
    }

    /**
     * Each chord lasts one bar. BPM counts quarter notes, except in compound time (6/8, 9/8, 12/8), where, as in the
     * metronome, it counts dotted quarters.
     */
    fun barSeconds(bpm: Int?, numerator: Int?, denominator: Int?): Double {
        val top = numerator ?: 4
        val bottom = denominator ?: 4
        val beats = if (bottom == 8 && top >= 6 && top % 3 == 0) top / 3.0 else top * 4.0 / bottom
        return beats * 60.0 / (bpm ?: DEFAULT_BPM)
    }
}

/** How long a mark lasts: its note value if it has one, otherwise a bar. */
fun SongPlaybackPlan.stepSeconds(figure: String?, bpm: Int?, numerator: Int?, denominator: Int?): Double {
    val duration = NoteDuration.parse(figure) ?: return barSeconds(bpm, numerator, denominator)
    val top = numerator ?: 4
    val compound = (denominator ?: 4) == 8 && top >= 6 && top % 3 == 0
    val quartersPerBeat = if (compound) 1.5 else 1.0
    return duration.quarters / quartersPerBeat * 60.0 / (bpm ?: SongPlaybackPlan.DEFAULT_BPM)
}

class ChordSound(val midi: List<Int>, val strumGapS: Double)

/** Picks the notes a chord sounds with: the song's own instrument if it has a shape, otherwise a keyboard voicing. */
object ChordSounding {
    private const val STRUM_GAP_S = 0.035
    private const val KEYBOARD_GAP_S = 0.012

    fun of(symbol: String, instrumentId: String?, tuningId: String?, capo: Int): ChordSound? {
        when (val mark = SongMarks.parse(symbol)) {
            SongMark.Rest -> return null
            // A written note is the sounding pitch, so a capo does not move it.
            is SongMark.Note -> return ChordSound(listOf(mark.midi), 0.0)
            is SongMark.Chord -> Unit
        }
        val tuning = tuningId?.let { TuningCatalog.tuning(it) }
        if (instrumentId != null && tuning != null) {
            val shapes = ChordVoicingCatalog.prioritizeForBeginner(ChordVoicingCatalog.forChord(symbol, instrumentId, tuningId))
            shapes.firstOrNull()?.let { shape ->
                val notes = shape.frets.mapIndexedNotNull { index, fret -> fret?.let { tuning.strings[index].midi + it + capo } }
                if (notes.isNotEmpty()) return ChordSound(notes, STRUM_GAP_S)
            }
        }
        val keys = PianoChordForms.forChord(symbol).firstOrNull() ?: return null
        // A capo shortens strings; a keyboard has none.
        val shift = if (instrumentId == PianoChordForms.INSTRUMENT_ID) 0 else capo
        return ChordSound(keys.midi.map { it + shift }, KEYBOARD_GAP_S)
    }
}
