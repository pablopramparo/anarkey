package org.anarkey.core.pitch

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin

/** One partial of a tracked note: where it really is, and how far it stands above its surroundings. */
data class PartialReading(val harmonic: Int, val frequencyHz: Double, val snrDb: Double)

/**
 * Follows individual partials of a note once its pitch is roughly known. A period detector
 * reports one compromise between partials, which shifts when their balance changes even though
 * none of them moved; each partial on its own only moves when the string does.
 *
 * Frequencies come from the phase advance between consecutive windows one hop apart, so the
 * caller must feed every hop in order. Stateful, single owner.
 */
class PartialTracker(
    private val sampleRate: Int,
    private val windowSize: Int = 4096,
    private val hopSize: Int = 1024,
    private val harmonics: IntRange = 2..4,
) {
    init {
        require(sampleRate > 0 && windowSize >= 8 && hopSize in 1..windowSize)
        require(harmonics.first >= 1 && !harmonics.isEmpty())
    }

    private val hann = DoubleArray(windowSize) { 0.5 - 0.5 * cos(2.0 * PI * it / (windowSize - 1)) }
    private var referenceHz = 0.0
    private val previousPhase = DoubleArray(harmonics.count())
    private var hasPrevious = false

    /** Fix the analysis frequencies at multiples of [fundamentalHz] and forget earlier windows. */
    fun start(fundamentalHz: Double) {
        require(fundamentalHz.isFinite() && fundamentalHz > 0.0)
        require(fundamentalHz * harmonics.last < sampleRate / 2.0)
        referenceHz = fundamentalHz
        hasPrevious = false
    }

    fun reset() {
        referenceHz = 0.0
        hasPrevious = false
    }

    /** Readings for the window just fed, or empty until two consecutive windows have been seen. */
    fun update(samples: FloatArray): List<PartialReading> {
        require(samples.size == windowSize)
        if (referenceHz <= 0.0) return emptyList()
        val readings = ArrayList<PartialReading>(previousPhase.size)
        val hopSeconds = hopSize.toDouble() / sampleRate
        for ((index, harmonic) in harmonics.withIndex()) {
            val centerHz = referenceHz * harmonic
            val (power, phase) = bin(samples, centerHz)
            if (hasPrevious) {
                // The partial is within a few hertz of the center, far inside the +-1/(2 hop) the
                // wrapped phase difference can tell apart.
                val expected = 2.0 * PI * centerHz * hopSeconds
                val deviation = wrap(phase - previousPhase[index] - expected)
                val frequency = centerHz + deviation / (2.0 * PI * hopSeconds)
                val floor = 0.5 * (bin(samples, centerHz - referenceHz / 2).first + bin(samples, centerHz + referenceHz / 2).first)
                readings += PartialReading(harmonic, frequency, 10.0 * log10(max(power, 1e-30) / max(floor, 1e-30)))
            }
            previousPhase[index] = phase
        }
        hasPrevious = true
        return readings
    }

    private fun bin(samples: FloatArray, frequencyHz: Double): Pair<Double, Double> {
        val omega = 2.0 * PI * frequencyHz / sampleRate
        val stepReal = cos(omega)
        val stepImaginary = sin(omega)
        var phasorReal = 1.0
        var phasorImaginary = 0.0
        var real = 0.0
        var imaginary = 0.0
        for (i in samples.indices) {
            val value = samples[i] * hann[i]
            real += value * phasorReal
            imaginary -= value * phasorImaginary
            val next = phasorReal * stepReal - phasorImaginary * stepImaginary
            phasorImaginary = phasorReal * stepImaginary + phasorImaginary * stepReal
            phasorReal = next
        }
        return (real * real + imaginary * imaginary) to atan2(imaginary, real)
    }

    private fun wrap(angle: Double): Double {
        var wrapped = angle % (2.0 * PI)
        if (wrapped > PI) wrapped -= 2.0 * PI
        if (wrapped < -PI) wrapped += 2.0 * PI
        return wrapped
    }
}
