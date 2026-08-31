package com.codeaza.bhaiyaaa.prayer

import com.codeaza.bhaiyaaa.data.db.entity.PrayerEntity
import com.codeaza.bhaiyaaa.data.db.entity.SilenceScheduleEntity
import com.codeaza.bhaiyaaa.domain.model.PrayerMadhab
import com.codeaza.bhaiyaaa.domain.model.PrayerMethod
import com.codeaza.bhaiyaaa.domain.model.PrayerMode
import com.codeaza.bhaiyaaa.domain.model.PrayerSettings
import com.codeaza.bhaiyaaa.domain.model.PrayerSilenceMode
import com.codeaza.bhaiyaaa.domain.model.SilenceSource
import com.codeaza.bhaiyaaa.domain.model.Weekdays
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Custom quiet periods, and how they merge with prayer windows.
 *
 * Custom schedules must work whether or not the prayer feature is on - they are
 * independent of it, and tying the two together would mean switching prayers
 * off silently disabled a meeting reminder that had nothing to do with them.
 */
class SilencePlanTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Karachi")

    /** Wednesday 27 August 2025, 08:00 local. */
    private val wednesday: Long = Calendar.getInstance(zone).apply {
        clear(); set(2025, Calendar.AUGUST, 27, 8, 0, 0)
    }.timeInMillis

    /** Sunday 31 August 2025. */
    private val sunday: Long = Calendar.getInstance(zone).apply {
        clear(); set(2025, Calendar.AUGUST, 31, 8, 0, 0)
    }.timeInMillis

    private val lahore = PrayerSettings(
        enabled = true, mode = PrayerMode.AUTOMATIC, method = PrayerMethod.KARACHI,
        madhab = PrayerMadhab.HANAFI, latitude = 31.5204, longitude = 74.3587,
        locationLabel = "Lahore", silenceMode = PrayerSilenceMode.SILENT
    )

    private fun prayers() = PrayerTimeCalculator.defaultPrayerRows()

    private fun schedule(
        id: Long = 1,
        label: String = "Standup",
        startMinutes: Int = 9 * 60 + 30,
        duration: Int = 30,
        days: Int = Weekdays.EVERY_DAY,
        enabled: Boolean = true,
        mode: PrayerSilenceMode = PrayerSilenceMode.VIBRATE
    ) = SilenceScheduleEntity(
        id = id, label = label, startMinutesFromMidnight = startMinutes,
        durationMinutes = duration, daysMask = days, enabled = enabled,
        silenceMode = mode.storageValue, createdAt = 0
    )

    @Test
    fun `a custom schedule produces a window on a matching day`() {
        val windows = SilencePlan.windowsForDay(
            lahore, prayers(), listOf(schedule()), wednesday, zone
        )
        val custom = windows.filter { it.source == SilenceSource.CUSTOM }
        assertThat(custom).hasSize(1)
        assertThat(custom.first().label).isEqualTo("Standup")
        assertThat(custom.first().durationMinutes).isEqualTo(30)
    }

    @Test
    fun `a schedule does not run on a day outside its mask`() {
        val weekdaysOnly = schedule(days = Weekdays.WEEKDAYS)
        assertThat(
            SilencePlan.windowsForDay(lahore, prayers(), listOf(weekdaysOnly), wednesday, zone)
                .filter { it.source == SilenceSource.CUSTOM }
        ).isNotEmpty()
        // Sunday is not a weekday.
        assertThat(
            SilencePlan.windowsForDay(lahore, prayers(), listOf(weekdaysOnly), sunday, zone)
                .filter { it.source == SilenceSource.CUSTOM }
        ).isEmpty()
    }

    @Test
    fun `custom schedules run even when prayer silence is switched off`() {
        val prayerOff = lahore.copy(enabled = false)
        val windows = SilencePlan.windowsForDay(
            prayerOff, prayers(), listOf(schedule()), wednesday, zone
        )
        // Switching prayers off must not disable an unrelated meeting reminder.
        assertThat(windows.map { it.source }).containsExactly(SilenceSource.CUSTOM)
    }

    @Test
    fun `prayers and custom periods merge in time order`() {
        val early = schedule(id = 1, label = "Early", startMinutes = 1)
        val late = schedule(id = 2, label = "Late", startMinutes = 23 * 60 + 30)
        val windows = SilencePlan.windowsForDay(
            lahore, prayers(), listOf(late, early), wednesday, zone
        )
        assertThat(windows.map { it.startMillis }).isInOrder()
        assertThat(windows.first().label).isEqualTo("Early")
        assertThat(windows.last().label).isEqualTo("Late")
        assertThat(windows.map { it.source }).contains(SilenceSource.PRAYER)
    }

    @Test
    fun `each schedule keeps its own silence mode`() {
        val vibrate = schedule(id = 1, label = "Meeting", mode = PrayerSilenceMode.VIBRATE)
        val windows = SilencePlan.windowsForDay(
            lahore, prayers(), listOf(vibrate), wednesday, zone
        )
        // A meeting can buzz while prayers stay silent.
        assertThat(windows.first { it.label == "Meeting" }.mode)
            .isEqualTo(PrayerSilenceMode.VIBRATE)
        assertThat(windows.first { it.source == SilenceSource.PRAYER }.mode)
            .isEqualTo(PrayerSilenceMode.SILENT)
    }

    @Test
    fun `a disabled schedule is present but never active`() {
        val off = schedule(enabled = false)
        val windows = SilencePlan.windowsForDay(lahore, prayers(), listOf(off), wednesday, zone)
        val custom = windows.first { it.source == SilenceSource.CUSTOM }
        assertThat(custom.enabled).isFalse()
        assertThat(SilencePlan.activeWindow(windows, custom.startMillis + 60_000)).isNull()
    }

    @Test
    fun `a window past midnight keeps its full length`() {
        val overnight = schedule(label = "Sleep", startMinutes = 23 * 60, duration = 120)
        val window = SilencePlan
            .windowsForDay(lahore, prayers(), listOf(overnight), wednesday, zone)
            .first { it.label == "Sleep" }
        // Belongs to the day it starts on and simply runs past the boundary;
        // splitting it would double-count the overlap.
        assertThat(window.endMillis - window.startMillis).isEqualTo(120 * 60_000L)
        assertThat(window.containsNow(window.startMillis + 90 * 60_000L)).isTrue()
    }

    @Test
    fun `an absurd duration is clamped rather than trusted`() {
        val silly = schedule(duration = 100_000)
        val window = SilencePlan
            .windowsForDay(lahore, prayers(), listOf(silly), wednesday, zone)
            .first { it.source == SilenceSource.CUSTOM }
        assertThat(window.durationMinutes).isEqualTo(720)
    }

    @Test
    fun `weekday masks describe themselves in the shortest true way`() {
        assertThat(Weekdays.describe(Weekdays.EVERY_DAY)).isEqualTo("Every day")
        assertThat(Weekdays.describe(Weekdays.WEEKDAYS)).isEqualTo("Weekdays")
        assertThat(Weekdays.describe(Weekdays.WEEKENDS)).isEqualTo("Weekends")
        assertThat(Weekdays.describe(0)).isEqualTo("Never")
        assertThat(Weekdays.describe(0b0000010)).isEqualTo("Mon")
    }

    @Test
    fun `toggling a day flips only that day`() {
        val mask = Weekdays.toggle(Weekdays.EVERY_DAY, 0)
        assertThat(Weekdays.isSet(mask, 0)).isFalse()
        (1..6).forEach { assertThat(Weekdays.isSet(mask, it)).isTrue() }
    }
}
