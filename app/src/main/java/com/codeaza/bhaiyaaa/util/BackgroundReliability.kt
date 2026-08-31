package com.codeaza.bhaiyaaa.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * The two settings that decide whether Sukoon works with the app closed.
 *
 * Neither can be granted from code, and neither is a normal runtime permission,
 * so the only honest thing an app can do is detect them and send the user to
 * the right screen.
 *
 * Battery optimisation matters twice over. It stops the system deferring the
 * app, and - less obviously - "battery optimisations disabled" is on Android's
 * short list of exemptions that permit starting a foreground service from the
 * background. ACTION_PHONE_STATE_CHANGED is not on that list, so without the
 * exemption the alert service cannot start at all when a call arrives.
 *
 * Autostart is a Xiaomi/MIUI invention with no AOSP equivalent. On those
 * devices a manifest broadcast receiver simply never fires once the app is
 * closed unless autostart is allowed - no amount of code changes that.
 */
object BackgroundReliability {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return runCatching {
            power.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)
    }

    /**
     * The direct request dialog. Falls back to the settings list if the device
     * blocks the direct intent, which some OEM builds do.
     */
    fun batteryOptimizationIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("BatteryLife")
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Permissions.appSettingsIntent(context)
        }

    fun batteryOptimizationListIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** True on Xiaomi's Android skin, where autostart is a separate gate. */
    fun isMiui(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("xiaomi") ||
            manufacturer.contains("redmi") ||
            manufacturer.contains("poco")
    }

    /** True for the other skins known to police background apps this way. */
    fun hasAggressiveBackgroundPolicy(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return isMiui() ||
            manufacturer.contains("oppo") ||
            manufacturer.contains("vivo") ||
            manufacturer.contains("realme") ||
            manufacturer.contains("oneplus") ||
            manufacturer.contains("huawei") ||
            manufacturer.contains("honor")
    }

    /**
     * The OEM's own autostart screen, where one exists.
     *
     * These are undocumented internal activities that get renamed between
     * versions, so every candidate is tried and the caller falls back to the
     * app's settings page. Never assume one of these resolves.
     */
    fun autostartIntent(context: Context): Intent? {
        val candidates = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity"
        )
        for ((pkg, cls) in candidates) {
            val intent = Intent()
                .setComponent(ComponentName(pkg, cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) return intent
        }
        return null
    }

    /** Plain-language instruction for this device, when there is no intent to fire. */
    fun autostartInstruction(): String = when {
        isMiui() ->
            "Open Security → Permissions → Autostart and switch Sukoon on. " +
                "Without it, this phone stops the app hearing incoming calls once it's closed."
        hasAggressiveBackgroundPolicy() ->
            "Open your phone's battery or app-management settings and allow Sukoon to " +
                "start in the background."
        else ->
            "Allow Sukoon to run in the background in your phone's battery settings."
    }
}
