package org.anarkey.core.tuner

import org.anarkey.core.music.*
import org.anarkey.core.pitch.*
import org.anarkey.core.audio.HighPassFilter
import kotlin.math.*

data class InputLevel(val dbfs: Double, val clipped: Boolean)
object Levels {
    fun measure(samples: FloatArray): InputLevel {
        require(samples.isNotEmpty())
        var energy = 0.0
        var clipped = false
        for (value in samples) {
            if (!value.isFinite()) return InputLevel(-120.0, false)
            energy += value.toDouble() * value
            if (abs(value) >= 0.999) clipped = true
        }
        return InputLevel((10 * log10(max(energy / samples.size, 1e-12))).coerceAtMost(0.0), clipped)
    }
}

/** Three consecutive coherent estimates; a missing estimate clears immediately. */
class PitchStabilizer {
    private val history = DoubleArray(3)
    private var count = 0
    private var cursor = 0
    fun reset() { count = 0; cursor = 0 }
    fun update(hz: Double?): Double? {
        if (hz == null || !hz.isFinite() || hz <= 0) { reset(); return null }
        if (count > 0 && abs(PitchMath.cents(hz, history[(cursor + 2) % 3])) > 80.0) reset()
        history[cursor] = hz
        cursor = (cursor + 1) % 3
        count = min(count + 1, 3)
        if (count < 3) return null
        val a = history[0]; val b = history[1]; val c = history[2]
        return max(min(a, b), min(max(a, b), c))
    }
}

enum class SignalStatus { LISTENING, WEAK, UNCERTAIN, CLIPPING, STABLE, HOLDING }
data class TunerState(
    val status: SignalStatus = SignalStatus.LISTENING,
    val frequencyHz: Double? = null,
    val rawFrequencyHz: Double? = null,
    val note: Note? = null,
    val cents: Double? = null,
    val confidence: Double = 0.0,
    val inputDbfs: Double = -120.0,
    val direction: TuningDirection? = null,
    /** The detector's own stabilized reading, before partial anchoring. */
    val detectorFrequencyHz: Double? = null,
)

