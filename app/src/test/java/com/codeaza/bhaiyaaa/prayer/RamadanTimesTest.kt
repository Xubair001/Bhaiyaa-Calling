package com.codeaza.bhaiyaaa.prayer

import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * A day of fasting, read off the prayer times.
 *
 * The property that matters most here is the one that is easiest to lose: the
 * fast's two ends are Fajr and Maghrib *as the app already computed them*, not
 * a second calculation that happens to agree today.
 */
class RamadanTimesTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Karachi")

    private fun at(hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(2025, Calendar.MARCH, 15, hour, minute, 0)
        }.timeInMillis

    private val anchors = mapOf(
        Prayer.FAJR to at(5, 0),
        Prayer.DHUHR to at(12, 20),
        Prayer.ASR to at(15, 45),
        Prayer.MAGHRIB to at(18, 25),
        Prayer.ISHA to at(19, 45)
    )

    @Test
    fun `the fast runs from Fajr to Maghrib`() {
        val day = requireNotNull(RamadanTimes.forDay(anchors))

        // Named from the prayer times, never recomputed.
        assertThat(day.suhoorEndsAt).isEqualTo(anchors[Prayer.FAJR])
        assertThat(day.iftarAt).isEqualTo(anchors[Prayer.MAGHRIB])
    }

    @Test
    fun `before Fajr there is still time to eat`() {
        val day = requireNotNull(RamadanTimes.forDay(anchors))

        val state = RamadanTimes.stateAt(day, at(4, 30))

        assertThat(state).isInstanceOf(RamadanState.BeforeSuhoorEnds::class.java)
        assertThat((state as RamadanState.BeforeSuhoorEnds).millisRemaining)
            .isEqualTo(TimeUnit.MINUTES.toMillis(30))
    }

    @Test
    fun `the fast begins the moment Fajr arrives`() {
        val day = requireNotNull(RamadanTimes.forDay(anchors))

        assertThat(RamadanTimes.stateAt(day, at(5, 0)))
            .isInstanceOf(RamadanState.Fasting::class.java)
    }

    @Test
    fun `during the day it counts down to iftar`() {
        val day = requireNotNull(RamadanTimes.forDay(anchors))

        val state = RamadanTimes.stateAt(day, at(17, 25))

        assertThat((state as RamadanState.Fasting).millisUntilIftar)
            .isEqualTo(TimeUnit.HOURS.toMillis(1))
    }

    @Test
    fun `the fast is complete once Maghrib arrives`() {
        val day = requireNotNull(RamadanTimes.forDay(anchors))

        assertThat(RamadanTimes.stateAt(day, at(18, 25))).isEqualTo(RamadanState.Complete)
        assertThat(RamadanTimes.stateAt(day, at(22, 0))).isEqualTo(RamadanState.Complete)
    }

    @Test
    fun `a day missing either end describes no fast`() {
        // Manual mode with only some times entered is a real state, and half a
        // fast is not worth showing.
        assertThat(RamadanTimes.forDay(anchors - Prayer.FAJR)).isNull()
        assertThat(RamadanTimes.forDay(anchors - Prayer.MAGHRIB)).isNull()
        assertThat(RamadanTimes.forDay(emptyMap())).isNull()
    }

    @Test
    fun `contradictory times describe no fast rather than a negative one`() {
        val muddled = anchors + (Prayer.MAGHRIB to at(4, 0))

        assertThat(RamadanTimes.forDay(muddled)).isNull()
    }
}
