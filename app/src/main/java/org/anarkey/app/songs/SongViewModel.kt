package org.anarkey.app.songs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.anarkey.app.AnarkeyApplication
import org.anarkey.app.recording.data.*

class SongViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<AnarkeyApplication>()
    private val repository by lazy { SongRepository(app.recordings.database) }
    val songs by lazy { repository.songs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()) }
    fun songsForTag(tagId: String?) = repository.songsForTag(tagId)
    val tags by lazy { repository.tags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()) }
    val recordings by lazy { app.recordings.recordings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()) }
    private val _document = MutableStateFlow<SongDocument?>(null)
    val document = _document
    private val _error = MutableStateFlow<String?>(null)
    val error = _error
    private val lineSaves = mutableMapOf<String, kotlinx.coroutines.Job>()
    fun load(id: String) = viewModelScope.launch { runCatching { repository.document(id) }.onSuccess { _document.value = it }.onFailure { _error.value = it.message } }
    fun create(title: String, onCreated: (String) -> Unit) = viewModelScope.launch { runCatching { repository.create(title) }.onSuccess { onCreated(it.id) }.onFailure { _error.value = it.message } }
    fun saveMetadata(id: String, title: String, artist: String?, key: String?, mode: String?, bpm: Int?, numerator: Int?, denominator: Int?, instrument: String?, tuning: String?, capo: Int, notes: String) = viewModelScope.launch {
        runCatching { repository.save(id, title, artist, key, mode, bpm, numerator, denominator, instrument, tuning, capo, notes) }
            .onSuccess { load(id) }.onFailure { _error.value = it.message }
    }
    fun favorite(id: String, favorite: Boolean) = viewModelScope.launch { repository.toggleFavorite(id, favorite); load(id) }
    fun delete(id: String, done: () -> Unit) = viewModelScope.launch { runCatching { repository.deleteSong(id) }.onSuccess { done() }.onFailure { _error.value = it.message } }
    fun createSection(id: String, title: String, kind: String = "custom") = viewModelScope.launch { runCatching { repository.createSection(id, title, kind) }.onSuccess { load(id) }.onFailure { _error.value = it.message } }
    fun renameSection(songId: String, sectionId: String, title: String) = viewModelScope.launch { runCatching { repository.renameSection(sectionId, title) }.onSuccess { load(songId) }.onFailure { _error.value = it.message } }
    fun reorderSection(songId: String, sectionId: String, delta: Int) = viewModelScope.launch { repository.reorderSection(sectionId, delta); load(songId) }
    fun deleteSection(songId: String, sectionId: String) = viewModelScope.launch { repository.deleteSection(sectionId); load(songId) }
    fun moveLine(songId: String, lineId: String, sectionId: String?) = viewModelScope.launch { runCatching { repository.moveLine(lineId, sectionId) }.onSuccess { load(songId) }.onFailure { _error.value = it.message } }
    fun reorderLine(songId: String, lineId: String, delta: Int) = viewModelScope.launch { repository.reorderLine(lineId, delta); load(songId) }
    fun replaceLyrics(songId: String, sectionId: String?, text: String) = viewModelScope.launch { runCatching { repository.replaceLyrics(songId, sectionId, text) }.onSuccess { load(songId) }.onFailure { _error.value = it.message } }
    fun updateLineText(songId: String, lineId: String, text: String) {
        lineSaves[lineId]?.cancel()
        lineSaves[lineId] = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            runCatching { repository.updateLineText(lineId, text); _document.value = repository.document(songId) }
                .onFailure { _error.value = it.message }
        }
    }
    fun placeChord(songId: String, lineId: String, position: Int, symbol: String) = viewModelScope.launch { runCatching { repository.placeChord(lineId, position, symbol) }.onSuccess { load(songId) }.onFailure { _error.value = it.message } }
    fun editChord(songId: String, chordId: String, symbol: String, position: Int, targetLineId: String? = null) = viewModelScope.launch { runCatching { repository.editChord(chordId, symbol, position, targetLineId) }.onSuccess { load(songId) }.onFailure { _error.value = it.message } }
    fun deleteChord(songId: String, chordId: String) = viewModelScope.launch { repository.deleteChord(chordId); load(songId) }
    fun addTag(songId: String, name: String) = viewModelScope.launch { runCatching { repository.addTag(songId, name) }.onSuccess { load(songId) }.onFailure { _error.value = it.message } }
    fun removeTag(songId: String, tagId: String) = viewModelScope.launch { repository.removeTag(songId, tagId); load(songId) }
    fun setRecordingSong(recordingId: String, songId: String?) = viewModelScope.launch { runCatching { repository.setRecordingSong(recordingId, songId) }.onFailure { _error.value = it.message }; songId?.let(::load) }
    fun clearError() { _error.value = null }
    fun transpose(id: String, semitones: Int, flats: Boolean, done: () -> Unit) = viewModelScope.launch {
        runCatching {
            lineSaves.values.toList().forEach { it.join() }
            repository.transpose(id, semitones, flats)
            _document.value = repository.document(id)
        }.onSuccess { done() }.onFailure { _error.value = it.message; done() }
    }
    fun reportError(message: String) { _error.value = message }
}
