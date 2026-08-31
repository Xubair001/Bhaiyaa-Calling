package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.ui.SukoonViewModel
import com.codeaza.bhaiyaaa.ui.components.SettingsLinkRow
import com.codeaza.bhaiyaaa.ui.components.SettingsSectionHeader
import com.codeaza.bhaiyaaa.util.SecurePrefs
import androidx.compose.ui.platform.LocalContext

@Composable
fun SettingsScreen(
    viewModel: SukoonViewModel,
    onOpenNotifications: () -> Unit,
    onOpenVipAlerts: () -> Unit,
    onOpenPrayer: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenPersonality: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenData: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SettingsSectionHeader("Alerts")
        SettingsLinkRow(
            title = "Notifications",
            subtitle = if (settings.notificationsEnabled) "On" else "Off",
            icon = Icons.Filled.Notifications,
            onClick = onOpenNotifications
        )
        SettingsLinkRow(
            title = "VIP alerts",
            subtitle = "Vibration, flashlight and sound per tier",
            icon = Icons.Filled.Star,
            onClick = onOpenVipAlerts
        )

        SettingsLinkRow(
            title = "Prayer silence",
            subtitle = if (settings.prayer.enabled) {
                if (settings.prayer.mode.name == "AUTOMATIC" && settings.prayer.hasLocation) {
                    "On · ${settings.prayer.locationLabel}"
                } else if (settings.prayer.mode.name == "AUTOMATIC") {
                    "On · location not set"
                } else {
                    "On · your own times"
                }
            } else {
                "Off"
            },
            icon = Icons.Filled.Schedule,
            onClick = onOpenPrayer
        )

        SettingsSectionHeader("Experience")
        SettingsLinkRow(
            title = "Appearance",
            subtitle = settings.themeMode.label,
            icon = Icons.Filled.Palette,
            onClick = onOpenAppearance
        )
        SettingsLinkRow(
            title = "Assistant personality",
            subtitle = settings.personality.label,
            icon = Icons.AutoMirrored.Filled.Chat,
            onClick = onOpenPersonality
        )
        SettingsLinkRow(
            title = "AI models",
            subtitle = "Install optional offline speech models",
            icon = Icons.Filled.Memory,
            onClick = onOpenModels
        )

        SettingsSectionHeader("Privacy & data")
        SettingsLinkRow(
            title = "Security",
            subtitle = if (SecurePrefs.isLockEnabled(context)) "Privacy lock on" else "Privacy lock off",
            icon = Icons.Filled.Lock,
            onClick = onOpenSecurity
        )
        SettingsLinkRow(
            title = "Data",
            subtitle = "Export, import, delete",
            icon = Icons.Filled.Storage,
            onClick = onOpenData
        )

        SettingsSectionHeader("About")
        SettingsLinkRow(
            title = "About Sukoon",
            subtitle = "Version, licences, what it can and can't do",
            icon = Icons.Filled.Info,
            onClick = onOpenAbout
        )
    }
}
