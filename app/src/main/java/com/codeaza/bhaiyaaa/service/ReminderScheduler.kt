package com.codeaza.bhaiyaaa.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Schedules reminder alarms.
 *
 * Uses setAndAllowWhileIdle rather than setExactAndAllowWhileIdle on purpose.
 * Exact alarms need SCHEDULE_EXACT_ALARM on Android 12+, which is a heavyweight
 * permission the platform reserves for alarm-clock-grade apps - and asking for
 * it here would be hard to justify. The trade-off, stated plainly in the UI, is
 * that a reminder can land a few minutes late if the phone is deep in Doze.
 */
object ReminderScheduler {

    fun schedule(context: Context, reminderId: Long, dueAt: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntent(context, reminderId) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pending)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, dueAt, pending)
            }
        } catch (e: SecurityException) {
            // Some OEM builds restrict alarms for background apps; the reminder
            // still exists in the list, it just won't buzz.
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        pendingIntent(context, reminderId, mutableFlagOnly = true)?.let { alarmManager.cancel(it) }
    }

    private fun pendingIntent(
        context: Context,
        reminderId: Long,
        mutableFlagOnly: Boolean = false
    ): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_FIRE
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, reminderId.toInt(), intent, flags)
    }
}
