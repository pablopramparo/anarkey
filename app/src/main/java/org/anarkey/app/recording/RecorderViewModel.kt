package org.anarkey.app.recording

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.anarkey.app.AnarkeyApplication
import org.anarkey.app.recording.data.RecordingEntity

class RecorderViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<AnarkeyApplication>()
    val recordings = app.recordings.recordings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sessions = app.recordings.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val runtime = RecordingRuntimeStore.value
    fun start(songId: String? = null) = send(RecordingService.ACTION_START, foreground = true, songId = songId)
    fun pause() = send(RecordingService.ACTION_PAUSE)
    fun resume() = send(RecordingService.ACTION_RESUME)
    fun mark() = send(RecordingService.ACTION_MARK)
    fun stop() = send(RecordingService.ACTION_STOP)
    fun cancel() = send(RecordingService.ACTION_CANCEL)

    private fun send(action: String, foreground: Boolean = false, songId: String? = null) {
        val intent = Intent(getApplication(), RecordingService::class.java).setAction(action)
        songId?.let { intent.putExtra(RecordingService.EXTRA_SONG_ID, it) }
        if (foreground) ContextCompat.startForegroundService(getApplication(), intent)
        else getApplication<Application>().startService(intent)
    }

    fun observeMarkers(recordingId: String) = app.recordings.observeMarkers(recordingId)
    fun addMarkerAfterRecording(recordingId: String, positionMs: Long, text: String?) = viewModelScope.launch {
        app.recordings.addMarkerAfterRecording(recordingId, positionMs, text)
    }
    fun createSession(name: String, notes: String = "") = viewModelScope.launch { app.recordings.createSession(name, notes) }
    fun saveSession(id: String, name: String, notes: String) = viewModelScope.launch { app.recordings.saveSession(id, name, notes) }
    fun deleteSession(id: String) = viewModelScope.launch { app.recordings.deleteSession(id) }
    fun saveDetails(row: RecordingEntity, name: String, notes: String, sessionId: String?) = viewModelScope.launch {
        app.recordings.saveDetails(row.id, name, notes, sessionId)
    }
    fun editMarker(id: String, recordingId: String, text: String?) = viewModelScope.launch {
        app.recordings.editMarker(id, recordingId, text)
    }
    fun deleteMarker(id: String, recordingId: String) = viewModelScope.launch {
        app.recordings.deleteMarker(id, recordingId)
    }
    fun delete(row: RecordingEntity) = viewModelScope.launch { app.recordings.delete(row.id) }
    suspend fun shareUri(row: RecordingEntity) = app.recordings.shareUri(getApplication<Application>(), row.id)
    suspend fun export(row: RecordingEntity, uri: android.net.Uri) = app.recordings.export(row.id, uri, getApplication<Application>().contentResolver)
    fun playbackFile(row: RecordingEntity) = app.recordings.sourceFor(row)
}
