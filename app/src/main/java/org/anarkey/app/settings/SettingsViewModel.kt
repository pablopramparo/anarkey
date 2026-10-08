package org.anarkey.app.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.anarkey.core.music.*

data class SettingsState(val preferences: TunerPreferences? = null, val failed: Boolean = false)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PreferencesRepository(application)
    private val attempt = MutableStateFlow(0)
    private val mutableWriteFailed = MutableStateFlow(false)
    val writeFailed = mutableWriteFailed.asStateFlow()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state = attempt.flatMapLatest {
        repository.preferences.map { SettingsState(it) }.catch { error ->
            if (error is IOException) emit(SettingsState(failed = true)) else throw error
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState())

    fun retry() { attempt.value++ }
    fun setSelection(value: TunerSelection) = write { repository.setSelection(value) }
    fun setA4(value: Double) = write { repository.setA4(value) }
    fun setNaming(value: NoteNaming) = write { repository.setNaming(value) }
    fun setChordPresentationMode(value: ChordPresentationMode) = write { repository.setChordPresentationMode(value) }
    fun clearWriteError() { mutableWriteFailed.value = false }
    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block(); mutableWriteFailed.value = false }
            catch (_: IOException) { mutableWriteFailed.value = true }
        }
    }
}
