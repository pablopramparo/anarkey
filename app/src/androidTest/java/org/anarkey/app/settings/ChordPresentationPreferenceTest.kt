package org.anarkey.app.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.anarkey.core.music.ChordPresentationMode
import org.anarkey.core.music.TunerPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChordPresentationPreferenceTest {
    @Test fun presentationModePersistsAndDefaultsToBeginner() = runBlocking {
        val repository = PreferencesRepository(ApplicationProvider.getApplicationContext())
        val previous = repository.preferences.first().chordPresentationMode
        assertEquals(ChordPresentationMode.BEGINNER, TunerPreferences().chordPresentationMode)
        repository.setChordPresentationMode(ChordPresentationMode.ADVANCED)
        assertEquals(ChordPresentationMode.ADVANCED, repository.preferences.first().chordPresentationMode)
        repository.setChordPresentationMode(previous)
    }
}
