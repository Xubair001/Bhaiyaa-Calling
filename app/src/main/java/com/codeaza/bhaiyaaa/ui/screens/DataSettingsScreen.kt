package com.codeaza.bhaiyaaa.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.data.export.DataTransfer
import com.codeaza.bhaiyaaa.ui.BhaiyaaaViewModel
import com.codeaza.bhaiyaaa.ui.components.SettingsLinkRow
import com.codeaza.bhaiyaaa.ui.components.SettingsSectionHeader
import com.codeaza.bhaiyaaa.ui.components.SettingsSwitchRow

/** A destructive action awaiting confirmation. */
private data class Confirmation(
    val title: String,
    val body: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit
)

@Composable
fun DataSettingsScreen(viewModel: BhaiyaaaViewModel) {
    var includeCallHistory by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }

    // Storage Access Framework: the user picks the file, so BHAIYAAA never
    // needs broad storage permissions and never writes anywhere unasked.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportData(it, includeCallHistory) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importData(it) } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SettingsSectionHeader("Export")
        SettingsSwitchRow(
            title = "Include call history",
            subtitle = "Off by default — it's the bulkiest and most sensitive part, and your " +
                "phone's own call log can rebuild it",
            checked = includeCallHistory,
            onCheckedChange = { includeCallHistory = it }
        )
        SettingsLinkRow(
            title = "Export my data",
            subtitle = "Contacts metadata, VIP tiers, notes, memories, reminders and settings as JSON",
            onClick = { exportLauncher.launch(DataTransfer.DEFAULT_FILE_NAME) }
        )

        SettingsSectionHeader("Import")
        SettingsLinkRow(
            title = "Import a backup",
            subtitle = "Merges into what's already here — nothing is deleted",
            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        )

        SettingsSectionHeader("Delete")
        SettingsLinkRow(
            title = "Reset VIP tiers, tags and notes",
            subtitle = "Keeps your contacts, clears what you set in BHAIYAAA",
            onClick = {
                confirmation = Confirmation(
                    "Reset VIP and annotations?",
                    "Every VIP tier, category, importance and note you set will be cleared. " +
                        "Your contacts themselves stay.",
                    "Reset"
                ) { viewModel.resetVipAndAnnotations() }
            }
        )
        SettingsLinkRow(
            title = "Clear local call history",
            subtitle = "Your phone's own call log is untouched",
            onClick = {
                confirmation = Confirmation(
                    "Clear local call history?",
                    "BHAIYAAA's copy is deleted. Your phone's call log is not touched, so a " +
                        "future sync will pull it back in.",
                    "Clear"
                ) { viewModel.clearCallHistory() }
            }
        )
        SettingsLinkRow(
            title = "Delete all memories",
            subtitle = "Every note and action item you saved",
            onClick = {
                confirmation = Confirmation(
                    "Delete all memories?",
                    "This can't be undone. Export first if you want to keep them.",
                    "Delete"
                ) { viewModel.clearMemories() }
            }
        )
        SettingsLinkRow(
            title = "Delete everything",
            subtitle = "Wipe BHAIYAAA back to a fresh install",
            onClick = {
                confirmation = Confirmation(
                    "Delete everything?",
                    "All contacts metadata, call history, memories, reminders and settings are " +
                        "deleted, and the privacy lock is switched off. This can't be undone.",
                    "Delete everything"
                ) { viewModel.deleteEverything() }
            }
        )

        Text(
            "Exports are plain JSON, not an encrypted backup. Anyone who opens the file can " +
                "read it, so keep it somewhere you trust.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }

    confirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(pending.title) },
            text = { Text(pending.body) },
            confirmButton = {
                TextButton(onClick = {
                    pending.onConfirm()
                    confirmation = null
                }) {
                    Text(pending.confirmLabel, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) { Text("Cancel") }
            }
        )
    }
}
