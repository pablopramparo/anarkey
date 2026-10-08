package org.anarkey.app.diagnostics

import android.util.Log
import android.os.SystemClock
import org.anarkey.app.BuildConfig
import org.anarkey.core.music.Note
import org.anarkey.core.pitch.PitchResult
import org.anarkey.core.music.PitchMath
import org.anarkey.core.tuner.SignalStatus
import org.anarkey.core.tuner.TunerState
import java.util.Locale
import java.util.ArrayDeque

/** Sparse debug-only transitions. Microphone samples are never logged. */
class TunerEventLogger {
    private data class TimedEvent(val elapsedMs: Long, val line: String)

    private val eventLock = Any()
    private val recentEvents = ArrayDeque<TimedEvent>()
    private var previousState: TunerState? = null
    private var holdStartedAtMs: Long? = null
    private var lastPitchSampleAtMs: Long? = null
    private var lastComparisonAtMs: Long? = null

    fun onFrame(state: TunerState, elapsedMs: Long, comparison: PitchResult? = null, a4Hz: Double = 440.0) {
        if (!BuildConfig.DEBUG) return
        if (comparison != null && (lastComparisonAtMs == null || elapsedMs - lastComparisonAtMs!! >= PITCH_SAMPLE_INTERVAL_MS)) {
            writeComparison(state, comparison, a4Hz)
            lastComparisonAtMs = elapsedMs
        }
        val previous = previousState
        when {
            state.status == SignalStatus.HOLDING && previous?.status != SignalStatus.HOLDING -> {
                holdStartedAtMs = elapsedMs
                write("SIGNAL_HOLDING", state, "duration_limit_ms=125")
            }
            previous?.status == SignalStatus.HOLDING && state.status == SignalStatus.STABLE -> {
                write("SIGNAL_HOLD_END", state, "result=RECOVERED duration_ms=${holdDuration(elapsedMs)}")
                holdStartedAtMs = null
                lastPitchSampleAtMs = elapsedMs
            }
            previous?.status == SignalStatus.HOLDING && state.status != SignalStatus.HOLDING -> {
                write("SIGNAL_HOLD_END", state, "result=EXPIRED next=${state.status} duration_ms=${holdDuration(elapsedMs)}")
                holdStartedAtMs = null
            }
            state.status == SignalStatus.STABLE && previous?.status != SignalStatus.STABLE -> {
                val event = if (previous == null || previous.status != SignalStatus.HOLDING) "SIGNAL_RECOVERED" else "PITCH_STABLE"
                write(event, state)
                lastPitchSampleAtMs = elapsedMs
            }
            state.status == SignalStatus.STABLE && state.note != previous?.note -> {
                write("PITCH_CHANGED", state)
                lastPitchSampleAtMs = elapsedMs
            }
            state.status == SignalStatus.STABLE &&
                (lastPitchSampleAtMs == null || elapsedMs - lastPitchSampleAtMs!! >= PITCH_SAMPLE_INTERVAL_MS) -> {
                write("PITCH_SAMPLE", state)
                lastPitchSampleAtMs = elapsedMs
            }
            previous == null || state.status != previous.status -> write("SIGNAL_GAP", state)
        }
        previousState = state
    }

    private fun writeComparison(state: TunerState, comparison: PitchResult, a4Hz: Double) {
        val yin = state.rawFrequencyHz?.let { String.format(Locale.US, "%.2f", it) } ?: "none"
        val mpmHz = comparison.frequencyHz
        val mpm = mpmHz?.let { String.format(Locale.US, "%.2f", it) } ?: "none"
        val note = mpmHz?.let { formatNote(PitchMath.nearestNote(it, a4Hz)) } ?: "none"
        val cents = if (mpmHz != null && yin != "none") {
            String.format(Locale.US, "%+.1f", PitchMath.cents(mpmHz, state.rawFrequencyHz!!))
        } else "none"
        emit("PITCH_COMPARE status=${state.status} yin_raw_hz=$yin mpm_hz=$mpm mpm_note=$note mpm_clarity=${String.format(Locale.US, "%.3f", comparison.confidence)} delta_cents=$cents")
    }

    /** Snapshot sparse tuner events covering the same recent interval as the exported audio. */
    fun recentLog(durationMs: Long): String {
        val now = SystemClock.elapsedRealtime()
        val cutoff = now - durationMs.coerceAtLeast(0L)
        val events = synchronized(eventLock) { recentEvents.filter { it.elapsedMs >= cutoff } }
        return buildString {
            appendLine("# Anarkey diagnostic event log")
            appendLine("# exported_at_epoch_ms=${System.currentTimeMillis()}")
            appendLine("# audio_duration_ms=${durationMs.coerceAtLeast(0L)}")
            events.forEach { appendLine("elapsed_ms=${it.elapsedMs} ${it.line}") }
        }
    }

    fun captureFailed(reason: String) {
        emit("CAPTURE_FAILED reason=$reason")
    }

    fun captureStarting(a4Hz: Double = 440.0) {
        synchronized(eventLock) { recentEvents.clear() }
        previousState = null
        holdStartedAtMs = null
        lastPitchSampleAtMs = null
        lastComparisonAtMs = null
        emit("CAPTURE_STARTING a4_hz=$a4Hz")
    }

    fun captureStopped() {
        emit("CAPTURE_STOPPED")
    }

    private fun holdDuration(elapsedMs: Long): Long? = holdStartedAtMs?.let { elapsedMs - it }

    private fun write(event: String, state: TunerState, detail: String = "") {
        val note = state.note?.let(::formatNote) ?: "none"
        val frequency = state.frequencyHz?.let { String.format(Locale.US, "%.2f", it) } ?: "none"
        val detectorFrequency = state.detectorFrequencyHz?.let { String.format(Locale.US, "%.2f", it) } ?: "none"
        val raw = state.rawFrequencyHz?.let { String.format(Locale.US, "%.2f", it) } ?: "none"
        val cents = state.cents?.let { String.format(Locale.US, "%+.2f", it) } ?: "none"
        val clarity = String.format(Locale.US, "%.3f", state.confidence)
        val input = String.format(Locale.US, "%.1f", state.inputDbfs)
        val fields = "status=${state.status} note=$note stable_hz=$frequency detector_hz=$detectorFrequency raw_hz=$raw cents=$cents clarity=$clarity input_dbfs=$input"
        emit("$event $fields${if (detail.isEmpty()) "" else " $detail"}")
    }

    private fun emit(line: String) {
        if (!BuildConfig.DEBUG) return
        val elapsed = SystemClock.elapsedRealtime()
        synchronized(eventLock) {
            recentEvents.addLast(TimedEvent(elapsed, line))
            val cutoff = elapsed - EVENT_BUFFER_MS
            while (recentEvents.peekFirst()?.elapsedMs?.let { it < cutoff } == true) recentEvents.removeFirst()
        }
        Log.i(TAG, line)
    }

    private fun formatNote(note: Note): String {
        val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        return "${names[note.pitchClass]}${note.octave}"
    }

    private companion object {
        const val TAG = "AnarkeyTuner"
        const val PITCH_SAMPLE_INTERVAL_MS = 250L
        const val EVENT_BUFFER_MS = 30_000L
    }
}
