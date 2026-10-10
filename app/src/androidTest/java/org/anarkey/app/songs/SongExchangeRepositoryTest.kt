package org.anarkey.app.songs

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.anarkey.app.recording.data.RecordingDatabase
import org.anarkey.app.recording.data.SongRepository
import org.anarkey.core.exchange.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on an in-memory database, so it never touches the songs on the device. */
@RunWith(AndroidJUnit4::class)
class SongExchangeRepositoryTest {
    private lateinit var database: RecordingDatabase
    private lateinit var repository: SongRepository

    private val song = ExchangeSong(
        title = "Round trip", artist = "Anarkey", keyRoot = "Bb", keyMode = "minor", bpm = 88, timeNumerator = 6, timeDenominator = 8,
        capo = 2, instrumentId = "ukulele", tuningId = "ukulele.high_g", notes = "una\nnota", favorite = true, transposeOffset = 4,
        tags = listOf("folk", "ensayo"),
        unsectionedLines = listOf(ExchangeLine("suelta", listOf(ExchangeMark(0, "C", "1/2")))),
        sections = listOf(
            ExchangeSection("verse", "Verso uno", "n", listOf(
                ExchangeLine("Hola 🎵 mundo", listOf(ExchangeMark(0, "Am"), ExchangeMark(0, "♪A4", "1/4."), ExchangeMark(5, "_", "1/8"), ExchangeMark(7, "G/B"))),
                ExchangeLine(""),
            )),
            ExchangeSection("chorus", "", lines = listOf(ExchangeLine("coro", listOf(ExchangeMark(4, "F#m7b5"))))),
        ),
    )

    @Before fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RecordingDatabase::class.java).allowMainThreadQueries().build()
        repository = SongRepository(database)
    }

    @After fun close() = database.close()

    @Test fun importedSongComesBackExactlyAndKeepsMarksInOrder() = runBlocking {
        val created = repository.importExchange(song) { kind, ordinal, total -> "$kind $ordinal/$total" }
        val back = repository.exchange(created.id)
        // A section without a title receives the generated one, everything else is identical.
        // Tags have no order of their own (the library lists them alphabetically); a section without a title gets a generated one.
        val expected = song.copy(sections = song.sections.map { if (it.title.isEmpty()) it.copy(title = "chorus 1/1") else it })
        assertEquals(expected.copy(tags = expected.tags.sorted()), back.copy(tags = back.tags.sorted()))
        assertEquals("Round trip", repository.document(created.id).song.title)
    }

    @Test fun nativeFileRoundTripsThroughTheDatabase() = runBlocking {
        val first = repository.importExchange(song) { _, _, _ -> "S" }
        val text = AnarkeyFormat.encode(listOf(repository.exchange(first.id)), "2026-10-10T00:00:00Z")
        val decoded = AnarkeyFormat.decode(text).songs.single()
        assertTrue(decoded.notices.isEmpty())
        val second = repository.importExchange(decoded.song) { _, _, _ -> "S" }
        assertNotEquals(first.id, second.id) // importing never replaces: it always creates a new song
        assertEquals(repository.exchange(first.id).copy(tags = emptyList()), repository.exchange(second.id).copy(tags = emptyList()))
        assertEquals(repository.exchange(first.id).tags.toSet(), repository.exchange(second.id).tags.toSet())
        assertEquals(2, repository.exchangeAll().size)
    }

    @Test fun chordProExportOfADatabaseSongParsesBack() = runBlocking {
        val created = repository.importExchange(song) { _, _, _ -> "S" }
        val written = ChordProFormat.write(repository.exchange(created.id))
        val back = ChordProFormat.parse(written.text).songs.single().song
        assertEquals(song.title, back.title)
        // ChordPro has no trailing blank lines, so a blank line at the end of a section is not carried over.
        assertEquals(song.sections.map { it.lines.dropLastWhile { line -> line.text.isEmpty() && line.marks.isEmpty() } },
            back.sections.map { it.lines })
        assertEquals(song.tags.toSet(), back.tags.toSet())
    }
}
