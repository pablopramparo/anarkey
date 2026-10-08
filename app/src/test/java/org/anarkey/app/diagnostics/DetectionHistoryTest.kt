package org.anarkey.app.diagnostics

import org.anarkey.core.music.Note
import org.anarkey.core.tuner.SignalStatus
import org.anarkey.core.tuner.TunerState
import org.junit.Assert.*
import org.junit.Test

class DetectionHistoryTest {
    private val valid = TunerState(status = SignalStatus.STABLE, note = Note(45),
        frequencyHz = 110.0, rawFrequencyHz = 110.1, cents = 0.0, confidence = 0.98)

    @Test fun recordsOnlyAt250MillisecondIntervalsAndPreservesFields() {
        val history = DetectionHistory()
        history.record(valid, 0, 1000)
        for (time in 1L..249L) history.record(valid, time, time + 1000)
        assertEquals(1, history.entries.value.size)
        history.record(valid.copy(rawFrequencyHz = 109.9), 250, 1250)
        assertEquals(2, history.entries.value.size)
        assertEquals(DetectionEntry(1250, Note(45), 110.0, 109.9, 0.0, 0.98), history.entries.value.first())
    }

    @Test fun invalidFramesNeitherRecordNorConsumeThrottleSlot() {
        val history = DetectionHistory()
        val invalid = listOf(
            valid.copy(status = SignalStatus.UNCERTAIN), valid.copy(note = null),
            valid.copy(frequencyHz = null), valid.copy(rawFrequencyHz = null),
            valid.copy(cents = null), valid.copy(frequencyHz = Double.NaN),
            valid.copy(rawFrequencyHz = -1.0), valid.copy(cents = Double.POSITIVE_INFINITY),
            valid.copy(confidence = 0.3), valid.copy(confidence = Double.NaN),
        )
        invalid.forEach { history.record(it, 100, 1000) }
        assertTrue(history.entries.value.isEmpty())
        history.record(valid, 100, 1000)
        assertEquals(1, history.entries.value.size)
    }

    @Test fun keepsOnlyNewestThirtyAndDoesNotMutatePublishedSnapshots() {
        val history = DetectionHistory()
        history.record(valid, 0, 0)
        val oldSnapshot = history.entries.value
        for (index in 1L..40L) history.record(valid, index * 250, index)
        assertEquals(30, history.entries.value.size)
        assertEquals((40L downTo 11L).toList(), history.entries.value.map { it.timestampMs })
        assertEquals(listOf(0L), oldSnapshot.map { it.timestampMs })
    }

    @Test fun clearEmptiesHistoryAndAllowsImmediateNextValidDetection() {
        val history = DetectionHistory()
        history.record(valid, 100, 1000)
        history.clear()
        assertTrue(history.entries.value.isEmpty())
        history.record(valid, 101, 1001)
        assertEquals(listOf(1001L), history.entries.value.map { it.timestampMs })
    }

    @Test fun wallClockChangesDoNotAffectThrottle() {
        val history = DetectionHistory()
        history.record(valid, 1000, 10000)
        history.record(valid, 1100, 999999)
        assertEquals(1, history.entries.value.size)
        history.record(valid, 1250, 1)
        assertEquals(listOf(1L, 10000L), history.entries.value.map { it.timestampMs })
    }
}
