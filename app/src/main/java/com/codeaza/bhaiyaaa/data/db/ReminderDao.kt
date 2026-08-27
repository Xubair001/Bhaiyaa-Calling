package com.codeaza.bhaiyaaa.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE isDone = 0 ORDER BY createdAtMillis DESC")
    fun observeActive(): Flow<List<ReminderEntity>>

    @Insert
    suspend fun insert(reminder: ReminderEntity)

    @Query("UPDATE reminders SET isDone = 1 WHERE id = :id")
    suspend fun markDone(id: Long)
}
