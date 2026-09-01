package com.codeaza.bhaiyaaa.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.codeaza.bhaiyaaa.data.db.entity.VoiceRecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceRecordingDao {

    @Query("SELECT * FROM voice_recordings ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VoiceRecordingEntity>>

    @Query("SELECT * FROM voice_recordings ORDER BY createdAt DESC")
    suspend fun allOnce(): List<VoiceRecordingEntity>

    @Query("SELECT * FROM voice_recordings WHERE id = :id LIMIT 1")
    suspend fun find(id: Long): VoiceRecordingEntity?

    /** One call's voice notes, oldest first so they read in the order made. */
    @Query("SELECT * FROM voice_recordings WHERE callId = :callId ORDER BY createdAt ASC")
    fun observeForCall(callId: Long): Flow<List<VoiceRecordingEntity>>

    @Insert
    suspend fun insert(recording: VoiceRecordingEntity): Long

    @Query("UPDATE voice_recordings SET label = :label WHERE id = :id")
    suspend fun rename(id: Long, label: String)

    @Query("DELETE FROM voice_recordings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM voice_recordings")
    suspend fun count(): Int
}
