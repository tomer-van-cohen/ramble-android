package io.github.tomervancohen.rambleflow

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity
data class Source(
    @PrimaryKey val kind: String,
    val tree: String,
    val document: String,
    val cutoff: Long,
    val baselineComplete: Boolean = false,
    val enabled: Boolean = true,
)

@Entity(primaryKeys = ["source", "document"])
data class Observation(
    val source: String,
    val document: String,
    val title: String,
    val size: Long,
    val modified: Long,
    val firstSeen: Long,
    val stableSince: Long,
    val baseline: Boolean,
    @ColumnInfo(defaultValue = "''") val proof: String = "",
    @ColumnInfo(defaultValue = "0") val proofAt: Long = 0,
)

@Entity(indices = [Index(value = ["source", "document"], unique = true)])
data class Recording(
    @PrimaryKey val id: String,
    val source: String,
    val document: String,
    val title: String,
    val recordedAt: Long?,
    val state: String = "waiting",
    @ColumnInfo(defaultValue = "''") val completionProof: String = "",
    val reason: String = "completion",
    val hash: String = "",
    val provider: String = "",
    val duration: Long = 0,
    val localUri: String = "",
    val localTarget: String = "downloads",
    val driveId: String = "",
    val driveTarget: String = "",
    val driveState: String = "",
    val nextAttempt: Long = 0,
    @ColumnInfo(defaultValue = "1") val planComplete: Boolean = false,
)

@Entity(primaryKeys = ["recording", "ordinal"])
data class Chunk(
    val recording: String,
    val ordinal: Int,
    val file: String,
    val startSample: Long,
    val samples: Int,
    val state: String = "ready",
    val text: String = "",
    val attempt: String = "",
    val attempts: Int = 0,
    val nextAttempt: Long = 0,
    val hash: String = "",
)

@Entity
data class Attempt(
    @PrimaryKey val id: String,
    val recording: String,
    val ordinal: Int,
    val started: Long,
    val outcome: String,
)

@Entity data class Lease(@PrimaryKey val name: String, val owner: String, val expires: Long)

data class RecordingSummary(val recording: String, val preview: String, val durationMillis: Long, val addedAt: Long)

@Dao
interface QueueDao {
    @Query("SELECT * FROM Source") suspend fun sources(): List<Source>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun source(value: Source)

    @Query("SELECT * FROM Observation WHERE source=:source AND document=:doc")
    suspend fun observation(source: String, doc: String): Observation?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun observation(value: Observation)

    @Query("SELECT * FROM Recording WHERE state!='deleted' ORDER BY recordedAt DESC") fun watch(): Flow<List<Recording>>

    @Query("""SELECT r.id AS recording,
        COALESCE((SELECT substr(text, 1, 180) FROM Chunk WHERE recording=r.id AND state='done' AND trim(text)!='' ORDER BY ordinal LIMIT 1), '') AS preview,
        CASE WHEN r.duration>0 THEN r.duration ELSE COALESCE((SELECT max(startSample+samples)*1000/16000 FROM Chunk WHERE recording=r.id),0) END AS durationMillis,
        COALESCE((SELECT firstSeen FROM Observation WHERE source=r.source AND document=r.document),0) AS addedAt
        FROM Recording r""")
    fun summaries(): Flow<List<RecordingSummary>>

    @Query("SELECT * FROM Recording ORDER BY recordedAt DESC")
    suspend fun recordings(): List<Recording>

    // A validated replacement key can recover all jobs blocked on this provider's credentials.
    // Never replay uncertain/in-flight requests, completed jobs, or a different provider.
    @Query("""UPDATE Recording SET provider=:profile, state='queued', reason='', nextAttempt=0
        WHERE state='key' AND (provider=:provider OR provider LIKE :provider || '@%')""")
    suspend fun resumeKeyBlocked(provider: String, profile: String): Int

    @Query("SELECT * FROM Recording WHERE source=:source AND document=:document")
    suspend fun origin(source: String, document: String): Recording?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun attempt(value: Attempt)

    @Query("SELECT * FROM Recording WHERE id=:id") suspend fun recording(id: String): Recording?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun recording(value: Recording)

    @Query("SELECT * FROM Chunk WHERE recording=:id ORDER BY ordinal")
    suspend fun chunks(id: String): List<Chunk>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun chunk(value: Chunk)

    @Query("DELETE FROM Chunk WHERE recording=:id") suspend fun deleteChunks(id: String)
    @Query("DELETE FROM Attempt WHERE recording=:id") suspend fun deleteAttempts(id: String)

    @Query("SELECT * FROM Lease WHERE name=:name") suspend fun lease(name: String): Lease?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun lease(value: Lease)

    @Query("DELETE FROM Lease WHERE name=:name AND owner=:owner")
    suspend fun release(name: String, owner: String)
}

@Database(
    entities =
        [
            Source::class,
            Observation::class,
            Recording::class,
            Chunk::class,
            Attempt::class,
            Lease::class,
        ],
    version = 3,
    exportSchema = true,
)
abstract class QueueDatabase : RoomDatabase() {
    abstract fun queue(): QueueDao
}

object QueueMigrations {
    val FROM_2_TO_3 = object : androidx.room.migration.Migration(2,3) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            // Old chunks were committed as one batch only after the whole plan completed.
            db.execSQL("ALTER TABLE Recording ADD COLUMN planComplete INTEGER NOT NULL DEFAULT 1")
        }
    }

    val FROM_1_TO_2 =
        object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Observation ADD COLUMN proof TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE Observation ADD COLUMN proofAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "ALTER TABLE Recording ADD COLUMN completionProof TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL("UPDATE Recording SET state='waiting' WHERE state='confirm'")
            }
        }
}
