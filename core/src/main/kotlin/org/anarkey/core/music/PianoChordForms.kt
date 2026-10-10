package org.anarkey.core.music

/** One way to play a chord on a keyboard; [midi] is ascending, lowest key first. */
data class PianoVoicing(val id: String, val chordSymbol: String, val midi: List<Int>, val inversion: Int)

/**
 * Keyboard forms are derived straight from chord theory (root position plus inversions), so unlike fretted
 * instruments they need no curated catalog and no tuning: the piano is a chord-only instrument.
 */
object PianoChordForms {
    const val INSTRUMENT_ID = "piano"
    private const val ROOT_FLOOR = 48 // C3: the root of the root-position form lands in C3..B3
    private const val MAX_INVERSIONS = 4

    fun forChord(symbol: String): List<PianoVoicing> {
        val chord = ChordTheory.resolve(symbol) ?: return emptyList()
        val rootMidi = ROOT_FLOOR + chord.root.pitchClass
        val rootPosition = chord.tones.map { rootMidi + it.intervalSemitones }
        return (0 until minOf(rootPosition.size, MAX_INVERSIONS)).map { inversion ->
            val lifted = rootPosition.drop(inversion) + rootPosition.take(inversion).map { it + 12 }
            val keys = lifted.sorted().toMutableList()
            chord.bass?.let { bass ->
                var bassMidi = keys.first() - 1
                while (bassMidi.mod(12) != bass.pitchClass) bassMidi--
                keys.add(0, bassMidi)
            }
            PianoVoicing("piano.$symbol.$inversion", symbol, keys, inversion)
        }
    }
}
