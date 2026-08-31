package com.codeaza.bhaiyaaa.domain

import com.codeaza.bhaiyaaa.domain.usecase.ReminderBucket
import com.codeaza.bhaiyaaa.domain.usecase.ReminderGrouping
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReminderGroupingTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Karachi")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private val now = at(2026, 3, 15, 10, 0)

    @Test
    fun `no due date is someday`() {
        assertThat(ReminderGrouping.bucketOf(null, now, zone)).isEqualTo(ReminderBucket.SOMEDAY)
    }

    @Test
    fun `earlier the same day is overdue, not today`() {
        // The failure people notice: something due at 09:00 when it is 10:00
        // filed under Today looks like the app forgot about it.
        val due = at(2026, 3, 15, 9, 0)
        assertThat(ReminderGrouping.bucketOf(due, now, zone)).isEqualTo(ReminderBucket.OVERDUE)
    }

    @Test
    fun `later the same day is today`() {
        val due = at(2026, 3, 15, 17, 30)
        assertThat(ReminderGrouping.bucketOf(due, now, zone)).isEqualTo(ReminderBucket.TODAY)
    }

    @Test
    fun `one minute before midnight is still today`() {
        val due = at(2026, 3, 15, 23, 59)
        assertThat(ReminderGrouping.bucketOf(due, now, zone)).isEqualTo(ReminderBucket.TODAY)
    }

    @Test
    fun `midnight belongs to tomorrow, not today`() {
        val due = at(2026, 3, 16, 0, 0)
        assertThat(ReminderGrouping.bucketOf(due, now, zone)).isEqualTo(ReminderBucket.TOMORROW)
    }

    @Test
    fun `the day after tomorrow is upcoming`() {
        val due = at(2026, 3, 17, 0, 0)
        assertThat(ReminderGrouping.bucketOf(due, now, zone)).isEqualTo(ReminderBucket.UPCOMING)
    }

    @Test
    fun `day boundaries hold across a spring-forward night`() {
        // London loses an hour at 01:00 on 29 March 2026. Adding 24-hour blocks
        // instead of calendar days puts the Today-Tomorrow boundary at 01:00,
        // so an 00:30 reminder would be filed a day early.
        val london = TimeZone.getTimeZone("Europe/London")
        fun londonAt(day: Int, hour: Int, minute: Int = 0) =
            Calendar.getInstance(london).apply {
                clear(); set(2026, Calendar.MARCH, day, hour, minute, 0)
            }.timeInMillis

        val evening = londonAt(28, 22, 0)
        assertThat(ReminderGrouping.bucketOf(londonAt(28, 23, 30), evening, london))
            .isEqualTo(ReminderBucket.TODAY)
        assertThat(ReminderGrouping.bucketOf(londonAt(29, 0, 30), evening, london))
            .isEqualTo(ReminderBucket.TOMORROW)
        assertThat(ReminderGrouping.bucketOf(londonAt(29, 9, 0), evening, london))
            .isEqualTo(ReminderBucket.TOMORROW)
        assertThat(ReminderGrouping.bucketOf(londonAt(30, 9, 0), evening, london))
            .isEqualTo(ReminderBucket.UPCOMING)
    }

    @Test
    fun `groups come back in bucket order with empties dropped`() {
        val items = listOf(
            at(2026, 3, 20, 9, 0),   // upcoming
            at(2026, 3, 15, 8, 0),   // overdue
            at(2026, 3, 15, 20, 0)   // today
        )
        val groups = ReminderGrouping.group(items, now, zone) { it }
        assertThat(groups.map { it.bucket })
            .containsExactly(ReminderBucket.OVERDUE, ReminderBucket.TODAY, ReminderBucket.UPCOMING)
            .inOrder()
    }

    @Test
    fun `order within a bucket is the order it was given`() {
        // The DAO already sorts by due date; re-sorting here would fight it.
        val a = at(2026, 3, 20, 9, 0)
        val b = at(2026, 3, 21, 9, 0)
        val groups = ReminderGrouping.group(listOf(b, a), now, zone) { it }
        assertThat(groups.single().items).containsExactly(b, a).inOrder()
    }
}
