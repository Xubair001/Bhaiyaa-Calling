package com.codeaza.bhaiyaaa.prayer

import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Which prayer period the clock is in.
 *
 * A window is the few minutes the phone is silent; a period is the stretch a
 * prayer names, and it is what "show this during Asr" has to mean. Getting
 * this wrong would make prayer-related content appear for fifteen minutes a
 * day and then vanish.
 */
class PrayerPeriodsTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Karachi")

    private fun at(hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(2025, Calendar.AUGUST, 27, hour, minute, 0)
        }.timeInMillis

    private val anchors = mapOf(
        Prayer.FAJR to at(4, 30),
        Prayer.DHUHR to at(12, 30),
        Prayer.ASR to at(16, 15),
        Prayer.MAGHRIB to at(18, 50),
        Prayer.ISHA to at(20, 15)
    )

    @Test
    fun `the period runs from its prayer until the next one`() {
        assertThat(PrayerPeriods.current(anchors, at(13, 0))).isEqualTo(Prayer.DHUHR)
        assertThat(PrayerPeriods.current(anchors, at(16, 14))).isEqualTo(Prayer.DHUHR)
        assertThat(PrayerPeriods.current(anchors, at(16, 15))).isEqualTo(Prayer.ASR)
        assertThat(PrayerPeriods.current(anchors, at(18, 49))).isEqualTo(Prayer.ASR)
    }

    @Test
    fun `before the day's first prayer the night's Isha period is still running`() {
        // Resolving it this way means the caller never has to plan yesterday
        // to answer a question about this moment.
        assertThat(PrayerPeriods.current(anchors, at(2, 0))).isEqualTo(Prayer.ISHA)
        assertThat(PrayerPeriods.current(anchors, at(4, 29))).isEqualTo(Prayer.ISHA)
    }

    @Test
    fun `after the last prayer the Isha period continues`() {
        assertThat(PrayerPeriods.current(anchors, at(23, 59))).isEqualTo(Prayer.ISHA)
    }

    @Test
    fun `the moment a prayer arrives it owns the period`() {
        assertThat(PrayerPeriods.current(anchors, at(4, 30))).isEqualTo(Prayer.FAJR)
    }

    @Test
    fun `no configured times means no period rather than a guess`() {
        assertThat(PrayerPeriods.current(emptyMap(), at(13, 0))).isNull()
    }

    @Test
    fun `a partially configured day still answers`() {
        // Manual mode with only two times entered is a real state.
        val partial = mapOf(Prayer.FAJR to at(4, 30), Prayer.MAGHRIB to at(18, 50))
        assertThat(PrayerPeriods.current(partial, at(13, 0))).isEqualTo(Prayer.FAJR)
        assertThat(PrayerPeriods.current(partial, at(19, 0))).isEqualTo(Prayer.MAGHRIB)
        // Isha has no time, so before Fajr there is nothing to fall back to.
        assertThat(PrayerPeriods.current(partial, at(2, 0))).isNull()
    }

    @Test
    fun `a mistyped time cannot reorder the day`() {
        // Asr entered after Maghrib is a user error, not a new ordering. Both
        // have begun by eight o'clock, and the period belongs to whichever
        // comes later in the day - Maghrib - rather than to whichever happens
        // to hold the later clock time. Sorting by time would have let one
        // wrong entry rename the whole evening.
        val muddled = anchors + (Prayer.ASR to at(19, 30))
        assertThat(PrayerPeriods.current(muddled, at(20, 0))).isEqualTo(Prayer.MAGHRIB)
        // And before Maghrib, the mistyped Asr still has not begun, so the
        // period is the one before it.
        assertThat(PrayerPeriods.current(muddled, at(17, 0))).isEqualTo(Prayer.DHUHR)
    }

    @Test
    fun `the period end is the next prayer's time`() {
        assertThat(PrayerPeriods.currentPeriodEnd(anchors, at(13, 0))).isEqualTo(at(16, 15))
        // Nothing left today: the next one is tomorrow's and is not in here.
        assertThat(PrayerPeriods.currentPeriodEnd(anchors, at(23, 0))).isNull()
    }
}
