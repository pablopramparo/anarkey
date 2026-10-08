package org.anarkey.core

import java.time.ZoneId
import java.util.Locale
import org.anarkey.core.recording.RecordingNames
import org.anarkey.core.recording.RecordingStateMachine
import org.anarkey.core.recording.RecordingStatus
import org.anarkey.core.recording.RecordingTimeline
import org.anarkey.core.recording.WaveformCodec
import org.junit.Assert.*
import org.junit.Test

class RecordingDomainTest {
    @Test fun pauseTimeDoesNotShiftMarkerPositions() {
        val timeline = RecordingTimeline()
        timeline.start(1_000)
        assertEquals(5_000, timeline.markerPositionMs(6_000))
        timeline.pause(6_000)
        assertEquals(5_000, timeline.markerPositionMs(500_000))
        timeline.resume(500_000)
        assertEquals(8_500, timeline.markerPositionMs(503_500))
        assertEquals(8_500, timeline.finish(503_500))
        assertEquals(8_500, timeline.markerPositionMs(800_000, 8_500))
    }

    @Test fun everyRecordingStatusHasOnlyIntentionalTransitions() {
        assertTrue(RecordingStateMachine.canTransition(RecordingStatus.RECORDING, RecordingStatus.PAUSED))
        assertTrue(RecordingStateMachine.canTransition(RecordingStatus.PAUSED, RecordingStatus.RECORDING))
        assertTrue(RecordingStateMachine.canTransition(RecordingStatus.FINALIZING, RecordingStatus.READY))
        assertTrue(RecordingStateMachine.canTransition(RecordingStatus.READY, RecordingStatus.DELETING))
        assertTrue(RecordingStateMachine.canTransition(RecordingStatus.DELETING, RecordingStatus.DELETING).not())
        assertFalse(RecordingStateMachine.canTransition(RecordingStatus.READY, RecordingStatus.RECORDING))
        assertFalse(RecordingStateMachine.canTransition(RecordingStatus.MISSING, RecordingStatus.READY))
    }

    @Test fun generatedNamesAreLocalizedAndIndependentOfFilesystemIdentifiers() {
        val time = 1_791_300_300_000L
        assertTrue(RecordingNames.generated(time, Locale("es", "AR"), ZoneId.of("UTC")).startsWith("Grabación "))
        assertTrue(RecordingNames.generated(time, Locale.US, ZoneId.of("UTC")).startsWith("Recording "))
    }

    @Test fun waveformIsBoundedAndDecodesUnsignedPeaks() {
        val source = floatArrayOf(-0.8f, 0.2f, Float.NaN, 0.5f, 0.1f, 1f)
        val encoded = WaveformCodec.encode(source, bins = 3)
        assertEquals(3, encoded.size)
        val decoded = WaveformCodec.decode(encoded)
        assertEquals(0.8f, decoded[0], 0.01f)
        assertEquals(0.5f, decoded[1], 0.01f)
        assertEquals(1f, decoded[2], 0.01f)
        assertEquals(0, WaveformCodec.encode(floatArrayOf(), 16).size)
    }
}
