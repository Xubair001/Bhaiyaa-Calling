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

    @Query("UPDATE reminders SET isDone = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

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
