package com.codeaza.bhaiyaaa.ai

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Result of scanning a phrase for a "when".
 *
 * @param dueAt absolute epoch millis, or null if the phrase named no time.
 * @param matchedText the substring that expressed the time, so the caller can
 *   strip it out of the reminder body ("call Ali tomorrow" -> "call Ali").
 */
data class ParsedTime(val dueAt: Long?, val matchedText: String? = null)

/**
 * A deliberately small, predictable time parser for reminder phrases.
 *
 * This is rule-based on purpose. A language model guessing at "next Friday"
 * would occasionally be confidently wrong about a date the user is relying on,
 * and a reminder that fires on the wrong day is worse than one that admits it
 * didn't understand. Anything not matched here returns a null [ParsedTime.dueAt],
 * and the reminder is saved without a due time rather than with a guessed one.
 */
object TimeExpressions {

    private const val DEFAULT_MORNING_HOUR = 9
    private const val DEFAULT_EVENING_HOUR = 20

    private val WEEKDAYS = mapOf(
        "monday" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY,
        "sunday" to Calendar.SUNDAY
    )

    private val RELATIVE = Regex("""\bin\s+(\d{1,3})\s*(min|mins|minute|minutes|hour|hours|hr|hrs|day|days|week|weeks)\b""")
    private val CLOCK = Regex("""\bat\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""")
    private val BARE_MERIDIEM = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""")

    fun parse(text: String, now: Long, zone: TimeZone = TimeZone.getDefault()): ParsedTime {
        val lower = text.lowercase(Locale.ROOT)

        // "in 20 minutes" / "in 3 days" - fully specifies an instant on its own.
        RELATIVE.find(lower)?.let { m ->
            val amount = m.groupValues[1].toIntOrNull() ?: return@let
            val unit = m.groupValues[2]
            val cal = calendar(now, zone)
            when {
                unit.startsWith("min") -> cal.add(Calendar.MINUTE, amount)
                unit.startsWith("hour") || unit.startsWith("hr") -> cal.add(Calendar.HOUR_OF_DAY, amount)
                unit.startsWith("day") -> cal.add(Calendar.DAY_OF_YEAR, amount)
                unit.startsWith("week") -> cal.add(Calendar.DAY_OF_YEAR, amount * 7)
            }
            return ParsedTime(cal.timeInMillis, m.value)
        }

        // Otherwise: find a day, then a time of day, and combine them.
        var matched: String? = null
        val cal = calendar(now, zone)
        var dayFound = false

        when {
            lower.contains("day after tomorrow") -> {
                cal.add(Calendar.DAY_OF_YEAR, 2); dayFound = true; matched = "day after tomorrow"
            }
            lower.contains("tomorrow") -> {
                cal.add(Calendar.DAY_OF_YEAR, 1); dayFound = true; matched = "tomorrow"
            }
            lower.contains("tonight") -> {
                setTime(cal, DEFAULT_EVENING_HOUR, 0); dayFound = true; matched = "tonight"
            }
            lower.contains("today") -> {
                dayFound = true; matched = "today"
            }
            else -> {
                for ((name, dow) in WEEKDAYS) {
                    if (!lower.contains(name)) continue
                    // "next monday" always means the following week's monday;
                    // a bare "monday" means the soonest upcoming one.
                    val wantNextWeek = lower.contains("next $name")
                    advanceToWeekday(cal, dow, wantNextWeek)
                    dayFound = true
                    matched = if (wantNextWeek) "next $name" else name
                    break
                }
            }
        }

        // Explicit clock time overrides any default hour chosen above.
        val clock = CLOCK.find(lower) ?: BARE_MERIDIEM.find(lower)
        if (clock != null) {
            val rawHour = clock.groupValues[1].toIntOrNull()
            if (rawHour != null && rawHour in 0..23) {
                val minute = clock.groupValues[2].toIntOrNull() ?: 0
                val meridiem = clock.groupValues[3]
                var hour = rawHour
                if (meridiem == "pm" && hour < 12) hour += 12
                if (meridiem == "am" && hour == 12) hour = 0
                if (minute in 0..59) {
                    setTime(cal, hour, minute)
                    matched = listOfNotNull(matched, clock.value).joinToString(" ")
                    // A bare time with no day named means the next time it comes
                    // round - today if it hasn't passed, otherwise tomorrow.
                    if (!dayFound && cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
                    dayFound = true
                }
            }
        } else if (dayFound && !lower.contains("tonight")) {
            when {
                lower.contains("evening") -> setTime(cal, DEFAULT_EVENING_HOUR, 0)
                lower.contains("afternoon") -> setTime(cal, 15, 0)
                else -> setTime(cal, DEFAULT_MORNING_HOUR, 0)
            }
        }

        if (!dayFound) return ParsedTime(null)

        // Never schedule into the past - "today at 9am" said at 6pm means tomorrow.
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return ParsedTime(cal.timeInMillis, matched)
    }

    /** Removes the time phrase from a reminder body so the saved text reads cleanly. */
    fun stripTimePhrase(text: String, matched: String?): String {
        if (matched.isNullOrBlank()) return text.trim()
        var out = text
        for (token in matched.split(" ").filter { it.isNotBlank() }) {
            out = out.replace(Regex("(?i)\\b${Regex.escape(token)}\\b"), " ")
        }
        return out.replace(Regex("\\s+"), " ")
            .trim()
            .trim(',', '.', ':', ';', '-')
            .trim()
    }

    private fun calendar(now: Long, zone: TimeZone): Calendar =
        Calendar.getInstance(zone).apply { timeInMillis = now }

    private fun setTime(cal: Calendar, hour: Int, minute: Int) {
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }

    private fun advanceToWeekday(cal: Calendar, targetDow: Int, nextWeek: Boolean) {
        var guard = 0
        do {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            guard++
        } while (cal.get(Calendar.DAY_OF_WEEK) != targetDow && guard < 8)
        if (nextWeek) cal.add(Calendar.DAY_OF_YEAR, 7)
    }
}
