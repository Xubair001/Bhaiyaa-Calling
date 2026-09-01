package com.codeaza.bhaiyaaa.ui.prayer

/**
 * What the user came to this screen to do.
 *
 * Set either by the route that opened the screen - the dashboard card knows
 * whether it is sending someone to set their times - or by what they touch
 * once they are here.
 */
enum class QuietTimesFocus {
    /** Opened from Settings with no particular intent. */
    NONE,

    /** Turning prayer silence on or off, and the controls that govern it. */
    PRAYER_SWITCH,

    /** Entering or correcting the five prayer times. */
    PRAYER_TIMES,

    /** Adding or editing a quiet period of the user's own. */
    QUIET_TIME;

    companion object {
        fun from(value: String?): QuietTimesFocus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NONE
    }
}

/** Every block the Quiet times screen can show. One entry, one place. */
enum class QuietTimesSection {
    /** Platform grants that have to be fixed before anything else works. */
    WARNINGS,
    CUSTOM_QUIET,
    PRAYER_SWITCH,
    HOW_QUIET,
    TIME_SOURCE,
    LOCATION,
    METHOD,
    PRAYER_TIMES,
    ADHAN,
    WHO_RINGS,
    TIME_ZONE,
    FOOTNOTE
}

/**
 * Decides what order the screen's sections appear in.
 *
 * This screen carries three jobs that used to sit in a fixed order with
 * whichever one you wanted possibly three scrolls down. Rather than splitting
 * it into three screens - which would duplicate the prayer list and make
 * "where do I change that?" a navigation problem - the same sections are
 * simply ordered around what the user is doing.
 *
 * Two rules keep that from becoming disorienting:
 *
 *  - **Nothing is added or removed by focus.** Every section a configuration
 *    can show is present in every order, so a section never vanishes because
 *    the user touched something else. Only the sequence changes.
 *  - **Warnings stay first, always.** A missing Do Not Disturb grant is the
 *    reason the whole feature would not work, and no focus is a good reason to
 *    push it below the fold.
 *
 * Pure, so the behaviour is a table a test can assert rather than something to
 * be checked by scrolling.
 */
object QuietTimesLayout {

    /**
     * @param prayerEnabled when false the prayer-specific blocks are not shown
     *   at all - they configure a feature that is switched off.
     * @param automatic location and calculation method only apply when times
     *   come from coordinates.
     */
    fun order(
        focus: QuietTimesFocus,
        prayerEnabled: Boolean,
        automatic: Boolean
    ): List<QuietTimesSection> {
        val prayerDetail = buildList {
            add(QuietTimesSection.HOW_QUIET)
            add(QuietTimesSection.TIME_SOURCE)
            if (automatic) {
                add(QuietTimesSection.LOCATION)
                add(QuietTimesSection.METHOD)
            }
            add(QuietTimesSection.ADHAN)
            add(QuietTimesSection.WHO_RINGS)
        }

        val body: List<QuietTimesSection> = when {
            // Prayer silence is off. There is nothing to prioritise but the
            // switch that turns it on and the user's own quiet times, which
            // work regardless.
            !prayerEnabled -> listOf(
                QuietTimesSection.PRAYER_SWITCH,
                QuietTimesSection.CUSTOM_QUIET
            )

            focus == QuietTimesFocus.QUIET_TIME -> buildList {
                add(QuietTimesSection.CUSTOM_QUIET)
                add(QuietTimesSection.PRAYER_SWITCH)
                add(QuietTimesSection.PRAYER_TIMES)
                addAll(prayerDetail)
            }

            focus == QuietTimesFocus.PRAYER_TIMES -> buildList {
                add(QuietTimesSection.PRAYER_TIMES)
                // Where the times come from sits directly under them: it is
                // the first thing someone reaches for when a time looks wrong.
                add(QuietTimesSection.TIME_SOURCE)
                if (automatic) {
                    add(QuietTimesSection.LOCATION)
                    add(QuietTimesSection.METHOD)
                }
                add(QuietTimesSection.ADHAN)
                add(QuietTimesSection.PRAYER_SWITCH)
                add(QuietTimesSection.HOW_QUIET)
                add(QuietTimesSection.WHO_RINGS)
                add(QuietTimesSection.CUSTOM_QUIET)
            }

            focus == QuietTimesFocus.PRAYER_SWITCH -> buildList {
                add(QuietTimesSection.PRAYER_SWITCH)
                addAll(prayerDetail)
                add(QuietTimesSection.PRAYER_TIMES)
                add(QuietTimesSection.CUSTOM_QUIET)
            }

            // No stated intent: the times are what people come here for most,
            // so they lead.
            else -> buildList {
                add(QuietTimesSection.PRAYER_SWITCH)
                add(QuietTimesSection.PRAYER_TIMES)
                addAll(prayerDetail)
                add(QuietTimesSection.CUSTOM_QUIET)
            }
        }

        return buildList {
            add(QuietTimesSection.WARNINGS)
            addAll(body)
            add(QuietTimesSection.TIME_ZONE)
            // The footnote explains what prayer silence does to alarms, so it
            // says nothing worth a slot when prayer silence is off.
            if (prayerEnabled) add(QuietTimesSection.FOOTNOTE)
        }
    }
}
