package org.anarkey.core

import org.anarkey.core.music.PitchMath
import org.anarkey.core.pitch.PartialTracker
import org.anarkey.core.pitch.YinPitchDetector
import org.anarkey.core.tuner.SignalStatus
import org.anarkey.core.tuner.TunerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/** Each partial only moves when the string does, whatever a period detector makes of their mix. */
class PartialTrackerTest {
    @Test fun reportsEachPartialWhereItIsNotAtAMultipleOfTheReference() {
        // As measured on a real E2: second and fourth partials about 10 cents below the third.
        val offsetsCents = mapOf(1 to -10.0, 2 to -10.0, 3 to 0.0, 4 to -9.0, 5 to -5.0)
        val tracker = PartialTracker(RATE)
        tracker.start(E2)
        var last = tracker.update(inharmonic(0, offsetsCents, thirdGain = 1.0))
        assertTrue("Needs two windows before it can measure a phase advance", last.isEmpty())
        for (frame in 1..8) last = tracker.update(inharmonic(frame, offsetsCents, thirdGain = 1.0))
        for (reading in last) {
            val expected = E2 * reading.harmonic * 2.0.pow(offsetsCents.getValue(reading.harmonic) / 1200.0)
            assertEquals("partial ${reading.harmonic}", 0.0, PitchMath.cents(reading.frequencyHz, expected), 0.5)
            assertTrue("partial ${reading.harmonic} stands out: ${reading.snrDb}", reading.snrDb > 20.0)
        }
    }

    @Test fun changingTheBalanceOfPartialsMovesYinButNoPartial() {
        val offsetsCents = mapOf(1 to -10.0, 2 to -10.0, 3 to 0.0, 4 to -9.0, 5 to -5.0)
        val yin = YinPitchDetector(RATE)
        val tracker = PartialTracker(RATE)
        tracker.start(E2)
        tracker.update(inharmonic(0, offsetsCents, thirdGain = 3.0))
        val strongThird = tracker.update(inharmonic(1, offsetsCents, thirdGain = 3.0)).associate { it.harmonic to it.frequencyHz }
        val yinStrongThird = yin.detect(inharmonic(1, offsetsCents, thirdGain = 3.0)).frequencyHz!!
        tracker.start(E2)
        tracker.update(inharmonic(0, offsetsCents, thirdGain = 0.3))
        val weakThird = tracker.update(inharmonic(1, offsetsCents, thirdGain = 0.3)).associate { it.harmonic to it.frequencyHz }
        val yinWeakThird = yin.detect(inharmonic(1, offsetsCents, thirdGain = 0.3)).frequencyHz!!

        val yinShift = PitchMath.cents(yinWeakThird, yinStrongThird)
        println("PARTIALS synthetic yin_shift_cents=${"%.1f".format(java.util.Locale.US, yinShift)}")
        assertTrue("YIN follows whichever partials dominate: $yinShift", yinShift < -3.0)
        for (harmonic in listOf(2, 4)) {
            assertEquals("partial $harmonic did not move", 0.0, PitchMath.cents(weakThird.getValue(harmonic), strongThird.getValue(harmonic)), 0.5)
        }
    }

    /** Samsung S21 open E2: the detector falls about 10 cents during the decay, the string does not. */
    @Test fun shownReadingIgnoresRebalancingOfPartialsButFollowsARealDetune() {
        val pcm = fixture("/audio/s21-e2-decay-capture.wav")
        val asRecorded = movement(pcm)
        // Read the same audio progressively slower: every partial really drops 10 cents.
        val detuned = movement(glide(pcm, startSeconds = 1.2, endSeconds = 2.7, cents = -10.0))
        println("PARTIALS capture as_recorded=$asRecorded detuned_10_cents=$detuned")

        assertTrue("The detector's reading drifts down as the note decays: ${asRecorded.detector}", asRecorded.detector < -5.0)
        assertTrue("The second partial stays where it was: ${asRecorded.secondPartial}", abs(asRecorded.secondPartial) <= 4.0)
        assertTrue("so the reading shown stays too: ${asRecorded.shown}", abs(asRecorded.shown) <= 4.0)
        assertTrue("A real 10 cent drop shows on the second partial: ${detuned.secondPartial}", detuned.secondPartial in -14.0..-6.0)
        assertTrue("and on the reading shown: ${detuned.shown}", detuned.shown in -14.0..-6.0)
    }

