package org.anarkey.core

import org.anarkey.core.metronome.*
import org.junit.Assert.*
import org.junit.Test

class MetronomePcmTest {
    @Test fun quarterNoteMetersScheduleOneClickPerBeatAtExactTempo() {
        val planner = SamplePulsePlanner(48_000, MetronomeSettings(bpm = 60, meter = Meter.FOUR_FOUR))
        assertEquals(listOf(0L, 48_000L, 96_000L, 144_000L), List(4) { planner.nextPulse().frame })
    }

    @Test fun sixEightUsesDottedQuarterTempoAndSixEighthClicksInTwoGroups() {
        val planner = SamplePulsePlanner(48_000, MetronomeSettings(bpm = 60, meter = Meter.SIX_EIGHT))
        val pulses = List(6) { planner.nextPulse() }
        assertEquals(listOf(0L, 16_000L, 32_000L, 48_000L, 64_000L, 80_000L), pulses.map { it.frame })
        assertEquals(listOf(0, 1, 2, 3, 4, 5), pulses.map { it.beatInBar })
        assertEquals(Accent.PRIMARY, pulses[0].accent)
        assertEquals(Accent.SECONDARY, pulses[3].accent)
        assertEquals(0, planner.nextPulse().beatInBar)
    }

    @Test fun allSupportedMetersHaveExpectedClickCountsAndUnit() {
        assertEquals(listOf(2, 3, 4, 6), Meter.entries.map { it.clicksPerBar })
        assertFalse(Meter.FOUR_FOUR.isDottedQuarterTempo)
        assertTrue(Meter.SIX_EIGHT.isDottedQuarterTempo)
        Meter.entries.forEach { meter ->
            val planner = SamplePulsePlanner(48_000, MetronomeSettings(meter = meter))
            val bar = List(meter.clicksPerBar) { planner.nextPulse() }
            assertEquals((0 until meter.clicksPerBar).toList(), bar.map { it.beatInBar })
            assertEquals(0, planner.nextPulse().beatInBar)
        }
    }

    @Test fun configurablePulsePatternControlsEachPulseAndKeepsSixEightGrouping() {
        val sixEightPattern = listOf(Accent.PRIMARY, Accent.NORMAL, Accent.SECONDARY, Accent.PRIMARY, Accent.NORMAL, Accent.NORMAL)
        val settings = MetronomeSettings(meter = Meter.SIX_EIGHT, accentPatterns = mapOf(Meter.SIX_EIGHT to sixEightPattern))
        val planner = SamplePulsePlanner(48_000, settings)
        assertEquals(sixEightPattern, List(6) { planner.nextPulse().accent })

        val otherMeter = settings.copy(meter = Meter.FOUR_FOUR)
        assertEquals(Accent.PRIMARY, otherMeter.accentAt(0))
        assertEquals(Accent.SECONDARY, otherMeter.accentAt(2))
        assertEquals(sixEightPattern, otherMeter.copy(meter = Meter.SIX_EIGHT).let { value ->
            List(6) { value.accentAt(it) }
        })
    }

    @Test fun tempoChangeStartsAtNextPulseAndMeterChangeStartsANewBar() {
        val planner = SamplePulsePlanner(48_000, MetronomeSettings(bpm = 60, meter = Meter.FOUR_FOUR))
        assertEquals(0L, planner.nextPulse().frame)
        planner.updateAtNextPulse(MetronomeSettings(bpm = 120, meter = Meter.FOUR_FOUR))
        val second = planner.nextPulse()
        val third = planner.nextPulse()
        assertEquals(48_000L, second.frame)
        assertEquals(beatDuration(48_000, 120), third.frame - second.frame)

        planner.updateAtNextPulse(MetronomeSettings(bpm = 120, meter = Meter.THREE_FOUR))
        assertEquals(0, planner.nextPulse().beatInBar)
    }

    @Test fun fractionalSampleIntervalsDoNotAccumulateDrift() {
        val planner = SamplePulsePlanner(48_000, MetronomeSettings(bpm = 123, meter = Meter.FOUR_FOUR))
        val pulseCount = 20_000
        var last = 0L
        repeat(pulseCount) { last = planner.nextPulse().frame }
        val expected = ((pulseCount - 1L) * 48_000L * 60L) / 123L
        assertEquals(expected, last)
    }

    @Test fun pcmGeneratorMakesDistinctAccentsSoundsAndHonorsVolume() {
        val loud = MetronomePcmGenerator(48_000, MetronomeSettings(bpm = 30, meter = Meter.TWO_FOUR, volume = 1f))
        val normal = MetronomePcmGenerator(48_000, MetronomeSettings(bpm = 30, meter = Meter.TWO_FOUR, primaryAccent = false, secondaryAccent = false, volume = 1f))
        val muted = MetronomePcmGenerator(48_000, MetronomeSettings(bpm = 30, volume = 0f))
        val wood = ShortArray(1_200)
        val soft = ShortArray(1_200)
        val silence = ShortArray(1_200)
        loud.render(wood); normal.render(soft); muted.render(silence)
        assertTrue(wood.any { it != 0.toShort() })
        assertTrue(peak(wood) > peak(soft))
        assertTrue(silence.all { it == 0.toShort() })

        val digital = MetronomePcmGenerator(48_000, MetronomeSettings(sound = ClickSound.DIGITAL))
        val digitalPcm = ShortArray(1_200)
        digital.render(digitalPcm)
        assertFalse(wood.contentEquals(digitalPcm))
    }

    @Test fun visualBeatFollowsPlayedSampleFrameNotRenderedFuture() {
        val generator = MetronomePcmGenerator(48_000, MetronomeSettings(bpm = 60))
        generator.render(ShortArray(2_000))
        assertEquals(0, generator.beatAtPlayedFrame(0))
        assertEquals(0, generator.beatAtPlayedFrame(47_999))
        generator.render(ShortArray(48_000))
        assertEquals(1, generator.beatAtPlayedFrame(48_000))
    }

    @Test fun tapTempoUsesMedianFiltersOutliersAndResetsAfterPause() {
        val tap = TapTempoCalculator()
        assertNull(tap.tap(0))
        assertEquals(120, tap.tap(500))
        assertEquals(120, tap.tap(1_000))
        assertNull(tap.tap(1_100)) // rejected short outlier
        assertEquals(120, tap.tap(1_500))
        assertNull(tap.tap(4_000)) // long pause resets the interval window
        assertEquals(120, tap.tap(4_500))
        assertEquals(120, tap.tap(5_000))
    }

    private fun beatDuration(sampleRate: Long, bpm: Int) = sampleRate * 60L / bpm
    private fun peak(samples: ShortArray) = samples.maxOf { kotlin.math.abs(it.toInt()) }
}
