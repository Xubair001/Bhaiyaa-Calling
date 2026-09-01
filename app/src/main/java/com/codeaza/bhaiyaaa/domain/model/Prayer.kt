package com.codeaza.bhaiyaaa.domain.model

/**
 * Which half of the clock a time falls in.
 *
 * Modelled rather than left as a boolean because it is the thing the UI, the
 * validation and the storage layer all have to agree about, and "isAm = false"
 * reads as an assertion about nothing in particular at the call site.
 */
enum class Meridiem(val label: String) {
    AM("AM"),
    PM("PM");

    /** Minutes past midnight that belong to this half of the clock. */
    val minuteRange: IntRange
        get() = when (this) {
            AM -> 0..(MINUTES_PER_HALF_DAY - 1)
            PM -> MINUTES_PER_HALF_DAY..(MINUTES_PER_DAY - 1)
        }

    companion object {
        const val MINUTES_PER_HALF_DAY = 12 * 60
        const val MINUTES_PER_DAY = 24 * 60

        fun of(minutesFromMidnight: Int): Meridiem =
            if (minutesFromMidnight < MINUTES_PER_HALF_DAY) AM else PM
    }
}

/**
 * The five daily prayers, in order.
 *
 * [meridiem] is not decoration: each prayer only ever falls in one half of the
 * clock, and treating that as data rather than as a rule written out again in
 * the picker, the view model and the DAO is what keeps the three from drifting.
 * Fajr is before dawn and therefore always AM; every prayer after it is after
 * noon and therefore always PM. A Dhuhr stored at 12:30 *AM* is not a slightly
 * wrong setting - it is a prayer silenced twelve hours from when it was meant,
 * which looks exactly like the feature not working at all.
 *
 * [defaultClockMinutes] is where the time picker opens when no time has been
 * set yet. It is only a starting position, never a stored value, and it is
 * required to sit inside [validMinuteRange] so the picker can never open on a
 * time the prayer is not allowed to hold.
 */
enum class Prayer(
    val storageValue: String,
    val label: String,
    val order: Int,
    val meridiem: Meridiem,
    val defaultClockMinutes: Int
) {
    FAJR("FAJR", "Fajr", 0, Meridiem.AM, 5 * 60),
    DHUHR("DHUHR", "Dhuhr", 1, Meridiem.PM, 12 * 60 + 30),
    ASR("ASR", "Asr", 2, Meridiem.PM, 16 * 60),
    MAGHRIB("MAGHRIB", "Maghrib", 3, Meridiem.PM, 18 * 60 + 30),
    ISHA("ISHA", "Isha", 4, Meridiem.PM, 20 * 60);

    /** True when this prayer's default sits before noon. Fajr only. */
    val defaultsToMorning: Boolean get() = meridiem == Meridiem.AM

    /** The only minutes-past-midnight this prayer may be stored at. */
    val validMinuteRange: IntRange get() = meridiem.minuteRange

    fun isValidTime(minutesFromMidnight: Int): Boolean =
        minutesFromMidnight in validMinuteRange

    /**
     * Brings a time into this prayer's half of the clock.
     *
     * Flips the meridiem rather than clamping to the nearest boundary, because
     * the two produce very different answers from the same mistake: 5:30
     * offered for Asr becomes 5:30 PM, which is almost certainly what was
     * meant, where clamping would produce 12:00 PM - a time nobody typed.
     *
     * This is the single normalisation used by the picker, the view model and
     * the DAO wrapper, so a time can only ever be stored valid no matter which
     * of them a value arrives through.
     */
    fun normaliseTime(minutesFromMidnight: Int): Int {
        val withinDay = Math.floorMod(minutesFromMidnight, Meridiem.MINUTES_PER_DAY)
        if (withinDay in validMinuteRange) return withinDay
        return Math.floorMod(
            withinDay + Meridiem.MINUTES_PER_HALF_DAY,
            Meridiem.MINUTES_PER_DAY
        )
    }

    companion object {
        fun from(value: String?): Prayer =
            entries.firstOrNull { it.storageValue == value } ?: FAJR
    }
}

/**
 * Whether times are worked out from coordinates or typed in.
 *
 * AUTOMATIC still allows a per-prayer override, because a calculation is a
 * model of the sky and the masjid down the road is the actual answer - they
 * routinely differ by a few minutes, and the one that matters is the jamaat.
 * An override, once set, is never overwritten by a later location change: the
 * calculation is a fallback for prayers the user has not spoken about, not a
 * correction to the ones they have.
 */
