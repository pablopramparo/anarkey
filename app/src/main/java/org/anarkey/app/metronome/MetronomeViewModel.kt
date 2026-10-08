package org.anarkey.app.metronome

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import org.anarkey.core.metronome.MetronomeSettings

@OptIn(FlowPreview::class)
class MetronomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MetronomePreferencesRepository(application)
    val preferences = repository.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val mutablePlayback = MutableStateFlow(MetronomePlaybackState())
    val playback = mutablePlayback.asStateFlow()
    private val pendingPreferences = MutableSharedFlow<MetronomePreferences>(
        replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val player = MetronomeAudioPlayer { state -> mutablePlayback.value = state }
    private val startGeneration = AtomicLong(0)
    private val playbackCommandLock = Any()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            pendingPreferences.debounce(PREFERENCE_DEBOUNCE_MS).collect(repository::save)
        }
        MetronomeService.onStopRequested = ::stop
        // The foreground service lives exactly as long as the click track plays.
        viewModelScope.launch {
            var serviceRunning = false
            playback.map { it.isPlaying }.distinctUntilChanged().collect { playing ->
                if (playing) { MetronomeService.start(application); serviceRunning = true }
                else if (serviceRunning) { MetronomeService.stop(application); serviceRunning = false }
            }
        }
    }

    fun savePreferences(value: MetronomePreferences) {
        pendingPreferences.tryEmit(value)
    }

    fun start(settings: MetronomeSettings) {
        val generation = startGeneration.incrementAndGet()
        viewModelScope.launch(Dispatchers.IO) {
            synchronized(playbackCommandLock) {
                if (startGeneration.get() != generation) return@synchronized
                runCatching { player.start(settings) }
                    .onFailure { mutablePlayback.value = MetronomePlaybackState(error = it.message ?: "audio_track_error") }
            }
        }
    }

    fun update(settings: MetronomeSettings) = player.update(settings)

    fun stop() {
        startGeneration.incrementAndGet()
        synchronized(playbackCommandLock) { player.stop() }
    }

    override fun onCleared() {
        MetronomeService.onStopRequested = null
        player.stop()
        MetronomeService.stop(getApplication())
        super.onCleared()
    }

    private companion object { const val PREFERENCE_DEBOUNCE_MS = 300L }
}
