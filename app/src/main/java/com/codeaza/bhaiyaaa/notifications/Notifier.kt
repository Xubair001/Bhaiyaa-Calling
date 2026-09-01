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
import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.service.AdhanService
import com.codeaza.bhaiyaaa.service.ReminderActionReceiver

/**
 * All notification posting goes through here.
 *
 * Three rules hold everywhere in this file:
 *  - POST_NOTIFICATIONS is checked before every post, so a denied permission is
 *    a quiet no-op rather than a SecurityException.
 *  - No phone number, note or memory text is ever put in a log statement.
 *  - Anything the user wrote - a reminder's text, a caller's name - is posted
 *    with `VISIBILITY_PRIVATE` and a redacted public version, so it does not
 *    sit on a locked screen for whoever is nearby to read. That is the same
 *    promise the rest of the app makes about this data, and a notification is
 *    the one place it would otherwise leak by default.
 *
 * The visual treatment is deliberately consistent: every notification carries
 * the app accent, a `BigTextStyle` so long text expands rather than being
 * truncated at one line, and a `subText` naming which kind of alert it is.
 * Icons differ per kind, because a status bar icon is often all the user sees.
 */
object Notifier {

    private const val VIP_NOTIFICATION_BASE = 1000
    private const val REMINDER_NOTIFICATION_BASE = 2000
    private const val MISSED_NOTIFICATION_ID = 3001
    private const val TEST_NOTIFICATION_ID = 4001
    private const val PRAYER_NOTIFICATION_ID = 5002

    /**
     * Grouping key for reminders.
     *
     * Without it, five due reminders are five separate heads-up cards that
     * bury everything else in the shade. With it the platform collapses them
     * under one summary and the user expands if they want the detail.
     */
    private const val GROUP_REMINDERS = "com.codeaza.bhaiyaaa.REMINDERS"
    private const val REMINDER_SUMMARY_ID = 2999

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------------ calls

    fun notifyVipCall(
        context: Context,
        contactName: String,
        rawNumber: String,
        level: VipLevel,
        message: String
    ) {
        if (!canPost(context)) return

        val title = when (level) {
            VipLevel.EMERGENCY -> "Emergency caller"
            VipLevel.SUPER_VIP -> "Super VIP calling"
            else -> "VIP calling"
        }

        val builder = base(context, NotificationChannels.channelFor(level))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSubText(level.label)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Whether this rings through Do Not Disturb is decided by the
            // channel (see NotificationChannels.setBypassDnd), not here - a
            // notification cannot override DND on its own.
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOnlyAlertOnce(false)
            .private(
                context,
                channelId = NotificationChannels.channelFor(level),
                publicTitle = title,
                publicText = "Someone on your ${level.label} list is calling"
            )

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
        val text = "This is how a ${level.label} call will reach you."
        val builder = base(context, NotificationChannels.channelFor(level))
            .setContentTitle("${level.label} test alert")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText("Test")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            // Clears itself so a test never lingers in the shade.
            .setTimeoutAfter(10_000)
        post(context, TEST_NOTIFICATION_ID, builder.build())
    }

    fun notifyMissedImportant(context: Context, contactName: String, count: Int) {
        if (!canPost(context)) return
        val text = if (count > 1) "$contactName called $count times"
        else "$contactName tried to reach you"
        val builder = base(context, NotificationChannels.MISSED)
            .setContentTitle("Missed an important call")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText("Missed call")
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .private(
                context,
                channelId = NotificationChannels.MISSED,
                publicTitle = "Missed an important call",
                publicText = "Open Sukoon to see who"
            )
        post(context, MISSED_NOTIFICATION_ID, builder.build())
    }

