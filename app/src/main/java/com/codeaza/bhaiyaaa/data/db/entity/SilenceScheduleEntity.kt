package com.codeaza.bhaiyaaa.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A quiet period the user defined themselves - a meeting, a class, sleep.
 *
 * Stored as a local wall-clock time and a weekday mask rather than as instants,
 * because "quiet from 9pm every night" is a fact about the clock, not about a
 * particular evening. It has to keep meaning the same thing tomorrow without
 * being rewritten, and has to survive the user changing time zone.
 */
@Entity(tableName = "silence_schedules")
data class SilenceScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val startMinutesFromMidnight: Int,
    val durationMinutes: Int,
    /** Bit 0 Sunday .. bit 6 Saturday. */
    val daysMask: Int,
    val enabled: Boolean = true,
    /** SILENT or VIBRATE, per schedule - a meeting and sleep want different things. */
    val silenceMode: String,
    val createdAt: Long
)
