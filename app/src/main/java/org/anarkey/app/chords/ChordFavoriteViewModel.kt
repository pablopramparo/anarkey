package org.anarkey.app.chords

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.anarkey.app.AnarkeyApplication
import org.anarkey.app.recording.data.ChordFavoriteEntity
import org.anarkey.core.music.ChordVoicingCatalog

class ChordFavoriteViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = (application as AnarkeyApplication).recordings.database.chordFavorites()
    val favoriteIds = dao.observeAll().map { rows -> rows.mapTo(mutableSetOf()) { it.voicingId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun setFavorite(id: String, favorite: Boolean) = viewModelScope.launch {
        if (ChordVoicingCatalog.find(id) == null) return@launch
        if (favorite) dao.add(ChordFavoriteEntity(id, System.currentTimeMillis())) else dao.remove(id)
    }
}
