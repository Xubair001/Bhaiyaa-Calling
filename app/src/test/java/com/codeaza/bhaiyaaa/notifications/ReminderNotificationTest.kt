package com.codeaza.bhaiyaaa.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codeaza.bhaiyaaa.service.ReminderActionReceiver
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The reminder notification's contract.
 *
 * A reminder is flagged notified as soon as it fires, and the alarm receiver
 * skips anything already flagged. That makes Snooze the only way to defer one
 * without opening the app - if these actions go missing, swiping the alert
 * away silently becomes the same as deleting the reminder.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReminderNotificationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationChannels.createAll(context)
    }

    private val manager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test
    fun `a reminder alert carries snooze and done`() {
        Notifier.notifyReminder(context, 42L, "Call the bank")

        val posted = shadowOf(manager).allNotifications.single()
        assertThat(posted.actions.map { it.title.toString() })
            .containsExactly("Snooze 10 min", "Done").inOrder()
    }

    @Test
    fun `the alert says what the reminder is`() {
        Notifier.notifyReminder(context, 42L, "Call the bank")
        val posted = shadowOf(manager).allNotifications.single()
        assertThat(posted.extras.getString("android.text")).isEqualTo("Call the bank")
    }

    @Test
    fun `handling it in the app clears the alert`() {
        Notifier.notifyReminder(context, 42L, "Call the bank")
        assertThat(shadowOf(manager).allNotifications).hasSize(1)

        Notifier.cancelReminder(context, 42L)
        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }

    @Test
    fun `two reminders get their own alerts`() {
        // Sharing a notification id would make the second reminder replace the
        // first, so one of them would vanish before anyone saw it.
        Notifier.notifyReminder(context, 1L, "Call the bank")
        Notifier.notifyReminder(context, 2L, "Buy milk")
        assertThat(shadowOf(manager).allNotifications).hasSize(2)

        Notifier.cancelReminder(context, 1L)
        assertThat(shadowOf(manager).allNotifications).hasSize(1)
    }

    @Test
    fun `snooze and done are distinct intents`() {
        // Both buttons broadcast to the same receiver, so they are told apart
        // by action. Equal request codes would make the second PendingIntent
        // overwrite the first and Snooze would start completing reminders.
        Notifier.notifyReminder(context, 42L, "Call the bank")
        val posted = shadowOf(manager).allNotifications.single()

        val actions = posted.actions.map { shadowOf(it.actionIntent).savedIntent }
        assertThat(actions.map { it.action })
            .containsExactly(ReminderActionReceiver.ACTION_SNOOZE, ReminderActionReceiver.ACTION_DONE)
            .inOrder()
        actions.forEach {
            assertThat(it.getLongExtra(ReminderActionReceiver.EXTRA_REMINDER_ID, -1L)).isEqualTo(42L)
        }
    }
}
