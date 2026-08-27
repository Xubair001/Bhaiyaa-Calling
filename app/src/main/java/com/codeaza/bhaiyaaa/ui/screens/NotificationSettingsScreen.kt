package com.codeaza.bhaiyaaa.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.notifications.Notifier
import com.codeaza.bhaiyaaa.service.CallAlertManager
import com.codeaza.bhaiyaaa.ui.BhaiyaaaViewModel
import com.codeaza.bhaiyaaa.ui.components.InfoBanner
import com.codeaza.bhaiyaaa.ui.components.SettingsLinkRow
import com.codeaza.bhaiyaaa.ui.components.SettingsSectionHeader
import com.codeaza.bhaiyaaa.ui.components.SettingsSwitchRow

@Composable
fun NotificationSettingsScreen(viewModel: BhaiyaaaViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var canPost by remember { mutableStateOf(Notifier.canPost(context)) }
    val hasFlashlight = remember { CallAlertManager.hasFlashlight(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> canPost = granted }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (!canPost) {
            InfoBanner(
                text = "Notifications are blocked for BHAIYAAA, so VIP alerts can't reach you.",
                modifier = Modifier.padding(16.dp),
                actionLabel = "Allow",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }

        SettingsSectionHeader("Alerts")
        SettingsSwitchRow(
            title = "VIP call alerts",
            subtitle = "Vibration, flashlight and heads-up notifications when a VIP calls",
            checked = settings.notificationsEnabled,
            onCheckedChange = { viewModel.setNotificationsEnabled(it) }
        )
        SettingsSwitchRow(
            title = "Flashlight patterns",
            subtitle = if (hasFlashlight) {
                "Flash the torch when a VIP calls"
            } else {
                "This device has no flashlight"
            },
            checked = settings.flashlightEnabled && hasFlashlight,
            enabled = hasFlashlight,
            onCheckedChange = { viewModel.setFlashlightEnabled(it) }
        )
        SettingsSwitchRow(
            title = "Missed important call nudge",
            subtitle = "A quiet reminder when someone important couldn't reach you",
            checked = settings.missedCallNudgeEnabled,
            onCheckedChange = { viewModel.setMissedCallNudge(it) }
        )

        SettingsSectionHeader("Background")
        SettingsSwitchRow(
            title = "Auto-sync call history",
            subtitle = "Refresh contacts and calls every few hours",
            checked = settings.autoSyncEnabled,
            onCheckedChange = { viewModel.setAutoSync(it) }
        )

        SettingsSectionHeader("System")
        SettingsLinkRow(
            title = "Android notification settings",
            subtitle = "Per-channel sound and importance for each VIP tier",
            onClick = {
                // Channel importance and sound are owned by the system on
                // Android 8+, so this hands off rather than pretending to
                // control them from inside the app.
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else {
                    com.codeaza.bhaiyaaa.util.Permissions.appSettingsIntent(context)
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
        )

        Text(
            "Some phones (Xiaomi, Oppo, and Samsung in aggressive battery modes) delay or drop " +
                "the ringing broadcast for background apps. If VIP alerts are unreliable, allow " +
                "BHAIYAAA unrestricted battery use in your phone's settings. This is a platform " +
                "restriction, not something the app can work around.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }
}
