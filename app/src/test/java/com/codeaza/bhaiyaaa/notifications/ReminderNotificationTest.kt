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

    /**
     * The reminder alerts, without the group summary.
     *
     * Posting a reminder also posts a summary, which is what makes several due
     * at once collapse into one card in the shade instead of burying
     * everything else. It is not itself a reminder, so these assertions are
     * about the children.
     */
    private fun reminderAlerts() =
        shadowOf(manager).allNotifications.filterNot {
            it.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0
        }

    @Test
    fun `a reminder alert carries snooze and done`() {
        Notifier.notifyReminder(context, 42L, "Call the bank")

        val posted = reminderAlerts().single()
        assertThat(posted.actions.map { it.title.toString() })
            .containsExactly("Snooze 10 min", "Done").inOrder()
    }

    @Test
    fun `the alert says what the reminder is`() {
        Notifier.notifyReminder(context, 42L, "Call the bank")
        val posted = reminderAlerts().single()
        assertThat(posted.extras.getString("android.text")).isEqualTo("Call the bank")
    }

    @Test
    fun `handling it in the app clears the alert`() {
        Notifier.notifyReminder(context, 42L, "Call the bank")
        assertThat(reminderAlerts()).hasSize(1)

        Notifier.cancelReminder(context, 42L)
        assertThat(reminderAlerts()).isEmpty()
    }

    @Test
    fun `two reminders get their own alerts`() {
        // Sharing a notification id would make the second reminder replace the
        // first, so one of them would vanish before anyone saw it.
        Notifier.notifyReminder(context, 1L, "Call the bank")
        Notifier.notifyReminder(context, 2L, "Buy milk")
        assertThat(reminderAlerts()).hasSize(2)

        Notifier.cancelReminder(context, 1L)
        assertThat(reminderAlerts()).hasSize(1)
    }

    @Test
    fun `snooze and done are distinct intents`() {
        // Both buttons broadcast to the same receiver, so they are told apart
        // by action. Equal request codes would make the second PendingIntent
        // overwrite the first and Snooze would start completing reminders.
        Notifier.notifyReminder(context, 42L, "Call the bank")
        val posted = reminderAlerts().single()

        val actions = posted.actions.map { shadowOf(it.actionIntent).savedIntent }
        assertThat(actions.map { it.action })
            .containsExactly(ReminderActionReceiver.ACTION_SNOOZE, ReminderActionReceiver.ACTION_DONE)
            .inOrder()
        actions.forEach {
            assertThat(it.getLongExtra(ReminderActionReceiver.EXTRA_REMINDER_ID, -1L)).isEqualTo(42L)
        }
    }
}
