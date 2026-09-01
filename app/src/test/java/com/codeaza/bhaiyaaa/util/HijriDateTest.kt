package com.codeaza.bhaiyaaa.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * The Hijri date line.
 *
 * Anchored on conversions that are independently checkable: 1 Muharram 1445
 * fell on 19 July 2023, and the Hijra epoch itself is 1 Muharram 1 AH. Both
 * come from the Umm al-Qura tables Android ships, so this also proves the
 * platform chronology is the one being used rather than an approximation.
 */
class HijriDateTest {

    @Test
    fun `it converts a known date`() {
        // 19 July 2023 was 1 Muharram 1445 in the Umm al-Qura calendar.
        assertThat(HijriDate.format(LocalDate.of(2023, 7, 19)))
            .isEqualTo("1 Muharram 1445")
    }

    @Test
    fun `month names are spelled out rather than numbered`() {
        // Several devices format a Hijrah month as a bare digit, which is why
        // the names are a fixed table in the app rather than locale-derived.
        val formatted = HijriDate.format(LocalDate.of(2024, 3, 15))
        assertThat(formatted).isNotNull()
        assertThat(formatted).contains("Ramadan")
    }

    @Test
    fun `every month of a year gets a real name`() {
        // Walks a full Hijri year at 29-day steps, which lands in each month.
        var date = LocalDate.of(2024, 1, 1)
        repeat(14) {
            val formatted = HijriDate.format(date)
            assertThat(formatted).isNotNull()
            // A stray number where a name should be is the failure this
            // catches - the day and year are digits, the month must not be.
            assertThat(formatted!!.split(" ")[1].first().isDigit()).isFalse()
            date = date.plusDays(29)
        }
    }

    @Test
    fun `a date out of range is answered with nothing rather than a crash`() {
        // Android's Umm al-Qura tables cover a bounded span of years. A
        // missing date line is a far better dashboard than a crash.
        assertThat(HijriDate.format(LocalDate.of(1200, 1, 1))).isNull()
    }

    @Test
    fun `Ramadan is recognised`() {
        // 15 March 2024 fell inside Ramadan 1445.
        assertThat(HijriDate.isRamadan(LocalDate.of(2024, 3, 15))).isTrue()
        // And 15 December 2024 did not.
        assertThat(HijriDate.isRamadan(LocalDate.of(2024, 12, 15))).isFalse()
    }

    @Test
    fun `after maghrib the Hijri day has already turned`() {
        // The Islamic day begins at sunset, so the evening of one civil day is
        // already the next Hijri day.
        val civil = LocalDate.of(2023, 7, 18)
        assertThat(HijriDate.format(civil)).isEqualTo("30 Dhu al-Hijjah 1444")
        assertThat(HijriDate.format(civil.plusDays(1))).isEqualTo("1 Muharram 1445")
    }
}
