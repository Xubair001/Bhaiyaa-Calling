package com.codeaza.bhaiyaaa.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Reminder times are something a user relies on, so the parser is pinned to a
 * fixed "now" and an explicit zone - these must not drift with the wall clock
 * of whatever machine runs the suite.
 */
class TimeExpressionsTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Karachi")

    /** Wednesday 27 August 2025, 10:00 local. */
    private val now: Long = Calendar.getInstance(zone).apply {
        clear()
        set(2025, Calendar.AUGUST, 27, 10, 0, 0)
    }.timeInMillis

    private fun field(millis: Long, field: Int): Int =
        Calendar.getInstance(zone).apply { timeInMillis = millis }.get(field)

    @Test
    fun `no time expression yields no due date`() {
        val parsed = TimeExpressions.parse("call the bank", now, zone)
        // Better to save a reminder with no time than to invent one.
        assertThat(parsed.dueAt).isNull()
    }

    @Test
    fun `tomorrow defaults to the morning`() {
        val parsed = TimeExpressions.parse("call Ali tomorrow", now, zone)
        val due = requireNotNull(parsed.dueAt)
        assertThat(field(due, Calendar.DAY_OF_MONTH)).isEqualTo(28)
        assertThat(field(due, Calendar.HOUR_OF_DAY)).isEqualTo(9)
    }

    @Test
    fun `tomorrow at a stated time uses that time`() {
        val parsed = TimeExpressions.parse("call Ali tomorrow at 5pm", now, zone)
        val due = requireNotNull(parsed.dueAt)
        assertThat(field(due, Calendar.DAY_OF_MONTH)).isEqualTo(28)
        assertThat(field(due, Calendar.HOUR_OF_DAY)).isEqualTo(17)
    }

    @Test
    fun `relative offsets are added to now`() {
        val parsed = TimeExpressions.parse("in 30 minutes", now, zone)
        assertThat(parsed.dueAt).isEqualTo(now + 30 * 60_000L)
    }

    @Test
    fun `a bare time that has already passed rolls to tomorrow`() {
        // It is 10:00; "at 9am" must mean tomorrow, not four hours ago.
        val parsed = TimeExpressions.parse("at 9am", now, zone)
        val due = requireNotNull(parsed.dueAt)
        assertThat(due).isGreaterThan(now)
        assertThat(field(due, Calendar.DAY_OF_MONTH)).isEqualTo(28)
    }

    @Test
    fun `a bare time still ahead stays today`() {
        val parsed = TimeExpressions.parse("at 3pm", now, zone)
        val due = requireNotNull(parsed.dueAt)
        assertThat(field(due, Calendar.DAY_OF_MONTH)).isEqualTo(27)
        assertThat(field(due, Calendar.HOUR_OF_DAY)).isEqualTo(15)
    }

    @Test
    fun `tonight means this evening`() {
        val parsed = TimeExpressions.parse("call mum tonight", now, zone)
        val due = requireNotNull(parsed.dueAt)
        assertThat(field(due, Calendar.DAY_OF_MONTH)).isEqualTo(27)
        assertThat(field(due, Calendar.HOUR_OF_DAY)).isEqualTo(20)
    }

    @Test
    fun `a weekday name picks the next such day`() {
        // Wednesday the 27th -> Friday the 29th.
        val parsed = TimeExpressions.parse("friday", now, zone)
        val due = requireNotNull(parsed.dueAt)
        assertThat(field(due, Calendar.DAY_OF_WEEK)).isEqualTo(Calendar.FRIDAY)
        assertThat(field(due, Calendar.DAY_OF_MONTH)).isEqualTo(29)
    }

    @Test
    fun `next weekday skips a further week`() {
        val parsed = TimeExpressions.parse("next friday", now, zone)
        val due = requireNotNull(parsed.dueAt)
        assertThat(field(due, Calendar.DAY_OF_WEEK)).isEqualTo(Calendar.FRIDAY)
        assertThat(field(due, Calendar.DAY_OF_MONTH)).isEqualTo(5)
    }

    @Test
    fun `a parsed due date is never in the past`() {
        listOf("today", "at 1am", "tonight", "monday").forEach { phrase ->
            val due = TimeExpressions.parse(phrase, now, zone).dueAt
            if (due != null) {
                assertThat(due).isGreaterThan(now)
            }
        }
    }

    @Test
    fun `the time phrase is stripped out of the reminder body`() {
        val parsed = TimeExpressions.parse("call Ali tomorrow", now, zone)
        val body = TimeExpressions.stripTimePhrase("call Ali tomorrow", parsed.matchedText)
        assertThat(body).isEqualTo("call Ali")
    }

    @Test
    fun `stripping leaves the text alone when nothing matched`() {
        assertThat(TimeExpressions.stripTimePhrase("call the bank", null)).isEqualTo("call the bank")
    }

    @Test
    fun `an out of range clock time is ignored rather than wrapped`() {
        val parsed = TimeExpressions.parse("at 99:99", now, zone)
        assertThat(parsed.dueAt).isNull()
    }
}
