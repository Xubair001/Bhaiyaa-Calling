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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

    val lifecycleOwner = LocalLifecycleOwner.current
    var grantVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) grantVersion++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val lockEnabled = remember(grantVersion) { SecurePrefs.isLockEnabled(context) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SettingsSectionHeader("When someone calls")
        SettingsLinkRow(
            title = "Alerts & sounds",
            subtitle = if (settings.notificationsEnabled)
                "VIP alerts are on" else "VIP alerts are off",
            icon = Icons.Filled.Notifications,
            onClick = onOpenNotifications
        )
        SettingsLinkRow(
            title = "How VIPs reach you",
            subtitle = "Vibration, flashlight and sound for each tier",
            icon = Icons.Filled.Star,
            onClick = onOpenVipAlerts
        )

        SettingsLinkRow(
            title = "Quiet times",
            subtitle = "Your own quiet periods, and prayer silence",
            icon = Icons.Filled.Schedule,
            onClick = onOpenPrayer
        )

        SettingsSectionHeader("Look and feel")
        SettingsLinkRow(
            title = "Appearance",
            subtitle = "Theme and colours — ${settings.themeMode.label}",
            icon = Icons.Filled.Palette,
            onClick = onOpenAppearance
        )
        SettingsLinkRow(
            title = "How Sukoon talks",
            subtitle = "Tone of replies — ${settings.personality.label}",
            icon = Icons.AutoMirrored.Filled.Chat,
            onClick = onOpenPersonality
        )
        SettingsLinkRow(
            title = "Offline voice",
            subtitle = "Speak to the assistant with no internet",
            icon = Icons.Filled.Memory,
            onClick = onOpenModels
        )

        SettingsSectionHeader("Your data")
        SettingsLinkRow(
            title = "App lock",
            // Remembered: the first read opens Keystore-backed prefs, which
            // is not work to repeat on every recomposition. Re-read on resume
            // so returning from the security screen shows the new state.
            subtitle = if (lockEnabled)
                "PIN required to open Sukoon" else "Anyone can open Sukoon",
            icon = Icons.Filled.Lock,
            onClick = onOpenSecurity
        )
        SettingsLinkRow(
            title = "Backup & delete",
            subtitle = "Save a copy of your data, or erase it",
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
