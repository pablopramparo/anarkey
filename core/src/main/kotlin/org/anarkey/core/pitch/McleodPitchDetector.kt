package org.anarkey.core.pitch

import kotlin.math.*

/** McLeod Pitch Method (NSDF peak picking), kept separate for prototype comparison. */
class McleodPitchDetector(
    private val sampleRate: Int,
    private val windowSize: Int = 4096,
    private val minHz: Double = 35.0,
    private val maxHz: Double = 1000.0,
    private val cutoff: Double = 0.9,
) : PitchDetector {
    init {
        require(sampleRate > 0 && windowSize >= 8)
        require(minHz > 0 && maxHz > minHz && maxHz < sampleRate / 2.0)
        require(cutoff in 0.0..1.0)
    }

    private val minLag = max(2, floor(sampleRate / maxHz).toInt())
    private val maxLag = min(windowSize / 2, floor(sampleRate / minHz).toInt())
    private val nsdf = DoubleArray(maxLag + 2)

    override fun detect(samples: FloatArray): PitchResult {
        require(samples.size == windowSize)
        if (samples.any { !it.isFinite() }) return PitchResult()

        for (lag in 1..maxLag) {
            var correlation = 0.0
            var energy = 0.0
            for (i in 0 until windowSize - lag) {
                val a = samples[i].toDouble()
                val b = samples[i + lag].toDouble()
                correlation += a * b
                energy += a * a + b * b
            }
            val value = if (energy > 1e-20) (2.0 * correlation / energy).coerceIn(-1.0, 1.0) else 0.0
            nsdf[lag] = value
        }

        // Ignore the initial NSDF lobe around lag zero. It is not a period
        // candidate and can otherwise look like a very high note (~1 kHz).
        var globalPeak = 0.0
        var lag = minLag
        var sawNegative = hasNegativeBeforeMinLag()
        while (lag <= maxLag) {
            if (nsdf[lag] <= 0.0) {
                sawNegative = true
                lag++
            } else if (sawNegative) {
                var end = lag
                while (end < maxLag && nsdf[end + 1] > 0.0) end++
                var peakLag = lag
                for (candidate in lag + 1..end) if (nsdf[candidate] > nsdf[peakLag]) peakLag = candidate
                if (nsdf[peakLag] > globalPeak) globalPeak = nsdf[peakLag]
                lag = end + 1
            } else lag++
        }
        if (globalPeak < cutoff) return PitchResult()

        var selected = -1
        lag = minLag
        sawNegative = hasNegativeBeforeMinLag()
        var selectedClarity = 0.0
        while (lag <= maxLag) {
            if (nsdf[lag] <= 0.0) {
                sawNegative = true
                lag++
            } else if (sawNegative) {
                var end = lag
                while (end < maxLag && nsdf[end + 1] > 0.0) end++
                var peakLag = lag
                for (candidate in lag + 1..end) if (nsdf[candidate] > nsdf[peakLag]) peakLag = candidate
                if (nsdf[peakLag] >= globalPeak * cutoff) {
                    selected = peakLag
                    selectedClarity = nsdf[peakLag]
                    break
                }
                lag = end + 1
            } else lag++
        }
        if (selected < 0) return PitchResult()

        val left = nsdf[selected - 1]
        val center = nsdf[selected]
        val right = nsdf[selected + 1]
        val denominator = left - 2.0 * center + right
        val offset = if (abs(denominator) > 1e-20) 0.5 * (left - right) / denominator else 0.0
        val frequency = sampleRate / (selected + offset.coerceIn(-1.0, 1.0))
        return if (frequency.isFinite() && frequency in minHz..maxHz) {
            PitchResult(frequency, selectedClarity.coerceIn(0.0, 1.0))
        } else PitchResult()
    }

    private fun hasNegativeBeforeMinLag(): Boolean {
        for (lag in 1 until minLag) if (nsdf[lag] <= 0.0) return true
        return false
    }
}
