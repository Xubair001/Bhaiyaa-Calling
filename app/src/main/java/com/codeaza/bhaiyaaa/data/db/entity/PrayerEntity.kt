package com.codeaza.bhaiyaaa.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-prayer configuration. Exactly five rows, seeded on first run.
 *
 * `manualMinutesFromMidnight` is a local wall-clock time rather than an instant,
 * because "Dhuhr is at 12:30" is a fact about the day, not about a moment - it
 * has to mean the same thing tomorrow without being rewritten.
 */
@Entity(tableName = "prayers")
data class PrayerEntity(
    @PrimaryKey val name: String,
    val enabled: Boolean = true,
    /** Total length of the quiet window, counted from [startOffsetMinutes]. */
    val silenceMinutes: Int = 15,
    /** Null means "use the calculated time". Set, it overrides the calculation. */
    val manualMinutesFromMidnight: Int? = null,
    /**
     * How long before the prayer the quiet window opens, in minutes.
     *
     * Negative means earlier, which is the useful direction: the phone should
     * already be silent as you reach the masjid, not start silencing once the
     * jamaat has begun. Defaults to three minutes early.
     */
    @ColumnInfo(defaultValue = "-3")
    val startOffsetMinutes: Int = -3,
    val sortOrder: Int = 0
)
