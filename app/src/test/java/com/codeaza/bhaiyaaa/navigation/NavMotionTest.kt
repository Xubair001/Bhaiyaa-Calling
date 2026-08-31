package com.codeaza.bhaiyaaa.navigation

import com.codeaza.bhaiyaaa.ui.navigation.NavMotion
import com.codeaza.bhaiyaaa.ui.navigation.Routes
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Which screens crossfade and which slide.
 *
 * This is the part of the motion work that can actually be wrong. The timings
 * either look right or they do not, and a person can see that in a second;
 * mixing up a tab and a detail screen is invisible until someone notices a
 * screen sliding the wrong way.
 */
class NavMotionTest {

    @Test
    fun `moving between tabs is lateral`() {
        assertThat(NavMotion.isLateral(Routes.HOME, Routes.CALLS)).isTrue()
        assertThat(NavMotion.isLateral(Routes.CONTACTS, Routes.MORE)).isTrue()
        assertThat(NavMotion.isLateral(Routes.ASSISTANT, Routes.HOME)).isTrue()
    }

    @Test
    fun `opening a settings screen is not lateral`() {
        assertThat(NavMotion.isLateral(Routes.MORE, Routes.SETTINGS)).isFalse()
        assertThat(NavMotion.isLateral(Routes.SETTINGS, Routes.SETTINGS_PRAYER)).isFalse()
    }

    @Test
    fun `going back out of a detail screen is not lateral`() {
        assertThat(NavMotion.isLateral(Routes.SETTINGS, Routes.MORE)).isFalse()
        assertThat(NavMotion.isLateral(Routes.REMINDERS, Routes.MORE)).isFalse()
    }

    @Test
    fun `a parameterised detail route is not mistaken for a tab`() {
        // CONTACT_DETAIL is "contact/{phoneNumber}", which is close enough to
        // CONTACTS to be worth pinning down.
        assertThat(NavMotion.isLateral(Routes.CONTACTS, Routes.CONTACT_DETAIL)).isFalse()
        assertThat(NavMotion.isLateral(Routes.CALLS, Routes.CALL_DETAIL)).isFalse()
    }

    @Test
    fun `an unknown route falls back to the directional slide`() {
        // A null route happens on the very first entry. Sliding is the safe
        // default: a crossfade between unrelated screens reads as a glitch.
        assertThat(NavMotion.isLateral(null, Routes.HOME)).isFalse()
        assertThat(NavMotion.isLateral(Routes.HOME, null)).isFalse()
    }
}
