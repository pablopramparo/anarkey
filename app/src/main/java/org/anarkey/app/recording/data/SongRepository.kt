package org.anarkey.app.recording.data

import androidx.room.withTransaction
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.anarkey.core.music.ChordAnchors
import org.anarkey.core.music.ChordSymbolParser
import org.anarkey.core.music.SongMetadata

data class SongDocument(val song: SongEntity, val sections: List<SongSectionEntity>, val lines: List<SongLineEntity>, val chords: Map<String, List<ChordPlacementEntity>>, val tags: List<TagEntity>)

class SongRepository(private val database: RecordingDatabase) {
    private val dao get() = database.songs()
    val songs: Flow<List<SongEntity>> = dao.observeAll()
    val tags: Flow<List<TagEntity>> = dao.observeTags()
    fun songsForTag(tagId: String?): Flow<List<SongEntity>> = if (tagId == null) songs else dao.observeByTag(tagId)
    fun recordings(songId: String): Flow<List<RecordingEntity>> = database.recordings().observeForSong(songId)

    suspend fun create(title: String): SongEntity {
        val clean = title.trim().take(160)
        require(clean.isNotEmpty()) { "title_required" }
        val now = System.currentTimeMillis()
        return SongEntity(UUID.randomUUID().toString(), clean, null, null, null, null, null, null,
            null, null, 0, "", false, now, now).also { dao.insert(it) }
    }

    suspend fun save(songId: String, title: String, artist: String?, keyRoot: String?, keyMode: String?,
        bpm: Int?, timeNumerator: Int?, timeDenominator: Int?, instrumentId: String?, tuningId: String?, capo: Int, notes: String) {
        val cleanTitle = title.trim().take(160)
        require(cleanTitle.isNotEmpty()) { "title_required" }
        val validation = SongMetadata(bpm, timeNumerator, timeDenominator, capo, instrumentId, tuningId).validationError()
        require(validation == null) { validation ?: "invalid_metadata" }
        require(keyRoot == null || keyRoot in setOf("A", "B", "C", "D", "E", "F", "G", "A#", "Bb", "B#", "Cb", "C#", "Db", "D#", "Eb", "E#", "Fb", "F#", "Gb", "G#", "Ab")) { "key_invalid" }
        require(keyMode == null || keyMode in setOf("major", "minor")) { "key_mode_invalid" }
        val old = dao.find(songId) ?: throw NoSuchElementException("song_missing")
        dao.update(old.copy(title = cleanTitle, artist = artist?.trim()?.take(160)?.ifBlank { null },
            keyRoot = keyRoot, keyMode = keyMode?.take(30), bpm = bpm, timeNumerator = timeNumerator,
            timeDenominator = timeDenominator, instrumentId = instrumentId, tuningId = tuningId, capo = capo,
            notes = notes.take(8_000), updatedAtMs = System.currentTimeMillis()))
    }

    suspend fun document(songId: String): SongDocument {
        val song = dao.find(songId) ?: throw NoSuchElementException("song_missing")
        val sections = dao.sections(songId)
        val lines = dao.lines(songId)
        val chords = if (lines.isEmpty()) emptyList() else dao.chordsForLines(lines.map { it.id })
        return SongDocument(song, sections, lines, chords.groupBy { it.lineId }, dao.tags(songId))
    }

    suspend fun toggleFavorite(songId: String, favorite: Boolean) {
        dao.find(songId)?.let { dao.update(it.copy(favorite = favorite, updatedAtMs = System.currentTimeMillis())) }
    }

    suspend fun transpose(songId: String, semitones: Int, flats: Boolean) = database.withTransaction {
        require(semitones in -11..11)
        if (semitones == 0) return@withTransaction
        val document = document(songId)
        document.chords.values.flatten().forEach { chord ->
            val symbol = org.anarkey.core.music.ChordTransposition.transpose(chord.originalSymbol, semitones, flats)
            if (symbol != chord.originalSymbol) {
                val parsed = ChordSymbolParser.parse(symbol)
                dao.updateChord(chord.copy(originalSymbol = symbol, rootLetter = parsed.rootLetter,
                    rootAccidental = parsed.rootAccidental, quality = parsed.quality, extension = parsed.extension,
                    bassLetter = parsed.bassLetter, bassAccidental = parsed.bassAccidental))
            }
        }
        dao.update(document.song.copy(keyRoot = document.song.keyRoot?.let {
            org.anarkey.core.music.ChordTransposition.transpose(it, semitones, flats)
        }, updatedAtMs = System.currentTimeMillis()))
    }

