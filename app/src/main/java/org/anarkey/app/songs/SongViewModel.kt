package org.anarkey.app.songs

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.anarkey.app.AnarkeyApplication
import org.anarkey.app.chords.ChordPlayer
import org.anarkey.app.recording.data.*
import org.anarkey.core.music.ChordSounding
import org.anarkey.core.music.DemoSongs
import org.anarkey.core.music.toExchange
import org.anarkey.core.exchange.*
import org.anarkey.core.music.PlanChord
import org.anarkey.core.music.PlanLine
import org.anarkey.core.music.PlaybackStep
import org.anarkey.core.music.SongPlaybackPlan
import org.anarkey.core.music.stepSeconds

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
    fun placeChord(songId: String, lineId: String, position: Int, symbol: String, figure: String?) = viewModelScope.launch { runCatching { repository.placeChord(lineId, position, symbol, figure) }.onSuccess { load(songId) }.onFailure { _error.value = it.message } }
    fun editChord(songId: String, chordId: String, symbol: String, position: Int, figure: String?, targetLineId: String? = null) = viewModelScope.launch { runCatching { repository.editChord(chordId, symbol, position, figure, targetLineId) }.onSuccess { load(songId) }.onFailure { _error.value = it.message } }
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
    fun setInstrument(id: String, instrumentId: String?, tuningId: String?) = viewModelScope.launch {
        runCatching { repository.setInstrument(id, instrumentId, tuningId) }.onSuccess { load(id) }.onFailure { _error.value = it.message }
    }
    fun reportError(message: String) { _error.value = message }

    /** Brings the song back to the key it was first saved in, using the offset recorded by each transposition. */
    fun restoreOriginalKey(id: String, flats: Boolean, done: () -> Unit) = viewModelScope.launch {
        runCatching {
            lineSaves.values.toList().forEach { it.join() }
            val offset = repository.document(id).song.transposeOffset
            if (offset != 0) repository.transpose(id, 12 - offset, flats)
            _document.value = repository.document(id)
        }.onSuccess { done() }.onFailure { _error.value = it.message; done() }
    }

    /** Adds the bundled public-domain songs that are not in the library yet (matched by title). */
    fun addDemoSongs(sectionTitle: (kind: String, ordinal: Int, total: Int) -> String) = viewModelScope.launch {
        val existing = songs.value.map { it.title }.toSet()
        runCatching {
            DemoSongs.all.filter { it.title !in existing }.forEach { repository.importExchange(it.toExchange(), sectionTitle) }
        }.onFailure { _error.value = it.message }
    }

    private val _playing = MutableStateFlow<PlaybackStep?>(null)
    /** The chord sounding now; null when the song is not playing. */
    val playing: StateFlow<PlaybackStep?> = _playing
    private var playback: Job? = null

    fun stopPlayback() { playback?.cancel() }

    /** Plays the song's chords in reading order, one bar each, moving [playing] along with the sound. */
    fun togglePlayback(song: SongDocument, a4Hz: Double, instrumentId: String?, tuningId: String?) {
        if (playback?.isActive == true) { stopPlayback(); return }
        val ordered = song.lines.filter { it.sectionId == null }.sortedBy { it.position } +
            song.sections.sortedBy { it.position }.flatMap { section -> song.lines.filter { it.sectionId == section.id }.sortedBy { it.position } }
        val steps = SongPlaybackPlan.steps(ordered.map { line ->
            PlanLine(line.id, line.text, song.chords[line.id].orEmpty().map { PlanChord(it.id, it.position, it.orderInPosition, it.originalSymbol, it.figure) })
        })
        if (steps.isEmpty()) return
        val details = song.song
        fun stepMs(step: PlaybackStep) =
            (SongPlaybackPlan.stepSeconds(step.figure, details.bpm, details.timeNumerator, details.timeDenominator) * 1000).toLong()
        playback = viewModelScope.launch {
            suspend fun prepare(step: PlaybackStep) = ChordSounding.of(step.symbol, instrumentId, tuningId, details.capo)
                ?.let { ChordPlayer.render(it.midi, stepMs(step) / 1000.0, it.strumGapS, a4Hz) }
            var next = async { prepare(steps.first()) }
            var deadline = 0L
            steps.forEachIndexed { index, step ->
                val pcm = next.await()
                // The following chord is rendered while this one rings, so changes land on the beat.
                val following = steps.getOrNull(index + 1)
                next = async { following?.let { prepare(it) } }
                if (index == 0) deadline = SystemClock.elapsedRealtime()
                _playing.value = step
                pcm?.let { ChordPlayer.playNow(it) }
                deadline += stepMs(step)
                delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            }
        }.also { job -> job.invokeOnCompletion { ChordPlayer.stop(); _playing.value = null } }
    }

    // ---- Import and export -------------------------------------------------------------------------------

    /** A song found in a file, with whether the library already has one with the same title and artist. */
    data class ImportCandidate(val imported: ImportedSong, val duplicate: Boolean)

    private val _importPreview = MutableStateFlow<List<ImportCandidate>?>(null)
    val importPreview: StateFlow<List<ImportCandidate>?> = _importPreview
    private val _exchangeError = MutableStateFlow<String?>(null)
    /** Language-free code of the last file problem ([ExchangeException.code]); null when there is none. */
    val exchangeError: StateFlow<String?> = _exchangeError

    fun clearImport() { _importPreview.value = null }
    fun clearExchangeError() { _exchangeError.value = null }

    /** Reads a chosen file's text and shows what it holds; nothing is stored until the person confirms. */
    fun previewImport(text: String?, fallbackTitle: String) {
        if (text == null) { _exchangeError.value = "unreadable"; return }
        runCatching { SongFiles.read(text, fallbackTitle) }.onSuccess { result ->
            val existing = songs.value.map { it.title.trim().lowercase() to it.artist.orEmpty().trim().lowercase() }.toSet()
            _importPreview.value = result.songs.map {
                ImportCandidate(it, (it.song.title.trim().lowercase() to it.song.artist.orEmpty().trim().lowercase()) in existing)
            }
        }.onFailure { _exchangeError.value = (it as? ExchangeException)?.code ?: "unreadable" }
    }

    /** Stores the chosen songs as new ones (never replacing any) and reports how many were added. */
    fun confirmImport(chosen: List<ImportedSong>, sectionTitle: (kind: String, ordinal: Int, total: Int) -> String,
        untitled: String, done: (added: Int, firstId: String?) -> Unit) = viewModelScope.launch {
        runCatching {
            chosen.map { repository.importExchange(it.song.let { s -> if (s.title.isBlank()) s.copy(title = untitled) else s }, sectionTitle) }
        }.onSuccess { created ->
            _importPreview.value = null
            done(created.size, created.firstOrNull()?.id)
        }.onFailure { _exchangeError.value = it.message ?: "unreadable" }
    }

    /** What a person gets when sharing: the file's text, a name, and what the format could not carry. */
    data class ExportFile(val fileName: String, val mimeType: String, val text: String, val notices: List<Notice>)

    fun exportChordPro(songId: String, done: (ExportFile) -> Unit) = viewModelScope.launch {
        runCatching { repository.exchange(songId) }.onSuccess { song ->
            val written = ChordProFormat.write(song)
            done(ExportFile(safeName(song.title) + ".cho", "text/plain", written.text, written.notices))
        }.onFailure { _error.value = it.message }
    }

    /** One song, or the whole library when [songId] is null. */
    fun exportNative(songId: String?, done: (ExportFile) -> Unit) = viewModelScope.launch {
        runCatching { if (songId != null) listOf(repository.exchange(songId)) else repository.exchangeAll() }.onSuccess { songs ->
            val name = if (songId != null) safeName(songs.first().title) else "anarkey-canciones"
            val text = AnarkeyFormat.encode(songs, java.time.Instant.now().toString())
            done(ExportFile("$name.${AnarkeyFormat.EXTENSION}", "application/json", text, emptyList()))
        }.onFailure { _error.value = it.message }
    }

    private fun safeName(title: String) = title.trim().replace(Regex("[\\/:*?\"<>|\\p{Cntrl}]"), "_").take(60).trim().ifEmpty { "cancion" }

    override fun onCleared() { stopPlayback() }
}
