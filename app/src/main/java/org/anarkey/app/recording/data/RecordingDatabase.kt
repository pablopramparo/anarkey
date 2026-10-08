package org.anarkey.app.recording.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.anarkey.core.recording.RecordingStatus

@Entity(tableName = "sessions", indices = [Index("updatedAtMs")])
data class SessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val notes: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(tableName = "songs", indices = [Index("title"), Index("artist"), Index("updatedAtMs")])
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String?,
    val keyRoot: String?,
    val keyMode: String?,
    val bpm: Int?,
    val timeNumerator: Int?,
    val timeDenominator: Int?,
    val instrumentId: String?,
    val tuningId: String?,
    val capo: Int,
    val notes: String,
    val favorite: Boolean,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(tableName = "song_sections", foreignKeys = [ForeignKey(entity = SongEntity::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["songId", "position"])])
data class SongSectionEntity(@PrimaryKey val id: String, val songId: String, val position: Int, val kind: String, val title: String, val notes: String)

@Entity(tableName = "song_lines", foreignKeys = [
    ForeignKey(entity = SongEntity::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = SongSectionEntity::class, parentColumns = ["id"], childColumns = ["sectionId"], onDelete = ForeignKey.SET_NULL),
], indices = [Index(value = ["songId", "position"]), Index(value = ["sectionId", "position"])])
data class SongLineEntity(@PrimaryKey val id: String, val songId: String, val sectionId: String?, val position: Int, val text: String)

