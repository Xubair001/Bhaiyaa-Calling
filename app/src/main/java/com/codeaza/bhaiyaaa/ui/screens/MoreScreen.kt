package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.ui.SukoonViewModel
import com.codeaza.bhaiyaaa.ui.components.SettingsLinkRow
import com.codeaza.bhaiyaaa.ui.components.SettingsSectionHeader
import com.codeaza.bhaiyaaa.util.Formatting

/** Hub for the four top-level screens that don't fit in the bottom bar. */
@Composable
fun MoreScreen(
    viewModel: SukoonViewModel,
    onOpenVip: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPrivacyCenter: () -> Unit
) {
    val vips by viewModel.vipContacts.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SettingsSectionHeader("Your people and notes")
        SettingsLinkRow(
            title = "VIP contacts",
            subtitle = if (vips.isEmpty()) "Nobody marked VIP yet"
            else "${vips.size} people get special alerts",
            icon = Icons.Filled.Star,
            onClick = onOpenVip
        )
        SettingsLinkRow(
            title = "Saved notes",
            subtitle = if (memories.isEmpty()) "Nothing saved yet"
            else "${memories.size} notes you wrote about people",
            icon = Icons.Outlined.Lightbulb,
            onClick = onOpenMemory
        )
        SettingsLinkRow(
            title = "Reminders",
            subtitle = "Things you asked Sukoon to remind you about",
            icon = Icons.Filled.Notifications,
            onClick = onOpenReminders
        )
        SettingsLinkRow(
            title = "Call insights",
            subtitle = "Who you talk to, and when",
            icon = Icons.Filled.Insights,
            onClick = onOpenInsights
        )

        SettingsSectionHeader("App")
        SettingsLinkRow(
            title = "Privacy",
            subtitle = "What Sukoon stores, and what it can see",
            icon = Icons.Filled.Security,
            onClick = onOpenPrivacyCenter
        )
        SettingsLinkRow(
            title = "Settings",
            subtitle = "Alerts, quiet times, appearance, app lock",
            icon = Icons.Filled.Settings,
            onClick = onOpenSettings
        )

        Text(
            text = if (settings.lastSyncAt > 0) {
                "Last synced ${Formatting.relativeDateTime(settings.lastSyncAt)}"
            } else {
                "Not synced yet"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }
}
