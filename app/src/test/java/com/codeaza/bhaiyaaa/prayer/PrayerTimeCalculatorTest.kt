package com.codeaza.bhaiyaaa.prayer

import com.codeaza.bhaiyaaa.data.db.entity.PrayerEntity
import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.codeaza.bhaiyaaa.domain.model.PrayerMadhab
import com.codeaza.bhaiyaaa.domain.model.PrayerMethod
import com.codeaza.bhaiyaaa.domain.model.PrayerMode
import com.codeaza.bhaiyaaa.domain.model.PrayerSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Pinned to Lahore on a fixed date in a fixed zone, so these assert real
 * behaviour rather than drifting with the machine's clock or locale.
 *
 * Exact minute values are deliberately not asserted - those belong to the Adhan
 * library, and pinning them here would just re-test someone else's arithmetic.
 * What is asserted is everything Sukoon is responsible for: ordering, the
 * override rules, the silence window, and never inventing a time it doesn't have.
 */
class PrayerTimeCalculatorTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Karachi")

    /** Wednesday 27 August 2025, 08:00 local. */
    private val day: Long = Calendar.getInstance(zone).apply {
        clear()
        set(2025, Calendar.AUGUST, 27, 8, 0, 0)
    }.timeInMillis

    private val lahore = PrayerSettings(
        enabled = true,
        mode = PrayerMode.AUTOMATIC,
        method = PrayerMethod.KARACHI,
        madhab = PrayerMadhab.HANAFI,
        latitude = 31.5204,
        longitude = 74.3587,
        locationLabel = "Lahore"
    )

    private fun rows(
        silence: Int = 15,
        enabled: Boolean = true,
        offset: Int = -3,
        overrides: Map<Prayer, Int?> = emptyMap()
    ) = Prayer.entries.map { p ->
        PrayerEntity(
            name = p.storageValue,
            enabled = enabled,
            silenceMinutes = silence,
            manualMinutesFromMidnight = overrides[p],
            startOffsetMinutes = offset,
            sortOrder = p.order
        )
    }

    private fun localHour(millis: Long): Int =
        Calendar.getInstance(zone).apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)

    @Test
    fun `automatic mode produces all five prayers in order`() {
        val windows = PrayerTimeCalculator.windowsForDay(lahore, rows(), day, zone)

        assertThat(windows).hasSize(5)
        assertThat(windows.map { it.prayer })
            .containsExactly(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
            .inOrder()
        assertThat(windows.map { it.startMillis }).isInOrder()
    }

    @Test
    fun `calculated times land in plausible parts of the day`() {
        val windows = PrayerTimeCalculator.windowsForDay(lahore, rows(), day, zone)
            .associateBy { it.prayer }

        // Not exact values - just that the arithmetic and time zone agree.
        assertThat(localHour(windows.getValue(Prayer.FAJR).prayerTimeMillis)).isIn(2..6)
        assertThat(localHour(windows.getValue(Prayer.DHUHR).prayerTimeMillis)).isIn(11..14)
        assertThat(localHour(windows.getValue(Prayer.ASR).prayerTimeMillis)).isIn(14..18)
        assertThat(localHour(windows.getValue(Prayer.MAGHRIB).prayerTimeMillis)).isIn(17..20)
        assertThat(localHour(windows.getValue(Prayer.ISHA).prayerTimeMillis)).isIn(18..23)
    }

    @Test
    fun `madhab changes Asr and nothing else`() {
        val hanafi = PrayerTimeCalculator.windowsForDay(lahore, rows(), day, zone)
            .associateBy { it.prayer }
        val shafi = PrayerTimeCalculator
            .windowsForDay(lahore.copy(madhab = PrayerMadhab.SHAFI), rows(), day, zone)
            .associateBy { it.prayer }

        // Hanafi uses a longer shadow, so Asr is later.
        assertThat(hanafi.getValue(Prayer.ASR).prayerTimeMillis)
            .isGreaterThan(shafi.getValue(Prayer.ASR).prayerTimeMillis)
        assertThat(hanafi.getValue(Prayer.FAJR).prayerTimeMillis)
            .isEqualTo(shafi.getValue(Prayer.FAJR).prayerTimeMillis)
        assertThat(hanafi.getValue(Prayer.MAGHRIB).prayerTimeMillis)
            .isEqualTo(shafi.getValue(Prayer.MAGHRIB).prayerTimeMillis)
    }

    @Test
    fun `a manual override beats the calculation for that prayer only`() {
        val calculated = PrayerTimeCalculator.windowsForDay(lahore, rows(), day, zone)
            .associateBy { it.prayer }

        // Dhuhr forced to 12:30 local.
        val overridden = PrayerTimeCalculator.windowsForDay(
            lahore, rows(overrides = mapOf(Prayer.DHUHR to 12 * 60 + 30)), day, zone
        ).associateBy { it.prayer }

        val dhuhr = overridden.getValue(Prayer.DHUHR)
        val cal = Calendar.getInstance(zone).apply { timeInMillis = dhuhr.prayerTimeMillis }
        assertThat(cal.get(Calendar.HOUR_OF_DAY)).isEqualTo(12)
        assertThat(cal.get(Calendar.MINUTE)).isEqualTo(30)
        assertThat(dhuhr.isOverridden).isTrue()

        // Everything else is untouched and still marked as calculated.
        assertThat(overridden.getValue(Prayer.FAJR).prayerTimeMillis)
            .isEqualTo(calculated.getValue(Prayer.FAJR).prayerTimeMillis)
        assertThat(overridden.getValue(Prayer.FAJR).isOverridden).isFalse()
    }

    @Test
    fun `manual mode uses only the times the user entered`() {
        val settings = lahore.copy(mode = PrayerMode.MANUAL)
        val windows = PrayerTimeCalculator.windowsForDay(
            settings,
            rows(overrides = mapOf(Prayer.FAJR to 4 * 60 + 30, Prayer.ISHA to 20 * 60)),
            day, zone
        )

        // A prayer with no entered time is omitted, never guessed at.
        assertThat(windows.map { it.prayer }).containsExactly(Prayer.FAJR, Prayer.ISHA).inOrder()
        assertThat(windows.all { it.isOverridden }).isTrue()
    }

    @Test
    fun `automatic mode without a location yields nothing`() {
        val noLocation = lahore.copy(latitude = null, longitude = null)
        assertThat(PrayerTimeCalculator.windowsForDay(noLocation, rows(), day, zone)).isEmpty()
    }

    @Test
    fun `the feature being off yields nothing`() {
        assertThat(PrayerTimeCalculator.windowsForDay(lahore.copy(enabled = false), rows(), day, zone))
            .isEmpty()
    }

    @Test
    fun `the silence window runs for the configured minutes`() {
        val windows = PrayerTimeCalculator.windowsForDay(lahore, rows(silence = 25), day, zone)
        windows.forEach {
            assertThat(it.endMillis - it.startMillis).isEqualTo(25 * 60_000L)
        }
    }

    @Test
    fun `an absurd silence duration is clamped rather than trusted`() {
        val windows = PrayerTimeCalculator.windowsForDay(lahore, rows(silence = 100_000), day, zone)
        // Three hours is the ceiling; a corrupt value must not silence the phone all day.
        assertThat(windows.first().silenceMinutes).isEqualTo(180)
    }

    @Test
    fun `activeWindow finds the window containing now and no other`() {
        val windows = PrayerTimeCalculator.windowsForDay(lahore, rows(silence = 20), day, zone)
        val dhuhr = windows.first { it.prayer == Prayer.DHUHR }

        assertThat(PrayerTimeCalculator.activeWindow(windows, dhuhr.startMillis)).isEqualTo(dhuhr)
        assertThat(PrayerTimeCalculator.activeWindow(windows, dhuhr.startMillis + 60_000)).isEqualTo(dhuhr)
        // The end is exclusive - the phone must come back at the moment it said.
        assertThat(PrayerTimeCalculator.activeWindow(windows, dhuhr.endMillis)).isNull()
        assertThat(PrayerTimeCalculator.activeWindow(windows, dhuhr.startMillis - 1)).isNull()
    }

    @Test
    fun `a disabled prayer never becomes active`() {
        val windows = PrayerTimeCalculator.windowsForDay(lahore, rows(enabled = false), day, zone)
        windows.forEach {
            assertThat(PrayerTimeCalculator.activeWindow(windows, it.startMillis + 1)).isNull()
        }
    }

    @Test
    fun `nextWindow returns the soonest upcoming prayer`() {
        val windows = PrayerTimeCalculator.windowsForDay(lahore, rows(), day, zone)
        val fajr = windows.first { it.prayer == Prayer.FAJR }

        val next = PrayerTimeCalculator.nextWindow(windows, fajr.startMillis - 1)
        assertThat(next).isEqualTo(fajr)

        // After the last prayer of the day there is nothing left today.
        assertThat(PrayerTimeCalculator.nextWindow(windows, windows.last().startMillis + 1)).isNull()
    }

    @Test
    fun `local time conversion lands on the intended wall clock`() {
        val millis = PrayerTimeCalculator.localTimeToMillis(day, 5 * 60 + 45, zone)
        val cal = Calendar.getInstance(zone).apply { timeInMillis = millis }
        assertThat(cal.get(Calendar.HOUR_OF_DAY)).isEqualTo(5)
        assertThat(cal.get(Calendar.MINUTE)).isEqualTo(45)
        assertThat(cal.get(Calendar.DAY_OF_MONTH)).isEqualTo(27)
    }

    @Test
    fun `the window opens before the prayer by the configured offset`() {
        val windows = PrayerTimeCalculator.windowsForDay(lahore, rows(offset = -3), day, zone)
        windows.forEach {
            assertThat(it.startMillis).isEqualTo(it.prayerTimeMillis - 3 * 60_000L)
        }
    }

    @Test
    fun `the offset shifts the window without lengthening it`() {
        val windows = PrayerTimeCalculator.windowsForDay(
            lahore, rows(silence = 15, offset = -3), day, zone
        )
        windows.forEach {
            // Fifteen minutes total, from three before to twelve after.
            assertThat(it.endMillis - it.startMillis).isEqualTo(15 * 60_000L)
            assertThat(it.endMillis).isEqualTo(it.prayerTimeMillis + 12 * 60_000L)
        }
    }

    @Test
    fun `a zero offset starts the window exactly at the prayer`() {
        val windows = PrayerTimeCalculator.windowsForDay(lahore, rows(offset = 0), day, zone)
        windows.forEach { assertThat(it.startMillis).isEqualTo(it.prayerTimeMillis) }
    }

    @Test
    fun `an absurd offset is clamped`() {
        val windows = PrayerTimeCalculator.windowsForDay(lahore, rows(offset = -9999), day, zone)
        windows.forEach {
            assertThat(it.startMillis).isEqualTo(it.prayerTimeMillis - 60 * 60_000L)
        }
    }

    @Test
    fun `defaults are fifteen minutes starting three minutes early`() {
        val defaults = PrayerTimeCalculator.defaultPrayerRows()
        assertThat(defaults.all { it.silenceMinutes == 15 }).isTrue()
        assertThat(defaults.all { it.startOffsetMinutes == -3 }).isTrue()
    }

    @Test
    fun `default rows cover all five prayers exactly once`() {
        val defaults = PrayerTimeCalculator.defaultPrayerRows()
        assertThat(defaults).hasSize(5)
        assertThat(defaults.map { it.name }).containsNoDuplicates()
        assertThat(defaults.all { it.enabled }).isTrue()
        assertThat(defaults.all { it.manualMinutesFromMidnight == null }).isTrue()
    }
}