    private data class Movement(val detector: Double, val shown: Double, val secondPartial: Double) {
        override fun toString() = "(detector=%.1f, shown=%.1f, partial2=%.1f)".format(java.util.Locale.US, detector, shown, secondPartial)
    }

    /** Cents moved between 0.25-0.75 s and 1.5-2.25 s after the note is acquired; the attack is left out. */
    private fun movement(pcm: FloatArray): Movement {
        val engine = TunerEngine(YinPitchDetector(RATE), RATE, highPassCutoffHz = 60.0, hopSamples = 1024)
        val tracker = PartialTracker(RATE, harmonics = 2..2)
        val samples = FloatArray(4096)
        var acquiredAt = -1
        val early = ArrayList<Double>(); val late = ArrayList<Double>()
        val earlyShown = ArrayList<Double>(); val lateShown = ArrayList<Double>()
        val earlyPartial = ArrayList<Double>(); val latePartial = ArrayList<Double>()
        var start = 0
        while (start + samples.size <= pcm.size) {
            pcm.copyInto(samples, 0, start, start + samples.size)
            val state = engine.analyze(samples, start * 1000L / RATE)
            val stable = state.detectorFrequencyHz.takeIf { state.status == SignalStatus.STABLE }
            val shown = state.frequencyHz.takeIf { state.status == SignalStatus.STABLE }
            if (acquiredAt < 0 && stable != null) {
                acquiredAt = start
                tracker.start(stable)
            }
            if (acquiredAt >= 0) {
                val seconds = (start - acquiredAt).toDouble() / RATE
                val partial = tracker.update(samples).firstOrNull()?.frequencyHz
                if (seconds in 0.25..0.75) { stable?.let(early::add); shown?.let(earlyShown::add); partial?.let(earlyPartial::add) }
                if (seconds in 1.5..2.25) { stable?.let(late::add); shown?.let(lateShown::add); partial?.let(latePartial::add) }
            }
            start += 1024
        }
        return Movement(
            PitchMath.cents(late.median(), early.median()),
            PitchMath.cents(lateShown.median(), earlyShown.median()),
            PitchMath.cents(latePartial.median(), earlyPartial.median()),
        )
    }

    private fun List<Double>.median() = sorted()[size / 2]

    private fun glide(pcm: FloatArray, startSeconds: Double, endSeconds: Double, cents: Double): FloatArray {
        val out = FloatArray(pcm.size)
        var position = 0.0
        for (i in out.indices) {
            val progress = ((i.toDouble() / RATE - startSeconds) / (endSeconds - startSeconds)).coerceIn(0.0, 1.0)
            val index = position.toInt()
            if (index + 1 >= pcm.size) break
            val fraction = position - index
            out[i] = (pcm[index] * (1 - fraction) + pcm[index + 1] * fraction).toFloat()
            position += 2.0.pow(cents * progress / 1200.0)
        }
        return out
    }

    private fun inharmonic(frame: Int, offsetsCents: Map<Int, Double>, thirdGain: Double) = FloatArray(4096) { i ->
        val t = (frame * 1024 + i).toDouble() / RATE
        var value = 0.0
        for ((harmonic, offset) in offsetsCents) {
            val gain = if (harmonic == 3) thirdGain else 1.0
            value += gain * sin(2 * PI * E2 * harmonic * 2.0.pow(offset / 1200.0) * t + 0.4 * harmonic)
        }
        (0.05 * value).toFloat()
    }

    private fun fixture(name: String): FloatArray {
        val wav = javaClass.getResourceAsStream(name)!!.use { it.readBytes() }
        fun u16(at: Int) = (wav[at].toInt() and 0xff) or ((wav[at + 1].toInt() and 0xff) shl 8)
        fun i32(at: Int) = u16(at) or (u16(at + 2) shl 16)
        require(i32(24) == RATE)
        var dataAt = 12
        while (String(wav, dataAt, 4, Charsets.US_ASCII) != "data") dataAt += 8 + i32(dataAt + 4)
        return FloatArray(i32(dataAt + 4) / 2) { u16(dataAt + 8 + it * 2).toShort() / 32768f }
    }

    private companion object {
        const val RATE = 48000
        const val E2 = 82.4069
    }
}
