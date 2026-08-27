package com.codeaza.bhaiyaaa.domain.model

/** The five daily prayers, in order. */
enum class Prayer(val storageValue: String, val label: String, val order: Int) {
    FAJR("FAJR", "Fajr", 0),
    DHUHR("DHUHR", "Dhuhr", 1),
    ASR("ASR", "Asr", 2),
    MAGHRIB("MAGHRIB", "Maghrib", 3),
    ISHA("ISHA", "Isha", 4);

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

/** One prayer resolved to an actual instant today, with its silence window. */
data class PrayerWindow(
    val prayer: Prayer,
    /** The prayer itself. Shown to the user - it is what they recognise. */
    val prayerTimeMillis: Long,
    /** When the phone goes quiet, which is usually a few minutes earlier. */
    val startMillis: Long,
    val silenceMinutes: Int,
    val enabled: Boolean,
    /** True when the time came from a manual override rather than the calculation. */
    val isOverridden: Boolean
) {
    val endMillis: Long get() = startMillis + silenceMinutes * 60_000L

    fun containsNow(now: Long): Boolean = enabled && now >= startMillis && now < endMillis
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
) {
    /** Automatic mode is only usable once we know where the user is. */
    val hasLocation: Boolean get() = latitude != null && longitude != null

    val isUsable: Boolean
        get() = enabled && (mode == PrayerMode.MANUAL || hasLocation)
}