    suspend fun deleteSong(songId: String) = database.withTransaction {
        // Detach first so M2 audio remains available even if deletion is retried.
        database.recordings().all().filter { it.songId == songId }.forEach {
            database.recordings().setSong(it.id, null, System.currentTimeMillis())
        }
        dao.delete(songId)
    }

    suspend fun createSection(songId: String, title: String, kind: String = "custom"): SongSectionEntity = database.withTransaction {
        dao.find(songId) ?: throw NoSuchElementException("song_missing")
        val clean = title.trim().take(80)
        require(clean.isNotEmpty()) { "section_title_required" }
        val sections = dao.sections(songId)
        SongSectionEntity(UUID.randomUUID().toString(), songId, sections.size, kind, clean, "").also { dao.insertSection(it); touch(songId) }
    }

    suspend fun renameSection(id: String, title: String) {
        val clean = title.trim().take(80)
        require(clean.isNotEmpty()) { "section_title_required" }
        val section = dao.section(id) ?: throw NoSuchElementException("section_missing")
        dao.updateSection(section.copy(title = clean))
        touch(section.songId)
    }

    private suspend fun sectionById(id: String) = dao.section(id)

    suspend fun deleteSection(id: String) = database.withTransaction {
        val section = dao.section(id) ?: return@withTransaction
        dao.deleteSection(id)
        dao.sections(section.songId).sortedBy { it.position }.forEachIndexed { index, other ->
            if (other.position != index) dao.updateSection(other.copy(position = index))
        }
        touch(section.songId)
        dao.linesInSection(section.songId, null).sortedBy { it.position }.forEachIndexed { index, line ->
            if (line.position != index) dao.updateLines(listOf(line.copy(position = index)))
        }
    }

    suspend fun reorderSection(id: String, delta: Int) = database.withTransaction {
        val current = dao.section(id) ?: return@withTransaction
        val targetIndex = current.position + delta
        val neighbor = dao.sectionAt(current.songId, targetIndex) ?: return@withTransaction
        dao.updateSections(listOf(current.copy(position = targetIndex), neighbor.copy(position = current.position)))
        touch(current.songId)
    }

    suspend fun updateLineText(lineId: String, text: String) = database.withTransaction {
        val line = dao.line(lineId) ?: return@withTransaction
        val clean = text.take(2_000)
        dao.updateLines(listOf(line.copy(text = clean)))
        dao.chords(lineId).forEach { chord -> dao.updateChord(chord.copy(position = ChordAnchors.remap(chord.position, line.text, clean))) }
        touch(line.songId)
    }

    suspend fun moveLine(lineId: String, sectionId: String?) = database.withTransaction {
        val line = dao.line(lineId) ?: throw NoSuchElementException("line_missing")
        val target = sectionId?.let { id -> sectionById(id)?.also { require(it.songId == line.songId) } ?: throw IllegalArgumentException("section_invalid") }
        val sourceLines = dao.linesInSection(line.songId, line.sectionId).filter { it.id != line.id }
        sourceLines.forEachIndexed { index, source -> if (source.position != index) dao.updateLines(listOf(source.copy(position = index))) }
        val targetLines = if (line.sectionId == target?.id) sourceLines else dao.linesInSection(line.songId, target?.id)
        dao.updateLines(listOf(line.copy(sectionId = target?.id, position = targetLines.size)))
        touch(line.songId)
    }

    suspend fun reorderLine(lineId: String, delta: Int) = database.withTransaction {
        val line = dao.line(lineId) ?: return@withTransaction
        val neighbor = dao.lineAt(line.songId, line.sectionId, line.position + delta) ?: return@withTransaction
        dao.updateLines(listOf(line.copy(position = neighbor.position), neighbor.copy(position = line.position)))
        touch(line.songId)
    }

    suspend fun replaceLyrics(songId: String, sectionId: String?, text: String) = database.withTransaction {
        require(dao.find(songId) != null) { "song_missing" }
        if (sectionId != null) require(sectionById(sectionId)?.songId == songId) { "section_invalid" }
        val current = dao.linesInSection(songId, sectionId)
        val newTexts = text.split('\n') // preserves meaningful blank and trailing lines
        val replacement = newTexts.mapIndexed { index, value ->
            val previous = current.getOrNull(index)
            SongLineEntity(previous?.id ?: UUID.randomUUID().toString(), songId, sectionId, index, value.removeSuffix("\r"))
        }
        val stable = minOf(current.size, replacement.size)
        if (stable > 0) {
            dao.updateLines(replacement.take(stable))
            replacement.take(stable).forEachIndexed { index, line ->
                dao.chords(current[index].id).forEach { chord ->
                    dao.updateChord(chord.copy(position = ChordAnchors.remap(chord.position, current[index].text, line.text)))
                }
            }
        }
        if (replacement.size > stable) dao.insertLines(replacement.drop(stable))
        if (current.size > replacement.size) {
            val last = replacement.last()
            val removed = current.drop(replacement.size)
            removed.flatMap { dao.chords(it.id) }.forEach { chord ->
                dao.updateChord(chord.copy(lineId = last.id, position = ChordAnchors.clamp(chord.position, last.text),
                    orderInPosition = dao.chords(last.id).count { it.position == chord.position }))
            }
            current.drop(replacement.size).forEach { dao.deleteLine(it.id) }
        }
        touch(songId)
    }

