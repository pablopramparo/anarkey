package org.anarkey.app.recording.data

import android.content.ContentResolver
import android.net.Uri
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaCodec
import android.os.SystemClock
import androidx.room.Room
import androidx.room.withTransaction
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardCopyOption
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.anarkey.core.recording.RecordingNames
import org.anarkey.core.recording.RecordingStateMachine
import org.anarkey.core.recording.RecordingStatus

class RecordingFiles(context: android.content.Context) {
    val directory = File(context.filesDir, "recordings").apply {
        if (!exists() && !mkdirs()) throw IOException("Cannot create private recording directory")
        if (!isDirectory) throw IOException("Recording path is not a directory")
    }

    fun validateId(id: String) { require(UUID.fromString(id).toString() == id) { "Invalid recording ID" } }
    fun temporary(id: String): File { validateId(id); return File(directory, "$id.m4a.partial") }
    fun final(id: String): File { validateId(id); return File(directory, "$id.m4a") }
    fun byDatabaseName(name: String): File {
        val id = name.removeSuffix(".m4a")
        require(name == "$id.m4a")
        validateId(id)
        return final(id)
    }

    fun promote(id: String) {
        val temporary = temporary(id)
        val destination = final(id)
        require(temporary.isFile && temporary.length() > 0)
        require(!destination.exists()) { "The final recording path already exists" }
        try {
            java.nio.file.Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (unsupported: java.nio.file.AtomicMoveNotSupportedException) {
            if (!temporary.renameTo(destination)) throw IOException("Cannot finish the recording", unsupported)
        }
        FileChannel.open(destination.toPath(), java.nio.file.StandardOpenOption.WRITE).use { it.force(true) }
    }

    fun deleteOwnFiles(id: String) {
        listOf(temporary(id), final(id)).forEach { file ->
            if (file.exists() && !file.delete()) throw IOException("Cannot delete the recording file")
        }
    }
}

class RecordingRepository(context: android.content.Context) {
    val files = RecordingFiles(context)
    private val resolver = context.applicationContext.contentResolver
    val database: RecordingDatabase = Room.databaseBuilder(context.applicationContext, RecordingDatabase::class.java,
        RecordingDatabase.FILE_NAME).addMigrations(RecordingDatabase.MIGRATION_1_2, RecordingDatabase.MIGRATION_2_3, RecordingDatabase.MIGRATION_3_4, RecordingDatabase.MIGRATION_4_5, RecordingDatabase.MIGRATION_5_6).build()
    val recordings: Flow<List<RecordingEntity>> = database.recordings().observeAll()
    val sessions: Flow<List<SessionEntity>> = database.sessions().observeAll()

    suspend fun createRecording(songId: String? = null, nowMs: Long = System.currentTimeMillis(), locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): RecordingEntity {
        if (songId != null) require(database.songs().find(songId) != null) { "Song no longer exists" }
        val id = UUID.randomUUID().toString()
        files.validateId(id)
        val row = RecordingEntity(id, RecordingNames.generated(nowMs, locale, zone), nowMs, nowMs, 0,
            "$id.m4a", "audio/mp4", "audio/mp4a-latm", null, 1, 0,
            RecordingStatus.RECORDING, "", null, songId)
        database.recordings().insert(row)
        return row
    }

    suspend fun find(id: String) = database.recordings().find(id)
    suspend fun all() = database.recordings().all()
    suspend fun inState(status: RecordingStatus) = database.recordings().byStatus(status)
    suspend fun transition(id: String, to: RecordingStatus): RecordingEntity = database.withTransaction {
        val dao = database.recordings()
        val existing = dao.find(id) ?: throw NoSuchElementException("Recording no longer exists")
        RecordingStateMachine.requireTransition(existing.status, to)
        existing.copy(status = to, updatedAtMs = System.currentTimeMillis()).also { dao.update(it) }
    }

    suspend fun markRecovery(id: String) = updateStatusIfAllowed(id, RecordingStatus.RECOVERY_REQUIRED)
    suspend fun markMissing(id: String) = updateStatusIfAllowed(id, RecordingStatus.MISSING)

    suspend fun markFinalizing(id: String, durationMs: Long, bytes: Long, sampleRateHz: Int, waveform: ByteArray?) = database.withTransaction {
        val dao = database.recordings()
        val row = dao.find(id) ?: throw NoSuchElementException("Recording metadata is missing")
        RecordingStateMachine.requireTransition(row.status, RecordingStatus.FINALIZING)
        require(durationMs >= 0 && bytes > 0 && sampleRateHz > 0)
        dao.update(row.copy(status = RecordingStatus.FINALIZING, updatedAtMs = System.currentTimeMillis(),
            durationMs = durationMs, sizeBytes = bytes, sampleRateHz = sampleRateHz, waveformPeaks = waveform))
        database.markers().clampPositions(id, durationMs)
    }

