package com.codeaza.bhaiyaaa.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.codeaza.bhaiyaaa.data.db.entity.PrayerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrayerDao {

    @Query("SELECT * FROM prayers ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<PrayerEntity>>

    @Query("SELECT * FROM prayers ORDER BY sortOrder ASC")
    suspend fun allOnce(): List<PrayerEntity>

    @Query("SELECT * FROM prayers WHERE name = :name LIMIT 1")
    suspend fun find(name: String): PrayerEntity?

    /** IGNORE so seeding on every launch never overwrites the user's own settings. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(prayers: List<PrayerEntity>)

    @Upsert
    suspend fun upsert(prayer: PrayerEntity)

    @Query("UPDATE prayers SET enabled = :enabled WHERE name = :name")
    suspend fun setEnabled(name: String, enabled: Boolean)

    @Query("UPDATE prayers SET silenceMinutes = :minutes WHERE name = :name")
    suspend fun setSilenceMinutes(name: String, minutes: Int)

    @Query("UPDATE prayers SET manualMinutesFromMidnight = :minutes WHERE name = :name")
    suspend fun setManualTime(name: String, minutes: Int?)
}
