package com.codeaza.bhaiyaaa.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.codeaza.bhaiyaaa.R
import com.codeaza.bhaiyaaa.domain.model.VipLevel

/**
 * One channel per VIP tier plus reminders and missed-call nudges.
 *
 * Separate channels matter on Android 8+: importance, sound and vibration are
 * owned by the channel, not the notification, and a user who wants Emergency
 * alerts loud but ordinary VIP alerts quiet can only express that if the tiers
 * are separate channels they can tune in system settings.
 */
object NotificationChannels {

    const val VIP = "vip_calls"
    const val SUPER_VIP = "super_vip_calls"
    const val EMERGENCY = "emergency_calls"
    const val REMINDERS = "reminders"
    const val MISSED = "missed_important"

    fun channelFor(level: VipLevel): String = when (level) {
        VipLevel.EMERGENCY -> EMERGENCY
        VipLevel.SUPER_VIP -> SUPER_VIP
        else -> VIP
    }

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        create(context, manager, VIP, R.string.channel_vip_name, R.string.channel_vip_desc, NotificationManager.IMPORTANCE_HIGH)
        create(context, manager, SUPER_VIP, R.string.channel_super_vip_name, R.string.channel_super_vip_desc, NotificationManager.IMPORTANCE_HIGH)
        create(context, manager, EMERGENCY, R.string.channel_emergency_name, R.string.channel_emergency_desc, NotificationManager.IMPORTANCE_HIGH)
        create(context, manager, REMINDERS, R.string.channel_reminders_name, R.string.channel_reminders_desc, NotificationManager.IMPORTANCE_DEFAULT)
        create(context, manager, MISSED, R.string.channel_missed_name, R.string.channel_missed_desc, NotificationManager.IMPORTANCE_DEFAULT)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun create(
        context: Context,
        manager: NotificationManager,
        id: String,
        nameRes: Int,
        descRes: Int,
        importance: Int
    ) {
        // Re-creating an existing channel is a no-op, so this is safe to call on
        // every launch - and importantly it does NOT override user changes.
        val channel = NotificationChannel(id, context.getString(nameRes), importance).apply {
            description = context.getString(descRes)
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }
}
