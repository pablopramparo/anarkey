package org.anarkey.app.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.anarkey.core.music.Note
import org.anarkey.core.tuner.SignalStatus
import org.anarkey.core.tuner.TunerState

data class DetectionEntry(
    val timestampMs: Long,
    val note: Note,
    val frequencyHz: Double,
    val rawFrequencyHz: Double,
    val cents: Double,
    val clarity: Double,
)

/** Prototype diagnostics only. In-memory, newest first; never stores audio. */
class DetectionHistory {
    private val mutableEntries = MutableStateFlow<List<DetectionEntry>>(emptyList())
    val entries = mutableEntries.asStateFlow()
    private var lastRecordedElapsedMs: Long? = null

    // Capture runs on IO, Clear on Main. Serialize both to prevent stale snapshots
    // from reappearing after Clear. Wall-clock adjustments must not affect throttle.
    @Synchronized
    fun record(state: TunerState, elapsedMs: Long, timestampMs: Long) {
        if (state.status != SignalStatus.STABLE) return
        val note = state.note ?: return
        val frequency = state.frequencyHz ?: return
        val raw = state.rawFrequencyHz ?: return
        val cents = state.cents ?: return
        if (!frequency.isFinite() || frequency <= 0 || !raw.isFinite() || raw <= 0 ||
            !cents.isFinite() || !state.confidence.isFinite() || state.confidence !in 0.85..1.0) return
        val previous = lastRecordedElapsedMs
        if (previous != null && elapsedMs - previous < INTERVAL_MS) return
        val entry = DetectionEntry(timestampMs, note, frequency, raw, cents, state.confidence)
        mutableEntries.value = listOf(entry) + mutableEntries.value.take(CAPACITY - 1)
        lastRecordedElapsedMs = elapsedMs
    }

    @Synchronized
    fun clear() {
        mutableEntries.value = emptyList()
        lastRecordedElapsedMs = null
    }

    companion object {
        const val CAPACITY = 30
        const val INTERVAL_MS = 250L
    }
}