    suspend fun placeChord(lineId: String, position: Int, symbol: String): ChordPlacementEntity {
        val line = findLine(lineId) ?: throw NoSuchElementException("line_missing")
        val clean = symbol.trim().take(40)
        require(clean.isNotEmpty()) { "chord_required" }
        val anchor = ChordAnchors.clamp(position, line.text)
        val atPosition = dao.chords(lineId).count { it.position == anchor }
        val parsed = ChordSymbolParser.parse(clean)
        return ChordPlacementEntity(UUID.randomUUID().toString(), lineId, anchor, atPosition, parsed.original,
            parsed.rootLetter, parsed.rootAccidental, parsed.quality, parsed.extension, parsed.bassLetter, parsed.bassAccidental)
            .also { database.withTransaction { dao.insertChord(it); touch(line.songId) } }
    }

    suspend fun editChord(id: String, symbol: String, position: Int, targetLineId: String? = null) = database.withTransaction {
        val current = dao.chord(id) ?: return@withTransaction
        val line = findLine(targetLineId ?: current.lineId) ?: return@withTransaction
        val originalLine = findLine(current.lineId) ?: return@withTransaction
        require(line.songId == originalLine.songId) { "line_song_mismatch" }
        val parsed = ChordSymbolParser.parse(symbol.trim().take(40))
        require(parsed.original.isNotEmpty()) { "chord_required" }
        val anchor = ChordAnchors.clamp(position, line.text)
        val order = if (line.id == current.lineId && anchor == current.position) current.orderInPosition
            else dao.chords(line.id).count { it.position == anchor && it.id != id }
        dao.updateChord(current.copy(lineId = line.id, position = anchor, orderInPosition = order, originalSymbol = parsed.original,
            rootLetter = parsed.rootLetter, rootAccidental = parsed.rootAccidental, quality = parsed.quality,
            extension = parsed.extension, bassLetter = parsed.bassLetter, bassAccidental = parsed.bassAccidental))
        touch(line.songId)
    }
    suspend fun deleteChord(id: String) = database.withTransaction { dao.chord(id)?.let { chord ->
        dao.deleteChord(id); findLine(chord.lineId)?.let { touch(it.songId) }
    } }
    private suspend fun findLine(id: String): SongLineEntity? = dao.line(id)

    suspend fun addTag(songId: String, name: String) = database.withTransaction {
        require(dao.find(songId) != null) { "song_missing" }
        val normalized = name.trim().lowercase(Locale.ROOT).take(40)
        require(normalized.isNotBlank()) { "tag_required" }
        val existing = dao.findTag(normalized)
        val tag = existing ?: TagEntity(UUID.randomUUID().toString(), name.trim().take(40), normalized).also { dao.insertTag(it) }
        dao.attachTag(SongTagEntity(songId, tag.id))
        touch(songId)
    }
    suspend fun removeTag(songId: String, tagId: String) = database.withTransaction { dao.detachTag(songId, tagId); touch(songId) }
    suspend fun setRecordingSong(recordingId: String, songId: String?) = database.withTransaction {
        if (songId != null) require(dao.find(songId) != null) { "song_missing" }
        val recording = database.recordings().find(recordingId)
            ?: throw NoSuchElementException("recording_missing")
        require(recording.status == org.anarkey.core.recording.RecordingStatus.READY || recording.status == org.anarkey.core.recording.RecordingStatus.RECOVERY_REQUIRED)
        if (songId != null) require(recording.songId == null || recording.songId == songId) { "recording_linked_elsewhere" }
        database.recordings().setSong(recordingId, songId, System.currentTimeMillis())
        recording.songId?.takeIf { it != songId }?.let { touch(it) }
        songId?.let { touch(it) }
    }

    private suspend fun touch(songId: String) {
        dao.find(songId)?.let { current -> dao.update(current.copy(updatedAtMs = System.currentTimeMillis())) }
    }
}
