package com.codeaza.bhaiyaaa.notifications

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.codeaza.bhaiyaaa.MainActivity
import com.codeaza.bhaiyaaa.R
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.service.ReminderActionReceiver

/**
 * All notification posting goes through here.
 *
 * Two rules hold everywhere in this file:
 *  - POST_NOTIFICATIONS is checked before every post, so a denied permission is
 *    a quiet no-op rather than a SecurityException.
 *  - No phone number, note or memory text is ever put in a log statement.
 */
object Notifier {

    private const val VIP_NOTIFICATION_BASE = 1000
    private const val REMINDER_NOTIFICATION_BASE = 2000
    private const val MISSED_NOTIFICATION_ID = 3001
    private const val TEST_NOTIFICATION_ID = 4001

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun notifyVipCall(
        context: Context,
        contactName: String,
        rawNumber: String,
        level: VipLevel,
        message: String
    ) {
        if (!canPost(context)) return

        val title = when (level) {
            VipLevel.EMERGENCY -> "⚠️ Emergency caller"
            VipLevel.SUPER_VIP -> "Super VIP calling"
            else -> "VIP calling"
        }

        val builder = NotificationCompat.Builder(context, NotificationChannels.channelFor(level))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .setOnlyAlertOnce(false)
            // Whether this rings through Do Not Disturb is decided by the
            // channel (see NotificationChannels.setBypassDnd), not here - a
            // notification cannot override DND on its own.
            .setCategory(NotificationCompat.CATEGORY_CALL)

        post(context, VIP_NOTIFICATION_BASE + (rawNumber.hashCode() and 0xFFF), builder.build())
    }

    /**
     * Posts a real notification on a tier's channel so "Test alert" tests the
     * whole thing, not just the parts the app drives directly.
     *
     * Sound is owned by the notification channel, not by the app - so vibration
     * and the torch can fire while the phone stays silent, because nothing was
     * ever posted. Going through the real channel means the test hears exactly
     * what an actual call on that tier would, including whether it breaks
     * through Do Not Disturb.
     */
    fun notifyTestAlert(context: Context, level: VipLevel) {
        if (!canPost(context)) return
        val builder = NotificationCompat.Builder(context, NotificationChannels.channelFor(level))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${level.label} test alert")
            .setContentText("This is how a ${level.label} call will reach you.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            // Clears itself so a test never lingers in the shade.
            .setTimeoutAfter(10_000)
        post(context, TEST_NOTIFICATION_ID, builder.build())
    }

    /**
     * Reminder alert, with Snooze and Done on it.
     *
     * The actions are what make the notification useful rather than decorative.
     * A reminder is marked notified once it fires and the receiver skips
     * anything already notified, so without a way to defer from here, swiping
     * the alert away was the same as deleting it - the reminder stayed in the
     * list and never spoke again.
     */
    fun notifyReminder(context: Context, reminderId: Long, text: String) {
        if (!canPost(context)) return
        val builder = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Reminder")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .addAction(
                0,
                "Snooze 10 min",
                reminderAction(context, ReminderActionReceiver.ACTION_SNOOZE, reminderId)
            )
            .addAction(
                0,
                "Done",
                reminderAction(context, ReminderActionReceiver.ACTION_DONE, reminderId)
            )
        post(context, REMINDER_NOTIFICATION_BASE + reminderId.toInt(), builder.build())
    }

    /** Clears a reminder's alert once it has been handled inside the app. */
    fun cancelReminder(context: Context, reminderId: Long) {
        runCatching {
            NotificationManagerCompat.from(context)
                .cancel(REMINDER_NOTIFICATION_BASE + reminderId.toInt())
        }
    }

    /**
     * Request codes mix the action into the id. Sharing one code across both
     * buttons would make the second PendingIntent overwrite the first, and
     * Snooze would silently start completing reminders.
     */
    private fun reminderAction(context: Context, action: String, reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        val code = REMINDER_NOTIFICATION_BASE + reminderId.toInt() * 2 +
            if (action == ReminderActionReceiver.ACTION_DONE) 1 else 0
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun notifyMissedImportant(context: Context, contactName: String, count: Int) {
        if (!canPost(context)) return
        val text = if (count > 1) "$contactName called $count times" else "$contactName tried to reach you"
        val builder = NotificationCompat.Builder(context, NotificationChannels.MISSED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Missed an important call")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
        post(context, MISSED_NOTIFICATION_ID, builder.build())
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun post(context: Context, id: Int, notification: Notification) {
        // Permission can be revoked between the check above and this call.
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Nothing to do but skip the alert.
        }
    }
}
