package org.anarkey.core

import org.anarkey.core.music.PitchMath
import org.anarkey.core.music.TuningDirection
import org.anarkey.core.music.TuningIndicator
import org.anarkey.core.pitch.PitchCandidate
import org.anarkey.core.pitch.PitchDetector
import org.anarkey.core.pitch.PitchResult
import org.anarkey.core.pitch.YinPitchDetector
import org.anarkey.core.tuner.SignalStatus
import org.anarkey.core.tuner.TunerEngine
import org.anarkey.core.tuner.TunerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/** A note that keeps ringing must keep its reading and its verdict while it fades. */
class DecayTrackingTest {
    @Test fun yinKeepsWeakMinimaWhenNoneIsDeepEnoughToNameAPitch() {
        val noisy = SyntheticAudio.signal(E2, RATE, 0.3, doubleArrayOf(1.0, 0.5, 0.33), noise = 0.39)
        val result = YinPitchDetector(RATE).detect(noisy)
        assertNull("Too noisy to name a pitch", result.frequencyHz)
        val weak = result.candidates.filter { abs(PitchMath.cents(it.frequencyHz, E2)) <= 50.0 }
        assertTrue("The E2 minimum should survive as a weak candidate: ${result.candidates}", weak.any { it.confidence in 0.70..0.85 })
    }

    @Test fun trackedNoteSurvivesFramesWhereTheDetectorOnlyHasWeakCandidates() {
        var result = PitchResult(E2, 0.95)
        val engine = TunerEngine(PitchDetector { result })
        var clock = 0L
        fun step(): TunerState = engine.analyze(LOUD, clock.also { clock += HOP_MS })
        repeat(4) { step() }
        assertEquals(SignalStatus.STABLE, step().status)

        result = PitchResult(candidates = listOf(PitchCandidate(E2 * 0.998, 0.78, 0.22), PitchCandidate(E2 / 2, 0.82, 0.18)))
        repeat(12) {
            val state = step()
            assertEquals("A weak minimum at the tracked pitch keeps the note", SignalStatus.STABLE, state.status)
            assertEquals(E2 * 0.998, state.frequencyHz!!, 0.2)
        }
    }

    @Test fun weakCandidatesNeverStartOrChangeANote() {
        var result = PitchResult(candidates = listOf(PitchCandidate(E2, 0.84, 0.16)))
        val engine = TunerEngine(PitchDetector { result })
        var clock = 0L
        fun step(): TunerState = engine.analyze(LOUD, clock.also { clock += HOP_MS })
        repeat(12) { assertNull("Cold start needs a confident pitch", step().frequencyHz) }

        result = PitchResult(E2, 0.95)
        repeat(5) { step() }
        assertEquals(SignalStatus.STABLE, step().status)

        // Only a subharmonic remains as a weak minimum: the old note is held briefly, then dropped.
        result = PitchResult(candidates = listOf(PitchCandidate(E2 / 2, 0.84, 0.16)))
        repeat(12) {
            val state = step()
            assertTrue("Never show the weak subharmonic: ${state.frequencyHz}", state.frequencyHz == null || abs(PitchMath.cents(state.frequencyHz!!, E2)) <= 50.0)
        }
        assertEquals(SignalStatus.UNCERTAIN, step().status)
    }

    @Test fun inTuneIndicatorDoesNotFlickerWithReadingJitter() {
        val indicator = TuningIndicator()
        assertEquals(TuningDirection.IN_TUNE, indicator.update(-2.0))
        // The stable reading of a real string wanders by about four cents.
        for (cents in listOf(-5.8, -1.0, 2.5, -4.9, 5.9, -6.0)) assertEquals("at $cents", TuningDirection.IN_TUNE, indicator.update(cents))
    }

    @Test fun inTuneIndicatorEntersAtThreeCentsAndLeavesPastSix() {
        val indicator = TuningIndicator()
        assertEquals(TuningDirection.FLAT, indicator.update(-3.01))
        assertEquals(TuningDirection.SHARP, indicator.update(5.0))
        assertEquals(TuningDirection.IN_TUNE, indicator.update(3.0))
        assertEquals(TuningDirection.SHARP, indicator.update(6.01))
        assertEquals("Re-entering needs the tight threshold again", TuningDirection.SHARP, indicator.update(4.0))

        // A reading drifting from 0 to -11 cents leaves in-tune exactly once.
        val drifting = TuningIndicator()
        val verdicts = (0..22).map { drifting.update(-it * 0.5) }
        assertEquals(1, verdicts.zipWithNext().count { (a, b) -> a != b })
        assertEquals(TuningDirection.FLAT, verdicts.last())
        assertEquals(TuningDirection.IN_TUNE, verdicts[12])
    }

    @Test fun inTuneIndicatorForgetsWhenNothingIsShown() {
        val indicator = TuningIndicator()
        assertEquals(TuningDirection.IN_TUNE, indicator.update(1.0))
        assertNull(indicator.update(null))
        assertEquals(TuningDirection.FLAT, indicator.update(-5.0))
    }

