package org.anarkey.core

import org.anarkey.core.music.PitchMath
import org.anarkey.core.pitch.YinPitchDetector
import org.anarkey.core.tuner.SignalStatus
import org.anarkey.core.tuner.TunerEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Synthetic bass strings: a phone microphone hears the low fundamental much weaker than its harmonics.
 * Real-instrument behaviour is not covered by these tests.
 */
class BassTunerTest {
    private val rate = 48_000
    private val strings = mapOf("E1" to 41.2034, "A1" to 55.0, "D2" to 73.4162, "G2" to 97.9989)

    private fun pluck(hz: Double, seconds: Double, cents: Double): FloatArray {
        val f = hz * 2.0.pow(cents / 1200.0)
        val harmonicLevel = doubleArrayOf(0.35, 1.0, 0.7, 0.45, 0.3, 0.2)
        return FloatArray((rate * seconds).toInt()) { n ->
            val t = n.toDouble() / rate
            var v = 0.0
            for (h in harmonicLevel.indices) v += harmonicLevel[h] * exp(-t * (0.9 + 0.5 * h)) * sin(2 * PI * f * (h + 1) * t)
            (v * 0.35).toFloat()
        }
    }

    private fun readings(hz: Double, cutoff: Double, cents: Double): List<Double> {
        val engine = TunerEngine(YinPitchDetector(rate), rate, highPassCutoffHz = cutoff, hopSamples = 1024)
        val signal = pluck(hz, 1.6, cents)
        val window = FloatArray(4096)
        val shown = mutableListOf<Double>()
        var start = 0
        while (start + window.size <= signal.size) {
            signal.copyInto(window, 0, start, start + window.size)
            val state = engine.analyze(window, start * 1000L / rate)
            val reading = state.frequencyHz
            if (reading != null && (state.status == SignalStatus.STABLE || state.status == SignalStatus.HOLDING)) shown += PitchMath.cents(reading, hz)
            start += 1024
        }
        return shown
    }

    @Test fun bassStringsReadOnTheStringWithALowHighPass() {
        for ((name, hz) in strings) {
            for (offset in listOf(0.0, -20.0, 15.0)) {
                val shown = readings(hz, 25.0, offset)
                println("BASS $name offset=$offset shown=${shown.size} median=${shown.sorted().getOrNull(shown.size / 2)}")
                assertTrue("$name ($offset cents) was never shown", shown.isNotEmpty())
                val median = shown.sorted()[shown.size / 2]
                assertTrue("$name ($offset cents) read ${"%.1f".format(median)} cents", abs(median - offset) <= 8.0)
            }
        }
    }
}
