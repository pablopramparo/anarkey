package org.anarkey.app.chords

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import org.anarkey.app.AnarkeyApplication
import org.anarkey.app.recording.data.ChordFavoriteEntity
import org.anarkey.app.recording.data.RecordingDatabase
import org.anarkey.core.music.ChordVoicingCatalog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChordFavoriteTest {
    @Test fun favoriteIdsSurviveReopenAreUniqueAndUnknownCatalogIdsAreHarmless() {
        val app = ApplicationProvider.getApplicationContext<AnarkeyApplication>()
        val name = "chord-favorite-test.db"
        app.deleteDatabase(name)
        val first = Room.databaseBuilder(app, RecordingDatabase::class.java, name).allowMainThreadQueries().build()
        kotlinx.coroutines.runBlocking {
            val db = first
            db.chordFavorites().add(ChordFavoriteEntity("gtr.c.open", 1))
            db.chordFavorites().add(ChordFavoriteEntity("gtr.c.open", 2))
            db.chordFavorites().add(ChordFavoriteEntity("removed.in.future", 3))
        }
        first.close()
        val reopened = Room.databaseBuilder(app, RecordingDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            val db = reopened
            val ids = db.chordFavorites().observeAll()
            val rows = kotlinx.coroutines.runBlocking { ids.first() }
            assertEquals(setOf("gtr.c.open", "removed.in.future"), rows.map { it.voicingId }.toSet())
            assertNotNull(ChordVoicingCatalog.find("gtr.c.open"))
            assertNull("stale IDs are simply not offered by the UI", ChordVoicingCatalog.find("removed.in.future"))
            kotlinx.coroutines.runBlocking { db.chordFavorites().remove("gtr.c.open") }
        } finally { reopened.close() }
        app.deleteDatabase(name)
    }
}
