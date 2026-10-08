package org.anarkey.app.recording

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.anarkey.app.AnarkeyApplication
import org.anarkey.core.recording.RecordingStatus as DomainStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingRepositoryTest {
    @Test fun processRestartReconcilesAndPreservesTheSingleTargetedPartial() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AnarkeyApplication>()
        app.ensureReconciled()
        val repository = app.recordings
        val injectedId = InstrumentationRegistry.getArguments().getString("recoveryRecordingId")
        val row = injectedId?.let { repository.find(it) ?: throw AssertionError("Target recovery row was not found") }
            ?: repository.createRecording()
        val partial = repository.files.temporary(row.id)
        if (injectedId == null) partial.writeBytes(byteArrayOf(1, 2, 3, 4))
        try {
            repository.reconcile()
            assertEquals(DomainStatus.RECOVERY_REQUIRED, repository.find(row.id)!!.status)
            assertTrue("Interrupted audio must remain on disk", partial.isFile && partial.length() > 0)
        } finally {
            repository.find(row.id)?.let {
                repository.transition(row.id, DomainStatus.DELETING)
                repository.retryDeletes()
            }
        }
    }

    @Test fun sessionDeletionUnlinksAndMarkersBelongToTheirRecording() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AnarkeyApplication>()
        app.ensureReconciled()
        val repository = app.recordings
        val session = repository.createSession("Ensayo", "apuntes")
        val row = repository.createRecording()
        repository.markRecovery(row.id)
        repository.saveDetails(row.id, row.displayName, "idea", session.id)
        repository.transition(row.id, DomainStatus.READY)
        val ready = repository.find(row.id)!!
        assertNotNull(ready.sessionId)
        assertTrue(ready.displayName.isNotBlank())
        repository.files.validateId(ready.id)
        repository.deleteSession(session.id)
        assertNull(repository.find(row.id)!!.sessionId)

        val blockedPath = repository.files.temporary(row.id)
        assertTrue(blockedPath.mkdir())
        val blocker = File(blockedPath, "busy").apply { writeBytes(byteArrayOf(7)) }
        repository.transition(row.id, DomainStatus.DELETING)
        try {
            repository.retryDeletes()
            fail("A non-empty directory at the owned audio path should defer deletion")
        } catch (_: IOException) { }
        assertEquals(DomainStatus.DELETING, repository.find(row.id)!!.status)
        assertTrue(blocker.delete()); assertTrue(blockedPath.delete())
        repository.retryDeletes()
        repository.retryDeletes()
        assertNull(repository.find(row.id))
    }

    @Test fun incompleteCaptureReconcilesWithoutDeletingItsPartialFile() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AnarkeyApplication>()
        app.ensureReconciled()
        val repository = app.recordings
        val row = repository.createRecording()
        val partial = repository.files.temporary(row.id)
        partial.writeBytes(byteArrayOf(1, 2, 3, 4))
        try {
            repository.reconcile()
            assertEquals(DomainStatus.RECOVERY_REQUIRED, repository.find(row.id)!!.status)
            assertTrue(partial.exists())
            assertEquals(partial, repository.sourceFor(repository.find(row.id)!!))
        } finally {
            repository.files.deleteOwnFiles(row.id)
            repository.transition(row.id, DomainStatus.DELETING)
            repository.retryDeletes()
        }
    }

    @Test fun pausedTimelineMarkersPersistWithoutPauseDuration() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AnarkeyApplication>()
        app.ensureReconciled()
        val repository = app.recordings
        val row = repository.createRecording()
        try {
            repository.addMarker(row.id, 1_500)
            val marker = repository.observeMarkers(row.id).first().single()
            assertEquals(row.id, marker.recordingId)
            assertEquals(1_500, marker.positionMs)
            repository.editMarker(marker.id, row.id, "riff bueno")
            assertEquals("riff bueno", repository.observeMarkers(row.id).first().single().text)
        } finally {
            repository.transition(row.id, DomainStatus.DELETING)
            repository.retryDeletes()
        }
    }

    @Test fun readyRecordingCanAddNamedMarkerWithinItsDuration() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AnarkeyApplication>()
        app.ensureReconciled()
        val repository = app.recordings
        val row = repository.createRecording()
        try {
            repository.markFinalizing(row.id, 3_000, 1, 44_100, null)
            repository.markReady(row.id)
            repository.addMarkerAfterRecording(row.id, 1_500, "  idea nueva  ")
            val marker = repository.observeMarkers(row.id).first().single()
            assertEquals(1_500, marker.positionMs)
            assertEquals("idea nueva", marker.text)
            try {
                repository.addMarkerAfterRecording(row.id, 3_001, "fuera")
                fail("Marker past the end must be rejected")
            } catch (_: IllegalArgumentException) { }
            assertEquals(1, repository.observeMarkers(row.id).first().size)
        } finally {
            repository.transition(row.id, DomainStatus.DELETING)
            repository.retryDeletes()
        }
    }
}
