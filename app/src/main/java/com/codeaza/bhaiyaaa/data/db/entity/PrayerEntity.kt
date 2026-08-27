package com.codeaza.bhaiyaaa.data.db.entity

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
    val silenceMinutes: Int = 20,
    /** Null means "use the calculated time". Set, it overrides the calculation. */
    val manualMinutesFromMidnight: Int? = null,
    val sortOrder: Int = 0
)
