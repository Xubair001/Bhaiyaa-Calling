package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.ai.model.ModelCatalog
import com.codeaza.bhaiyaaa.ai.model.ModelPurpose
import com.codeaza.bhaiyaaa.ai.model.ModelStatus
import com.codeaza.bhaiyaaa.data.db.entity.AiModelEntity
import com.codeaza.bhaiyaaa.ui.components.InfoBanner
import com.codeaza.bhaiyaaa.ui.components.SectionCard
import com.codeaza.bhaiyaaa.ui.models.ModelManagerViewModel
import com.codeaza.bhaiyaaa.util.Formatting

/**
 * Settings → AI Models (brief §18).
 *
 * Nothing downloads on its own. Every model shows its purpose, size and licence
 * before the user can start a download, and downloads are Wi-Fi only. Deleting
 * a model genuinely removes its files from disk.
 */
@Composable
fun ModelManagerScreen(viewModel: ModelManagerViewModel) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val diskUsage by viewModel.diskUsage.collectAsStateWithLifecycle()
    var pendingDownload by remember { mutableStateOf<AiModelEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<AiModelEntity?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            InfoBanner(
                text = "Sukoon works fully without any of these. Installing a speech model " +
                    "only makes voice input run entirely on-device instead of through your " +
                    "phone's recognizer."
            )
        }

        items(models, key = { it.id }) { model ->
            ModelCard(
                model = model,
                onDownload = { pendingDownload = model },
                onDelete = { pendingDelete = model },
                onToggleEnabled = { viewModel.setEnabled(model.id, it) },
                onCancel = { viewModel.cancelDownload(model.id) }
            )
        }

        item {
            Text(
                "Models on this device: ${Formatting.bytes(diskUsage)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    pendingDownload?.let { model ->
        val entry = ModelCatalog.find(model.id)
        AlertDialog(
            onDismissRequest = { pendingDownload = null },
            title = { Text("Download ${model.displayName}?") },
            text = {
                Column {
                    Text(entry?.description.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Size: about ${Formatting.bytes(model.sizeBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Licence: ${model.license}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Downloads only over Wi-Fi. This is the only network request Sukoon " +
                            "ever makes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.download(model.id)
                    pendingDownload = null
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDownload = null }) { Text("Cancel") }
            }
        )
    }

    pendingDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${model.displayName}?") },
            text = { Text("Frees ${Formatting.bytes(model.sizeBytes)}. You can download it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(model.id)
                    pendingDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ModelCard(
    model: AiModelEntity,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val status = ModelStatus.from(model.status)
    val purpose = ModelPurpose.from(model.purpose)

    SectionCard(title = model.displayName) {
        Text(
            "${purpose.label} · ${Formatting.bytes(model.sizeBytes)} · ${model.license}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ModelCatalog.find(model.id)?.let {
            Spacer(Modifier.height(6.dp))
            Text(it.description, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Status: ${status.label}",
                style = MaterialTheme.typography.bodySmall,
                color = if (status == ModelStatus.FAILED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (status == ModelStatus.DOWNLOADING) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }

        model.lastError?.takeIf { status == ModelStatus.FAILED }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(10.dp))
        when (status) {
            ModelStatus.NOT_INSTALLED, ModelStatus.FAILED ->
                Button(onClick = onDownload) {
                    Text(if (status == ModelStatus.FAILED) "Retry download" else "Download")
                }

            ModelStatus.DOWNLOADING ->
                OutlinedButton(onClick = onCancel) { Text("Cancel") }

            ModelStatus.INSTALLED -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Use this model", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = model.enabled, onCheckedChange = onToggleEnabled)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
