package com.codeaza.bhaiyaaa.prayer

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codeaza.bhaiyaaa.domain.model.PrayerSilenceMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins which silence mode needs which permission.
 *
 * Getting this wrong is invisible at compile time and silent at runtime: the
 * window simply does not happen. Vibrate-only was gated behind Do Not Disturb
 * access it never needed, so it failed for anyone who had not granted it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SilenceControllerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun setDndAccess(granted: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationPolicyAccessGranted(granted)
    }

    @Test
    fun `silent mode is blocked without do not disturb access`() {
        setDndAccess(false)
        val reason = SilenceController.blockedReason(context, PrayerSilenceMode.SILENT)
        assertThat(reason).isNotNull()
        // The message has to name the way out, not just the problem.
        assertThat(reason).contains("Vibrate only")
    }

    @Test
    fun `vibrate only is not blocked without do not disturb access`() {
        setDndAccess(false)
        // RINGER_MODE_VIBRATE needs no policy access - gating it behind one was
        // the bug that made vibrate-only do nothing at all.
        assertThat(SilenceController.blockedReason(context, PrayerSilenceMode.VIBRATE)).isNull()
    }

    @Test
    fun `neither mode is blocked once access is granted`() {
        setDndAccess(true)
        PrayerSilenceMode.entries.forEach { mode ->
            assertThat(SilenceController.blockedReason(context, mode)).isNull()
        }
    }

    @Test
    fun `silence is not reported active before anything has started`() {
        assertThat(SilenceController.isSilenceActive(context)).isFalse()
        assertThat(SilenceController.activePrayerName(context)).isNull()
    }

    @Test
    fun `entering silence records which prayer is running`() {
        setDndAccess(true)
        val entered = SilenceController.enterSilence(context, "FAJR", PrayerSilenceMode.SILENT)
        assertThat(entered).isTrue()
        assertThat(SilenceController.isSilenceActive(context)).isTrue()
        assertThat(SilenceController.activePrayerName(context)).isEqualTo("FAJR")
    }

    @Test
    fun `exiting silence clears the state`() {
        setDndAccess(true)
        SilenceController.enterSilence(context, "ASR", PrayerSilenceMode.SILENT)
        SilenceController.exitSilence(context)

        assertThat(SilenceController.isSilenceActive(context)).isFalse()
        assertThat(SilenceController.activePrayerName(context)).isNull()
    }

    @Test
    fun `exiting restores the interruption filter that was in force`() {
        setDndAccess(true)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)

        SilenceController.enterSilence(context, "ISHA", PrayerSilenceMode.SILENT)
        assertThat(manager.currentInterruptionFilter)
            .isEqualTo(NotificationManager.INTERRUPTION_FILTER_ALARMS)

        SilenceController.exitSilence(context)
        // Back to what the user had, not to a hardcoded "normal".
        assertThat(manager.currentInterruptionFilter)
            .isEqualTo(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    @Test
    fun `vibrate mode leaves the do not disturb filter alone`() {
        setDndAccess(true)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)

        SilenceController.enterSilence(context, "DHUHR", PrayerSilenceMode.VIBRATE)

        // Turning DND on here would suppress the very vibration being asked for.
        assertThat(manager.currentInterruptionFilter)
            .isEqualTo(NotificationManager.INTERRUPTION_FILTER_ALL)
        SilenceController.exitSilence(context)
    }

    @Test
    fun `a stale active flag is cleared when no window is running`() {
        setDndAccess(true)
        SilenceController.enterSilence(context, "MAGHRIB", PrayerSilenceMode.SILENT)

        // Simulates the process dying mid-window: the end alarm never ran.
        SilenceController.recoverIfStale(context, stillInsideWindow = false)

        assertThat(SilenceController.isSilenceActive(context)).isFalse()
    }

    @Test
    fun `recovery leaves an genuinely running window alone`() {
        setDndAccess(true)
        SilenceController.enterSilence(context, "FAJR", PrayerSilenceMode.SILENT)

        SilenceController.recoverIfStale(context, stillInsideWindow = true)

        assertThat(SilenceController.isSilenceActive(context)).isTrue()
        SilenceController.exitSilence(context)
    }
}
