package com.codeaza.bhaiyaaa.util

import java.time.Instant
import java.time.ZoneId
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * The Hijri date, worked out on the device.
 *
 * `java.time.chrono.HijrahDate` has been in the platform since API 26, which is
 * this app's minimum, so this needs no library, no network and no data file.
 *
 * ## Two honest caveats, both surfaced in the UI
 *
 * **It is a calculation, not a sighting.** Android's Hijrah chronology is the
 * Umm al-Qura calendar, which is arithmetic. Where a country announces the
 * month by local moon sighting the date can differ by a day, and no offline
 * calculation can know that. The UI says "calculated" rather than presenting it
 * as authoritative.
 *
 * **The Islamic day begins at maghrib, not midnight.** After sunset the Hijri
 * date has already advanced while the civil date has not. Showing the plain
 * conversion is what every calendar app does and is what people expect from a
 * date line, so that is what [today] returns. A caller that genuinely knows
 * the sun has set can convert tomorrow's date itself with [format].
 */
object HijriDate {

    /**
     * Transliterated month names, fixed rather than taken from the locale.
     *
     * Android's own Hijrah month formatting varies by device and locale, and on
     * several it produces bare numbers. A fixed table means every user sees the
     * same recognisable name.
     */
    private val MONTHS = listOf(
        "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
        "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha'ban",
        "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
    )

    /** "12 Rabi' al-Awwal 1447", or null if the platform cannot convert. */
    fun today(zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): String? =
        format(Instant.ofEpochMilli(now).atZone(zone).toLocalDate())

    fun format(date: java.time.LocalDate): String? = try {
        val hijri = HijrahDate.from(date)
        val month = MONTHS.getOrNull(hijri.get(ChronoField.MONTH_OF_YEAR) - 1)
        if (month == null) null
        else String.format(
            Locale.getDefault(),
            "%d %s %d",
            hijri.get(ChronoField.DAY_OF_MONTH),
            month,
            hijri.get(ChronoField.YEAR)
        )
    } catch (e: Exception) {
        // The Umm al-Qura tables Android ships cover a bounded range of years,
        // and a date outside it throws. A missing date line is a far better
        // outcome than a crash on the dashboard.
        null
    }

    /** True during Ramadan, for anything that wants to behave differently then. */
    fun isRamadan(date: java.time.LocalDate = java.time.LocalDate.now()): Boolean = try {
        HijrahDate.from(date).get(ChronoField.MONTH_OF_YEAR) == RAMADAN_MONTH
    } catch (e: Exception) {
        false
    }

    private const val RAMADAN_MONTH = 9
}