@Entity(tableName = "chord_placements", foreignKeys = [ForeignKey(entity = SongLineEntity::class, parentColumns = ["id"], childColumns = ["lineId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["lineId", "position", "orderInPosition"])])
data class ChordPlacementEntity(
    @PrimaryKey val id: String, val lineId: String, val position: Int, val orderInPosition: Int,
    val originalSymbol: String, val rootLetter: String?, val rootAccidental: Int?, val quality: String?,
    val extension: String?, val bassLetter: String?, val bassAccidental: Int?,
)

@Entity(tableName = "tags", indices = [Index(value = ["normalizedName"], unique = true)])
data class TagEntity(@PrimaryKey val id: String, val name: String, val normalizedName: String)

@Entity(tableName = "song_tags", primaryKeys = ["songId", "tagId"], foreignKeys = [
    ForeignKey(entity = SongEntity::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
], indices = [Index("tagId")])
data class SongTagEntity(val songId: String, val tagId: String)

@Entity(tableName = "chord_favorites", indices = [Index("voicingId")])
data class ChordFavoriteEntity(@PrimaryKey val voicingId: String, val addedAtMs: Long)

@Entity(
    tableName = "recordings",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = SongEntity::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("sessionId"), Index("songId"), Index(value = ["status", "createdAtMs"])],
)
data class RecordingEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val durationMs: Long,
    /** UUID-derived relative filename, never a display name or arbitrary path. */
    val audioFileName: String,
    val mimeType: String,
    val codec: String,
    val sampleRateHz: Int?,
    val channelCount: Int,
    val sizeBytes: Long,
    val status: RecordingStatus,
    val notes: String,
    val sessionId: String?,
    /** Reserved for a future Song relation; there is deliberately no M2 Song table or FK. */
    val songId: String? = null,
    /** Regenerable summary, never source audio. */
    val waveformPeaks: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (other !is RecordingEntity) return false
        return id == other.id && displayName == other.displayName && createdAtMs == other.createdAtMs &&
            updatedAtMs == other.updatedAtMs && durationMs == other.durationMs && audioFileName == other.audioFileName &&
            mimeType == other.mimeType && codec == other.codec && sampleRateHz == other.sampleRateHz &&
            channelCount == other.channelCount && sizeBytes == other.sizeBytes && status == other.status &&
            notes == other.notes && sessionId == other.sessionId && songId == other.songId &&
            (waveformPeaks?.contentEquals(other.waveformPeaks ?: return false) ?: (other.waveformPeaks == null))
    }
    override fun hashCode() = 31 * id.hashCode() + (waveformPeaks?.contentHashCode() ?: 0)
}

@Entity(
    tableName = "recording_markers",
    foreignKeys = [ForeignKey(entity = RecordingEntity::class, parentColumns = ["id"], childColumns = ["recordingId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["recordingId", "positionMs"])],
)
data class RecordingMarkerEntity(
    @PrimaryKey val id: String,
    val recordingId: String,
    val positionMs: Long,
    val text: String?,
    val createdAtMs: Long,
)

class RecordingConverters {
    @TypeConverter fun statusToString(value: RecordingStatus): String = value.name
    @TypeConverter fun stringToStatus(value: String): RecordingStatus = RecordingStatus.valueOf(value)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY updatedAtMs DESC, id") fun observeAll(): Flow<List<SessionEntity>>
    @Insert suspend fun insert(session: SessionEntity)
    @Update suspend fun update(session: SessionEntity)
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun find(id: String): SessionEntity?
    @Query("DELETE FROM sessions WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAtMs DESC, id") fun observeAll(): Flow<List<RecordingEntity>>
    @Query("SELECT * FROM recordings WHERE id = :id") suspend fun find(id: String): RecordingEntity?
    @Query("SELECT * FROM recordings") suspend fun all(): List<RecordingEntity>
    @Query("SELECT * FROM recordings WHERE songId = :songId ORDER BY createdAtMs DESC, id") fun observeForSong(songId: String): Flow<List<RecordingEntity>>
    @Query("UPDATE recordings SET songId = :songId, updatedAtMs = :updatedAtMs WHERE id = :recordingId") suspend fun setSong(recordingId: String, songId: String?, updatedAtMs: Long): Int
    @Query("SELECT * FROM recordings WHERE status = :status") suspend fun byStatus(status: RecordingStatus): List<RecordingEntity>
    @Insert suspend fun insert(recording: RecordingEntity)
    @Update suspend fun update(recording: RecordingEntity)
    @Query("DELETE FROM recordings WHERE id = :id AND status = 'DELETING'") suspend fun deleteMarked(id: String): Int
}

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY favorite DESC, title COLLATE NOCASE, id") fun observeAll(): Flow<List<SongEntity>>
    @Query("SELECT * FROM songs WHERE id = :id") suspend fun find(id: String): SongEntity?
    @Query("SELECT * FROM songs WHERE id IN (SELECT songId FROM song_tags WHERE tagId = :tagId) ORDER BY favorite DESC, title COLLATE NOCASE, id") fun observeByTag(tagId: String): Flow<List<SongEntity>>
    @Insert suspend fun insert(song: SongEntity)
    @Update suspend fun update(song: SongEntity)
    @Query("DELETE FROM songs WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM song_sections WHERE songId = :songId ORDER BY position, id") suspend fun sections(songId: String): List<SongSectionEntity>
    @Query("SELECT * FROM song_sections WHERE songId = :songId ORDER BY position, id") fun observeSections(songId: String): Flow<List<SongSectionEntity>>
    @Insert suspend fun insertSection(section: SongSectionEntity)
    @Query("SELECT * FROM song_sections WHERE id = :id") suspend fun section(id: String): SongSectionEntity?
    @Update suspend fun updateSections(sections: List<SongSectionEntity>)
    @Update suspend fun updateSection(section: SongSectionEntity)
    @Query("SELECT * FROM song_sections WHERE songId = :songId AND position = :position LIMIT 1") suspend fun sectionAt(songId: String, position: Int): SongSectionEntity?
    @Query("DELETE FROM song_sections WHERE id = :id") suspend fun deleteSection(id: String)
    @Query("SELECT * FROM song_lines WHERE songId = :songId ORDER BY sectionId, position, id") suspend fun lines(songId: String): List<SongLineEntity>
    @Query("SELECT * FROM song_lines WHERE songId = :songId AND sectionId IS :sectionId ORDER BY position, id") suspend fun linesInSection(songId: String, sectionId: String?): List<SongLineEntity>
    @Query("SELECT * FROM song_lines WHERE songId = :songId AND sectionId IS :sectionId AND position = :position LIMIT 1") suspend fun lineAt(songId: String, sectionId: String?, position: Int): SongLineEntity?
    @Query("SELECT * FROM song_lines WHERE id = :id") suspend fun line(id: String): SongLineEntity?
    @Query("SELECT * FROM song_lines WHERE songId = :songId ORDER BY sectionId, position, id") fun observeLines(songId: String): Flow<List<SongLineEntity>>
    @Insert suspend fun insertLines(lines: List<SongLineEntity>)
    @Update suspend fun updateLines(lines: List<SongLineEntity>)
    @Query("DELETE FROM song_lines WHERE id = :id") suspend fun deleteLine(id: String)
    @Query("DELETE FROM song_lines WHERE songId = :songId") suspend fun deleteLines(songId: String)
    @Query("SELECT * FROM chord_placements WHERE lineId = :lineId ORDER BY position, orderInPosition, id") suspend fun chords(lineId: String): List<ChordPlacementEntity>
    @Query("SELECT * FROM chord_placements WHERE id = :id") suspend fun chord(id: String): ChordPlacementEntity?
    @Query("SELECT * FROM chord_placements WHERE lineId IN (:lineIds) ORDER BY lineId, position, orderInPosition, id") suspend fun chordsForLines(lineIds: List<String>): List<ChordPlacementEntity>
    @Insert suspend fun insertChord(chord: ChordPlacementEntity)
    @Update suspend fun updateChord(chord: ChordPlacementEntity)
    @Query("DELETE FROM chord_placements WHERE id = :id") suspend fun deleteChord(id: String)
    @Insert suspend fun insertTag(tag: TagEntity)
    @Query("SELECT * FROM tags WHERE normalizedName = :normalized LIMIT 1") suspend fun findTag(normalized: String): TagEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun attachTag(relation: SongTagEntity)
    @Query("DELETE FROM song_tags WHERE songId = :songId AND tagId = :tagId") suspend fun detachTag(songId: String, tagId: String)
    @Query("SELECT tags.* FROM tags INNER JOIN song_tags ON tags.id = song_tags.tagId WHERE song_tags.songId = :songId ORDER BY tags.normalizedName") suspend fun tags(songId: String): List<TagEntity>
    @Query("SELECT * FROM tags ORDER BY normalizedName") fun observeTags(): Flow<List<TagEntity>>
}

@Dao
interface ChordFavoriteDao {
    @Query("SELECT * FROM chord_favorites ORDER BY addedAtMs, voicingId") fun observeAll(): Flow<List<ChordFavoriteEntity>>
    @Query("SELECT * FROM chord_favorites WHERE voicingId = :id") suspend fun find(id: String): ChordFavoriteEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun add(row: ChordFavoriteEntity)
    @Query("DELETE FROM chord_favorites WHERE voicingId = :id") suspend fun remove(id: String)
}

@Dao
interface RecordingMarkerDao {
    @Query("SELECT * FROM recording_markers WHERE recordingId = :recordingId ORDER BY positionMs, id")
    fun observeForRecording(recordingId: String): Flow<List<RecordingMarkerEntity>>
    @Insert suspend fun insert(marker: RecordingMarkerEntity)
    @Update suspend fun update(marker: RecordingMarkerEntity)
    @Query("SELECT * FROM recording_markers WHERE id = :id") suspend fun find(id: String): RecordingMarkerEntity?
    @Query("DELETE FROM recording_markers WHERE id = :id AND recordingId = :recordingId")
    suspend fun delete(id: String, recordingId: String): Int
    @Query("UPDATE recording_markers SET positionMs = :durationMs WHERE recordingId = :recordingId AND positionMs > :durationMs")
    suspend fun clampPositions(recordingId: String, durationMs: Long)
}

@Database(entities = [SessionEntity::class, RecordingEntity::class, RecordingMarkerEntity::class, SongEntity::class, SongSectionEntity::class, SongLineEntity::class, ChordPlacementEntity::class, TagEntity::class, SongTagEntity::class, ChordFavoriteEntity::class], version = 4, exportSchema = true)
@TypeConverters(RecordingConverters::class)
abstract class RecordingDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun recordings(): RecordingDao
    abstract fun markers(): RecordingMarkerDao
    abstract fun songs(): SongDao
    abstract fun chordFavorites(): ChordFavoriteDao

    companion object {
        const val FILE_NAME = "recordings.db"
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recordings` ADD COLUMN `songId` TEXT")
                db.execSQL("ALTER TABLE `recordings` ADD COLUMN `waveformPeaks` BLOB")
                db.execSQL("CREATE TABLE IF NOT EXISTS `recording_markers` (`id` TEXT NOT NULL, `recordingId` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, `text` TEXT, `createdAtMs` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`recordingId`) REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recording_markers_recordingId_positionMs` ON `recording_markers` (`recordingId`, `positionMs`)")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `songs` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT, `keyRoot` TEXT, `keyMode` TEXT, `bpm` INTEGER, `timeNumerator` INTEGER, `timeDenominator` INTEGER, `instrumentId` TEXT, `tuningId` TEXT, `capo` INTEGER NOT NULL, `notes` TEXT NOT NULL, `favorite` INTEGER NOT NULL, `createdAtMs` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_title` ON `songs` (`title`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_artist` ON `songs` (`artist`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_updatedAtMs` ON `songs` (`updatedAtMs`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `song_sections` (`id` TEXT NOT NULL, `songId` TEXT NOT NULL, `position` INTEGER NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `notes` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_sections_songId_position` ON `song_sections` (`songId`, `position`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `song_lines` (`id` TEXT NOT NULL, `songId` TEXT NOT NULL, `sectionId` TEXT, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`sectionId`) REFERENCES `song_sections`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_lines_songId_position` ON `song_lines` (`songId`, `position`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_lines_sectionId_position` ON `song_lines` (`sectionId`, `position`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `chord_placements` (`id` TEXT NOT NULL, `lineId` TEXT NOT NULL, `position` INTEGER NOT NULL, `orderInPosition` INTEGER NOT NULL, `originalSymbol` TEXT NOT NULL, `rootLetter` TEXT, `rootAccidental` INTEGER, `quality` TEXT, `extension` TEXT, `bassLetter` TEXT, `bassAccidental` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`lineId`) REFERENCES `song_lines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chord_placements_lineId_position_orderInPosition` ON `chord_placements` (`lineId`, `position`, `orderInPosition`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `normalizedName` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_normalizedName` ON `tags` (`normalizedName`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `song_tags` (`songId` TEXT NOT NULL, `tagId` TEXT NOT NULL, PRIMARY KEY(`songId`, `tagId`), FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_tags_tagId` ON `song_tags` (`tagId`)")

                // Rebuild both related tables together so the new Song FK is real without losing M2 markers.
                db.execSQL("CREATE TEMP TABLE marker_preserve AS SELECT * FROM recording_markers")
                db.execSQL("CREATE TABLE recordings_new (`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `createdAtMs` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `audioFileName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `codec` TEXT NOT NULL, `sampleRateHz` INTEGER, `channelCount` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, `status` TEXT NOT NULL, `notes` TEXT NOT NULL, `sessionId` TEXT, `songId` TEXT, `waveformPeaks` BLOB, PRIMARY KEY(`id`), FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("INSERT INTO recordings_new SELECT * FROM recordings")
                db.execSQL("DROP TABLE recording_markers")
                db.execSQL("DROP TABLE recordings")
                db.execSQL("ALTER TABLE recordings_new RENAME TO recordings")
                db.execSQL("CREATE INDEX `index_recordings_sessionId` ON `recordings` (`sessionId`)")
                db.execSQL("CREATE INDEX `index_recordings_songId` ON `recordings` (`songId`)")
                db.execSQL("CREATE INDEX `index_recordings_status_createdAtMs` ON `recordings` (`status`, `createdAtMs`)")
                db.execSQL("CREATE TABLE `recording_markers` (`id` TEXT NOT NULL, `recordingId` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, `text` TEXT, `createdAtMs` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`recordingId`) REFERENCES `recordings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX `index_recording_markers_recordingId_positionMs` ON `recording_markers` (`recordingId`, `positionMs`)")
                db.execSQL("INSERT INTO recording_markers SELECT * FROM marker_preserve")
                db.execSQL("DROP TABLE marker_preserve")
            }
        }
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `chord_favorites` (`voicingId` TEXT NOT NULL, `addedAtMs` INTEGER NOT NULL, PRIMARY KEY(`voicingId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chord_favorites_voicingId` ON `chord_favorites` (`voicingId`)")
            }
        }
    }
}
