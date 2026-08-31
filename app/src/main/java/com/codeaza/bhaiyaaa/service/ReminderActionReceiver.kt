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

/**
 * Handles Snooze and Done from a reminder notification.
 *
 * These run without the app being open, so they go straight to the DAO rather
 * than through the ViewModel - there may not be one alive.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_SNOOZE && action != ACTION_DONE) return
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0) return

        val appContext = context.applicationContext
        // Dismiss straight away. Waiting for the database means the alert sits
        // there looking unresponsive for as long as the write takes.
        Notifier.cancelReminder(appContext, reminderId)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(5_000L) {
                    val dao = AppDatabase.getInstance(appContext).reminderDao()
                    when (action) {
                        ACTION_DONE -> {
                            dao.setDone(reminderId, true)
                            ReminderScheduler.cancel(appContext, reminderId)
                        }
                        ACTION_SNOOZE -> {
                            val until = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
                            // rescheduleTo clears notified, so the alarm below
                            // is allowed to post when it lands.
                            dao.rescheduleTo(reminderId, until)
                            ReminderScheduler.schedule(appContext, reminderId, until)
                        }
                    }
                }
            } catch (e: Exception) {
                // A notification action must never crash the app.
            } finally {
                pendingResult.finish()
            }
        }
    }


    companion object {
        const val ACTION_SNOOZE = "com.codeaza.bhaiyaaa.action.SNOOZE_REMINDER"
        const val ACTION_DONE = "com.codeaza.bhaiyaaa.action.COMPLETE_REMINDER"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val SNOOZE_MINUTES = 10
    }
}
