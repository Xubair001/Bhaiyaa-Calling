package com.codeaza.bhaiyaaa.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Telling an answered call from a missed one.
 *
 * PHONE_STATE arrives as separate broadcasts and a receiver keeps no state
 * between them, so this is the only thing standing between "you spoke to Ali,
 * want to note it down?" and the app offering to write notes about calls
 * nobody picked up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CallSessionTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = CallSession.clear(context)

    @Test
    fun `an answered incoming call reports its number`() {
        CallSession.onRinging(context, "+923001234567")
        CallSession.onAnswered(context)

        assertThat(CallSession.onEnded(context)).isEqualTo("+923001234567")
    }

    @Test
    fun `a missed call reports nothing`() {
        // Rang, was never picked up. Same two broadcasts as a completed call
        // apart from the OFFHOOK in the middle, which is the whole point.
        CallSession.onRinging(context, "+923001234567")

        assertThat(CallSession.onEnded(context)).isNull()
    }

    @Test
    fun `an outgoing call reports nothing`() {
        // No RINGING, so no session was ever started - OFFHOOK on its own
        // must not resurrect a number from an earlier call.
        CallSession.onAnswered(context)

        assertThat(CallSession.onEnded(context)).isNull()
    }

    @Test
    fun `an outgoing call cannot inherit the last incoming number`() {
        CallSession.onRinging(context, "+923001234567")
        CallSession.onAnswered(context)
        assertThat(CallSession.onEnded(context)).isEqualTo("+923001234567")

        // Now an outgoing one. Nothing from the previous call may survive.
        CallSession.onAnswered(context)
        assertThat(CallSession.onEnded(context)).isNull()
    }

    @Test
    fun `one call is never reported twice`() {
        // The platform can redeliver IDLE, and a second prompt about the same
        // conversation would read as a bug.
        CallSession.onRinging(context, "+923001234567")
        CallSession.onAnswered(context)

        assertThat(CallSession.onEnded(context)).isNotNull()
        assertThat(CallSession.onEnded(context)).isNull()
    }

    @Test
    fun `no number means no session at all`() {
        // EXTRA_INCOMING_NUMBER is empty unless READ_CALL_LOG is granted.
        // Nothing can be attributed later, so nothing is started.
        CallSession.onRinging(context, null)
        CallSession.onAnswered(context)

        assertThat(CallSession.onEnded(context)).isNull()
    }

    @Test
    fun `a blank number is treated as no number`() {
        CallSession.onRinging(context, "   ")
        CallSession.onAnswered(context)

        assertThat(CallSession.onEnded(context)).isNull()
    }

    @Test
    fun `a new call replaces an abandoned one`() {
        // A session left behind by a dropped IDLE broadcast must not be
        // reported under the next caller's name.
        CallSession.onRinging(context, "+923001111111")
        CallSession.onRinging(context, "+923002222222")
        CallSession.onAnswered(context)

        assertThat(CallSession.onEnded(context)).isEqualTo("+923002222222")
    }
}
