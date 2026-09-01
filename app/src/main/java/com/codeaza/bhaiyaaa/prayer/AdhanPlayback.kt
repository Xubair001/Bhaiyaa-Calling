package com.codeaza.bhaiyaaa.prayer

import com.codeaza.bhaiyaaa.domain.model.Prayer
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * The decision of whether an adhan should sound, kept separate from the code
 * that makes noise.
 *
 * Everything here is pure, so "does not play twice", "does not play when
 * disabled" and "a new day is a new chance to play" are properties a test can
 * assert rather than behaviours someone has to sit and wait for. The service
 * only makes sound after [shouldPlay] has said yes.
 */
object AdhanPlayback {

    /**
     * Identifies one prayer on one local day.
     *
     * A prayer alone is not enough - tomorrow's Fajr must be allowed to sound
     * after today's has. A timestamp alone is not enough either, since the
     * alarm may fire a second or two from the instant it was armed for. The
     * local calendar day is the unit that matches how people think about it,
     * and resolving it against the user's chosen zone rather than the device's
     * is what makes a time-zone override behave.
     */
    fun dayKey(prayer: Prayer, at: Long, zone: TimeZone): String {
        val cal = Calendar.getInstance(zone).apply { timeInMillis = at }
        return String.format(
            Locale.US,
            "%s@%04d-%02d-%02d",
            prayer.storageValue,
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * @param adhanEnabled the user's preference, re-read at the moment of
     *   playing rather than trusted from when the alarm was armed. An alarm
     *   set an hour ago must not play for someone who has since turned the
     *   adhan off - that is precisely the "unexpectedly plays audio" failure.
     * @param prayerEnabled whether this prayer is still switched on.
     * @param lastPlayedKey the [dayKey] of the last adhan actually played.
     * @param requestKey the [dayKey] of the one being considered.
     * @param minutesLate how far past the prayer the alarm arrived.
     */
    fun shouldPlay(
        adhanEnabled: Boolean,
        prayerEnabled: Boolean,
        lastPlayedKey: String?,
        requestKey: String,
        minutesLate: Long
    ): Boolean = when {
        !adhanEnabled -> false
        !prayerEnabled -> false
        // Already sounded for this prayer today. Doze, a reboot re-arming, and
        // a duplicate broadcast can all deliver the same alarm twice.
        lastPlayedKey == requestKey -> false
        // An alarm held back by deep Doze until long after the prayer should
        // stay silent: an adhan forty minutes late is not a call to prayer,
        // it is a confusing noise.
        minutesLate > MAX_LATENESS_MINUTES -> false
        else -> true
    }

    /**
     * Beyond this, the moment has passed.
     *
     * Chosen to match the shortest gap the app itself expects to be meaningful
     * - a quiet window is fifteen minutes by default - rather than a round
     * number picked for looking tidy.
     */
    const val MAX_LATENESS_MINUTES = 15L
}
