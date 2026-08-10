package com.wovenledger.app.data.outbox

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: OutboxEntry): Long

    @Update
    suspend fun update(entry: OutboxEntry)

    @Delete
    suspend fun delete(entry: OutboxEntry)

    /** Everything the user should be able to see: what is waiting, what failed, what warned. */
    @Query("SELECT * FROM outbox ORDER BY created_at ASC, id ASC")
    fun observeAll(): Flow<List<OutboxEntry>>

    /**
     * The next write to send: oldest first, so a create is never overtaken by the edit
     * that follows it.
     *
     * Failed rows are deliberately excluded — they have been surfaced to the user and
     * only an explicit retry brings them back.
     */
    @Query(
        "SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY created_at ASC, id ASC LIMIT 1"
    )
    suspend fun oldestPending(): OutboxEntry?

    /**
     * A pending write already queued for the same record, if there is one.
     *
     * An edit to something that has not uploaded yet replaces its payload rather than
     * queueing behind it: the server has never seen either version, so sending both
     * would be a create followed by an update that says the same thing.
     */
    @Query(
        "SELECT * FROM outbox WHERE target = :target AND record_id = :recordId " +
            "AND status = 'PENDING' ORDER BY created_at ASC, id ASC LIMIT 1"
    )
    suspend fun pendingFor(target: OutboxTarget, recordId: Long): OutboxEntry?

    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'PENDING'")
    suspend fun countPending(): Int

    /**
     * Drops the wait on every queued write.
     *
     * A backoff is a guess that the next few minutes will look like the last few. A
     * connection coming back is evidence to the contrary, and honouring a half-hour
     * wait then would leave the user's work sitting still on a working network.
     */
    @Query("UPDATE outbox SET next_attempt_at = 0 WHERE status = 'PENDING'")
    suspend fun clearBackoff()

    @Query("SELECT * FROM outbox WHERE id = :id")
    suspend fun find(id: Long): OutboxEntry?
}
