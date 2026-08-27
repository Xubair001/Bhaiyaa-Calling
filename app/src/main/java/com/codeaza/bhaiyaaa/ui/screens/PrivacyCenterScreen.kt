package com.codeaza.bhaiyaaa.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.ui.BhaiyaaaViewModel
import com.codeaza.bhaiyaaa.ui.components.SectionCard
import com.codeaza.bhaiyaaa.ui.components.SettingsInfoRow
import com.codeaza.bhaiyaaa.util.Formatting
import com.codeaza.bhaiyaaa.util.Permissions
import com.codeaza.bhaiyaaa.util.SecurePrefs

/**
 * A single honest page answering "what does this app know about me, and what
 * can it reach?" (brief §20).
 */
@Composable
fun PrivacyCenterScreen(
    viewModel: BhaiyaaaViewModel,
    onOpenData: () -> Unit
) {
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val vips by viewModel.vipContacts.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var permissionVersion by remember { mutableIntStateOf(0) }
    val lockEnabled = remember(permissionVersion) { SecurePrefs.isLockEnabled(context) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionCard(title = "Network use") {
                Text(
                    "BHAIYAAA makes exactly one kind of network request: downloading an AI model, " +
                        "and only when you tap download. Your contacts, calls, notes and memories " +
                        "are never uploaded anywhere.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "There is no account, no analytics and no crash reporting service.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = "Stored on this device") {
                SettingsInfoRow("Contacts", contacts.size.toString())
                SettingsInfoRow("VIP contacts", vips.size.toString())
                SettingsInfoRow("Call records", calls.size.toString())
                SettingsInfoRow("Memories", memories.size.toString())
                SettingsInfoRow("Pending reminders", reminders.size.toString())
                SettingsInfoRow(
                    "Last sync",
                    if (settings.lastSyncAt > 0) Formatting.relativeDateTime(settings.lastSyncAt)
                    else "Never"
                )
            }
        }

        item {
            SectionCard(
                title = "Permissions granted",
                action = {
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(Permissions.appSettingsIntent(context))
                        }
                        permissionVersion++
                    }) { Text("Manage") }
                }
            ) {
                Permissions.CORE.forEach { group ->
                    val granted = remember(permissionVersion, group) { group.isGranted(context) }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (granted) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = if (granted) "Granted" else "Not granted",
                            tint = if (granted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(0.dp))
                        Text(
                            "  ${stringResource(group.titleRes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        item {
            SectionCard(title = "Protection") {
                SettingsInfoRow("Privacy lock", if (lockEnabled) "On" else "Off")
                SettingsInfoRow("PIN storage", "Salted hash in Android Keystore")
                SettingsInfoRow("Cloud backup", "Disabled")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Cloud backup is switched off in the manifest so the platform can never copy " +
                        "your call metadata or notes off this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = "Your data, your call") {
                Text(
                    "Export it, import it back, or delete any part of it.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onOpenData) { Text("Export, import & delete") }
            }
        }
    }
}
