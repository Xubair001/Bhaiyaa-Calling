package com.codeaza.bhaiyaaa.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.notifications.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Posts a reminder notification when its alarm fires. */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(5_000L) {
                    val dao = AppDatabase.getInstance(appContext).reminderDao()
                    val reminder = dao.findById(reminderId) ?: return@withTimeoutOrNull
                    // Don't nag about something already ticked off, and don't
                    // double-post if the alarm somehow fires twice.
                    if (reminder.isDone || reminder.notified) return@withTimeoutOrNull
                    Notifier.notifyReminder(appContext, reminder.id, reminder.text)
                    dao.markNotified(reminder.id)
                }
            } catch (e: Exception) {
                // Never crash out of an alarm broadcast.
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.codeaza.bhaiyaaa.action.FIRE_REMINDER"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}
