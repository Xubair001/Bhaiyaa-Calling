package com.codeaza.bhaiyaaa.prayer

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.codeaza.bhaiyaaa.data.db.entity.PrayerEntity
import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.codeaza.bhaiyaaa.domain.model.PrayerMadhab
import com.codeaza.bhaiyaaa.domain.model.PrayerMethod
import com.codeaza.bhaiyaaa.domain.model.PrayerMode
import com.codeaza.bhaiyaaa.domain.model.PrayerSettings
import com.codeaza.bhaiyaaa.domain.model.PrayerWindow
import java.util.Calendar
import java.util.TimeZone

/**
 * Works out when each prayer falls and how long Sukoon should stay quiet.
 *
 * Prayer times are astronomy, not a web service: given coordinates and a date,
 * the sun's position is computable on the device, forever, with no network.
 * That is the only reason this feature belongs in an offline-first app at all.
 *
 * A calculation is still a model, and the masjid down the road is the actual
 * answer - the two routinely differ by a few minutes. So any prayer can carry a
 * manual override that wins over the calculation, and the UI marks which times
 * were overridden.
 *
 * Pure and side-effect free, taking its date and zone as parameters, so the
 * whole thing is testable without a device or a clock.
 */
object PrayerTimeCalculator {

    /**
     * @param dayStartMillis any instant within the local day being calculated.
     * @return one window per configured prayer, ordered, earliest first.
     *   A prayer with no time available at all is omitted rather than guessed at.
     */
    fun windowsForDay(
        settings: PrayerSettings,
        prayers: List<PrayerEntity>,
        dayStartMillis: Long,
        zone: TimeZone = TimeZone.getDefault()
    ): List<PrayerWindow> {
        if (!settings.isUsable) return emptyList()

        val calculated = calculatedTimes(settings, dayStartMillis, zone)

        return prayers
            .mapNotNull { entity ->
                val prayer = Prayer.from(entity.name)
                val override = entity.manualMinutesFromMidnight
                    ?.let { localTimeToMillis(dayStartMillis, it, zone) }

                // Manual mode uses only what the user typed. Automatic prefers
                // the override when there is one, and falls back to the sky.
                val start = when (settings.mode) {
                    PrayerMode.MANUAL -> override
                    PrayerMode.AUTOMATIC -> override ?: calculated[prayer]
                } ?: return@mapNotNull null

                // The offset shifts the window, it does not extend it: a
                // 15-minute window opening 3 minutes early runs from T-3 to
                // T+12, so "silent for 15 minutes" stays literally true.
                val offset = entity.startOffsetMinutes.coerceIn(-60, 60)
                PrayerWindow(
                    prayer = prayer,
                    prayerTimeMillis = start,
                    startMillis = start + offset * 60_000L,
                    silenceMinutes = entity.silenceMinutes.coerceIn(1, 180),
                    enabled = entity.enabled,
                    isOverridden = override != null
                )
            }
            .sortedBy { it.startMillis }
    }

    /** The window covering [now], if any. Used to decide whether to alert on a call. */
    fun activeWindow(windows: List<PrayerWindow>, now: Long): PrayerWindow? =
        windows.firstOrNull { it.containsNow(now) }

    /** The next window that starts after [now], for scheduling and for the UI. */
    fun nextWindow(windows: List<PrayerWindow>, now: Long): PrayerWindow? =
        windows.filter { it.enabled && it.startMillis > now }.minByOrNull { it.startMillis }

    private fun calculatedTimes(
        settings: PrayerSettings,
        dayStartMillis: Long,
        zone: TimeZone
    ): Map<Prayer, Long> {
        val latitude = settings.latitude
        val longitude = settings.longitude
        if (settings.mode != PrayerMode.AUTOMATIC || latitude == null || longitude == null) {
            return emptyMap()
        }

        return try {
            val cal = Calendar.getInstance(zone).apply { timeInMillis = dayStartMillis }
            val components = DateComponents(
                cal.get(Calendar.YEAR),
                // Calendar months are zero-based; Adhan's are not.
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            val parameters = toAdhanMethod(settings.method).parameters.apply {
                madhab = toAdhanMadhab(settings.madhab)
            }
            val times = PrayerTimes(Coordinates(latitude, longitude), components, parameters)
            mapOf(
                Prayer.FAJR to toMinute(times.fajr.time),
                Prayer.DHUHR to toMinute(times.dhuhr.time),
                Prayer.ASR to toMinute(times.asr.time),
                Prayer.MAGHRIB to toMinute(times.maghrib.time),
                Prayer.ISHA to toMinute(times.isha.time)
            )
        } catch (e: Exception) {
            // Absurd coordinates, or a polar latitude where a prayer has no
            // solution on this date. Better to show nothing than a wrong time.
            emptyMap()
        }
    }

    /**
     * Truncates to the start of the minute.
     *
     * Adhan rounds its results to the minute but builds the Date from a Calendar
     * seeded with the current clock, so the millisecond component is whatever
     * the wall clock happened to hold - identical inputs produce instants that
     * differ by a few milliseconds. A prayer time is a wall-clock minute, so it
     * is normalised here. This also makes the whole calculation deterministic,
     * which is what lets it be tested by equality at all.
     */
    internal fun toMinute(millis: Long): Long = millis - Math.floorMod(millis, 60_000L)

    /** Converts "minutes past local midnight" into an absolute instant on that day. */
    internal fun localTimeToMillis(dayStartMillis: Long, minutesFromMidnight: Int, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = dayStartMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // add() rather than set(HOUR_OF_DAY): on a daylight-saving day the
            // hour the clock skips does not exist, and set() would silently
            // land on the wrong instant.
            add(Calendar.MINUTE, minutesFromMidnight)
        }.timeInMillis

    private fun toAdhanMethod(method: PrayerMethod): CalculationMethod = when (method) {
        PrayerMethod.KARACHI -> CalculationMethod.KARACHI
        PrayerMethod.MUSLIM_WORLD_LEAGUE -> CalculationMethod.MUSLIM_WORLD_LEAGUE
        PrayerMethod.UMM_AL_QURA -> CalculationMethod.UMM_AL_QURA
        PrayerMethod.EGYPTIAN -> CalculationMethod.EGYPTIAN
        PrayerMethod.NORTH_AMERICA -> CalculationMethod.NORTH_AMERICA
        PrayerMethod.DUBAI -> CalculationMethod.DUBAI
        PrayerMethod.QATAR -> CalculationMethod.QATAR
        PrayerMethod.KUWAIT -> CalculationMethod.KUWAIT
        PrayerMethod.SINGAPORE -> CalculationMethod.SINGAPORE
        PrayerMethod.MOON_SIGHTING_COMMITTEE -> CalculationMethod.MOON_SIGHTING_COMMITTEE
    }

    private fun toAdhanMadhab(madhab: PrayerMadhab): Madhab = when (madhab) {
        PrayerMadhab.HANAFI -> Madhab.HANAFI
        PrayerMadhab.SHAFI -> Madhab.SHAFI
    }

    /**
     * Default rows, seeded once: quiet from three minutes before the prayer,
     * for fifteen minutes in total. Enough for wudu, jamaat and sunnah without
     * holding the phone silent long after everyone has left.
     */
    fun defaultPrayerRows(): List<PrayerEntity> = Prayer.entries.map { prayer ->
        PrayerEntity(
            name = prayer.storageValue,
            enabled = true,
            silenceMinutes = 15,
            manualMinutesFromMidnight = null,
            startOffsetMinutes = -3,
            sortOrder = prayer.order
        )
    }
}