    suspend fun markReady(id: String) = updateStatusIfAllowed(id, RecordingStatus.READY)

    suspend fun cacheWaveform(id: String, waveform: ByteArray) {
        val row = find(id) ?: return
        if (row.status == RecordingStatus.READY && row.waveformPeaks == null) {
            database.recordings().update(row.copy(waveformPeaks = waveform, updatedAtMs = System.currentTimeMillis()))
        }
    }

    suspend fun reconcile() = withContext(Dispatchers.IO) {
        retryDeletes()
        for (row in all()) {
            val file = runCatching { files.byDatabaseName(row.audioFileName) }.getOrNull()
            val finalExists = file?.isFile == true && file.length() > 0
            when (row.status) {
                RecordingStatus.RECORDING, RecordingStatus.PAUSED -> {
                    val partial = files.temporary(row.id)
                    if (finalExists && runCatching { inspectM4a(file) }.isSuccess) {
                        promoteRecoveredFinal(row, file!!)
                    } else {
                        if (partial.isFile && partial.length() > 0) markRecovery(row.id)
                        else markRecovery(row.id) // retain metadata; show that the audio itself is missing
                    }
                }
                RecordingStatus.FINALIZING -> when {
                    finalExists && runCatching { inspectM4a(file) }.isSuccess -> markReady(row.id)
                    files.temporary(row.id).let { it.isFile && it.length() > 0 } -> markRecovery(row.id)
                    else -> markRecovery(row.id)
                }
                RecordingStatus.READY -> if (!finalExists) markMissing(row.id)
                RecordingStatus.RECOVERY_REQUIRED -> Unit // no automatic deletion or destructive repair
                RecordingStatus.MISSING, RecordingStatus.DELETING -> Unit
            }
        }
        adoptOldOwnedOrphans()
    }

    /** A delete is two-phase and safe to retry after process death. */
    suspend fun delete(id: String) {
        transition(id, RecordingStatus.DELETING)
        files.deleteOwnFiles(id)
        val deleted = database.recordings().deleteMarked(id)
        check(deleted == 1) { "Deletion record changed before cleanup" }
    }

    suspend fun retryDeletes() {
        for (row in inState(RecordingStatus.DELETING)) {
            files.deleteOwnFiles(row.id)
            database.recordings().deleteMarked(row.id)
        }
    }

    suspend fun createSession(name: String, notes: String = "", nowMs: Long = System.currentTimeMillis()): SessionEntity {
        val cleanName = name.trim().take(100)
        require(cleanName.isNotEmpty())
        return SessionEntity(UUID.randomUUID().toString(), cleanName, notes.trim().take(4_000), nowMs, nowMs).also {
            database.sessions().insert(it)
        }
    }

    suspend fun saveSession(id: String, name: String, notes: String) {
        val row = database.sessions().find(id) ?: return
        val clean = name.trim().take(100)
        require(clean.isNotEmpty())
        database.sessions().update(row.copy(name = clean, notes = notes.trim().take(4_000), updatedAtMs = System.currentTimeMillis()))
    }
    suspend fun deleteSession(id: String) = database.sessions().delete(id)

    suspend fun saveDetails(id: String, name: String, notes: String, sessionId: String?) = database.withTransaction {
        val dao = database.recordings()
        val row = dao.find(id) ?: throw NoSuchElementException("Recording no longer exists")
        require(row.status == RecordingStatus.READY || row.status == RecordingStatus.RECOVERY_REQUIRED)
        if (sessionId != null) require(database.sessions().find(sessionId) != null)
        val cleanName = name.trim().take(100)
        require(cleanName.isNotEmpty())
        dao.update(row.copy(displayName = cleanName, notes = notes.trim().take(4_000), sessionId = sessionId,
            updatedAtMs = System.currentTimeMillis()))
    }

    fun observeMarkers(id: String) = database.markers().observeForRecording(id)
    suspend fun addMarker(recordingId: String, positionMs: Long, nowMs: Long = System.currentTimeMillis()) {
        val row = find(recordingId) ?: throw NoSuchElementException("Recording no longer exists")
        require(row.status == RecordingStatus.RECORDING || row.status == RecordingStatus.PAUSED)
        require(positionMs >= 0)
        database.markers().insert(RecordingMarkerEntity(UUID.randomUUID().toString(), recordingId,
            positionMs, null, nowMs))
    }
    suspend fun addMarkerAfterRecording(recordingId: String, positionMs: Long, text: String?) {
        val row = find(recordingId) ?: throw NoSuchElementException("Recording no longer exists")
        require(row.status == RecordingStatus.READY && positionMs in 0..row.durationMs)
        database.markers().insert(RecordingMarkerEntity(UUID.randomUUID().toString(), recordingId,
            positionMs, text?.trim()?.take(120)?.ifBlank { null }, System.currentTimeMillis()))
    }
    suspend fun editMarker(id: String, recordingId: String, text: String?) {
        val existing = database.withTransaction {
            val dao = database.markers()
            // An ownership-scoped update avoids a second lookup interface and cannot touch another recording.
            dao.find(id)?.takeIf { it.recordingId == recordingId }
        } ?: return
        database.markers().update(existing.copy(text = text?.trim()?.take(120)?.ifBlank { null }))
    }
    suspend fun deleteMarker(id: String, recordingId: String) { database.markers().delete(id, recordingId) }
    suspend fun seekPosition(id: String, positionMs: Long) {
        val row = find(id) ?: return
        val allowed = row.status == RecordingStatus.READY || row.status == RecordingStatus.RECOVERY_REQUIRED
        require(allowed && positionMs >= 0 && positionMs <= row.durationMs)
    }

