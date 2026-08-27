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
            .setSmallIcon(android.R.drawable.sym_call_incoming)
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
            .setSmallIcon(android.R.drawable.sym_call_incoming)
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

    fun notifyReminder(context: Context, reminderId: Long, text: String) {
        if (!canPost(context)) return
        val builder = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Reminder")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
        post(context, REMINDER_NOTIFICATION_BASE + reminderId.toInt(), builder.build())
    }

    fun notifyMissedImportant(context: Context, contactName: String, count: Int) {
        if (!canPost(context)) return
        val text = if (count > 1) "$contactName called $count times" else "$contactName tried to reach you"
        val builder = NotificationCompat.Builder(context, NotificationChannels.MISSED)
            .setSmallIcon(android.R.drawable.sym_call_missed)
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
