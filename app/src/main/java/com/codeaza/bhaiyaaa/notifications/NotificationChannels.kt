package com.codeaza.bhaiyaaa.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
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

    /**
     * Whether the user has given BHAIYAAA permission to override Do Not Disturb.
     *
     * This is not a runtime permission and cannot be requested with a dialog -
     * the user grants it in a dedicated system settings screen, so the UI links
     * them there instead of pretending the toggle works on its own.
     */
    fun hasDndAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching { manager.isNotificationPolicyAccessGranted }.getOrDefault(false)
    }

    fun dndAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the system page for one channel, where sound and DND are user-owned. */
    fun channelSettingsIntent(context: Context, channelId: String): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Actually applies "ring through Do Not Disturb" to a tier's channel.
     *
     * Android only honours this on the channel, never on an individual
     * notification, and only when the app holds notification-policy access.
     *
     * @return true if it was applied; false means the user still needs to grant
     *   DND access, and the caller must say so rather than silently no-op.
     */
    fun setBypassDnd(context: Context, level: VipLevel, enabled: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (enabled && !hasDndAccess(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val id = channelFor(level)
        return runCatching {
            val existing = manager.getNotificationChannel(id) ?: return@runCatching false
            val updated = NotificationChannel(id, existing.name, existing.importance).apply {
                description = existing.description
                enableVibration(existing.shouldVibrate())
                setShowBadge(existing.canShowBadge())
                setBypassDnd(enabled)
            }
            manager.createNotificationChannel(updated)
            manager.getNotificationChannel(id)?.canBypassDnd() == enabled
        }.getOrDefault(false)
    }

    fun canBypassDnd(context: Context, level: VipLevel): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching {
            manager.getNotificationChannel(channelFor(level))?.canBypassDnd() == true
        }.getOrDefault(false)
    }

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
