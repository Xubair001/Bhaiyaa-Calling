package com.codeaza.bhaiyaaa.domain.model

/**
 * The five daily prayers, in order.
 *
 * [defaultClockMinutes] is where the time picker opens when no time has been
 * set yet - minutes past midnight. It is only a starting position, never a
 * stored value, but it matters: an editor that opens at 12 AM for every prayer
 * means Dhuhr, Asr, Maghrib and Isha all need the meridiem flipped before
 * anything else, which is a step it is easy to miss and produces a prayer
 * scheduled twelve hours out. Fajr opens in the morning, the rest after noon.
 */
enum class Prayer(
    val storageValue: String,
    val label: String,
    val order: Int,
    val defaultClockMinutes: Int
) {
    FAJR("FAJR", "Fajr", 0, 5 * 60),
    DHUHR("DHUHR", "Dhuhr", 1, 12 * 60 + 30),
    ASR("ASR", "Asr", 2, 16 * 60),
    MAGHRIB("MAGHRIB", "Maghrib", 3, 18 * 60 + 30),
    ISHA("ISHA", "Isha", 4, 20 * 60);

    /** True when this prayer's default sits before noon. Fajr only. */
    val defaultsToMorning: Boolean get() = defaultClockMinutes < 12 * 60

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
