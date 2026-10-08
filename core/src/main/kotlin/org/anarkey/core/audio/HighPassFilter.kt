package org.anarkey.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Fourth-order Butterworth high-pass applied independently to each analysis window. */
class HighPassFilter(sampleRate: Int, cutoffHz: Double) {
    private val sections: Array<Biquad>

    init {
        require(sampleRate > 0)
        require(cutoffHz > 0.0 && cutoffHz < sampleRate / 2.0)
        val omega = 2.0 * PI * cutoffHz / sampleRate
        val cosine = cos(omega)
        val sine = sin(omega)
        val qValues = doubleArrayOf(1.0 / (2.0 * cos(PI / 8.0)), 1.0 / (2.0 * cos(3.0 * PI / 8.0)))
        sections = Array(2) { index ->
            val alpha = sine / (2.0 * qValues[index])
            val a0 = 1.0 + alpha
            Biquad(
                b0 = ((1.0 + cosine) / 2.0) / a0,
                b1 = (-(1.0 + cosine)) / a0,
                b2 = ((1.0 + cosine) / 2.0) / a0,
                a1 = (-2.0 * cosine) / a0,
                a2 = (1.0 - alpha) / a0,
            )
        }
    }

    fun process(input: FloatArray): FloatArray {
        var output = input.copyOf()
        for (section in sections) output = section.process(output)
        return output
    }

    private class Biquad(
        private val b0: Double,
        private val b1: Double,
        private val b2: Double,
        private val a1: Double,
        private val a2: Double,
    ) {
        fun process(input: FloatArray): FloatArray {
            val output = FloatArray(input.size)
            if (input.isEmpty()) return output
            // A high-pass has zero steady-state response to the initial DC level.
            var x1 = input[0].toDouble()
            var x2 = x1
            var y1 = 0.0
            var y2 = 0.0
            for (i in input.indices) {
                val x = input[i].toDouble()
                val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
                output[i] = y.toFloat()
                x2 = x1
                x1 = x
                y2 = y1
                y1 = y
            }
            return output
        }
    }
}
