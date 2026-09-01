package com.codeaza.bhaiyaaa.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.codeaza.bhaiyaaa.data.db.entity.PrayerEntity
import com.codeaza.bhaiyaaa.domain.model.Prayer
import kotlinx.coroutines.flow.Flow

/**
 * Per-prayer configuration.
 *
 * An abstract class rather than an interface so the AM/PM rule can be enforced
 * *here*, at the persistence boundary, and not only in the picker. A screen can
 * be bypassed - by the assistant, by an import, by the next feature someone
 * adds - and "Fajr is in the morning" is a fact about the data, not a property
 * of one dialog. [setManualTime] is the only way in, and it normalises.
 */
@Dao
abstract class PrayerDao {

    @Query("SELECT * FROM prayers ORDER BY sortOrder ASC")
    abstract fun observeAll(): Flow<List<PrayerEntity>>

    @Query("SELECT * FROM prayers ORDER BY sortOrder ASC")
    abstract suspend fun allOnce(): List<PrayerEntity>

    @Query("SELECT * FROM prayers WHERE name = :name LIMIT 1")
    abstract suspend fun find(name: String): PrayerEntity?

    /** IGNORE so seeding on every launch never overwrites the user's own settings. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(prayers: List<PrayerEntity>)

    @Upsert
    abstract suspend fun upsert(prayer: PrayerEntity)

    @Query("UPDATE prayers SET enabled = :enabled WHERE name = :name")
    abstract suspend fun setEnabled(name: String, enabled: Boolean)

    @Query("UPDATE prayers SET silenceMinutes = :minutes WHERE name = :name")
    abstract suspend fun setSilenceMinutes(name: String, minutes: Int)

    /**
     * Writes a time without checking it. Not for callers - use [setManualTime].
     *
     * It exists only because Room has to generate the statement somewhere, and
     * the name is deliberately unpleasant so that reaching for it reads as the
     * mistake it is.
     */
    @Query("UPDATE prayers SET manualMinutesFromMidnight = :minutes WHERE name = :name")
    abstract suspend fun setManualTimeUnchecked(name: String, minutes: Int?)

    /**
     * Sets - or clears - a prayer's manual time.
     *
     * @param minutesFromMidnight null clears the override and returns that
     *   prayer to the calculation. Any other value is brought into the
     *   prayer's own half of the clock by [Prayer.normaliseTime], so an
     *   invalid time is corrected rather than stored and complained about
     *   afterwards.
     */
    suspend fun setManualTime(prayer: Prayer, minutesFromMidnight: Int?) {
        setManualTimeUnchecked(
            prayer.storageValue,
            minutesFromMidnight?.let(prayer::normaliseTime)
        )
    }

    /**
     * One prayer's whole editable state, written atomically.
     *
     * The editor changes the time, the quiet length and the head start
     * together, and writing them as three statements meant three database
     * writes, three Flow emissions and - because every write re-armed the
     * alarms - three full rescheduling passes for one tap on Save. One
     * transaction is both faster and the only version where a failure halfway
     * through cannot leave a prayer with a new time and an old window.
     *
     * @param minutesFromMidnight null keeps the calculated time.
     */
    @Transaction
    open suspend fun saveEdit(
        prayer: Prayer,
        minutesFromMidnight: Int?,
        silenceMinutes: Int,
        startOffsetMinutes: Int
    ) {
        val existing = find(prayer.storageValue)
            ?: PrayerEntity(name = prayer.storageValue, sortOrder = prayer.order)
        upsert(
            existing.copy(
                manualMinutesFromMidnight = minutesFromMidnight?.let(prayer::normaliseTime),
                silenceMinutes = silenceMinutes.coerceIn(SILENCE_MINUTES_MIN, SILENCE_MINUTES_MAX),
                startOffsetMinutes = startOffsetMinutes.coerceIn(OFFSET_MIN, OFFSET_MAX)
            )
        )
    }

    companion object {
        const val SILENCE_MINUTES_MIN = 1
        const val SILENCE_MINUTES_MAX = 180
        const val OFFSET_MIN = -60
        const val OFFSET_MAX = 60
    }
}
