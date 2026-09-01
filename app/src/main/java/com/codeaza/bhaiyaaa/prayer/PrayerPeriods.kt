package com.codeaza.bhaiyaaa.prayer

import com.codeaza.bhaiyaaa.domain.model.Prayer

/**
 * Which prayer's period the clock is currently in.
 *
 * A *window* is the few minutes the phone goes quiet. A *period* is the far
 * longer stretch a prayer names - from its adhan until the next prayer's - and
 * it is what content shown "during Asr" has to be keyed to. Conflating the two
 * would mean prayer-related content appeared for fifteen minutes a day and then
 * vanished.
 *
 * Takes the anchors [PrayerTimeCalculator.anchorsForDay] produces, so there is
 * one definition of when a prayer is and this adds no second one. Pure and
 * clock-free apart from the [now] it is handed.
 */
object PrayerPeriods {

    /**
     * The prayer whose period contains [now], or null when no prayer has a
     * time at all.
     *
     * Before the day's first prayer the answer is Isha: that period began last
     * night and has not ended. Resolving it this way rather than looking up
     * yesterday's anchors means the caller never has to plan two days to ask a
     * question about one moment.
     */
    fun current(anchors: Map<Prayer, Long>, now: Long): Prayer? {
        if (anchors.isEmpty()) return null

        // Ordered by the prayer's place in the day rather than by time, so a
        // mistyped time cannot reorder the day itself.
        val begun = Prayer.entries.filter { prayer ->
            anchors[prayer]?.let { it <= now } == true
        }

        return begun.maxByOrNull { it.order }
            ?: Prayer.ISHA.takeIf { anchors.containsKey(it) }
    }

    /**
     * When the current period gives way to the next, or null when the next
     * prayer is tomorrow's and therefore not in [anchors].
     *
     * Lets content that is specific to a period know how long it stays valid
     * without re-deriving the period on a timer.
     */
    fun currentPeriodEnd(anchors: Map<Prayer, Long>, now: Long): Long? =
        anchors.values.filter { it > now }.minOrNull()
}
