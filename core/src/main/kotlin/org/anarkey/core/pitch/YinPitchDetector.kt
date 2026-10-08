/*
 * Adapted from Tunify Yin.kt, commit 0a8f8b9ac952a394d9d740b2fb363c09be7acd8a.
 * Copyright (c) 2023 Stavros Barousis. MIT license; see THIRD_PARTY_NOTICES.md.
 * Anarkey changes: bounded lags, safe normalization, clarity, reusable buffers,
 * input validation, and evidence-based harmonic candidate comparison.
 */
package org.anarkey.core.pitch

import org.anarkey.core.music.PitchMath
import kotlin.math.*

data class PitchCandidate(val frequencyHz: Double, val confidence: Double, val yinDifference: Double)

data class PitchResult(
    val frequencyHz: Double? = null,
    val confidence: Double = 0.0,
    val candidates: List<PitchCandidate> = emptyList(),
) {
    val isPitched: Boolean get() = frequencyHz != null
}

fun interface PitchDetector { fun detect(samples: FloatArray): PitchResult }

/** Stateful, single-owner detector. PCM floats in [-1, 1]; no Android dependencies. */
class YinPitchDetector(
    private val sampleRate: Int,
    private val windowSize: Int = 4096,
    private val minHz: Double = 35.0,
    private val maxHz: Double = 1000.0,
) : PitchDetector {
    init {
        require(sampleRate > 0 && windowSize >= 8)
        require(minHz > 0 && maxHz > minHz && maxHz < sampleRate / 2.0)
        require(ceil(sampleRate / minHz).toInt() + 1 < windowSize / 2)
    }
    private val comparisonSize = windowSize / 2
    private val maxLag = ceil(sampleRate / minHz).toInt() + 1
    private val minLag = max(2, floor(sampleRate / maxHz).toInt())
    private val yin = DoubleArray(maxLag + 1)

    override fun detect(samples: FloatArray): PitchResult {
        require(samples.size == windowSize)
        if (samples.any { !it.isFinite() }) return PitchResult()
        yin[0] = 1.0
        var sum = 0.0
        for (tau in 1..maxLag) {
            var difference = 0.0
            for (j in 0 until comparisonSize) {
                val delta = samples[j].toDouble() - samples[j + tau]
                difference += delta * delta
            }
            sum += difference
            yin[tau] = if (sum > 1e-20) difference * tau / sum else 1.0
        }
        var tau = minLag
        while (tau < maxLag) {
            if (yin[tau] < 0.15) {
                while (tau + 1 < maxLag && yin[tau + 1] < yin[tau]) tau++
                break
            }
            tau++
        }
        // No minimum is deep enough to name a pitch, but the weaker ones still let a tracker
        // keep following a note it already has while that note decays into the noise.
        if (tau >= maxLag) return PitchResult(candidates = collectCandidates())
        val candidates = collectCandidates()
        // A strong second/third harmonic can cross the threshold first. Only
        // prefer a multiple when the waveform fits it substantially better.
        // Never infer a missing fundamental from an instrument's expected note.
        val first = tau
        if (yin[first] > 0.01) {
            for (multiple in 2..3) {
                val center = first * multiple
                val radius = multiple + 1
                if (center + radius >= maxLag) continue
                var best = center
                for (candidate in center - radius..center + radius) {
                    if (yin[candidate] < yin[best]) best = candidate
                }
                if (yin[best] < yin[tau] * 0.2) tau = best
            }
        }
        val s0 = yin[tau - 1]
        val s1 = yin[tau]
        val s2 = yin[tau + 1]
        val denominator = 2.0 * s1 - s2 - s0
        val offset = if (abs(denominator) > 1e-20) 0.5 * (s2 - s0) / denominator else 0.0
        val frequency = sampleRate / (tau + offset.coerceIn(-1.0, 1.0))
        return if (frequency.isFinite() && frequency in minHz..maxHz) {
            PitchResult(frequency, (1.0 - s1).coerceIn(0.0, 1.0), candidates)
        } else PitchResult()
    }

    /** Preserve alternate YIN minima so temporal tracking can arbitrate octave candidates. */
    private fun collectCandidates(): List<PitchCandidate> {
        val result = ArrayList<PitchCandidate>(8)
        for (candidateLag in minLag + 1 until maxLag) {
            val value = yin[candidateLag]
            if (value > MAX_CANDIDATE_DIFFERENCE || value > yin[candidateLag - 1] || value > yin[candidateLag + 1]) continue
            val before = yin[candidateLag - 1]
            val after = yin[candidateLag + 1]
            val denominator = 2.0 * value - after - before
            val offset = if (abs(denominator) > 1e-20) 0.5 * (after - before) / denominator else 0.0
            val frequency = sampleRate / (candidateLag + offset.coerceIn(-1.0, 1.0))
            if (frequency in minHz..maxHz && result.none { abs(PitchMath.cents(it.frequencyHz, frequency)) < 15.0 }) {
                result += PitchCandidate(frequency, (1.0 - value).coerceIn(0.0, 1.0), value)
            }
        }
        return result
    }

    private companion object { const val MAX_CANDIDATE_DIFFERENCE = 0.30 }
}
