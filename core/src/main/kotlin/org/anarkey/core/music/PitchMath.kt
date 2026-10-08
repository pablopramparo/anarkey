package org.anarkey.core.music

import kotlin.math.*

/** Canonical equal-tempered note; naming/localization belongs to presentation. */
data class Note(val midi: Int) {
    val pitchClass: Int get() = Math.floorMod(midi, 12)
    val octave: Int get() = Math.floorDiv(midi, 12) - 1
}

object PitchMath {
    fun frequency(note: Note, a4: Double = 440.0): Double {
        require(a4.isFinite() && a4 > 0)
        return a4 * 2.0.pow((note.midi - 69) / 12.0)
    }
    fun nearestNote(hz: Double, a4: Double = 440.0): Note {
        require(hz.isFinite() && hz > 0 && a4.isFinite() && a4 > 0)
        return Note((69 + 12 * log2(hz / a4)).roundToInt())
    }
    fun cents(measured: Double, target: Double): Double {
        require(measured.isFinite() && measured > 0 && target.isFinite() && target > 0)
        return 1200 * log2(measured / target)
    }
}

enum class TuningDirection { FLAT, IN_TUNE, SHARP }
object TuningThresholds {
    const val IN_TUNE_CENTS = 3.0
    /** Once in tune, the indicator stays there until the reading is this far off. */
    const val IN_TUNE_RELEASE_CENTS = 6.0
    fun direction(cents: Double): TuningDirection {
        require(cents.isFinite())
        return when {
            cents < -IN_TUNE_CENTS -> TuningDirection.FLAT
            cents > IN_TUNE_CENTS -> TuningDirection.SHARP
            else -> TuningDirection.IN_TUNE
        }
    }
}

/**
 * In-tune indicator with hysteresis, so the few cents a reading wanders by do not make it
 * flicker: entered within [TuningThresholds.IN_TUNE_CENTS], left beyond
 * [TuningThresholds.IN_TUNE_RELEASE_CENTS]. Single owner; feed it every displayed reading.
 */
class TuningIndicator {
    private var inTune = false

    fun update(cents: Double?): TuningDirection? {
        if (cents == null || !cents.isFinite()) {
            inTune = false
            return null
        }
        val limit = if (inTune) TuningThresholds.IN_TUNE_RELEASE_CENTS else TuningThresholds.IN_TUNE_CENTS
        inTune = kotlin.math.abs(cents) <= limit
        return when {
            inTune -> TuningDirection.IN_TUNE
            cents < 0 -> TuningDirection.FLAT
            else -> TuningDirection.SHARP
        }
    }
}
