package com.codeaza.bhaiyaaa.ui

import com.codeaza.bhaiyaaa.ui.prayer.QuietTimesFocus
import com.codeaza.bhaiyaaa.ui.prayer.QuietTimesLayout
import com.codeaza.bhaiyaaa.ui.prayer.QuietTimesSection
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The Quiet times screen's section order.
 *
 * The screen reorders itself around what the user is doing rather than
 * splitting into three screens that would each need their own copy of the
 * prayer list. Reordering is only safe if two things stay true - nothing
 * disappears because of a focus, and the warnings stay first - so those are
 * asserted here rather than left to be noticed by scrolling.
 */
class QuietTimesLayoutTest {

    private fun order(
        focus: QuietTimesFocus,
        prayerEnabled: Boolean = true,
        automatic: Boolean = true
    ) = QuietTimesLayout.order(focus, prayerEnabled, automatic)

    @Test
    fun `adding a quiet time puts the user's own quiet times first`() {
        val sections = order(QuietTimesFocus.QUIET_TIME)
        assertThat(sections.first { it != QuietTimesSection.WARNINGS })
            .isEqualTo(QuietTimesSection.CUSTOM_QUIET)
    }

    @Test
    fun `setting prayer times puts the times first`() {
        val sections = order(QuietTimesFocus.PRAYER_TIMES)
        assertThat(sections.first { it != QuietTimesSection.WARNINGS })
            .isEqualTo(QuietTimesSection.PRAYER_TIMES)
    }

    @Test
    fun `turning prayer silence on or off puts the switch first`() {
        val sections = order(QuietTimesFocus.PRAYER_SWITCH)
        assertThat(sections.first { it != QuietTimesSection.WARNINGS })
            .isEqualTo(QuietTimesSection.PRAYER_SWITCH)
    }

    @Test
    fun `warnings come first whatever the user is doing`() {
        QuietTimesFocus.entries.forEach { focus ->
            listOf(true, false).forEach { enabled ->
                // A missing Do Not Disturb grant is the reason the whole
                // feature would do nothing. No intent outranks saying so.
                assertThat(order(focus, prayerEnabled = enabled).first())
                    .isEqualTo(QuietTimesSection.WARNINGS)
            }
        }
    }

    @Test
    fun `focus changes the order and never the contents`() {
        val baseline = order(QuietTimesFocus.NONE).toSet()
        QuietTimesFocus.entries.forEach { focus ->
            // A section vanishing because the user touched something else
            // would make the screen feel like it was hiding things.
            assertThat(order(focus).toSet()).isEqualTo(baseline)
        }
    }

    @Test
    fun `no section is ever listed twice`() {
        QuietTimesFocus.entries.forEach { focus ->
            listOf(true, false).forEach { enabled ->
                listOf(true, false).forEach { automatic ->
                    val sections = order(focus, enabled, automatic)
                    // A duplicate key would break the LazyColumn outright,
                    // and duplicated UI is what the brief ruled out.
                    assertThat(sections).containsNoDuplicates()
                }
            }
        }
    }

    @Test
    fun `prayer settings disappear when prayer silence is off`() {
        val sections = order(QuietTimesFocus.NONE, prayerEnabled = false)
        // Configuring a feature that is switched off is noise, and the switch
        // to turn it on is right there.
        assertThat(sections).doesNotContain(QuietTimesSection.PRAYER_TIMES)
        assertThat(sections).doesNotContain(QuietTimesSection.HOW_QUIET)
        assertThat(sections).doesNotContain(QuietTimesSection.ADHAN)
        // The user's own quiet times run regardless, so they stay.
        assertThat(sections).contains(QuietTimesSection.CUSTOM_QUIET)
        assertThat(sections).contains(QuietTimesSection.PRAYER_SWITCH)
    }

    @Test
    fun `location and method are only shown when times are calculated`() {
        val manual = order(QuietTimesFocus.NONE, automatic = false)
        assertThat(manual).doesNotContain(QuietTimesSection.LOCATION)
        assertThat(manual).doesNotContain(QuietTimesSection.METHOD)

        val automatic = order(QuietTimesFocus.NONE, automatic = true)
        assertThat(automatic).contains(QuietTimesSection.LOCATION)
        assertThat(automatic).contains(QuietTimesSection.METHOD)
    }

    @Test
    fun `an unknown focus falls back to the default order rather than failing`() {
        assertThat(QuietTimesFocus.from("nonsense")).isEqualTo(QuietTimesFocus.NONE)
        assertThat(QuietTimesFocus.from(null)).isEqualTo(QuietTimesFocus.NONE)
        assertThat(QuietTimesFocus.from("prayer_times")).isEqualTo(QuietTimesFocus.PRAYER_TIMES)
    }
}
