package com.codeaza.bhaiyaaa.domain.model

/** Where a quiet window came from. Prayers are computed; custom ones are typed. */
enum class SilenceSource { PRAYER, CUSTOM }

/**
 * One resolved period of quiet, whatever produced it.
 *
 * The scheduler, the dashboard and the incoming-call path all work in these.
 * Prayers and custom schedules differ only in how their times are arrived at -
 * once resolved to an instant they behave identically, and giving them a shared
 * shape is what stops the two growing separate, subtly different code paths.
 */
data class SilenceWindow(
    /** Stable across a reschedule, so an alarm can be cancelled and re-armed. */
    val key: String,
    val label: String,
    val source: SilenceSource,
    /** The moment the window is named after - the adhan, or the start you typed. */
    val anchorMillis: Long,
    /** When the phone actually goes quiet, which may be earlier than the anchor. */
    val startMillis: Long,
    val durationMinutes: Int,
    val enabled: Boolean,
    val mode: PrayerSilenceMode,
    /** True when a prayer's calculated time was replaced by a typed one. */
    val isOverridden: Boolean = false
) {
    val endMillis: Long get() = startMillis + durationMinutes * 60_000L

    fun containsNow(now: Long): Boolean = enabled && now >= startMillis && now < endMillis

    companion object {
        fun prayerKey(prayer: Prayer): String = "prayer:${prayer.storageValue}"
        fun customKey(id: Long): String = "custom:$id"
    }
}

/**
 * Which days a custom schedule runs on, as a bitmask.
 *
 * Bit 0 is Sunday through bit 6 Saturday, matching Calendar.DAY_OF_WEEK minus
 * one. A mask rather than a list because it is a single INTEGER column and a
 * single comparison, and there are only ever seven of them.
 */
object Weekdays {
    const val EVERY_DAY = 0b1111111
    const val WEEKDAYS = 0b0111110
    const val WEEKENDS = 0b1000001

    val labels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    fun includes(mask: Int, calendarDayOfWeek: Int): Boolean {
        val bit = (calendarDayOfWeek - 1).coerceIn(0, 6)
        return mask shr bit and 1 == 1
    }

    fun toggle(mask: Int, index: Int): Int = mask xor (1 shl index.coerceIn(0, 6))

    fun isSet(mask: Int, index: Int): Boolean = mask shr index.coerceIn(0, 6) and 1 == 1

    /** "Every day", "Weekdays", "Mon, Wed, Fri" - whichever is shortest and true. */
    fun describe(mask: Int): String = when {
        mask and EVERY_DAY == EVERY_DAY -> "Every day"
        mask and EVERY_DAY == WEEKDAYS -> "Weekdays"
        mask and EVERY_DAY == WEEKENDS -> "Weekends"
        mask and EVERY_DAY == 0 -> "Never"
        else -> labels.filterIndexed { i, _ -> isSet(mask, i) }.joinToString(", ")
    }
}
