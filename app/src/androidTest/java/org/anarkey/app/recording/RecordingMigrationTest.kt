package org.anarkey.app.recording

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import org.anarkey.app.AnarkeyApplication
import org.anarkey.app.recording.data.RecordingDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingMigrationTest {
    @Test fun v1PreservesRowsAndV2Relations() {
        val app = ApplicationProvider.getApplicationContext<AnarkeyApplication>()
        app.deleteDatabase(DB_NAME)
        val file = app.getDatabasePath(DB_NAME)
        file.parentFile?.mkdirs()
        val old = SQLiteDatabase.openOrCreateDatabase(file.absolutePath, null)
        old.execSQL("CREATE TABLE sessions (id TEXT NOT NULL, name TEXT NOT NULL, notes TEXT NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL, PRIMARY KEY(id))")
        old.execSQL("CREATE INDEX index_sessions_updatedAtMs ON sessions (updatedAtMs)")
        old.execSQL("CREATE TABLE recordings (id TEXT NOT NULL, displayName TEXT NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, audioFileName TEXT NOT NULL, mimeType TEXT NOT NULL, codec TEXT NOT NULL, sampleRateHz INTEGER, channelCount INTEGER NOT NULL, sizeBytes INTEGER NOT NULL, status TEXT NOT NULL, notes TEXT NOT NULL, sessionId TEXT, PRIMARY KEY(id), FOREIGN KEY(sessionId) REFERENCES sessions(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
        old.execSQL("CREATE INDEX index_recordings_sessionId ON recordings (sessionId)")
        old.execSQL("CREATE INDEX index_recordings_status_createdAtMs ON recordings (status, createdAtMs)")
        old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        old.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES (42, '7a03a4616681cd3c6ba2e79f987baa61')")
        old.execSQL("INSERT INTO sessions VALUES ('session-1', 'Ensayo', 'Notas', 10, 20)")
        old.execSQL("INSERT INTO recordings VALUES ('recording-1', 'Idea', 11, 21, 3000, 'recording-1.m4a', 'audio/mp4', 'audio/mp4a-latm', 44100, 1, 512, 'READY', 'texto', 'session-1')")
        old.version = 1
        old.close()

        val room = Room.databaseBuilder(app, RecordingDatabase::class.java, DB_NAME)
            .addMigrations(RecordingDatabase.MIGRATION_1_2, RecordingDatabase.MIGRATION_2_3, RecordingDatabase.MIGRATION_3_4).allowMainThreadQueries().build()
        val migrated = room.openHelper.writableDatabase
        migrated.query("SELECT name, notes FROM sessions WHERE id = 'session-1'").use { cursor ->
            assertTrue(cursor.moveToFirst()); assertEquals("Ensayo", cursor.getString(0)); assertEquals("Notas", cursor.getString(1))
        }
        migrated.query("SELECT displayName, status, sessionId, songId, waveformPeaks FROM recordings WHERE id = 'recording-1'").use { cursor ->
            assertTrue(cursor.moveToFirst()); assertEquals("Idea", cursor.getString(0)); assertEquals("READY", cursor.getString(1))
            assertEquals("session-1", cursor.getString(2)); assertTrue(cursor.isNull(3)); assertTrue(cursor.isNull(4))
        }
        migrated.execSQL("INSERT INTO recording_markers VALUES ('marker-1', 'recording-1', 1200, NULL, 30)")
        migrated.execSQL("DELETE FROM sessions WHERE id = 'session-1'")
        migrated.query("SELECT sessionId FROM recordings WHERE id = 'recording-1'").use { cursor ->
            assertTrue(cursor.moveToFirst()); assertTrue("Session deletion must SET NULL", cursor.isNull(0))
        }
        migrated.query("SELECT recordingId FROM recording_markers WHERE id = 'marker-1'").use { cursor ->
            assertTrue("Session deletion must preserve the recording marker", cursor.moveToFirst())
            assertEquals("recording-1", cursor.getString(0))
        }
        migrated.execSQL("DELETE FROM recordings WHERE id = 'recording-1'")
        migrated.query("SELECT COUNT(*) FROM recording_markers").use { cursor ->
            assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0))
        }
        room.close()
        app.deleteDatabase(DB_NAME)
    }

    @Test fun v2MigrationPreservesAudioMarkersAndUnlinksOnSongDeletion() {
        val app = ApplicationProvider.getApplicationContext<AnarkeyApplication>()
        app.deleteDatabase(DB_NAME)
        val file = app.getDatabasePath(DB_NAME); file.parentFile?.mkdirs()
        val old = SQLiteDatabase.openOrCreateDatabase(file.absolutePath, null)
        old.execSQL("CREATE TABLE sessions (id TEXT NOT NULL, name TEXT NOT NULL, notes TEXT NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL, PRIMARY KEY(id))")
        old.execSQL("CREATE INDEX index_sessions_updatedAtMs ON sessions (updatedAtMs)")
        old.execSQL("CREATE TABLE recordings (id TEXT NOT NULL, displayName TEXT NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL, durationMs INTEGER NOT NULL, audioFileName TEXT NOT NULL, mimeType TEXT NOT NULL, codec TEXT NOT NULL, sampleRateHz INTEGER, channelCount INTEGER NOT NULL, sizeBytes INTEGER NOT NULL, status TEXT NOT NULL, notes TEXT NOT NULL, sessionId TEXT, songId TEXT, waveformPeaks BLOB, PRIMARY KEY(id), FOREIGN KEY(sessionId) REFERENCES sessions(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
        old.execSQL("CREATE INDEX index_recordings_sessionId ON recordings (sessionId)")
        old.execSQL("CREATE INDEX index_recordings_status_createdAtMs ON recordings (status, createdAtMs)")
        old.execSQL("CREATE TABLE recording_markers (id TEXT NOT NULL, recordingId TEXT NOT NULL, positionMs INTEGER NOT NULL, text TEXT, createdAtMs INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(recordingId) REFERENCES recordings(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        old.execSQL("CREATE INDEX index_recording_markers_recordingId_positionMs ON recording_markers (recordingId, positionMs)")
        old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        old.execSQL("INSERT INTO room_master_table VALUES (42, 'b62e48fda51f341bb67e6e6fc280f3c6')")
        old.execSQL("INSERT INTO sessions VALUES ('session-2', 'Ensayo M2', 'no borrar', 10, 20)")
        old.execSQL("INSERT INTO recordings VALUES ('recording-2', 'Toma', 11, 21, 5000, 'recording-2.m4a', 'audio/mp4', 'audio/mp4a-latm', 44100, 1, 512, 'READY', 'audio', 'session-2', NULL, x'010203')")
        old.execSQL("INSERT INTO recording_markers VALUES ('marker-2', 'recording-2', 2100, 'estribillo', 30)")
        old.version = 2; old.close()

        val room = Room.databaseBuilder(app, RecordingDatabase::class.java, DB_NAME)
            .addMigrations(RecordingDatabase.MIGRATION_2_3, RecordingDatabase.MIGRATION_3_4).allowMainThreadQueries().build()
        val migrated = room.openHelper.writableDatabase
        migrated.query("SELECT name FROM sessions WHERE id='session-2'").use { assertTrue(it.moveToFirst()); assertEquals("Ensayo M2", it.getString(0)) }
        migrated.query("SELECT songId, waveformPeaks FROM recordings WHERE id='recording-2'").use {
            assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)); assertArrayEquals(byteArrayOf(1, 2, 3), it.getBlob(1))
        }
        migrated.execSQL("INSERT INTO chord_favorites VALUES ('gtr.c.open', 99)")
        migrated.query("SELECT voicingId, addedAtMs FROM chord_favorites").use {
            assertTrue("M3 to M4 migration must create the favorites table", it.moveToFirst())
            assertEquals("gtr.c.open", it.getString(0)); assertEquals(99, it.getLong(1))
        }
        migrated.query("SELECT text, positionMs FROM recording_markers WHERE id='marker-2'").use {
            assertTrue(it.moveToFirst()); assertEquals("estribillo", it.getString(0)); assertEquals(2100, it.getLong(1))
        }
        migrated.execSQL("INSERT INTO songs VALUES ('song-2','Canción',NULL,'C','major',90,4,4,'guitar','guitar.standard',0,'',0,1,2)")
        migrated.execSQL("UPDATE recordings SET songId='song-2' WHERE id='recording-2'")
        migrated.execSQL("DELETE FROM songs WHERE id='song-2'")
        migrated.query("SELECT songId FROM recordings WHERE id='recording-2'").use { assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)) }
        migrated.query("SELECT COUNT(*) FROM recording_markers WHERE id='marker-2'").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
        migrated.execSQL("DELETE FROM recordings WHERE id='recording-2'")
        migrated.query("SELECT COUNT(*) FROM recording_markers").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        room.close(); app.deleteDatabase(DB_NAME)
    }


    companion object { private const val DB_NAME = "recording-migration-test" }
}
