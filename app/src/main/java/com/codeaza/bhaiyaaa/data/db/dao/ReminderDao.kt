package com.codeaza.bhaiyaaa.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.codeaza.bhaiyaaa.data.db.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders WHERE isDone = 0 ORDER BY IFNULL(dueAt, 9223372036854775807) ASC, createdAt DESC")
    fun observeActive(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ReminderEntity?

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    /**
     * Un-completing also clears [ReminderEntity.notified].
     *
     * The alarm receiver skips anything already notified, so without this a
     * reminder ticked off and then brought back could never fire again - it
     * would sit in the list looking armed, and stay silent.
     */
    @Query(
        """UPDATE reminders
           SET isDone = :done,
               notified = (CASE WHEN :done THEN notified ELSE 0 END)
           WHERE id = :id"""
    )
    suspend fun setDone(id: Long, done: Boolean)

    /** Editing clears notified too: a re-timed reminder is owed a fresh alert. */
    @Query("UPDATE reminders SET text = :text, dueAt = :dueAt, notified = 0 WHERE id = :id")
    suspend fun edit(id: Long, text: String, dueAt: Long?)

    /** Snooze. Also un-completes, so snoozing from a notification always arms. */
    @Query("UPDATE reminders SET dueAt = :dueAt, notified = 0, isDone = 0 WHERE id = :id")
    suspend fun rescheduleTo(id: Long, dueAt: Long)

    @Query("SELECT * FROM reminders WHERE isDone = 1 ORDER BY IFNULL(dueAt, createdAt) DESC LIMIT :limit")
    fun observeDone(limit: Int = 30): Flow<List<ReminderEntity>>

    @Query("UPDATE reminders SET notified = 1 WHERE id = :id")
    suspend fun markNotified(id: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM reminders WHERE isDone = 0")
    fun observePendingCount(): Flow<Int>

    /** Everything still owed a notification - used to re-arm alarms after a reboot. */
    @Query("SELECT * FROM reminders WHERE isDone = 0 AND notified = 0 AND dueAt IS NOT NULL")
    suspend fun pendingScheduled(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE isDone = 0 AND text LIKE '%' || :q || '%' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun search(q: String, limit: Int = 50): List<ReminderEntity>

    @Query("SELECT * FROM reminders ORDER BY createdAt DESC")
    suspend fun allOnce(): List<ReminderEntity>

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}
