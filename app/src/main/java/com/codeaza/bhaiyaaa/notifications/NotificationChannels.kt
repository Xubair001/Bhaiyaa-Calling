package com.codeaza.bhaiyaaa.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
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

    /**
     * Channel ids carry a version suffix.
     *
     * Android freezes a channel's sound, importance and vibration the moment it
     * is created - an app can never change them afterwards, by design, because
     * they belong to the user from then on. The only way to ship a corrected
     * default is to publish a new channel and retire the old one, which is what
     * the suffix is for. v2 moved the VIP tiers from the default notification
     * ping to the ringtone, so an alert sounds like a call rather than an email.
     */
    const val VIP = "vip_calls_v2"
    const val SUPER_VIP = "super_vip_calls_v2"
    const val EMERGENCY = "emergency_calls_v2"
    const val REMINDERS = "reminders"
    const val MISSED = "missed_important"

    /**
     * Prayer times and the adhan.
     *
     * Deliberately silent at the channel level. The adhan is played by
     * [com.codeaza.bhaiyaaa.service.AdhanService] on the alarm stream, and a
     * channel sound here would produce a notification ping over the top of it.
     * It is also the reason this channel is separate rather than reusing
     * REMINDERS: a user who wants reminder pings but no notification sound at
     * prayer time can only say so if the two are different channels.
     */
    const val PRAYER = "prayer_times"

    /** Superseded ids, deleted on launch so they stop cluttering system settings. */
    private val LEGACY_CHANNEL_IDS = listOf("vip_calls", "super_vip_calls", "emergency_calls")

    /**
     * Whether the user has given Sukoon permission to override Do Not Disturb.
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
            // Report what the platform actually did, not what we asked for.
            // Some OEM builds accept the call and ignore the value.
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

    /**
     * Creates the channels, carrying the user's Do Not Disturb choice forward.
     *
     * @param bypassByChannelId the stored preference per channel. Anything not
     *   listed keeps whatever the channel already has.
     *
     * This runs on every launch, which is why [bypassByChannelId] matters. It is
     * commonly believed that re-creating an existing channel is a harmless
     * no-op - it is, for name, importance and sound. It is NOT for bypassDnd:
     * when the app holds notification-policy access the platform applies the
     * value from the supplied channel, so passing a freshly built channel with
     * the default `false` silently switched the user's setting back off on every
     * single app start.
     */
    fun createAll(context: Context, bypassByChannelId: Map<String, Boolean> = emptyMap()) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // Retire superseded channels first, otherwise the user sees two "VIP
        // calls" entries in system settings and cannot tell which is live.
        LEGACY_CHANNEL_IDS.forEach { old ->
            runCatching { manager.deleteNotificationChannel(old) }
        }

        create(context, manager, VIP, R.string.channel_vip_name, R.string.channel_vip_desc, NotificationManager.IMPORTANCE_HIGH, bypassByChannelId, ringtone = true)
        create(context, manager, SUPER_VIP, R.string.channel_super_vip_name, R.string.channel_super_vip_desc, NotificationManager.IMPORTANCE_HIGH, bypassByChannelId, ringtone = true)
        create(context, manager, EMERGENCY, R.string.channel_emergency_name, R.string.channel_emergency_desc, NotificationManager.IMPORTANCE_HIGH, bypassByChannelId, ringtone = true)
        create(context, manager, REMINDERS, R.string.channel_reminders_name, R.string.channel_reminders_desc, NotificationManager.IMPORTANCE_DEFAULT, bypassByChannelId)
        create(context, manager, MISSED, R.string.channel_missed_name, R.string.channel_missed_desc, NotificationManager.IMPORTANCE_DEFAULT, bypassByChannelId)
        create(context, manager, PRAYER, R.string.channel_prayer_name, R.string.channel_prayer_desc, NotificationManager.IMPORTANCE_DEFAULT, bypassByChannelId, silent = true)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun create(
        context: Context,
        manager: NotificationManager,
        id: String,
        nameRes: Int,
        descRes: Int,
        importance: Int,
        bypassByChannelId: Map<String, Boolean>,
        ringtone: Boolean = false,
        silent: Boolean = false
    ) {
        val existing = manager.getNotificationChannel(id)
        // Stored preference wins; otherwise keep whatever the channel already
        // has, so a launch never quietly changes the user's setting.
        val bypass = bypassByChannelId[id] ?: (existing?.canBypassDnd() ?: false)

        val channel = NotificationChannel(id, context.getString(nameRes), importance).apply {
            description = context.getString(descRes)
            enableVibration(!silent)
            setShowBadge(true)
            setBypassDnd(bypass)

            if (silent) {
                // Null sound, not a quiet one: the audio for this channel comes
                // from elsewhere, and a channel tone would play over it.
                setSound(null, null)
            }

            if (ringtone) {
                // A VIP alert should sound like a call, not like an email. The
                // default *notification* tone is a short ping; the ringtone with
                // USAGE_NOTIFICATION_RINGTONE is what the system treats as
                // call-class audio, which is also what DND exemptions key off.
                //
                // Android makes sound immutable once a channel exists, so this
                // applies on first creation. For a channel that already exists,
                // the per-tier "Ringtone & sound" link sends the user to the
                // system page, which is the only thing that can change it.
                runCatching {
                    setSound(
                        Settings.System.DEFAULT_RINGTONE_URI,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
            }
        }
        manager.createNotificationChannel(channel)
    }
}
