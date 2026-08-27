package com.codeaza.bhaiyaaa.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.db.entity.NotificationRuleEntity
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.notifications.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns a VIP alert for as long as it lasts.
 *
 * The alert used to run in a coroutine launched straight from the broadcast
 * receiver. That works only while something else is keeping the process alive -
 * which, in practice, meant only while the app was open. Once goAsync() finishes
 * (about ten seconds) a backgrounded process can be killed at any moment, taking
 * the ringtone and the flashing torch with it. A foreground service is what
 * actually holds the process up for the thirty to forty-five seconds a call
 * rings.
 *
 * A wake lock is taken too, because the screen being off is the exact case this
 * has to work in - and a sleeping CPU will not run the flash loop.
 *
 * Typed shortService: this is short, time-critical work that cannot be deferred,
 * which is precisely what that type is for, and unlike phoneCall it does not
 * require the app to be a dialer.
 */
class VipAlertService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }

        val level = VipLevel.from(intent?.getStringExtra(EXTRA_LEVEL))
        val name = intent?.getStringExtra(EXTRA_NAME).orEmpty()
        val number = intent?.getStringExtra(EXTRA_NUMBER).orEmpty()
        val message = intent?.getStringExtra(EXTRA_MESSAGE).orEmpty()
        val flashlightEnabled = intent?.getBooleanExtra(EXTRA_FLASHLIGHT, true) ?: true

        startInForeground(level, name, message)
        acquireWakeLock()

        scope.launch {
            val rule = runCatching {
                AppDatabase.getInstance(applicationContext)
                    .notificationRuleDao()
                    .findForLevel(level.storageValue)
            }.getOrNull() ?: NotificationRuleEntity(level.storageValue)

            CallAlertManager.triggerAlert(
                context = applicationContext,
                rule = rule,
                flashlightGloballyEnabled = flashlightEnabled
            )

            if (rule.notificationsEnabled && message.isNotBlank()) {
                com.codeaza.bhaiyaaa.notifications.Notifier.notifyVipCall(
                    context = applicationContext,
                    contactName = name,
                    rawNumber = number,
                    level = level,
                    message = message
                )
            }

            // Backstop. The call ending normally stops this sooner; this is for
            // the case where that broadcast never arrives, which some OEM builds
            // manage. shortService must end well inside its own limit anyway.
            delay(MAX_ALERT_MILLIS)
            stopEverything()
        }

        return START_NOT_STICKY
    }

    private fun startInForeground(level: VipLevel, name: String, message: String) {
        val notification = NotificationCompat.Builder(
            this,
            NotificationChannels.channelFor(level)
        )
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(if (name.isBlank()) "${level.label} calling" else name)
            .setContentText(message.ifBlank { "${level.label} caller" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                )
            } else {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
            }
        }
    }

    private fun acquireWakeLock() {
        runCatching {
            val power = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = power.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "bhaiyaaa:vip-alert"
            ).apply {
                setReferenceCounted(false)
                // Timed, so a bug can never hold the CPU awake indefinitely.
                acquire(MAX_ALERT_MILLIS + 5_000L)
            }
        }
    }

    /** Android 14+ calls this if a shortService overruns; ending cleanly is required. */
    override fun onTimeout(startId: Int) {
        stopEverything()
    }

    private fun stopEverything() {
        runCatching { CallAlertManager.cancelAlerts(applicationContext) }
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { CallAlertManager.cancelAlerts(applicationContext) }
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        scope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 5001

        /** Comfortably longer than a ring cycle, well inside the shortService limit. */
        private const val MAX_ALERT_MILLIS = 45_000L

        const val ACTION_ALERT = "com.codeaza.bhaiyaaa.action.VIP_ALERT"
        const val ACTION_STOP = "com.codeaza.bhaiyaaa.action.VIP_ALERT_STOP"
        const val EXTRA_LEVEL = "level"
        const val EXTRA_NAME = "name"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_FLASHLIGHT = "flashlight"

        fun alertIntent(
            context: Context,
            level: VipLevel,
            name: String,
            number: String,
            message: String,
            flashlightEnabled: Boolean
        ): Intent = Intent(context, VipAlertService::class.java).apply {
            action = ACTION_ALERT
            putExtra(EXTRA_LEVEL, level.storageValue)
            putExtra(EXTRA_NAME, name)
            putExtra(EXTRA_NUMBER, number)
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_FLASHLIGHT, flashlightEnabled)
        }

        fun stopIntent(context: Context): Intent =
            Intent(context, VipAlertService::class.java).apply { action = ACTION_STOP }
    }
}