    /** Samsung S21, open E2 plucked once and left to decay to the noise floor (about 3 s). */
    @Test fun capturedE2DecayKeepsItsReadingUntilTheNoiseFloor() {
        val wav = javaClass.getResourceAsStream("/audio/s21-e2-decay-capture.wav")!!.use { it.readBytes() }
        fun u16(at: Int) = (wav[at].toInt() and 0xff) or ((wav[at + 1].toInt() and 0xff) shl 8)
        val rate = u16(24) or (u16(26) shl 16)
        var dataAt = 12
        while (String(wav, dataAt, 4, Charsets.US_ASCII) != "data") dataAt += 8 + (u16(dataAt + 4) or (u16(dataAt + 6) shl 16))
        val pcm = ShortArray((u16(dataAt + 4) or (u16(dataAt + 6) shl 16)) / 2) { u16(dataAt + 8 + it * 2).toShort() }
        val engine = TunerEngine(YinPitchDetector(rate), rate, highPassCutoffHz = 60.0, hopSamples = 1024)
        val samples = FloatArray(4096)
        var frames = 0
        var shown = 0
        var wrongNote = 0
        var start = 0
        while (start + samples.size <= pcm.size) {
            for (i in samples.indices) samples[i] = pcm[start + i] / 32768f
            val state = engine.analyze(samples, start * 1000L / rate)
            // The pluck is 0.4 s into the clip.
            if (start >= rate * 4 / 10) {
                frames++
                val hz = state.frequencyHz.takeIf { state.status == SignalStatus.STABLE || state.status == SignalStatus.HOLDING }
                if (hz != null) {
                    shown++
                    if (abs(PitchMath.cents(hz, E2)) > 50.0) wrongNote++
                }
            }
            start += 1024
        }
        println("S21_E2_DECAY shown=$shown/$frames wrong_note=$wrongNote")
        assertEquals("The decaying E2 must never read as another note", 0, wrongNote)
        assertTrue("The reading should last through most of the decay ($shown/$frames)", shown >= frames * 8 / 10)
    }

    /** Samsung S21, open A string about 36 cents flat, plucked hard and gone within a second. */
    @Test fun shortHardPluckSettlesOnTheStringNotOnItsAttack() {
        // Its third and fifth partials sit steadily 36 cents below A2 for the whole note.
        val stringHz = 110.0 * 2.0.pow(-36.0 / 1200.0)
        val shown = replay("/audio/s21-a2-short-pluck-capture.wav")
            .filter { (seconds, state) -> seconds in 0.7..1.2 && state.status == SignalStatus.STABLE }
            .map { PitchMath.cents(it.second.frequencyHz!!, stringHz) }
            .sorted()
        assertTrue("The note should still be shown late in its decay", shown.size >= 10)
        val median = shown[shown.size / 2]
        println("S21_A2_SHORT shown_vs_string_cents=${"%.1f".format(java.util.Locale.US, median)}")
        assertTrue("The reading should sit on the string, not on the attack: $median", abs(median) <= 5.0)
    }

    /** Samsung S21, open E2 stopped with the hand: the pitch slides up as the string is damped. */
    @Test fun dampingAStringDoesNotShowTheNextNoteUp() {
        val wrong = replay("/audio/s21-e2-damped-capture.wav")
            .filter { (_, state) -> state.status == SignalStatus.STABLE || state.status == SignalStatus.HOLDING }
            .mapNotNull { it.second.frequencyHz }
            .filter { abs(PitchMath.cents(it, E2)) > 50.0 }
        assertTrue("A damped E2 must not read as another note: $wrong", wrong.isEmpty())
    }

    private fun replay(resource: String): List<Pair<Double, TunerState>> {
        val wav = javaClass.getResourceAsStream(resource)!!.use { it.readBytes() }
        fun u16(at: Int) = (wav[at].toInt() and 0xff) or ((wav[at + 1].toInt() and 0xff) shl 8)
        fun i32(at: Int) = u16(at) or (u16(at + 2) shl 16)
        val rate = i32(24)
        var dataAt = 12
        while (String(wav, dataAt, 4, Charsets.US_ASCII) != "data") dataAt += 8 + i32(dataAt + 4)
        val pcm = ShortArray(i32(dataAt + 4) / 2) { u16(dataAt + 8 + it * 2).toShort() }
        val engine = TunerEngine(YinPitchDetector(rate), rate, highPassCutoffHz = 60.0, hopSamples = 1024)
        val samples = FloatArray(4096)
        val states = ArrayList<Pair<Double, TunerState>>()
        var start = 0
        while (start + samples.size <= pcm.size) {
            for (i in samples.indices) samples[i] = pcm[start + i] / 32768f
            states += start.toDouble() / rate to engine.analyze(samples, start * 1000L / rate)
            start += 1024
        }
        return states
    }

    private companion object {
        const val RATE = 48000
        const val HOP_MS = 21L
        const val E2 = 82.4069
        val LOUD = FloatArray(4096) { 0.2f }
    }
}
