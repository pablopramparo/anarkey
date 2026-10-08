package org.anarkey.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SpectrumAnalyzerTest {
    @Test
    fun silenceProducesNoFrequencyBars() {
        val analyzer = SpectrumAnalyzer(sampleRate = 48_000, windowSize = 4096)

        assertTrue(analyzer.analyze(FloatArray(4096)).all { it == 0f })
    }

    @Test
    fun aToneProducesALocalizedSpectrumWithNormalizedPeak() {
        val sampleRate = 48_000
        val samples = FloatArray(4096) { index ->
            sin(2.0 * PI * 110.0 * index / sampleRate).toFloat()
        }
        val spectrum = SpectrumAnalyzer(sampleRate, samples.size).analyze(samples)

        assertEquals(40, spectrum.size)
        assertEquals(1f, spectrum.maxOrNull()!!, 1e-6f)
        assertTrue("single note should occupy a subset of the spectrum", spectrum.count { it > 0.4f } < 10)
    }
}
