package com.codeaza.bhaiyaaa.prayer

import com.codeaza.bhaiyaaa.data.db.entity.PrayerEntity
import com.codeaza.bhaiyaaa.data.db.entity.SilenceScheduleEntity
import com.codeaza.bhaiyaaa.domain.model.PrayerSettings
import com.codeaza.bhaiyaaa.domain.model.PrayerSilenceMode
import com.codeaza.bhaiyaaa.domain.model.SilenceSource
import com.codeaza.bhaiyaaa.domain.model.SilenceWindow
import com.codeaza.bhaiyaaa.domain.model.Weekdays
import java.util.Calendar
import java.util.TimeZone

/**
 * Every quiet period for a day, from both sources, in one ordered list.
 *
 * Prayers and custom schedules arrive at their times differently but behave
 * identically once resolved, so they are merged here and nothing downstream -
 * the scheduler, the dashboard, the incoming-call check - needs to know which
 * kind it is looking at. Keeping that seam in one place is what stops the two
 * growing separate and subtly different behaviour.
 */
object SilencePlan {

    /**
     * @param dayStartMillis any instant within the local day being planned.
     * @param includePrayers false when the prayer feature is switched off but
     *   custom schedules should still run - they are independent of it.
     */
    fun windowsForDay(
        settings: PrayerSettings,
        prayers: List<PrayerEntity>,
        schedules: List<SilenceScheduleEntity>,
        dayStartMillis: Long,
        zone: TimeZone = settings.zone,
        includePrayers: Boolean = true
    ): List<SilenceWindow> {
        val prayerWindows =
            if (includePrayers && settings.isUsable) {
                PrayerTimeCalculator.windowsForDay(settings, prayers, dayStartMillis, zone)
                    .map { it.copy(mode = settings.silenceMode) }
            } else {
                emptyList()
            }

        val customWindows = schedules.mapNotNull { it.toWindow(dayStartMillis, zone) }

        return (prayerWindows + customWindows).sortedBy { it.startMillis }
    }

    /**
     * Resolves a schedule against one day, or null when it does not run that day.
     *
     * A schedule crossing midnight - "quiet 22:00 for 8 hours" - belongs to the
     * day it starts on, and simply runs past the boundary. Splitting it in two
     * would double-count it on the overlap.
     */
    private fun SilenceScheduleEntity.toWindow(
        dayStartMillis: Long,
        zone: TimeZone
    ): SilenceWindow? {
        val cal = Calendar.getInstance(zone).apply { timeInMillis = dayStartMillis }
        if (!Weekdays.includes(daysMask, cal.get(Calendar.DAY_OF_WEEK))) return null

        val start = PrayerTimeCalculator.localTimeToMillis(
            dayStartMillis,
            startMinutesFromMidnight.coerceIn(0, 1439),
            zone
        )
        return SilenceWindow(
            key = SilenceWindow.customKey(id),
            label = label.ifBlank { "Quiet time" },
            source = SilenceSource.CUSTOM,
            anchorMillis = start,
            startMillis = start,
            durationMinutes = durationMinutes.coerceIn(1, 720),
            enabled = enabled,
            mode = PrayerSilenceMode.from(silenceMode)
        )
    }

    fun activeWindow(windows: List<SilenceWindow>, now: Long): SilenceWindow? =
        windows.firstOrNull { it.containsNow(now) }

    fun nextWindow(windows: List<SilenceWindow>, now: Long): SilenceWindow? =
        windows.filter { it.enabled && it.startMillis > now }.minByOrNull { it.startMillis }
}
