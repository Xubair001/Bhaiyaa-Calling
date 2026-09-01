package com.codeaza.bhaiyaaa.prayer

import com.codeaza.bhaiyaaa.domain.model.Prayer

/**
 * The two moments that shape a day of fasting.
 *
 * Both are prayer times the app already knows: the fast begins when Fajr comes
 * in and ends when Maghrib does. Nothing here computes a time - it names two
 * that [PrayerTimeCalculator] produced, which is the whole point. A separate
 * "Ramadan calculation" would be a second source of truth for the same instant,
 * and the two would eventually disagree by a minute with nobody able to say
 * which was right.
 */
data class RamadanDay(
    /** Fajr. Suhoor is over when it arrives. */
    val suhoorEndsAt: Long,
    /** Maghrib. The fast is broken when it arrives. */
    val iftarAt: Long
)

/** Where the clock is in a day of fasting. */
sealed interface RamadanState {
    /** Before Fajr: still time to eat. */
    data class BeforeSuhoorEnds(val millisRemaining: Long) : RamadanState

    /** Between Fajr and Maghrib. */
    data class Fasting(val millisUntilIftar: Long) : RamadanState

    /**
     * After Maghrib.
     *
     * Carries no next time on purpose: the next suhoor is tomorrow's Fajr,
     * which is not in today's times, and inventing it here would be exactly
     * the second source of truth this file exists to avoid.
     */
    data object Complete : RamadanState
}

/**
 * Reads a day of fasting off the prayer times.
 *
 * Pure, so "when does the fast end" is a test rather than something to be
 * checked by waiting until sunset.
 */
object RamadanTimes {

    /**
     * @param anchors today's prayer instants, from
     *   [PrayerTimeCalculator.anchorsForDay].
     * @return null unless both Fajr and Maghrib are known - a partially
     *   configured day cannot describe a fast, and guessing at either end of
     *   it would be worse than saying nothing.
     */
    fun forDay(anchors: Map<Prayer, Long>): RamadanDay? {
        val fajr = anchors[Prayer.FAJR] ?: return null
        val maghrib = anchors[Prayer.MAGHRIB] ?: return null
        // A Maghrib at or before Fajr means the two times contradict each
        // other - mistyped in manual mode, most likely. Better to show nothing
        // than a fast of zero or negative length.
        if (maghrib <= fajr) return null
        return RamadanDay(suhoorEndsAt = fajr, iftarAt = maghrib)
    }

    fun stateAt(day: RamadanDay, now: Long): RamadanState = when {
        now < day.suhoorEndsAt -> RamadanState.BeforeSuhoorEnds(day.suhoorEndsAt - now)
        now < day.iftarAt -> RamadanState.Fasting(day.iftarAt - now)
        else -> RamadanState.Complete
    }
}
