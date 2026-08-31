package com.codeaza.bhaiyaaa.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

class FormattingTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private val now = at(2026, 3, 15, 10, 0)

    @Test
    fun `whenDue names tomorrow rather than calling it today`() {
        // relativeDateTime answers "Today" for anything at or after this
        // morning. That is right for a call log and wrong for a reminder, and
        // it made every future reminder read as due today.
        assertThat(Formatting.whenDue(at(2026, 3, 16, 17, 0), now)).startsWith("Tomorrow, ")
    }

    @Test
    fun `whenDue keeps today for later the same day`() {
        assertThat(Formatting.whenDue(at(2026, 3, 15, 17, 0), now)).startsWith("Today, ")
    }

    @Test
    fun `whenDue names the weekday inside the coming week`() {
        assertThat(Formatting.whenDue(at(2026, 3, 18, 9, 0), now)).startsWith("Wednesday, ")
    }

    @Test
    fun `whenDue falls back to a date further out`() {
        assertThat(Formatting.whenDue(at(2026, 4, 20, 9, 0), now)).contains("Apr 20")
    }

    @Test
    fun `whenDue still reads backwards for something overdue`() {
        assertThat(Formatting.whenDue(at(2026, 3, 14, 9, 0), now)).startsWith("Yesterday, ")
        assertThat(Formatting.whenDue(at(2026, 3, 15, 9, 0), now)).startsWith("Today, ")
    }

    @Test
    fun `relativeDateTime still reads backwards for the call log`() {
        assertThat(Formatting.relativeDateTime(at(2026, 3, 14, 16, 12), now))
            .startsWith("Yesterday, ")
    }

    @Test
    fun `duration is compact`() {
        assertThat(Formatting.duration(45)).isEqualTo("45s")
        assertThat(Formatting.duration(200)).isEqualTo("3m 20s")
        assertThat(Formatting.duration(3900)).isEqualTo("1h 05m")
        assertThat(Formatting.duration(0)).isEqualTo("—")
    }

    @Test
    fun `plural picks the right form`() {
        assertThat(Formatting.plural(1, "call")).isEqualTo("1 call")
        assertThat(Formatting.plural(3, "call")).isEqualTo("3 calls")
    }
}
