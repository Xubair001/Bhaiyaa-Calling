package com.codeaza.bhaiyaaa.util

import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Calendar-aware window boundaries. "This week" means since the local week
 * started, not "the last 168 hours" - the two differ, and using the wrong one
 * makes Insights quietly disagree with what the user sees in their call log.
 */
object TimeRanges {

    fun startOfDay(now: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun startOfWeek(now: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = startOfDay(now, zone)
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            if (timeInMillis > now) add(Calendar.DAY_OF_YEAR, -7)
        }.timeInMillis

    fun startOfMonth(now: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = startOfDay(now, zone)
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

    /** Start of the day N days back, for the rolling 7-day chart. */
    fun startOfDaysAgo(now: Long, days: Int, zone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = startOfDay(now, zone)
            add(Calendar.DAY_OF_YEAR, -days)
        }.timeInMillis

    /**
     * Offset used to bucket UTC epoch millis into local days inside SQL.
     * Read at the given instant so it reflects the DST rules actually in force.
     */
    fun utcOffsetMillis(now: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        zone.getOffset(now).toLong()

    fun daysAgo(now: Long, days: Int): Long = now - TimeUnit.DAYS.toMillis(days.toLong())
}