    // -------------------------------------------------------------- reminders

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
        val builder = base(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle("Reminder")
            .setContentText(text)
            // Long reminder text expands rather than being cut at one line.
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText("Reminder")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(GROUP_REMINDERS)
            .addAction(
                R.drawable.ic_notification_reminder,
                "Snooze 10 min",
                reminderAction(context, ReminderActionReceiver.ACTION_SNOOZE, reminderId)
            )
            .addAction(
                R.drawable.ic_notification_reminder,
                "Done",
                reminderAction(context, ReminderActionReceiver.ACTION_DONE, reminderId)
            )
            .private(
                context,
                channelId = NotificationChannels.REMINDERS,
                publicTitle = "Reminder",
                publicText = "Open Sukoon to see it"
            )
        post(context, REMINDER_NOTIFICATION_BASE + reminderId.toInt(), builder.build())
        postReminderSummary(context)
    }

    /**
     * The group summary.
     *
     * Required on Android 7+ for grouping to actually collapse: without a
     * summary the platform shows every child as a separate card and the group
     * key does nothing.
     */
    private fun postReminderSummary(context: Context) {
        val builder = base(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle("Reminders")
            .setSubText("Reminder")
            .setGroup(GROUP_REMINDERS)
            .setGroupSummary(true)
            .setStyle(NotificationCompat.InboxStyle().setSummaryText("Reminders due"))
        post(context, REMINDER_SUMMARY_ID, builder.build())
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

    // ----------------------------------------------------------------- prayer

    /**
     * The card shown while the adhan is sounding, and the one the foreground
     * service is started with.
     *
     * Carries a Stop action, because audio that started on its own must always
     * have a visible way to stop it - a user who did not expect the sound
     * should not have to find a settings screen to end it.
     */
    fun buildAdhanPlayingNotification(context: Context, prayer: Prayer): Notification =
        base(context, NotificationChannels.PRAYER)
            .setSmallIcon(R.drawable.ic_notification_prayer)
            .setContentTitle(prayer.label)
            .setContentText("It is time for ${prayer.label}.")
            .setSubText("Prayer time")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            // Nothing personal here, so it can be read on a locked screen -
            // and being able to stop it from there is the point.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.ic_notification_prayer,
                "Stop",
                PendingIntent.getService(
                    context,
                    PRAYER_NOTIFICATION_ID,
                    AdhanService.stopIntent(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    /**
     * The quiet card left behind after the adhan.
     *
     * Separate from the foreground-service notification so that stopping the
     * service does not also erase the fact that the prayer has come in.
     */
    fun notifyPrayerTime(context: Context, prayer: Prayer, soundLabel: String) {
        if (!canPost(context)) return
        val builder = base(context, NotificationChannels.PRAYER)
            .setSmallIcon(R.drawable.ic_notification_prayer)
            .setContentTitle(prayer.label)
            .setContentText("It is time for ${prayer.label}.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (soundLabel.isBlank()) "It is time for ${prayer.label}."
                    else "It is time for ${prayer.label}. Playing $soundLabel."
                )
            )
            .setSubText("Prayer time")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Goes away on its own: a prayer time is news for a few minutes,
            // not something to be dismissed by hand every day.
            .setTimeoutAfter(PRAYER_NOTIFICATION_TIMEOUT_MILLIS)
        post(context, PRAYER_NOTIFICATION_ID, builder.build())
    }

    private const val PRAYER_NOTIFICATION_TIMEOUT_MILLIS = 20 * 60 * 1000L

    // ------------------------------------------------------------------ plumbing

    /**
     * The shared shape of every notification Sukoon posts.
     *
     * Having one builder rather than seven near-copies is what keeps the shade
     * looking like one app: same accent, same tap target, same auto-cancel.
     */
    private fun base(context: Context, channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setContentIntent(openApp(context))

    /**
     * Hides the content on a locked screen, replacing it with a version that
     * says a notification arrived but not what it says.
     */
    private fun NotificationCompat.Builder.private(
        context: Context,
        channelId: String,
        publicTitle: String,
        publicText: String
    ): NotificationCompat.Builder = apply {
        setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        setPublicVersion(
            // Same channel as the notification it stands in for: the platform
            // takes the parent's, and naming a different one here would only
            // mislead the next person to read it.
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.notification_accent))
                .setContentTitle(publicTitle)
                .setContentText(publicText)
                .build()
        )
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
