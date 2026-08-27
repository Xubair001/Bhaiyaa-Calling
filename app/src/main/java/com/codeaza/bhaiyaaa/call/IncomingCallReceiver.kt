package com.codeaza.bhaiyaaa.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Listens for the system's protected PHONE_STATE broadcast (this is the same
 * mechanism most caller-ID/call-blocker apps use - it does not require the
 * default-dialer or call-screening role). When a call starts ringing, it
 * checks the number against the local VIP list and fires the alert if matched.
 */
class IncomingCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state != TelephonyManager.EXTRA_STATE_RINGING) return

        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: return
        val normalized = incomingNumber.filter { it.isDigit() || it == '+' }
        if (normalized.isBlank()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context.applicationContext)
                val contact = db.contactDao().findByPhoneNumber(normalized)
                if (contact != null && contact.vipLevel != "NONE") {
                    CallAlertManager.triggerVipAlert(context.applicationContext, contact.vipLevel)
                    VipNotifier.notifyVipCall(context.applicationContext, contact.name, contact.vipLevel)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
