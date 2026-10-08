package org.anarkey.core.recording

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Persisted values are part of the recovery contract; add new values without reusing old names. */
enum class RecordingStatus { RECORDING, PAUSED, FINALIZING, READY, RECOVERY_REQUIRED, MISSING, DELETING }

object RecordingStateMachine {
    private val allowed = mapOf(
        RecordingStatus.RECORDING to setOf(RecordingStatus.PAUSED, RecordingStatus.FINALIZING, RecordingStatus.RECOVERY_REQUIRED, RecordingStatus.DELETING),
        RecordingStatus.PAUSED to setOf(RecordingStatus.RECORDING, RecordingStatus.FINALIZING, RecordingStatus.RECOVERY_REQUIRED, RecordingStatus.DELETING),
        RecordingStatus.FINALIZING to setOf(RecordingStatus.READY, RecordingStatus.RECOVERY_REQUIRED, RecordingStatus.DELETING),
        RecordingStatus.READY to setOf(RecordingStatus.MISSING, RecordingStatus.DELETING),
        RecordingStatus.RECOVERY_REQUIRED to setOf(RecordingStatus.READY, RecordingStatus.MISSING, RecordingStatus.DELETING),
        RecordingStatus.MISSING to setOf(RecordingStatus.DELETING),
        RecordingStatus.DELETING to emptySet(),
    )
    fun canTransition(from: RecordingStatus, to: RecordingStatus) = to in allowed.getValue(from)
    fun requireTransition(from: RecordingStatus, to: RecordingStatus) {
        require(canTransition(from, to)) { "Invalid recording state transition: $from → $to" }
    }
}

/** Maps wall clock monotonic timestamps to the encoded timeline; a pause contributes no audio time. */
class RecordingTimeline {
    enum class State { IDLE, RUNNING, PAUSED, FINISHED }
    var state: State = State.IDLE
        private set
    private var activeSinceMs = 0L
    private var accumulatedMs = 0L

    fun start(monotonicMs: Long) {
        check(state == State.IDLE && monotonicMs >= 0)
        activeSinceMs = monotonicMs
        state = State.RUNNING
    }
    fun pause(monotonicMs: Long) {
        check(state == State.RUNNING && monotonicMs >= activeSinceMs)
        accumulatedMs += monotonicMs - activeSinceMs
        state = State.PAUSED
    }
    fun resume(monotonicMs: Long) {
        check(state == State.PAUSED && monotonicMs >= 0)
        activeSinceMs = monotonicMs
        state = State.RUNNING
    }
    fun positionMs(monotonicMs: Long): Long = when (state) {
        State.IDLE -> 0L
        State.RUNNING -> accumulatedMs + (monotonicMs - activeSinceMs).coerceAtLeast(0L)
        State.PAUSED, State.FINISHED -> accumulatedMs
    }
    fun markerPositionMs(monotonicMs: Long, finalDurationMs: Long? = null): Long =
        positionMs(monotonicMs).coerceAtLeast(0L).let { position -> finalDurationMs?.let(position::coerceAtMost) ?: position }
    fun finish(monotonicMs: Long): Long {
        check(state == State.RUNNING || state == State.PAUSED)
        if (state == State.RUNNING) pause(monotonicMs)
        state = State.FINISHED
        return accumulatedMs
    }
}

object RecordingNames {
    fun generated(createdAtMs: Long, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String {
        val timestamp = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(locale).withZone(zone).format(Instant.ofEpochMilli(createdAtMs))
        val prefix = if (locale.language == "es") "Grabación" else "Recording"
        return String.format(locale, "%s %s", prefix, timestamp)
    }
}

/** Compact, bounded waveform thumbnail stored as one unsigned byte per peak. Regenerable from audio. */
object WaveformCodec {
    const val DEFAULT_BINS = 256
    fun encode(samples: FloatArray, bins: Int = DEFAULT_BINS): ByteArray {
        require(bins > 0)
        if (samples.isEmpty()) return ByteArray(0)
        val peaks = FloatArray(bins)
        samples.forEachIndexed { index, sample ->
            if (sample.isFinite()) {
                val bin = (index.toLong() * bins / samples.size).toInt().coerceAtMost(bins - 1)
                peaks[bin] = maxOf(peaks[bin], kotlin.math.abs(sample).coerceIn(0f, 1f))
            }
        }
        return ByteArray(bins) { index -> (peaks[index] * 255f).toInt().coerceIn(0, 255).toByte() }
    }
    fun decode(encoded: ByteArray?): FloatArray = encoded?.map { (it.toInt() and 0xFF) / 255f }?.toFloatArray() ?: FloatArray(0)
}
