package com.codeaza.bhaiyaaa.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object Formatting {

    fun dateTime(millis: Long): String =
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(millis)

    fun date(millis: Long): String =
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(millis)

    fun time(millis: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(millis)

    fun dayLabel(millis: Long): String =
        SimpleDateFormat("EEE", Locale.getDefault()).format(millis)

    /** "Yesterday, 4:12 PM" reads better than a bare date for recent activity. */
    fun relativeDateTime(millis: Long, now: Long = System.currentTimeMillis()): String {
        val startToday = TimeRanges.startOfDay(now)
        val startYesterday = startToday - TimeUnit.DAYS.toMillis(1)
        return when {
            millis >= startToday -> "Today, ${time(millis)}"
            millis >= startYesterday -> "Yesterday, ${time(millis)}"
            millis >= TimeRanges.startOfDaysAgo(now, 6) ->
                "${SimpleDateFormat("EEEE", Locale.getDefault()).format(millis)}, ${time(millis)}"
            else -> dateTime(millis)
        }
    }

    /** Compact duration: 45s, 3m 20s, 1h 05m. */
    fun duration(seconds: Long): String {
        if (seconds <= 0) return "—"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> String.format(Locale.getDefault(), "%dh %02dm", h, m)
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    fun bytes(count: Long): String {
        if (count <= 0) return "0 MB"
        val mb = count / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format(Locale.getDefault(), "%.1f GB", mb / 1024)
        else String.format(Locale.getDefault(), "%.0f MB", mb)
    }

    fun hourLabel(hour: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
        }
        return SimpleDateFormat("h a", Locale.getDefault()).format(cal.timeInMillis)
    }

    /** Grammatical plural without pulling in a full pluralisation library. */
    fun plural(count: Int, singular: String, plural: String = singular + "s"): String =
        if (count == 1) "$count $singular" else "$count $plural"
}
