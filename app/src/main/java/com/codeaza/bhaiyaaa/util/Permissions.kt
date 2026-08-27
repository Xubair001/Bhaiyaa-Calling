package com.codeaza.bhaiyaaa.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.codeaza.bhaiyaaa.R

/**
 * Permissions, grouped by what they unlock rather than requested in one blast.
 *
 * The brief is explicit (§22): no asking for everything on first launch. So
 * contacts + call log + phone state are requested together on the Home screen
 * because the dashboard is meaningless without them, notifications is asked at
 * the point VIP alerts are switched on, and the microphone is only ever asked
 * for when the user taps the mic in Assistant.
 */
enum class PermissionGroup(
    val permissions: List<String>,
    val titleRes: Int,
    val whyRes: Int
) {
    CONTACTS(
        listOf(Manifest.permission.READ_CONTACTS),
        R.string.perm_contacts_title,
        R.string.perm_contacts_why
    ),
    CALL_LOG(
        listOf(Manifest.permission.READ_CALL_LOG),
        R.string.perm_call_log_title,
        R.string.perm_call_log_why
    ),
    PHONE_STATE(
        listOf(Manifest.permission.READ_PHONE_STATE),
        R.string.perm_phone_state_title,
        R.string.perm_phone_state_why
    ),
    NOTIFICATIONS(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Pre-13 notifications need no runtime grant, so this group is
            // always satisfied and never prompts.
            emptyList()
        },
        R.string.perm_notifications_title,
        R.string.perm_notifications_why
    ),
    MICROPHONE(
        listOf(Manifest.permission.RECORD_AUDIO),
        R.string.perm_mic_title,
        R.string.perm_mic_why
    );

    fun isGranted(context: Context): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

object Permissions {

    /** Everything the dashboard, call list and VIP alerts need to be useful. */
    val CORE = listOf(
        PermissionGroup.CONTACTS,
        PermissionGroup.CALL_LOG,
        PermissionGroup.PHONE_STATE,
        PermissionGroup.NOTIFICATIONS
    )

    fun coreRequestArray(): Array<String> =
        CORE.flatMap { it.permissions }.distinct().toTypedArray()

    fun allCoreGranted(context: Context): Boolean = CORE.all { it.isGranted(context) }

    fun missingCore(context: Context): List<PermissionGroup> = CORE.filterNot { it.isGranted(context) }

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** For the "permanently denied" path, where a re-request no longer prompts. */
    fun appSettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
}
