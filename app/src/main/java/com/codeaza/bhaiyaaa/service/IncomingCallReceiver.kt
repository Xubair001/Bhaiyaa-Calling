package com.codeaza.bhaiyaaa.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.codeaza.bhaiyaaa.ai.DefaultPhrasebook
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.prefs.SettingsRepository
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.notifications.Notifier
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
 * neither the default-dialer nor the call-screening role - BHAIYAAA deliberately
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
            TelephonyManager.EXTRA_STATE_IDLE -> CallAlertManager.cancelAlerts(appContext)
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
                    if (!level.isVip) return@withTimeoutOrNull
                    if (!contact.notificationsEnabled) return@withTimeoutOrNull

                    val settings = SettingsRepository(context).settings.first()
                    if (!settings.notificationsEnabled) return@withTimeoutOrNull

                    val rule = db.notificationRuleDao().findForLevel(level.storageValue)
                        ?: return@withTimeoutOrNull

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
                            message = DefaultPhrasebook(settings.personality).vipCalling(contact.name)
                        )
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

    private companion object {
        const val WORK_TIMEOUT_MS = 5_000L
    }
}
