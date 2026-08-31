package com.codeaza.bhaiyaaa.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.codeaza.bhaiyaaa.data.db.entity.SilenceScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SilenceScheduleDao {

    @Query("SELECT * FROM silence_schedules ORDER BY startMinutesFromMidnight ASC, id ASC")
    fun observeAll(): Flow<List<SilenceScheduleEntity>>

    @Query("SELECT * FROM silence_schedules ORDER BY startMinutesFromMidnight ASC, id ASC")
    suspend fun allOnce(): List<SilenceScheduleEntity>

    @Query("SELECT * FROM silence_schedules WHERE id = :id LIMIT 1")
    suspend fun find(id: Long): SilenceScheduleEntity?

    @Insert
    suspend fun insert(schedule: SilenceScheduleEntity): Long

    @Upsert
    suspend fun upsert(schedule: SilenceScheduleEntity)

    @Query("UPDATE silence_schedules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM silence_schedules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Delete
    suspend fun delete(schedule: SilenceScheduleEntity)

    @Query("SELECT COUNT(*) FROM silence_schedules WHERE enabled = 1")
    fun observeEnabledCount(): Flow<Int>
}
