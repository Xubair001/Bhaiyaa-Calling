package com.codeaza.bhaiyaaa.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.codeaza.bhaiyaaa.MainActivity
import com.codeaza.bhaiyaaa.R
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.prefs.SettingsRepository
import com.codeaza.bhaiyaaa.domain.model.SilenceWindow
import com.codeaza.bhaiyaaa.prayer.SilencePlan
import com.codeaza.bhaiyaaa.util.Formatting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

/**
 * A home-screen widget showing when the phone next goes quiet.
 *
 * ## Why plain RemoteViews
 *
 * Glance would bring the Compose-Glance runtime into the APK to draw four
 * TextViews. A widget is the one place where that cost is paid by a process
 * that has to start before the launcher can finish drawing. Four TextViews and
 * a shape drawable add a few kilobytes and no dependency at all.
 *
 * ## Why it never polls
 *
 * `updatePeriodMillis` is zero. The platform's own widget refresh has a
 * half-hour floor and wakes the device to run it, which for something that
 * changes five times a day is almost all waste. Instead [refresh] is called
 * from the places that already know the answer has changed - the prayer alarms
 * as they fire, a settings change, a reboot - so the widget is exact and costs
 * no wake-ups of its own.
 */
class NextPrayerWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Added to the home screen, or restored after a restart. Every other
        // update arrives here too, via refresh().
        render(context, appWidgetManager, appWidgetIds)
    }

    private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val next = withTimeoutOrNull(WORK_TIMEOUT_MS) { nextWindow(appContext) }
                val views = buildViews(appContext, next)
                ids.forEach { id -> manager.updateAppWidget(id, views) }
            } catch (e: Exception) {
                // A widget update must never crash the app. Leaving the last
                // rendering in place is the correct failure.
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * The next quiet window, today or tomorrow.
     *
     * Two days, for the same reason the scheduler plans two: asked at eleven at
     * night, "today" has nothing left in it and the honest answer is tomorrow's
     * Fajr. Read through [SilencePlan], so the widget can never disagree with
     * the app about when a prayer is.
     */
    private suspend fun nextWindow(context: Context): SilenceWindow? {
        val now = System.currentTimeMillis()
        val settings = SettingsRepository(context).settings.first().prayer
        val db = AppDatabase.getInstance(context)
        val prayers = db.prayerDao().allOnce()
        val schedules = db.silenceScheduleDao().allOnce()

        val windows = listOf(0, 1).flatMap { dayOffset ->
            val day = Calendar.getInstance(settings.zone).apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }.timeInMillis
            SilencePlan.windowsForDay(settings, prayers, schedules, day, settings.zone)
        }

        // A window running right now is more useful than the one after it.
        return SilencePlan.activeWindow(windows, now) ?: SilencePlan.nextWindow(windows, now)
    }

    private fun buildViews(context: Context, window: SilenceWindow?): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_next_prayer).apply {
            val now = System.currentTimeMillis()

            if (window == null) {
                setTextViewText(R.id.widget_label, context.getString(R.string.widget_next_prayer))
                setTextViewText(R.id.widget_prayer, "—")
                setTextViewText(R.id.widget_time, "")
                setTextViewText(R.id.widget_detail, context.getString(R.string.widget_no_times))
            } else {
                val running = window.containsNow(now)
                setTextViewText(
                    R.id.widget_label,
                    if (running) "QUIET NOW" else context.getString(R.string.widget_next_prayer)
                )
                setTextViewText(R.id.widget_prayer, window.label)
                setTextViewText(R.id.widget_time, Formatting.time(window.anchorMillis))
                setTextViewText(
                    R.id.widget_detail,
                    if (running) {
                        "Until ${Formatting.time(window.endMillis)}"
                    } else {
                        "Quiet ${Formatting.time(window.startMillis)} – " +
                            Formatting.time(window.endMillis)
                    }
                )
            }

            // The whole widget opens the app. A widget with regions that do
            // different things is a widget people tap wrong.
            val open = openApp(context)
            setOnClickPendingIntent(R.id.widget_label, open)
            setOnClickPendingIntent(R.id.widget_prayer, open)
            setOnClickPendingIntent(R.id.widget_time, open)
            setOnClickPendingIntent(R.id.widget_detail, open)
        }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val WORK_TIMEOUT_MS = 5_000L
        private const val REQUEST_OPEN = 7001

        /**
         * Redraws every placed widget.
         *
         * Safe to call from anywhere and cheap when no widget exists - the
         * manager returns an empty array and nothing else runs. Called from the
         * prayer scheduler, so the widget updates exactly when the answer
         * changes and never on a timer.
         */
        fun refresh(context: Context) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, NextPrayerWidget::class.java)
                )
                if (ids.isEmpty()) return
                context.sendBroadcast(
                    Intent(context, NextPrayerWidget::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                )
            }
        }
    }
}