enum class PrayerMode(val storageValue: String, val label: String) {
    AUTOMATIC("AUTOMATIC", "Calculated for my location"),
    MANUAL("MANUAL", "Times I enter myself");

    companion object {
        fun from(value: String?): PrayerMode =
            entries.firstOrNull { it.storageValue == value } ?: AUTOMATIC
    }
}

/**
 * Calculation methods, mapped to the Adhan library's own set.
 *
 * These differ in the sun-angle each authority uses for Fajr and Isha, which is
 * why the same coordinates give different times depending on who you ask. The
 * right one is whichever your local masjid follows.
 */
enum class PrayerMethod(val storageValue: String, val label: String, val note: String) {
    KARACHI("KARACHI", "Karachi", "University of Islamic Sciences, Karachi"),
    MUSLIM_WORLD_LEAGUE("MUSLIM_WORLD_LEAGUE", "Muslim World League", "Widely used in Europe and the Far East"),
    UMM_AL_QURA("UMM_AL_QURA", "Umm al-Qura", "Makkah — used across Saudi Arabia"),
    EGYPTIAN("EGYPTIAN", "Egyptian", "Egyptian General Authority of Survey"),
    NORTH_AMERICA("NORTH_AMERICA", "ISNA", "Islamic Society of North America"),
    DUBAI("DUBAI", "Dubai", "Used in the UAE"),
    QATAR("QATAR", "Qatar", "Umm al-Qura with a modified Isha"),
    KUWAIT("KUWAIT", "Kuwait", "Used in Kuwait"),
    SINGAPORE("SINGAPORE", "Singapore", "Used in Singapore and the region"),
    MOON_SIGHTING_COMMITTEE("MOON_SIGHTING_COMMITTEE", "Moonsighting Committee", "Seasonal adjustment for high latitudes");

    companion object {
        fun from(value: String?): PrayerMethod =
            entries.firstOrNull { it.storageValue == value } ?: KARACHI
    }
}

/**
 * How quiet the phone goes during a prayer window.
 *
 * These need different mechanisms, which is not obvious. Do Not Disturb's
 * alarms-only filter suppresses vibration along with sound, so it cannot
 * deliver "vibrate only" - that has to come from the ringer mode instead.
 */
enum class PrayerSilenceMode(
    val storageValue: String,
    val label: String,
    val description: String
) {
    SILENT("SILENT", "Silent", "No sound and no vibration"),
    VIBRATE("VIBRATE", "Vibrate only", "No sound, but the phone still buzzes");

    companion object {
        fun from(value: String?): PrayerSilenceMode =
            entries.firstOrNull { it.storageValue == value } ?: SILENT
    }
}

/** Affects the Asr time only: Hanafi uses a longer shadow than the others. */
enum class PrayerMadhab(val storageValue: String, val label: String) {
    HANAFI("HANAFI", "Hanafi"),
    SHAFI("SHAFI", "Shafi'i, Maliki, Hanbali");

    companion object {
        fun from(value: String?): PrayerMadhab =
            entries.firstOrNull { it.storageValue == value } ?: HANAFI
    }
}

/** Everything the prayer feature needs, in one snapshot. */
data class PrayerSettings(
    val enabled: Boolean = false,
    val mode: PrayerMode = PrayerMode.AUTOMATIC,
    val method: PrayerMethod = PrayerMethod.KARACHI,
    val madhab: PrayerMadhab = PrayerMadhab.HANAFI,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String = "",
    val silenceMode: PrayerSilenceMode = PrayerSilenceMode.SILENT,
    /**
     * Null means "follow the device".
     *
     * Worth overriding when travelling: the phone may pick up the local zone
     * while the user still keeps the times of home, or the reverse. Everything
     * derives local midnight from this, so prayers and custom schedules agree.
     */
    val timeZoneId: String? = null,
    /** The adhan, off until the user asks for it. See [AdhanSettings]. */
    val adhan: AdhanSettings = AdhanSettings(),
) {
    /** Automatic mode is only usable once we know where the user is. */
    val hasLocation: Boolean get() = latitude != null && longitude != null

    val isUsable: Boolean
        get() = enabled && (mode == PrayerMode.MANUAL || hasLocation)

    /** The zone every time in the app is resolved against. */
    val zone: java.util.TimeZone
        get() = timeZoneId?.let { java.util.TimeZone.getTimeZone(it) }
            ?: java.util.TimeZone.getDefault()
}
