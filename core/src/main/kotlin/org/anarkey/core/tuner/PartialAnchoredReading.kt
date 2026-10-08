package org.anarkey.core.tuner

import org.anarkey.core.music.PitchMath
import org.anarkey.core.pitch.PartialTracker
import kotlin.math.abs
import kotlin.math.pow

/**
 * Keeps a tracked note's reading tied to what its partials do. A period detector's estimate
 * drifts as the balance between slightly inharmonic partials changes during a decay; the
 * partials themselves only move when the string is retuned. Over the first second of a note
 * the detector's reading, with any movement of the partials taken out, settles into an anchor;
 * the reading shown is that anchor moved by however far the partials have moved.
 *
 * Must see every analysis window in order, one hop apart. Stateful, single owner.
 */
internal class PartialAnchoredReading(private val sampleRate: Int, private val hopSamples: Int) {
    private var tracker: PartialTracker? = null
    private var startedAtMs = 0L
    private var startedHz = 0.0
    private var active = false
    private var anchorHz = Double.NaN
    private var lastStableAtMs = 0L
    private val baselineHz = DoubleArray(HARMONICS.count()) { Double.NaN }
    private val anchorDetector = ArrayList<Double>()
    private val anchorSamples = ArrayList<Double>()
    private val anchorPartials = Array(HARMONICS.count()) { ArrayList<Double>() }
    private val recentMovement = Array(HARMONICS.count()) { ArrayDeque<Double>() }
    private var movementCents = 0.0
    private var divergedFrames = 0
    private val scratch = ArrayList<Double>()

    fun reset() {
        active = false
        tracker?.reset()
    }

    /**
     * [stableHz] is the detector's stabilized reading for this window, or null when it has none.
     * [newEnergy] marks a fresh pluck, which always starts a new anchor. Returns the reading to show.
     */
    fun update(samples: FloatArray, stableHz: Double?, timestampMs: Long, newEnergy: Boolean): Double? {
        if (newEnergy) reset()
        if (!active) {
            if (stableHz == null) return null
            begin(samples.size, stableHz, timestampMs)
        }
        val readings = tracker!!.update(samples)
        if (stableHz == null) {
            if (timestampMs - lastStableAtMs > LOST_NOTE_MS) reset()
            return null
        }
        lastStableAtMs = timestampMs
        if (abs(PitchMath.cents(stableHz, startedHz)) > SAME_NOTE_CENTS) {
            begin(samples.size, stableHz, timestampMs)
            return stableHz
        }

        if (anchorHz.isNaN()) {
            // The attack bends every partial for a moment; anchor on what follows it.
            val elapsed = timestampMs - startedAtMs
            if (elapsed >= ANCHOR_FROM_MS) {
                anchorDetector += stableHz
                for (reading in readings) if (reading.snrDb >= MIN_SNR_DB) anchorPartials[reading.harmonic - HARMONICS.first] += reading.frequencyHz
            }
            if (elapsed >= ANCHOR_AT_MS) {
                // A first guess from the tail of the attack; refined below as the note settles.
                anchorHz = median(anchorDetector)
                for (index in baselineHz.indices) {
                    baselineHz[index] = if (anchorPartials[index].size >= MIN_ANCHOR_READINGS) median(anchorPartials[index]) else Double.NaN
                }
            }
            return stableHz
        }
        if (baselineHz.all { it.isNaN() }) return stableHz

        scratch.clear()
        for (reading in readings) {
            val index = reading.harmonic - HARMONICS.first
            if (reading.snrDb < MIN_SNR_DB || baselineHz[index].isNaN()) continue
            val history = recentMovement[index]
            history.addLast(PitchMath.cents(reading.frequencyHz, baselineHz[index]))
            if (history.size > MOVEMENT_HISTORY) history.removeFirst()
            scratch += median(history)
        }
        // One partial can wander by itself; the string moving shows on several at once. Without
        // that agreement the last movement they agreed on stands.
        val agreed = scratch.size >= 3 || (scratch.size == 2 && abs(scratch[0] - scratch[1]) <= PARTIAL_AGREEMENT_CENTS)
        if (agreed) movementCents = median(scratch)
        if (timestampMs - startedAtMs <= ANCHOR_UNTIL_MS) {
            anchorSamples += stableHz / 2.0.pow(movementCents / 1200.0)
            if (anchorSamples.size >= MIN_ANCHOR_READINGS) anchorHz = median(anchorSamples)
        }
        val shown = anchorHz * 2.0.pow(movementCents / 1200.0)

        // The detector and the partials should stay within its usual drift of each other;
        // if they do not, the anchor no longer describes this note.
        divergedFrames = if (abs(PitchMath.cents(stableHz, shown)) > MAX_DIVERGENCE_CENTS) divergedFrames + 1 else 0
        if (divergedFrames >= DIVERGED_FRAMES_TO_REANCHOR) {
            begin(samples.size, stableHz, timestampMs)
            return stableHz
        }
        return shown
    }

    private fun begin(windowSize: Int, hz: Double, timestampMs: Long) {
        val current = tracker ?: PartialTracker(sampleRate, windowSize, hopSamples, HARMONICS).also { tracker = it }
        active = hz * HARMONICS.last < sampleRate / 2.0
        if (!active) return
        current.start(hz)
        startedAtMs = timestampMs
        startedHz = hz
        lastStableAtMs = timestampMs
        anchorHz = Double.NaN
        anchorDetector.clear()
        anchorSamples.clear()
        anchorPartials.forEach { it.clear() }
        recentMovement.forEach { it.clear() }
        baselineHz.fill(Double.NaN)
        movementCents = 0.0
        divergedFrames = 0
    }

    private fun median(values: Collection<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else 0.5 * (sorted[middle - 1] + sorted[middle])
    }

    private companion object {
        val HARMONICS = 2..5
        const val ANCHOR_FROM_MS = 125L
        const val ANCHOR_AT_MS = 250L
        const val ANCHOR_UNTIL_MS = 1000L
        const val PARTIAL_AGREEMENT_CENTS = 6.0
        const val MIN_ANCHOR_READINGS = 3
        const val MIN_SNR_DB = 15.0
        const val MOVEMENT_HISTORY = 5
        const val SAME_NOTE_CENTS = 80.0
        const val LOST_NOTE_MS = 250L
        const val MAX_DIVERGENCE_CENTS = 35.0
        const val DIVERGED_FRAMES_TO_REANCHOR = 6
    }
}
