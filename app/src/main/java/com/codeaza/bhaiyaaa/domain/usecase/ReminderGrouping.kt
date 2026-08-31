package com.codeaza.bhaiyaaa.domain.usecase

import java.util.Calendar
import java.util.TimeZone

/**
 * Where a reminder sits relative to now.
 *
 * Declaration order is display order, so a screen can iterate the enum instead
 * of hard-coding a sequence that then drifts from this file.
 */
enum class ReminderBucket(val label: String) {
    OVERDUE("Overdue"),
    TODAY("Today"),
    TOMORROW("Tomorrow"),
    UPCOMING("Upcoming"),
    SOMEDAY("Someday"),
}

data class ReminderGroup<T>(val bucket: ReminderBucket, val items: List<T>)

/**
 * Buckets reminders for display.
 *
 * Kept free of Room and Compose types so the date arithmetic - the part that
 * actually breaks, around midnight and across a DST change - can be tested
 * directly.
 *
 * Overdue is decided against the clock, not the calendar: something due at
 * 09:00 when it is 10:00 the same morning is overdue, not "today". Sorting it
 * under Today is the failure people notice, because the app looks like it
 * forgot.
 *
 * Reminders use the phone's own zone. The time-zone override in Prayer
 * settings exists to correct where prayer times are *calculated* for, and
 * applying it here would move the user's whole day.
 */
object ReminderGrouping {

    fun bucketOf(
        dueAt: Long?,
        now: Long,
        zone: TimeZone = TimeZone.getDefault()
    ): ReminderBucket = when {
        dueAt == null -> ReminderBucket.SOMEDAY
        dueAt < now -> ReminderBucket.OVERDUE
        dueAt < startOfDayFromNow(now, zone, 1) -> ReminderBucket.TODAY
        dueAt < startOfDayFromNow(now, zone, 2) -> ReminderBucket.TOMORROW
        else -> ReminderBucket.UPCOMING
    }

    /**
     * Groups in bucket order, dropping empty buckets.
     *
     * Order within a bucket is whatever order the caller passed in - the DAO
     * already sorts by due date - so this never re-sorts and cannot fight the
     * query.
     */
    fun <T> group(
        items: List<T>,
        now: Long,
        zone: TimeZone = TimeZone.getDefault(),
        dueAt: (T) -> Long?
    ): List<ReminderGroup<T>> {
        val byBucket = items.groupBy { bucketOf(dueAt(it), now, zone) }
        return ReminderBucket.entries.mapNotNull { bucket ->
            byBucket[bucket]?.takeIf { it.isNotEmpty() }?.let { ReminderGroup(bucket, it) }
        }
    }

    /**
     * Midnight [daysAhead] days after the day containing [now].
     *
     * Built by adding calendar days rather than 24-hour blocks: on the night a
     * DST change lands, the day is 23 or 25 hours long, and adding milliseconds
     * puts the boundary an hour into the wrong day.
     */
    private fun startOfDayFromNow(now: Long, zone: TimeZone, daysAhead: Int): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, daysAhead)
        }.timeInMillis
}