class TunerEngine(
    private val detector: PitchDetector,
    private val detectorSampleRate: Int = 48000,
    highPassCutoffHz: Double? = null,
    /** Samples between consecutive windows passed to [analyze]; enables partial-anchored readings. */
    hopSamples: Int? = null,
    private val a4Hz: Double = 440.0,
) {
    init { require(detectorSampleRate > 0); require(a4Hz.isFinite() && a4Hz > 0) }
    private val anchoredReading = hopSamples?.let { PartialAnchoredReading(detectorSampleRate, it) }
    private var energyRoseThisFrame = false
    private val highPassFilter = highPassCutoffHz?.let { HighPassFilter(detectorSampleRate, it) }
    private val stabilizer = PitchStabilizer()
    private var hannWindow = DoubleArray(0)
    private var inputEnabled = false
    private var previousNote: Note? = null
    // Keep the last accepted pitch as context for selecting among YIN minima.
    private var trustedPitchHz: Double? = null
    // The last note shown, kept until new energy arrives. A decaying signal cannot start a
    // lower note, so a subharmonic of this pitch needs evidence of its own partials.
    private var lastConfirmedPitchHz: Double? = null
    private val recentLevelsDbfs = DoubleArray(ENERGY_RISE_LOOKBACK_FRAMES) { Double.NaN }
    private var recentLevelCursor = 0
    private var previousLevelDbfs: Double? = null
    private var attackUntilMs: Long? = null
    private val challengerHistory = DoubleArray(CHALLENGER_HISTORY_SIZE) { Double.NaN }
    private var challengerCursor = 0
    private var challengerCount = 0
    private var lastStableState: TunerState? = null
    private var lastStableTimestampMs: Long? = null

    fun analyze(
        samples: FloatArray,
        timestampMs: Long = System.nanoTime() / 1_000_000,
        targetFrequencyHz: Double? = null,
    ): TunerState {
        val level = Levels.measure(samples)
        val stableAt = lastStableTimestampMs
        if (stableAt == null || timestampMs < stableAt || timestampMs - stableAt > TRACKED_REFERENCE_TIMEOUT_MS) {
            trustedPitchHz = null
        }
        val wasInputEnabled = inputEnabled
        inputEnabled = when {
            inputEnabled && level.dbfs <= INPUT_RELEASE_DBFS -> false
            !inputEnabled && level.dbfs >= INPUT_ENABLE_DBFS -> true
            else -> inputEnabled
        }
        val analysisSamples = highPassFilter?.process(samples) ?: samples
        val pitch = if (inputEnabled && !level.clipped) detector.detect(analysisSamples) else PitchResult()
        val candidate = selectCandidate(pitch, analysisSamples, level.dbfs, timestampMs, wasInputEnabled, targetFrequencyHz)
        val reference = trustedPitchHz
        val accepted = candidate?.takeIf {
            it.confidence >= MIN_CANDIDATE_CONFIDENCE ||
                (reference != null && abs(PitchMath.cents(it.frequencyHz, reference)) <= CONTINUITY_CENTS &&
                    it.confidence >= MIN_MAINTAIN_CONFIDENCE)
        }?.frequencyHz
        val detectorStable = stabilizer.update(accepted)
        val stable = if (anchoredReading == null) detectorStable
            else anchoredReading.update(samples, detectorStable, timestampMs, energyRoseThisFrame)
        // A small note-boundary hysteresis avoids B/C (etc.) label chatter.
        val note = stable?.let { hz ->
            previousNote?.takeIf { abs(PitchMath.cents(hz, PitchMath.frequency(it, a4Hz))) <= 55 }
                ?: PitchMath.nearestNote(hz, a4Hz)
        }
        previousNote = note
        val cents = if (stable != null && note != null) PitchMath.cents(stable, PitchMath.frequency(note, a4Hz)) else null
        val current = TunerState(
            status = when {
                level.clipped -> SignalStatus.CLIPPING
                level.dbfs <= -100 -> SignalStatus.LISTENING
                !inputEnabled -> SignalStatus.WEAK
                stable != null -> SignalStatus.STABLE
                else -> SignalStatus.UNCERTAIN
            },
            frequencyHz = stable, rawFrequencyHz = pitch.frequencyHz, note = note,
            cents = cents, confidence = pitch.confidence, inputDbfs = level.dbfs,
            direction = cents?.let(TuningThresholds::direction),
            detectorFrequencyHz = detectorStable,
        )

        if (current.status == SignalStatus.STABLE) {
            // Candidate selection keeps following what the detector itself reports.
            trustedPitchHz = detectorStable
            lastConfirmedPitchHz = detectorStable
            lastStableState = current
            lastStableTimestampMs = timestampMs
            return current
        }

        val lastStable = lastStableState
        val lastStableAt = lastStableTimestampMs
        if (lastStable != null && lastStableAt != null &&
            timestampMs >= lastStableAt && timestampMs - lastStableAt <= HOLD_DURATION_MS) {
            return lastStable.copy(
                status = SignalStatus.HOLDING,
                rawFrequencyHz = pitch.frequencyHz,
                confidence = pitch.confidence,
                inputDbfs = level.dbfs,
            )
        }

        lastStableState = null
        lastStableTimestampMs = null
        return current
    }

    private fun selectCandidate(
        pitch: PitchResult,
        samples: FloatArray,
        levelDbfs: Double,
        timestampMs: Long,
        wasInputEnabled: Boolean,
        targetFrequencyHz: Double?,
    ): PitchCandidate? {
        val previousLevel = previousLevelDbfs
        val attack = wasInputEnabled && previousLevel != null && levelDbfs - previousLevel >= ATTACK_RISE_DB
        previousLevelDbfs = levelDbfs
        energyRoseThisFrame = energyRose(levelDbfs)
        if (energyRoseThisFrame) lastConfirmedPitchHz = null
        if (!inputEnabled) {
            trustedPitchHz = null
            lastConfirmedPitchHz = null
            attackUntilMs = null
            clearChallengers()
            return pitch.primaryCandidate()
        }
        if (attack) {
            attackUntilMs = timestampMs + ATTACK_SETTLE_MS
        }

        val candidates = pitch.candidatePool().filter { it.confidence >= MIN_CHAIN_CONFIDENCE }
        if (candidates.isEmpty()) {
            observeChallenger(null)
            return null
        }
        val reference = trustedPitchHz
        if (pitch.frequencyHz == null) {
            // The detector named no pitch, only weaker minima: enough to keep the tracked
            // note through its decay, never to start a note or change to another one. A string
            // being damped slides well away from its pitch, so only a close minimum counts.
            observeChallenger(null)
            return reference?.let { tracked ->
                candidates.filter { abs(PitchMath.cents(it.frequencyHz, tracked)) <= WEAK_CONTINUITY_CENTS }
                    .minByOrNull { abs(PitchMath.cents(it.frequencyHz, tracked)) }
            }
        }
        val rawPrimary = pitch.frequencyHz?.let { hz -> candidates.minByOrNull { abs(PitchMath.cents(it.frequencyHz, hz)) } }
            ?: candidates.maxByOrNull { it.confidence }
        // A user-selected string is a strong prior: if its candidate is present, do not
        // discard it merely because YIN also found a half-frequency subharmonic.
        val targetCandidate = targetFrequencyHz?.takeIf { it.isFinite() && it > 0.0 }?.let { target ->
            candidates.filter { abs(PitchMath.cents(it.frequencyHz, target)) <= TARGET_HINT_MAX_CENTS }
                .minByOrNull { abs(PitchMath.cents(it.frequencyHz, target)) }
        }
        // With no tracked note, constrain chain analysis to the raw primary;
        // exclusive lower harmonics then protect real low notes from promotion.
        val chainReference = reference ?: rawPrimary?.frequencyHz
        val chainRoot = chainReference?.let { supportedHarmonicRoot(candidates, samples, it, hasTrackedReference = reference != null) }
        // A period that only exists as a common multiple of shorter ones (two strings ringing
        // together, or a subharmonic of one string) is never shown: fall back to the highest
        // real note it is a multiple of, or to nothing.
        val primary = targetCandidate ?: (chainRoot ?: rawPrimary)?.let { chosen ->
            if (!isPhantomPeriod(chosen, candidates, samples, reference)) chosen
            else candidates.filter {
                isHarmonicMultiple(it.frequencyHz, chosen.frequencyHz, 2..MAX_ENERGY_HARMONIC) &&
                    !isPhantomPeriod(it, candidates, samples, reference)
            }.maxByOrNull { it.frequencyHz }
        }

        // During the first part of a pluck, hold the old pitch (or acquire no
        // pitch yet). Attack transients can bias the estimate by a semitone.
        val until = attackUntilMs
        if (until != null && timestampMs < until) {
            val proposed = primary
            if (proposed != null && (reference == null || abs(PitchMath.cents(proposed.frequencyHz, reference)) > CONTINUITY_CENTS)) {
                observeChallenger(proposed.frequencyHz)
                return null
            }
            observeChallenger(null)
        }

        if (until != null && timestampMs >= until) attackUntilMs = null

        if (reference == null) {
            observeChallenger(null)
            return primary
        }

        // Prefer a credible YIN minimum consistent with the ringing pitch.
        // This selects the fundamental when YIN's first crossing is a
        // subharmonic but the corresponding higher-lag candidate is present.
        val continuous = candidates.filter { abs(PitchMath.cents(it.frequencyHz, reference)) <= CONTINUITY_CENTS }
            .minByOrNull { abs(PitchMath.cents(it.frequencyHz, reference)) }
        if (primary != null && abs(PitchMath.cents(primary.frequencyHz, reference)) > CONTINUITY_CENTS) {
            if (observeChallenger(primary.frequencyHz)) {
                attackUntilMs = null
                return primary
            }
            return continuous ?: PitchCandidate(reference, 1.0, 0.0)
        }
        observeChallenger(null)
        return continuous ?: primary
    }

    /** Promote an upper member of a harmonic chain only when its YIN minimum is comparably deep. */
    private fun supportedHarmonicRoot(candidates: List<PitchCandidate>, samples: FloatArray, referenceHz: Double, hasTrackedReference: Boolean): PitchCandidate? {
        val ordered = candidates.sortedBy { it.frequencyHz }
        val roots = ordered.filter { upper ->
            ordered.any { lower ->
                val ratio = upper.frequencyHz / lower.frequencyHz
                val harmonic = ratio.roundToInt()
                harmonic in 2..MAX_HARMONIC_CHAIN &&
                    (hasTrackedReference || harmonic >= MIN_UNTRACKED_ROOT_RATIO) &&
                    abs(PitchMath.cents(ratio, harmonic.toDouble())) <= HARMONIC_RELATION_CENTS &&
                    (abs(PitchMath.cents(lower.frequencyHz, referenceHz)) <= CONTINUITY_CENTS ||
                        abs(PitchMath.cents(upper.frequencyHz, referenceHz)) <= CONTINUITY_CENTS) &&
                    upper.yinDifference <= lower.yinDifference + HARMONIC_SUPPORT_MARGIN &&
                    exclusiveHarmonicEnergyRatio(samples, lower.frequencyHz, harmonic) < EXCLUSIVE_ENERGY_THRESHOLD
            }
        }
        return roots.maxByOrNull { it.frequencyHz }
    }

    /**
     * A candidate whose partials all belong to a few shorter periods has no partial of its own:
     * its period is a common multiple of periods that are really present (one string's
     * subharmonic, or two or three strings ringing together). The bar is lowest when nothing
     * else points at those shorter periods, so a genuine low note with a weak fundamental still
     * stands when nothing competes with it.
     */
    private fun isPhantomPeriod(candidate: PitchCandidate, pool: List<PitchCandidate>, samples: FloatArray, referenceHz: Double?): Boolean {
        val hz = candidate.frequencyHz
        if (referenceHz != null && abs(PitchMath.cents(hz, referenceHz)) <= CONTINUITY_CENTS) return false
        val explainedByPool = pool.any { isHarmonicMultiple(it.frequencyHz, hz, 2..MAX_ENERGY_HARMONIC) }
        val belowConfirmed = lastConfirmedPitchHz?.let { isHarmonicMultiple(it, hz, 2..MAX_HARMONIC_CHAIN) } ?: false
        val ownEnergy = ownPartialEnergyRatio(samples, hz)
        return ownEnergy < OWN_PARTIAL_MIN_ALONE ||
            (explainedByPool && ownEnergy < OWN_PARTIAL_MIN_WITH_MULTIPLE) ||
            (belowConfirmed && ownEnergy < OWN_PARTIAL_MIN_BELOW_CONFIRMED)
    }

    private fun isHarmonicMultiple(upperHz: Double, lowerHz: Double, multiples: Iterable<Int>): Boolean {
        val ratio = upperHz / lowerHz
        val harmonic = ratio.roundToInt()
        return harmonic in multiples && abs(PitchMath.cents(ratio, harmonic.toDouble())) <= HARMONIC_RELATION_CENTS
    }

    /**
     * Share of harmonic energy that shorter periods cannot account for. The lowest partials that
     * carry a harmonic series of their own (at most [MAX_SHORTER_PERIODS]) are taken as the notes
     * that may really be sounding; whatever is not on a multiple of one of them is the candidate's own.
     * Two strings a fourth apart leave partials 1, 5, 7 and 11 empty, a 3:4:5 triad 1, 2, 7 and 11;
     * a real low note keeps energy there even when its fundamental is weak.
     */
    private fun ownPartialEnergyRatio(samples: FloatArray, hz: Double): Double {
        val highest = min(MAX_ENERGY_HARMONIC, ((detectorSampleRate / 2.0) * 0.9 / hz).toInt())
        if (highest < 2) return 1.0
        val powers = DoubleArray(highest + 1)
        var total = 0.0
        for (harmonic in 1..highest) {
            powers[harmonic] = nearbyGoertzelPower(samples, hz * harmonic)
            total += powers[harmonic]
        }
        if (total <= 1e-20) return 1.0
        val shorter = IntArray(MAX_SHORTER_PERIODS)
        var shorterCount = 0
        for (harmonic in 2..highest) {
            if (shorterCount == shorter.size) break
            if ((0 until shorterCount).any { harmonic % shorter[it] == 0 }) continue
            var series = 0.0
            for (multiple in harmonic..highest step harmonic) series += powers[multiple]
            // A note's series starts at its own fundamental, however weak the microphone leaves it.
            if (series < SHORTER_PERIOD_MIN_SHARE * total || powers[harmonic] < SHORTER_PERIOD_MIN_FUNDAMENTAL * series) continue
            shorter[shorterCount++] = harmonic
        }
        var own = 0.0
        for (harmonic in 1..highest) {
            if ((0 until shorterCount).none { harmonic % shorter[it] == 0 }) own += powers[harmonic]
        }
        return own / total
    }

    /** True while the level is clearly above its recent minimum, i.e. something new was played. */
    private fun energyRose(levelDbfs: Double): Boolean {
        var floor = Double.NaN
        for (level in recentLevelsDbfs) if (level.isFinite() && (floor.isNaN() || level < floor)) floor = level
        recentLevelsDbfs[recentLevelCursor] = levelDbfs
        recentLevelCursor = (recentLevelCursor + 1) % recentLevelsDbfs.size
        return floor.isFinite() && levelDbfs - floor >= ATTACK_RISE_DB
    }

    /** Measure partials of the lower hypothesis that cannot belong to its proposed upper root. */
    private fun exclusiveHarmonicEnergyRatio(samples: FloatArray, lowerHz: Double, rootRatio: Int): Double {
        var exclusive = 0.0
        var shared = 0.0
        val highest = min(MAX_ENERGY_HARMONIC, ((detectorSampleRate / 2.0) * 0.9 / lowerHz).toInt())
        for (harmonic in 1..highest) {
            val power = nearbyGoertzelPower(samples, lowerHz * harmonic)
            if (harmonic % rootRatio == 0) shared += power else exclusive += power
        }
        val total = exclusive + shared
        return if (total <= 1e-20) 1.0 else exclusive / total
    }

    private fun nearbyGoertzelPower(samples: FloatArray, frequencyHz: Double): Double {
        if (hannWindow.size != samples.size) {
            hannWindow = DoubleArray(samples.size) { i -> 0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * i / (samples.size - 1)) }
        }
        val spread = frequencyHz * SPECTRAL_SEARCH_FRACTION
        var power = 0.0
        for (offset in doubleArrayOf(-spread, 0.0, spread)) {
            val omega = 2.0 * Math.PI * (frequencyHz + offset) / detectorSampleRate
            val coefficient = 2.0 * kotlin.math.cos(omega)
            var previous = 0.0
            var previous2 = 0.0
            for (i in samples.indices) {
                val current = samples[i] * hannWindow[i] + coefficient * previous - previous2
                previous2 = previous
                previous = current
            }
            power = max(power, previous * previous + previous2 * previous2 - coefficient * previous * previous2)
        }
        return max(power, 0.0)
    }

    /** Require a coherent challenger in four of the last six frames before either direction changes. */
    private fun observeChallenger(frequencyHz: Double?): Boolean {
        challengerHistory[challengerCursor] = frequencyHz ?: Double.NaN
        if (frequencyHz == null) {
            challengerCursor = (challengerCursor + 1) % CHALLENGER_HISTORY_SIZE
            challengerCount = min(challengerCount + 1, CHALLENGER_HISTORY_SIZE)
            return false
        }
        challengerCursor = (challengerCursor + 1) % CHALLENGER_HISTORY_SIZE
        challengerCount = min(challengerCount + 1, CHALLENGER_HISTORY_SIZE)
        var coherentCount = 0
        for (i in 0 until challengerCount) {
            val observed = challengerHistory[i]
            if (observed.isFinite() && abs(PitchMath.cents(observed, frequencyHz)) <= CHALLENGER_COHERENCE_CENTS) coherentCount++
        }
        return coherentCount >= CHALLENGER_REQUIRED_OBSERVATIONS
    }

    private fun clearChallengers() {
        challengerHistory.fill(Double.NaN)
        challengerCursor = 0
        challengerCount = 0
    }

    private fun PitchResult.primaryCandidate(): PitchCandidate? = frequencyHz?.let { PitchCandidate(it, confidence, 1.0 - confidence) }

    private fun PitchResult.candidatePool(): List<PitchCandidate> {
        val pool = ArrayList(candidates)
        val primary = primaryCandidate()
        if (primary != null && pool.none { abs(PitchMath.cents(it.frequencyHz, primary.frequencyHz)) < 10.0 }) pool += primary
        return pool
    }

    companion object {
        const val HOLD_DURATION_MS = 125L
        const val INPUT_ENABLE_DBFS = -60.0
        const val INPUT_RELEASE_DBFS = -62.0
        const val ATTACK_RISE_DB = 6.0
        const val ATTACK_SETTLE_MS = 90L
        const val CONTINUITY_CENTS = 140.0
        const val WEAK_CONTINUITY_CENTS = 50.0
        const val MIN_CANDIDATE_CONFIDENCE = 0.85
        const val MIN_MAINTAIN_CONFIDENCE = 0.70
        const val TRACKED_REFERENCE_TIMEOUT_MS = 250L
        const val MIN_ALTERNATE_CONFIDENCE = 0.70
        private const val TARGET_HINT_MAX_CENTS = 120.0
        private const val MIN_CHAIN_CONFIDENCE = 0.70
        private const val HARMONIC_RELATION_CENTS = 35.0
        private const val HARMONIC_SUPPORT_MARGIN = 0.20
        private const val MIN_UNTRACKED_ROOT_RATIO = 3
        private const val MAX_HARMONIC_CHAIN = 5
        private const val CHALLENGER_HISTORY_SIZE = 6
        private const val CHALLENGER_REQUIRED_OBSERVATIONS = 4
        private const val CHALLENGER_COHERENCE_CENTS = 30.0
        private const val MAX_ENERGY_HARMONIC = 12
        private const val EXCLUSIVE_ENERGY_THRESHOLD = 0.08
        private const val SPECTRAL_SEARCH_FRACTION = 0.015
        private const val MAX_SHORTER_PERIODS = 3
        private const val SHORTER_PERIOD_MIN_SHARE = 0.03
        private const val SHORTER_PERIOD_MIN_FUNDAMENTAL = 0.01
        private const val OWN_PARTIAL_MIN_ALONE = 0.005
        private const val OWN_PARTIAL_MIN_WITH_MULTIPLE = 0.015
        private const val OWN_PARTIAL_MIN_BELOW_CONFIRMED = 0.10
        // One analysis window: a pluck raises the windowed level gradually over these hops.
        private const val ENERGY_RISE_LOOKBACK_FRAMES = 4
    }
}
