package org.anarkey.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/** Compact logarithmic spectrum for the live meter; it does not feed or alter pitch detection. */
internal class SpectrumAnalyzer(
    private val sampleRate: Int,
    windowSize: Int,
    private val bandCount: Int = 40,
) {
    private val hann = DoubleArray(windowSize) { index ->
        if (windowSize < 2) 1.0 else 0.5 - 0.5 * cos(2.0 * PI * index / (windowSize - 1))
    }
    private val frequencies = DoubleArray(bandCount) { index ->
        val low = MIN_HZ
        val high = minOf(MAX_HZ, sampleRate * MAX_NYQUIST_FRACTION)
        low * kotlin.math.exp(ln(high / low) * index / (bandCount - 1))
    }

    init {
        require(sampleRate > 0)
        require(windowSize >= 2)
        require(bandCount >= 2)
        require(sampleRate * MAX_NYQUIST_FRACTION > MIN_HZ)
    }

    /** Returns per-band amplitudes scaled to the strongest band in this window (0..1). */
    fun analyze(samples: FloatArray): FloatArray {
        require(samples.size == hann.size)
        val powers = DoubleArray(bandCount)
        var maxPower = 0.0
        for (band in frequencies.indices) {
            val omega = 2.0 * PI * frequencies[band] / sampleRate
            val coefficient = 2.0 * cos(omega)
            var previous = 0.0
            var previous2 = 0.0
            for (index in samples.indices) {
                val current = samples[index] * hann[index] + coefficient * previous - previous2
                previous2 = previous
                previous = current
            }
            val power = max(previous * previous + previous2 * previous2 - coefficient * previous * previous2, 0.0)
            powers[band] = power
            if (power > maxPower) maxPower = power
        }
        if (maxPower <= MIN_POWER) return FloatArray(bandCount)
        return FloatArray(bandCount) { sqrt(powers[it] / maxPower).toFloat().coerceIn(0f, 1f) }
    }

    private companion object {
        const val MIN_HZ = 55.0
        const val MAX_HZ = 1000.0
        const val MAX_NYQUIST_FRACTION = 0.45
        const val MIN_POWER = 1e-20
    }
}
