package com.codeaza.bhaiyaaa.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import com.codeaza.bhaiyaaa.ai.DefaultPhrasebook
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.prefs.SettingsRepository
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.domain.usecase.AlertDecision
import com.codeaza.bhaiyaaa.domain.usecase.AlertOutcome
import com.codeaza.bhaiyaaa.notifications.Notifier
import com.codeaza.bhaiyaaa.prayer.PrayerSilence
import com.codeaza.bhaiyaaa.util.PhoneNumbers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reacts to the system PHONE_STATE broadcast.
 *
 * This is the same mechanism caller-ID and call-blocker apps use, and it needs
 * neither the default-dialer nor the call-screening role - Sukoon deliberately
 * does not take over the dialer or interfere with the native call UI (brief §37).
 *
 * Known platform limits, surfaced to the user in the app rather than hidden:
 *  - EXTRA_INCOMING_NUMBER is only delivered when READ_CALL_LOG is granted.
 *  - Several OEMs (Xiaomi, Oppo, aggressive Samsung battery modes) delay or drop
 *    this broadcast for background apps, which the Privacy Center explains.
 */
class IncomingCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val appContext = context.applicationContext

        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                @Suppress("DEPRECATION")
                val incoming = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                handleRinging(appContext, incoming)
            }
            // Answered or ended: stop any flashing immediately so the torch is
            // never left on after the call is dealt with.
            TelephonyManager.EXTRA_STATE_OFFHOOK,
            TelephonyManager.EXTRA_STATE_IDLE -> {
                CallAlertManager.cancelAlerts(appContext)
                runCatching { appContext.startService(VipAlertService.stopIntent(appContext)) }
            }
        }
    }

    private fun handleRinging(context: Context, rawNumber: String?) {
        val matchKey = PhoneNumbers.matchKey(rawNumber)
        if (matchKey.isBlank()) return

        val pendingResult = goAsync()
        // A broadcast receiver gets roughly 10 seconds; the timeout keeps us well
        // inside that even if the database is slow to open.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(WORK_TIMEOUT_MS) {
                    val db = AppDatabase.getInstance(context)
                    val contact = db.contactDao().findByMatchKey(matchKey) ?: return@withTimeoutOrNull
                    val level = VipLevel.from(contact.vipLevel)
                    val settings = SettingsRepository(context).settings.first()
                    val rule = db.notificationRuleDao().findForLevel(level.storageValue)

                    // Every precedence rule lives in AlertDecision, so this
                    // receiver only has to act on the answer.
                    val outcome = AlertDecision.evaluate(
                        contact = contact,
                        rule = rule,
                        alertsGloballyEnabled = settings.notificationsEnabled,
                        prayerSilenceActive = PrayerSilence.isActiveNow(context)
                    )
                    if (outcome != AlertOutcome.ALERT || rule == null) return@withTimeoutOrNull

                    val message = DefaultPhrasebook(settings.personality).vipCalling(contact.name)

                    // Hand off to a foreground service, which keeps the process
                    // alive for the whole ring. Doing this work here would stop
                    // the moment goAsync() finished and the process was reaped.
                    val started = startAlertService(
                        context, level, contact.name, contact.phoneNumber, message,
                        settings.flashlightEnabled
                    )

                    if (!started) {
                        // The service was refused - almost always because
                        // battery optimisation is on, which Android requires off
                        // to allow a background service start. Run inline so the
                        // user still gets something, even if it may be cut short.
                        CallAlertManager.triggerAlert(
                            context = context,
                            rule = rule,
                            flashlightGloballyEnabled = settings.flashlightEnabled
                        )
                        if (rule.notificationsEnabled) {
                            Notifier.notifyVipCall(
                                context = context,
                                contactName = contact.name,
                                rawNumber = contact.phoneNumber,
                                level = level,
                                message = message
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // A failure here must never surface as an ANR or crash on an
                // incoming call. The user still gets their normal ringtone.
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * @return false when the platform refused the start, so the caller can fall
     *   back rather than the alert silently not happening.
     */
    private fun startAlertService(
        context: Context,
        level: VipLevel,
        name: String,
        number: String,
        message: String,
        flashlightEnabled: Boolean
    ): Boolean = try {
        val intent = VipAlertService.alertIntent(
            context, level, name, number, message, flashlightEnabled
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        true
    } catch (t: Throwable) {
        // ForegroundServiceStartNotAllowedException on Android 12+, or an OEM
        // refusing outright. Throwable because that exception type only exists
        // on newer API levels.
        false
    }

    private companion object {
        const val WORK_TIMEOUT_MS = 5_000L
    }
}
