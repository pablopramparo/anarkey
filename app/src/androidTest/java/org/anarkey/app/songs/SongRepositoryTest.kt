package org.anarkey.app.songs

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.anarkey.app.AnarkeyApplication
import org.anarkey.app.recording.data.*
import org.anarkey.core.recording.RecordingStatus
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SongRepositoryTest {
    private lateinit var db: RecordingDatabase
    private lateinit var repo: SongRepository

    @Before fun setUp() {
        val app = ApplicationProvider.getApplicationContext<AnarkeyApplication>()
        db = Room.inMemoryDatabaseBuilder(app, RecordingDatabase::class.java).allowMainThreadQueries().build()
        repo = SongRepository(db)
    }
    @After fun tearDown() { db.close() }

    @Test fun transpositionPreservesAnchorsCapoAndUnknownSymbols() = runBlocking {
        val song = repo.create("Transpose")
        repo.save(song.id, song.title, null, "F#", "minor", 90, 4, 4, null, null, 2, "notes")
        repo.replaceLyrics(song.id, null, "A😀BC\nsecond")
        val line = repo.document(song.id).lines.first()
        val chord = repo.placeChord(line.id, 3, "F#m7/C#")
        val unknown = repo.placeChord(line.id, 1, "Cfoo/G")
        val before = repo.document(song.id)
        repo.transpose(song.id, 2, false)
        val after = repo.document(song.id)
        assertEquals(before.lines, after.lines)
        assertEquals("G#", after.song.keyRoot)
        assertEquals("minor", after.song.keyMode)
        assertEquals(2, after.song.capo)
        assertEquals(90, after.song.bpm)
        assertEquals("notes", after.song.notes)
        val actual = after.chords.getValue(line.id).single { it.id == chord.id }
        assertEquals("G#m7/D#", actual.originalSymbol)
        assertEquals("G", actual.rootLetter)
        assertEquals("D", actual.bassLetter)
        assertEquals(chord.position, actual.position)
        assertEquals(chord.orderInPosition, actual.orderInPosition)
        assertEquals(unknown, after.chords.getValue(line.id).single { it.id == unknown.id })
        repo.transpose(song.id, -2, false)
        assertEquals(before.chords, repo.document(song.id).chords)
        assertEquals("F#", repo.document(song.id).song.keyRoot)
    }

    @Test fun transpositionWithoutKeyAndInvalidInterval() = runBlocking {
        val song = repo.create("No key")
        repo.replaceLyrics(song.id, null, "line")
        val line = repo.document(song.id).lines.single()
        repo.placeChord(line.id, 0, "Lam/Do")
        val before = repo.document(song.id)
        assertTrue(runCatching { repo.transpose(song.id, 12, true) }.isFailure)
        assertEquals(before, repo.document(song.id))
        repo.transpose(song.id, 1, true)
        val after = repo.document(song.id)
        assertNull(after.song.keyRoot)
        assertEquals("Bbm/Db", after.chords.getValue(line.id).single().originalSymbol)
    }

    @Test fun titleOnlySongAndPastedLyricsKeepBlankLinesAndChordAnchors() = runBlocking {
        val song = repo.create("Canción nueva")
        repo.replaceLyrics(song.id, null, "A😀BC\n\nFin\n")
        val before = repo.document(song.id)
        assertEquals(listOf("A😀BC", "", "Fin", ""), before.lines.map { it.text })
        val chord = repo.placeChord(before.lines[0].id, 3, "F#m7/C#")
        repo.updateLineText(before.lines[0].id, "A😀XBC")
        val preserved = repo.document(song.id).chords.getValue(before.lines[0].id).single()
        assertEquals(chord.id, preserved.id)
        assertEquals(4, preserved.position)
        repo.replaceLyrics(song.id, null, "A😀XBC")
        val afterRemoval = repo.document(song.id)
        assertEquals(1, afterRemoval.lines.size)
        assertEquals("F#m7/C#", afterRemoval.chords.getValue(afterRemoval.lines.single().id).single().originalSymbol)
        val unknown = repo.placeChord(afterRemoval.lines.single().id, 0, "Cfoo/G")
        assertNull(unknown.rootLetter)
        assertEquals("Cfoo/G", repo.document(song.id).chords.getValue(afterRemoval.lines.single().id).first().originalSymbol)
    }

    @Test fun sectionsCanBeOrderedRenamedMovedAndDeletedWithoutLosingTheirLines() = runBlocking {
        val song = repo.create("Orden")
        repo.replaceLyrics(song.id, null, "línea uno\nlínea dos")
        val original = repo.document(song.id).lines
        val verse = repo.createSection(song.id, "Verso", "verse")
        val chorus = repo.createSection(song.id, "Estribillo", "chorus")
        repo.renameSection(verse.id, "Verso A")
        repo.reorderSection(chorus.id, -1)
        repo.moveLine(original[0].id, verse.id)
        repo.moveLine(original[1].id, chorus.id)
        val document = repo.document(song.id)
        assertEquals(listOf("Estribillo", "Verso A"), document.sections.sortedBy { it.position }.map { it.title })
        assertEquals(verse.id, document.lines.single { it.id == original[0].id }.sectionId)
        repo.deleteSection(verse.id)
        assertNull(repo.document(song.id).lines.single { it.id == original[0].id }.sectionId)
        assertEquals("línea uno", repo.document(song.id).lines.first { it.id == original[0].id }.text)
    }

    @Test fun latinChordSymbolsPersistExactlyAndFormattingDoesNotMoveAnchors() = runBlocking {
        val song = repo.create("NotaciÃ³n")
        repo.replaceLyrics(song.id, null, "abcdef")
        val line = repo.document(song.id).lines.single()
        val chord = repo.placeChord(line.id, 3, "Lam/Do")
        assertEquals("Lam/Do", chord.originalSymbol)
        assertEquals("Lam/Do", org.anarkey.core.music.ChordSymbolFormatter.format(chord.originalSymbol, org.anarkey.core.music.NoteNaming.SOLFEGE_FLATS))
        val unchanged = repo.document(song.id).chords.getValue(line.id).single()
        assertEquals("Lam/Do", unchanged.originalSymbol)
        assertEquals(3, unchanged.position)
        assertEquals("Am/C", org.anarkey.core.music.ChordSymbolFormatter.format(unchanged.originalSymbol, org.anarkey.core.music.NoteNaming.LETTERS_SHARPS))
        assertEquals(3, repo.document(song.id).chords.getValue(line.id).single().position)
    }

    @Test fun metadataTagsAndSongDeletionPreserveLinkedRecordingFiles() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<AnarkeyApplication>()
        val song = repo.create("Luz")
        repo.save(song.id, "Luz", "Artista", "F#", "minor", 60, 6, 8, "guitar", "guitar.drop_d", 3, "notas")
        val saved = repo.document(song.id).song
        assertEquals("F#", saved.keyRoot); assertEquals("minor", saved.keyMode)
        assertEquals(8, saved.timeDenominator); assertEquals("guitar.drop_d", saved.tuningId)
        try { repo.save(song.id, "Luz", null, null, null, 401, null, null, null, null, 0, ""); fail("BPM outside range must be rejected") }
        catch (_: IllegalArgumentException) { }
        repo.toggleFavorite(song.id, true)
        assertTrue(repo.document(song.id).song.favorite)
        repo.addTag(song.id, "Ensayo"); repo.addTag(song.id, "ENSAYO")
        val tag = repo.document(song.id).tags.single()
        assertEquals(1, repo.songsForTag(tag.id).first().size)

        val id = UUID.randomUUID().toString()
        val file = repoRecordingFile(app, id)
        val session = SessionEntity(UUID.randomUUID().toString(), "Sesión", "", System.currentTimeMillis(), System.currentTimeMillis())
        db.sessions().insert(session)
        val now = System.currentTimeMillis()
        db.recordings().insert(RecordingEntity(id, "Take", now, now, 1000, "$id.m4a", "audio/mp4", "audio/aac", 44100, 1, 10,
            RecordingStatus.READY, "", session.id))
        repo.setRecordingSong(id, song.id)
        val linked = repo.recordings(song.id).first().single()
        assertEquals(id, linked.id); assertEquals(session.id, linked.sessionId)
        repo.setRecordingSong(id, null)
        assertNull(db.recordings().find(id)?.songId)
        repo.setRecordingSong(id, song.id)
        repo.deleteSong(song.id)
        assertTrue("Song deletion must retain the recording row", db.recordings().find(id) != null)
        assertNull(db.recordings().find(id)?.songId)
        assertTrue("Song deletion must not remove private audio", file.exists())
        file.delete()
        assertTrue(db.songs().observeAll().first().isEmpty())
    }

    private fun repoRecordingFile(app: AnarkeyApplication, id: String): java.io.File =
        java.io.File(app.filesDir, "recordings/$id.m4a").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
}
