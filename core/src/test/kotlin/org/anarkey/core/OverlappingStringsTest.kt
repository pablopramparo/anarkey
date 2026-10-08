package org.anarkey.core

import org.anarkey.core.music.PitchMath
import org.anarkey.core.pitch.YinPitchDetector
import org.anarkey.core.tuner.SignalStatus
import org.anarkey.core.tuner.TunerEngine
import org.anarkey.core.tuner.TunerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Two strings ringing together. A monophonic tuner may show either audible string or nothing,
 * but never a frequency neither string has; a string 10 dB or more above the other must be the
 * one shown, if any. Nothing here assumes the most recently plucked string wins.
 */
class OverlappingStringsTest {
    @Test fun steadyMixturesNeverShowAPhantomFrequency() {
        for ((spectrumName, spectrum) in SPECTRA) for ((first, second) in PAIRS) for (levelDb in listOf(0.0, -6.0, -12.0, -20.0)) {
            val engine = appEngine()
            val gain = 10.0.pow(levelDb / 20.0)
            val shown = ArrayList<Double>()
            repeat(48) { frame ->
                val state = engine.analyze(mix(frame, first to 1.0, second to gain, spectrum = spectrum), frame * HOP_MS)
                state.shownHz()?.let(shown::add)
            }
            val name = "$spectrumName ${first}Hz + ${second}Hz at $levelDb dB"
            val phantoms = shown.filter { !it.near(first) && !it.near(second) }
            println("OVERLAP_STEADY $name shown=${shown.size}/48 first=${shown.count { it.near(first) }} second=${shown.count { it.near(second) }} phantom_hz=${phantoms.histogram()}")
            assertTrue("$name showed phantom frequencies ${phantoms.histogram()}", phantoms.isEmpty())
            if (levelDb <= -10.0) {
                assertEquals("$name must show the dominant string or nothing", 0, shown.count { !it.near(first) })
            }
        }
    }

    @Test fun pluckedStringDecayingIntoARingingOneNeverShowsTheirCommonPeriod() {
        for ((spectrumName, spectrum) in SPECTRA) for ((ringing, plucked) in PAIRS) {
            val engine = appEngine()
            val shown = ArrayList<Double>()
            var frame = 0
            repeat(24) { engine.analyze(mix(frame, ringing to 1.0, spectrum = spectrum), frame++ * HOP_MS).shownHz()?.let(shown::add) }
            // The new string enters 8 dB above the ringing one and decays past it, as in the phone capture.
            repeat(96) { step ->
                val pluckedDb = 8.0 - 12.0 * step / 96.0
                val state = engine.analyze(mix(frame, ringing to 1.0, plucked to 10.0.pow(pluckedDb / 20.0), spectrum = spectrum), frame++ * HOP_MS)
                state.shownHz()?.let(shown::add)
            }
            val name = "$spectrumName ${plucked}Hz plucked over ${ringing}Hz"
            val phantoms = shown.filter { !it.near(ringing) && !it.near(plucked) }
            println("OVERLAP_DECAY $name shown=${shown.size}/120 ringing=${shown.count { it.near(ringing) }} plucked=${shown.count { it.near(plucked) }} phantom_hz=${phantoms.histogram()}")
            assertTrue("$name showed phantom frequencies ${phantoms.histogram()}", phantoms.isEmpty())
        }
    }

    @Test fun legitimateLowNotesAreStillDetected() {
        for ((spectrumName, spectrum) in SPECTRA) {
            // Cold start on a real low D2.
            var engine = appEngine()
            var state = TunerState()
            repeat(24) { frame -> state = engine.analyze(mix(frame, D2 to 1.0, spectrum = spectrum), frame * HOP_MS) }
            assertTrue("$spectrumName cold D2 resolved ${state.frequencyHz}", state.shownHz()?.near(D2) == true)

            // D2 plucked while its upper octave D3 is still ringing more quietly.
            engine = appEngine()
            var frame = 0
            repeat(24) { engine.analyze(mix(frame, D3 to 0.5, spectrum = spectrum), frame++ * HOP_MS) }
            repeat(36) { state = engine.analyze(mix(frame, D3 to 0.15, D2 to 1.0, spectrum = spectrum), frame++ * HOP_MS) }
            println("LOW_NOTE $spectrumName D2 plucked over a fading D3 resolved ${state.frequencyHz}")
            // With a weak fundamental the mix is almost all even partials of D2, which is the
            // documented octave-ambiguity boundary; only the full spectrum is asserted.
            if (spectrumName == "plucked") assertTrue("D2 plucked over a fading D3 resolved ${state.frequencyHz}", state.shownHz()?.near(D2) == true)
        }
    }

