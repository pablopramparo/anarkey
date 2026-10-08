package org.anarkey.core

import org.anarkey.core.music.*
import org.anarkey.core.pitch.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.*
import kotlin.random.Random

internal object SyntheticAudio {
    fun signal(hz: Double, rate: Int, amplitude: Double, harmonics: DoubleArray,
               phase: Double = 0.0, noise: Double = 0.0, offset: Int = 0): FloatArray {
        val random = Random(734)
        val scale = harmonics.sumOf { abs(it) }
        return FloatArray(4096) { index ->
            val angle = 2 * PI * hz * (index + offset) / rate + phase
            var value = 0.0
            for (h in harmonics.indices) value += harmonics[h] * sin((h + 1) * angle)
            (amplitude * (value / scale + noise * (2 * random.nextDouble() - 1))).toFloat()
        }
    }
}

@RunWith(Parameterized::class)
class SyntheticPitchTest(
    private val name: String, private val hz: Double, private val midi: Int,
    private val rate: Int, private val amplitude: Double, private val shape: DoubleArray,
    private val phase: Double, private val detune: Double,
) {
    @Test fun detectsFrequencyNoteOctaveAndCents() {
        val expected = hz * 2.0.pow(detune / 1200)
        val result = YinPitchDetector(rate).detect(SyntheticAudio.signal(expected, rate, amplitude, shape, phase))
        assertTrue("$name: no pitch", result.isPitched)
        val actual = result.frequencyHz!!
        assertEquals("$name: frequency error in cents", 0.0, PitchMath.cents(actual, expected), 2.0)
        val note = PitchMath.nearestNote(actual)
        assertEquals(Note(midi), note)
        assertEquals(midi / 12 - 1, note.octave)
        assertEquals(detune, PitchMath.cents(actual, PitchMath.frequency(note)), 2.0)
        assertTrue(result.confidence >= 0.85)
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}, {3}Hz, amp={4}, phase={6}, cents={7}")
        fun cases(): Collection<Array<Any>> = buildList {
            val frequencies = listOf(82.4069 to 40, 110.0 to 45, 146.8324 to 50, 195.9977 to 55, 246.9417 to 59, 329.6276 to 64)
            val shapes = listOf(
                "sine" to doubleArrayOf(1.0),
                "second" to doubleArrayOf(1.0, 0.6),
                "multiple" to doubleArrayOf(1.0, 0.7, 0.5, 0.25, 0.1),
                "weak-fundamental" to doubleArrayOf(0.15, 1.0),
                "strong-third" to doubleArrayOf(0.15, 0.0, 1.0),
            )
            for ((hz, midi) in frequencies) for (rate in listOf(44100, 48000))
                for (amplitude in listOf(0.02, 0.2, 0.8)) for ((shapeName, shape) in shapes)
                    for (phase in listOf(0.0, 1.3)) for (detune in listOf(-15.0, 0.0, 15.0))
                        add(arrayOf("$hz-$shapeName", hz, midi, rate, amplitude, shape, phase, detune))
        }
    }
}

class DetectorRobustnessTest {
    @Test fun moderateNoiseAndBass() {
        for (rate in listOf(44100, 48000)) {
            val detector = YinPitchDetector(rate)
            for (hz in listOf(41.2034, 82.4069, 110.0, 146.8324, 195.9977, 246.9417, 329.6276)) {
                for (shape in listOf(doubleArrayOf(1.0), doubleArrayOf(1.0, 0.7, 0.3), doubleArrayOf(0.15, 1.0))) {
                    // Uniform noise +/-5% of amplitude, seeded and reproducible.
                    val result = detector.detect(SyntheticAudio.signal(hz, rate, 0.4, shape, 0.7, 0.05))
                    assertNotNull("$rate / $hz", result.frequencyHz)
                    assertEquals(0.0, PitchMath.cents(result.frequencyHz!!, hz), 5.0)
                }
            }
        }
    }
    @Test fun silenceDcNoiseAndInvalidValuesHaveNoPitch() {
        val detector = YinPitchDetector(48000)
        assertFalse(detector.detect(FloatArray(4096)).isPitched)
        assertFalse(detector.detect(FloatArray(4096) { 0.3f }).isPitched)
        val random = Random(12)
        repeat(20) { assertFalse(detector.detect(FloatArray(4096) { random.nextFloat() * 0.5f - 0.25f }).isPitched) }
        assertFalse(detector.detect(FloatArray(4096) { Float.NaN }).isPitched)
    }
    @Test fun pureSecondHarmonicIsNotInventedAsLowerFundamental() {
        val result = YinPitchDetector(48000).detect(SyntheticAudio.signal(164.8138, 48000, 0.5, doubleArrayOf(1.0)))
        assertEquals(0.0, PitchMath.cents(result.frequencyHz!!, 164.8138), 2.0)
    }
    @Test fun independentInstancesAndRepeatedBuffers() {
        val low = YinPitchDetector(44100)
        val high = YinPitchDetector(48000)
        repeat(10) {
            assertEquals(110.0, low.detect(SyntheticAudio.signal(110.0, 44100, 0.2, doubleArrayOf(1.0))).frequencyHz!!, 0.1)
            assertEquals(440.0, high.detect(SyntheticAudio.signal(440.0, 48000, 0.2, doubleArrayOf(1.0))).frequencyHz!!, 0.2)
        }
    }
    @Test fun diagnosticDesktopBenchmark() {
        val detector = YinPitchDetector(48000)
        val input = SyntheticAudio.signal(82.4069, 48000, 0.5, doubleArrayOf(1.0, 0.7))
        repeat(30) { detector.detect(input) }
        val times = DoubleArray(100) {
            val start = System.nanoTime()
            detector.detect(input)
            (System.nanoTime() - start) / 1e6
        }.sorted()
        println("Desktop YIN 4096/48000 ms: median=${times[50]}, p95=${times[95]}; NOT Android latency")
    }
}
