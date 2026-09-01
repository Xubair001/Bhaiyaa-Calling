package com.codeaza.bhaiyaaa.prayer

import com.codeaza.bhaiyaaa.domain.model.Meridiem
import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The AM/PM rule, at the level it is actually defined.
 *
 * A prayer stored in the wrong half of the clock is not a slightly wrong
 * setting - the phone silences twelve hours from when it was meant, which
 * looks exactly like the feature not working at all. Every layer that can
 * write a time normalises through [Prayer.normaliseTime], so this is the test
 * that the rule itself is right.
 */
class PrayerClockRulesTest {

    @Test
    fun `Fajr is the only morning prayer`() {
        assertThat(Prayer.FAJR.meridiem).isEqualTo(Meridiem.AM)
        listOf(Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA).forEach {
            assertThat(it.meridiem).isEqualTo(Meridiem.PM)
        }
    }

    @Test
    fun `Fajr accepts only times before noon`() {
        assertThat(Prayer.FAJR.isValidTime(0)).isTrue()          // 12:00 AM
        assertThat(Prayer.FAJR.isValidTime(5 * 60 + 30)).isTrue() // 5:30 AM
        assertThat(Prayer.FAJR.isValidTime(11 * 60 + 59)).isTrue()
        assertThat(Prayer.FAJR.isValidTime(12 * 60)).isFalse()    // 12:00 PM
        assertThat(Prayer.FAJR.isValidTime(17 * 60)).isFalse()    // 5:00 PM
    }

    @Test
    fun `every prayer after Fajr accepts only times from noon`() {
        listOf(Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA).forEach { prayer ->
            assertThat(prayer.isValidTime(0)).isFalse()
            assertThat(prayer.isValidTime(11 * 60 + 59)).isFalse()
            assertThat(prayer.isValidTime(12 * 60)).isTrue()
            assertThat(prayer.isValidTime(23 * 60 + 59)).isTrue()
        }
    }

    @Test
    fun `normalising flips the meridiem rather than clamping`() {
        // 5:00 PM offered for Fajr was 5:00 AM meant. Clamping to 11:59 AM
        // would replace it with a time nobody typed.
        assertThat(Prayer.FAJR.normaliseTime(17 * 60)).isEqualTo(5 * 60)
        // 4:15 AM offered for Asr was 4:15 PM meant.
        assertThat(Prayer.ASR.normaliseTime(4 * 60 + 15)).isEqualTo(16 * 60 + 15)
    }

    @Test
    fun `normalising leaves a valid time exactly as it is`() {
        assertThat(Prayer.FAJR.normaliseTime(5 * 60 + 12)).isEqualTo(5 * 60 + 12)
        assertThat(Prayer.ISHA.normaliseTime(20 * 60 + 45)).isEqualTo(20 * 60 + 45)
        // Midnight is minute zero and a legitimate Fajr, not an unset value.
        assertThat(Prayer.FAJR.normaliseTime(0)).isEqualTo(0)
    }

    @Test
    fun `normalising is idempotent`() {
        Prayer.entries.forEach { prayer ->
            (0 until 24 * 60 step 7).forEach { minutes ->
                val once = prayer.normaliseTime(minutes)
                assertThat(prayer.normaliseTime(once)).isEqualTo(once)
                assertThat(prayer.isValidTime(once)).isTrue()
            }
        }
    }

    @Test
    fun `normalising handles values outside a day without landing outside one`() {
        Prayer.entries.forEach { prayer ->
            listOf(-1, -600, 1440, 5000).forEach { absurd ->
                val result = prayer.normaliseTime(absurd)
                assertThat(result).isIn(0..1439)
                assertThat(prayer.isValidTime(result)).isTrue()
            }
        }
    }

    @Test
    fun `the picker's default already sits in the prayer's own half of the clock`() {
        // "The default time and period should automatically follow the
        // prayer's valid AM/PM range" - proven rather than asserted in a
        // comment, because a later edit to one number could break it silently.
        Prayer.entries.forEach { prayer ->
            assertThat(prayer.isValidTime(prayer.defaultClockMinutes)).isTrue()
        }
    }

    @Test
    fun `picker defaults are real times in the right order`() {
        val defaults = Prayer.entries.map { it.defaultClockMinutes }
        assertThat(defaults.all { it in 0..1439 }).isTrue()
        assertThat(defaults).isInOrder()
    }
}