    @Test fun threeStringsInThreeFourFiveRatioNeverShowTheirCommonPeriod() {
        for ((spectrumName, spectrum) in SPECTRA) for ((tuningName, strings) in TRIADS) {
            val (low, mid, high) = strings
            // Cold start on the three strings at equal level.
            var engine = appEngine()
            val steady = ArrayList<Double>()
            repeat(48) { frame ->
                engine.analyze(mix(frame, low to 1.0, mid to 1.0, high to 1.0, spectrum = spectrum), frame * HOP_MS).shownHz()?.let(steady::add)
            }
            val steadyPhantoms = steady.filter { hz -> strings.none { hz.near(it) } }
            println("TRIAD_STEADY $spectrumName $tuningName shown=${steady.size}/48 phantom_hz=${steadyPhantoms.histogram()}")
            assertTrue("$spectrumName $tuningName steady triad showed ${steadyPhantoms.histogram()}", steadyPhantoms.isEmpty())

            // As in the phone capture: the top string is tracked, then it and the middle string fade
            // under the lowest one, whose third subharmonic is also the period of all three.
            engine = appEngine()
            val fading = ArrayList<Double>()
            var settledOther = 0
            var frame = 0
            repeat(24) { engine.analyze(mix(frame, low to 0.5, mid to 0.3, high to 1.0, spectrum = spectrum), frame++ * HOP_MS).shownHz()?.let(fading::add) }
            repeat(72) { step ->
                val fade = 10.0.pow(-18.0 * (step + 1) / 72.0 / 20.0)
                val state = engine.analyze(mix(frame, low to 0.5, mid to 0.3 * fade, high to 1.0 * fade * fade, spectrum = spectrum), frame++ * HOP_MS)
                val hz = state.shownHz() ?: return@repeat
                fading.add(hz)
                // In the last third the lowest string leads the others by more than 10 dB.
                if (step >= 48 && !hz.near(low)) settledOther++
            }
            val fadingPhantoms = fading.filter { hz -> strings.none { hz.near(it) } }
            println("TRIAD_FADING $spectrumName $tuningName shown=${fading.size}/96 low=${fading.count { it.near(low) }} mid=${fading.count { it.near(mid) }} high=${fading.count { it.near(high) }} phantom_hz=${fadingPhantoms.histogram()} other_when_low_dominates=$settledOther")
            assertTrue("$spectrumName $tuningName fading triad showed ${fadingPhantoms.histogram()}", fadingPhantoms.isEmpty())
            assertEquals("$spectrumName $tuningName must show the dominant low string or nothing", 0, settledOther)
        }
    }

    @Test fun realG1IsStillDetected() {
        for ((spectrumName, spectrum) in LOW_NOTE_SPECTRA) {
            // Cold start on a real G1, whose third to fifth partials sit where D3, G3 and B3 would.
            var engine = appEngine()
            var state = TunerState()
            repeat(24) { frame -> state = engine.analyze(mix(frame, G1 to 1.0, spectrum = spectrum), frame * HOP_MS) }
            println("REAL_G1 $spectrumName cold resolved ${state.frequencyHz}")
            assertTrue("$spectrumName cold G1 resolved ${state.frequencyHz}", state.shownHz()?.near(G1) == true)

            // G1 plucked while a B3 it is the fifth subharmonic of is still ringing quietly.
            engine = appEngine()
            var frame = 0
            repeat(24) { engine.analyze(mix(frame, 5 * G1 to 0.4, spectrum = spectrum), frame++ * HOP_MS) }
            repeat(36) { state = engine.analyze(mix(frame, 5 * G1 to 0.1, G1 to 1.0, spectrum = spectrum), frame++ * HOP_MS) }
            println("REAL_G1 $spectrumName plucked over its fifth harmonic resolved ${state.frequencyHz}")
            assertTrue("$spectrumName G1 plucked over B3 resolved ${state.frequencyHz}", state.shownHz()?.near(G1) == true)
        }
    }

    private fun appEngine() = TunerEngine(YinPitchDetector(RATE), RATE, highPassCutoffHz = 60.0, hopSamples = 1024)

    private fun mix(frame: Int, vararg strings: Pair<Double, Double>, spectrum: DoubleArray): FloatArray {
        val out = FloatArray(4096)
        for ((index, string) in strings.withIndex()) {
            val part = SyntheticAudio.signal(string.first, RATE, 0.1 * string.second, spectrum, phase = 0.37 + 1.3 * index, offset = frame * 1024)
            for (i in out.indices) out[i] += part[i]
        }
        return out
    }

    private fun TunerState.shownHz() = frequencyHz.takeIf { status == SignalStatus.STABLE || status == SignalStatus.HOLDING }
    private fun Double.near(hz: Double) = abs(PitchMath.cents(this, hz)) <= 50.0
    private fun List<Double>.histogram() = groupingBy { it.roundToInt() }.eachCount().toSortedMap()

    private companion object {
        const val RATE = 48000
        const val HOP_MS = 1024_000L / RATE
        // Fundamentals measured on the captured guitar; its strings are not at equal-tempered pitch.
        const val E2 = 79.8
        const val A2 = 108.5
        const val D3 = 144.6
        const val D2 = 72.3
        const val G1 = 49.0
        val TRIADS = listOf(
            "exact 3:4:5" to listOf(144.6, 192.8, 241.0),
            "captured D3 G3 B3" to listOf(144.4, 193.4, 245.0),
        )
        val LOW_NOTE_SPECTRA = listOf(
            "plucked" to doubleArrayOf(1.0, 0.5, 0.33, 0.25, 0.2, 0.17, 0.14, 0.12, 0.1, 0.09, 0.08, 0.07),
            // Small microphone: almost no fundamental, the usual 1/h roll-off above it.
            "weak-fundamental" to doubleArrayOf(0.2, 1.0, 0.7, 0.5, 0.4, 0.3, 0.25, 0.2, 0.15, 0.12, 0.1, 0.08),
        )
        val PAIRS = listOf(E2 to A2, A2 to E2, A2 to D3, D3 to A2)
        val SPECTRA = listOf(
            "plucked" to doubleArrayOf(1.0, 0.5, 0.33, 0.25, 0.2, 0.17, 0.14, 0.12),
            // Phone microphone: weak fundamental, energy mostly in the second to fourth partials.
            "phone-mic" to doubleArrayOf(0.18, 1.0, 0.35, 0.6, 0.12, 0.15, 0.08, 0.05),
        )
    }
}
