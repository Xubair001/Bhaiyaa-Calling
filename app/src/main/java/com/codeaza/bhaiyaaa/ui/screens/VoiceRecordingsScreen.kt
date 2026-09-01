package com.codeaza.bhaiyaaa.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.data.db.entity.VoiceRecordingEntity
import com.codeaza.bhaiyaaa.data.db.entity.VoiceRecordingSource
import com.codeaza.bhaiyaaa.ui.components.EmptyState
import com.codeaza.bhaiyaaa.ui.components.InfoBanner
import com.codeaza.bhaiyaaa.ui.components.SectionCard
import com.codeaza.bhaiyaaa.ui.recordings.VoiceRecordingViewModel
import com.codeaza.bhaiyaaa.util.Permissions
import java.util.Locale

/**
 * Record or import a sound, and choose one as the adhan.
 *
 * The screen says plainly what it does not do. Android does not permit a
 * non-system app to capture call audio, and consent law around recording a
 * conversation varies by jurisdiction - so this records only what the user
 * deliberately records here, and the text says so rather than leaving anyone
 * to wonder whether the app has been listening.
 */
@Composable
fun VoiceRecordingsScreen(viewModel: VoiceRecordingViewModel) {
    val context = LocalContext.current
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val previewingId by viewModel.previewingId.collectAsStateWithLifecycle()
    val selectedUri by viewModel.selectedSoundUri.collectAsStateWithLifecycle()

    var renaming by remember { mutableStateOf<VoiceRecordingEntity?>(null) }
    var confirmingDelete by remember { mutableStateOf<VoiceRecordingEntity?>(null) }
    var newLabel by remember { mutableStateOf("") }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startRecording() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.import(it, newLabel.ifBlank { "Imported sound" }) } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionCard(title = "Add a sound") {
                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    label = { Text("Name") },
                    placeholder = { Text("Adhan, Fajr call…") },
                    singleLine = true,
                    enabled = !isRecording,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                if (isRecording) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.stopRecording(newLabel) }) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Stop and save")
                        }
                        OutlinedButton(onClick = { viewModel.cancelRecording() }) { Text("Discard") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Recording. The microphone is open only while this says so.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (Permissions.isGranted(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO
                                )
                            ) {
                                viewModel.startRecording()
                            } else {
                                // Progressive: the microphone is requested here
                                // and nowhere else on this screen.
                                micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        }) {
                            Icon(Icons.Filled.Mic, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Record")
                        }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("audio/*")) }) {
                            Text("Import a file")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Sounds are copied into Sukoon's own storage, are not backed up, and go " +
                        "when the app does. Playback stops after " +
                        "${viewModel.maxAdhanSeconds / 60} minutes however long the file is.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            InfoBanner(
                text = "Sukoon never records calls. Android does not allow it, and consent " +
                    "rules differ by country — this records only what you record here."
            )
        }

        if (recordings.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Mic,
                    title = "No recordings yet",
                    body = "Record an adhan in your own voice, or import one you already have."
                )
            }
        }

        items(recordings, key = { it.id }) { recording ->
            RecordingRow(
                recording = recording,
                isSelected = viewModel.isSelectedAsAdhan(recording, selectedUri),
                isPreviewing = previewingId == recording.id,
                onPreview = { viewModel.togglePreview(recording) },
                onUse = { viewModel.useAsAdhan(recording) },
                onRename = { renaming = recording },
                onDelete = { confirmingDelete = recording },
                modifier = Modifier.animateItem()
            )
        }
    }

    renaming?.let { target ->
        var label by remember(target.id) { mutableStateOf(target.label) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.rename(target.id, label)
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }

    confirmingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text("Delete \"${target.label}\"?") },
            // Deleting removes the audio for good, so it is worth one tap of
            // confirmation - and worth saying what happens if it was in use.
            text = {
                Text(
                    "The recording is removed from this phone. If it is your adhan, " +
                        "Sukoon goes back to the phone's alarm tone."
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.delete(target)
                    confirmingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RecordingRow(
    recording: VoiceRecordingEntity,
    isSelected: Boolean,
    isPreviewing: Boolean,
    onPreview: () -> Unit,
    onUse: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = if (isSelected) "Your adhan" else "Recording",
        modifier = modifier
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    recording.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(formatSeconds(recording.durationMillis))
                        append(" · ")
                        append(VoiceRecordingSource.from(recording.source).label)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Filled.Check,
                    // Selection is stated in the card title too, so this is
                    // not the only cue - but it still needs a description.
                    contentDescription = "Currently used as the adhan",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onPreview) {
                Icon(
                    imageVector = if (isPreviewing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (isPreviewing) "Stop preview" else "Play preview"
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!isSelected) {
                TextButton(onClick = onUse) { Text("Use as adhan") }
            }
            TextButton(onClick = onRename) { Text("Rename") }
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** "1:24" - or "—" for a clip whose length could not be read. */
private fun formatSeconds(millis: Long): String {
    if (millis <= 0L) return "—"
    val total = millis / 1000
    return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60)
}
