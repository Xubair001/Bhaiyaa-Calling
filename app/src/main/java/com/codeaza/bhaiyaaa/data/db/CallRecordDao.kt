package com.codeaza.bhaiyaaa.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CallRecordDao {
    @Query("SELECT * FROM call_records ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CallRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(calls: List<CallRecordEntity>)

    @Query("SELECT COUNT(*) FROM call_records WHERE type = 'MISSED' AND timestamp >= :sinceMillis")
    suspend fun missedSince(sinceMillis: Long): Int

    @Query("DELETE FROM call_records")
    suspend fun clearAll()
}