    suspend fun shareUri(context: android.content.Context, id: String): Uri {
        val row = find(id) ?: throw NoSuchElementException("Recording no longer exists")
        require(row.status == RecordingStatus.READY)
        require(files.byDatabaseName(row.audioFileName).isFile)
        return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", files.byDatabaseName(row.audioFileName))
    }

    suspend fun export(id: String, destination: Uri, contentResolver: ContentResolver) = withContext(Dispatchers.IO) {
        val row = find(id) ?: throw NoSuchElementException("Recording no longer exists")
        require(row.status == RecordingStatus.READY || row.status == RecordingStatus.RECOVERY_REQUIRED)
        val source = sourceFor(row)
        require(source.isFile)
        val output = contentResolver.openOutputStream(destination, "w") ?: throw IOException("Cannot open export destination")
        output.use { destinationStream -> FileInputStream(source).use { input -> input.copyTo(destinationStream) } }
    }

    fun sourceFor(row: RecordingEntity): File {
        val final = files.byDatabaseName(row.audioFileName)
        if (final.isFile && final.length() > 0) return final
        val partial = files.temporary(row.id)
        require(row.status == RecordingStatus.RECOVERY_REQUIRED && partial.isFile && partial.length() > 0)
        return partial
    }

    private suspend fun updateStatusIfAllowed(id: String, status: RecordingStatus) = database.withTransaction {
        val dao = database.recordings()
        val row = dao.find(id) ?: return@withTransaction
        if (row.status != status) {
            RecordingStateMachine.requireTransition(row.status, status)
            dao.update(row.copy(status = status, updatedAtMs = System.currentTimeMillis()))
        }
    }

    private suspend fun promoteRecoveredFinal(row: RecordingEntity, file: File) {
        val info = inspectM4a(file)
        database.recordings().update(row.copy(status = RecordingStatus.FINALIZING, durationMs = info.durationMs,
            sampleRateHz = info.sampleRateHz, codec = info.codec, channelCount = info.channelCount,
            sizeBytes = file.length(), updatedAtMs = System.currentTimeMillis()))
        markReady(row.id)
    }

    private suspend fun adoptOldOwnedOrphans() {
        val known = all().mapTo(mutableSetOf()) { it.audioFileName }
        val cutoff = System.currentTimeMillis() - ORPHAN_MIN_AGE_MS
        files.directory.listFiles().orEmpty().forEach { file ->
            if (file.lastModified() >= cutoff || file.name in known || file.length() == 0L) return@forEach
            val partial = file.name.matches(Regex("[0-9a-fA-F-]{36}\\.m4a\\.partial"))
            val complete = file.name.matches(Regex("[0-9a-fA-F-]{36}\\.m4a"))
            if (!partial && !complete) return@forEach
            val id = file.name.take(36).lowercase(Locale.ROOT)
            if (runCatching { files.validateId(id) }.isFailure) return@forEach
            val finalName = "$id.m4a"
            if (database.recordings().find(id) != null) return@forEach
            val info = if (complete) runCatching { inspectM4a(file) }.getOrNull() else null
            val now = file.lastModified()
            database.recordings().insert(RecordingEntity(id, RecordingNames.generated(now), now, now,
                info?.durationMs ?: 0, finalName, "audio/mp4", info?.codec ?: "audio/mp4a-latm",
                info?.sampleRateHz, info?.channelCount ?: 1, file.length(), RecordingStatus.RECOVERY_REQUIRED, "", null))
            if (partial) { /* user can export the untouched partial into a chosen recovery location */ }
        }
    }

    companion object { const val ORPHAN_MIN_AGE_MS = 5 * 60_000L }
}

data class AudioFileInfo(val durationMs: Long, val sampleRateHz: Int, val channelCount: Int, val codec: String)

fun inspectM4a(file: File): AudioFileInfo {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(file.absolutePath)
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                val mime = format.getString(MediaFormat.KEY_MIME)!!
                val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
                val rate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44_100
                val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
                return AudioFileInfo(durationUs / 1_000, rate, channels, mime)
            }
        }
        throw IOException("No audio track was found")
    } finally { extractor.release() }
}
