package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.data.db.entity.CallRecordEntity
import com.codeaza.bhaiyaaa.domain.model.CallType
import com.codeaza.bhaiyaaa.domain.model.Lookup
import com.codeaza.bhaiyaaa.domain.model.MemorySource
import com.codeaza.bhaiyaaa.ui.SukoonViewModel
import com.codeaza.bhaiyaaa.ui.recordings.VoiceRecordingViewModel
import com.codeaza.bhaiyaaa.ui.components.CallTypeIcon
import com.codeaza.bhaiyaaa.ui.components.ContactAvatar
import com.codeaza.bhaiyaaa.ui.components.LoadingState
import com.codeaza.bhaiyaaa.ui.components.SectionCard
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.codeaza.bhaiyaaa.util.ContactActions
import com.codeaza.bhaiyaaa.util.Permissions
import com.codeaza.bhaiyaaa.util.Formatting
import com.codeaza.bhaiyaaa.util.PhoneNumbers

/**
 * One call, with the annotations the user can attach to it.
 *
 * There is deliberately no transcript and no capture of the call itself.
 * `AudioSource.VOICE_CALL` needs `CAPTURE_AUDIO_OUTPUT`, which Android grants
 * only to privileged, pre-installed apps - being the default dialer is not
 * enough - and Play policy has barred the accessibility workaround since 2022.
 * Consent law varies by country on top of that.
 *
 * What is here is the achievable half of the same need: a voice note recorded
 * straight after the call, or a file the phone's own dialer produced and the
 * user imported, filed against this call so it is findable later. Sukoon never
 * implies it heard anything.
 */
@Composable
fun CallDetailScreen(
    callId: Long,
    viewModel: SukoonViewModel,
    recordingViewModel: VoiceRecordingViewModel,
    onOpenContact: (String) -> Unit
) {
    val context = LocalContext.current
    // Observed rather than fetched once, so ticking "important" or saving a
    // note re-renders from the database instead of from a mirrored local copy.
    val lookup by remember(callId) { viewModel.observeCallLookup(callId) }
        .collectAsStateWithLifecycle(initialValue = Lookup.Loading)

    when (lookup) {
        is Lookup.Loading -> {
            LoadingState(label = "Opening call…")
            return
        }
        is Lookup.Missing -> {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Call not found", style = MaterialTheme.typography.titleMedium)
            }
            return
        }
        is Lookup.Found -> Unit
    }
    val current = (lookup as Lookup.Found<CallRecordEntity>).value

    var noteDraft by remember(current.id) { mutableStateOf(current.note.orEmpty()) }
    var memoryDraft by remember(current.id) { mutableStateOf("") }
    val type = CallType.from(current.type)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ContactAvatar(current.contactName ?: current.phoneNumber, size = 56)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    current.contactName ?: PhoneNumbers.forDisplay(current.phoneNumber),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CallTypeIcon(type)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${type.label} · ${Formatting.dateTime(current.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    if (!ContactActions.dial(context, current.phoneNumber)) {
                        viewModel.showMessage("No dialer app on this device.")
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Call, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Call back")
            }
            OutlinedButton(
                onClick = {
                    if (!ContactActions.message(context, current.phoneNumber)) {
                        viewModel.showMessage("No messaging app on this device.")
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Message")
            }
        }

        SectionCard(title = "Details") {
            DetailRow("Direction", type.label)
            DetailRow("When", Formatting.dateTime(current.timestamp))
            DetailRow(
                "Duration",
                if (type.isUnanswered) "Never connected"
                else Formatting.duration(current.durationSeconds)
            )
            DetailRow("Number", PhoneNumbers.forDisplay(current.phoneNumber))
        }

        SectionCard(title = "Mark important") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Show this call under the Important filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = current.isImportant,
                    onCheckedChange = { viewModel.setCallImportant(current.id, it) }
                )
            }
        }

        SectionCard(title = "Call note") {
            OutlinedTextField(
                value = noteDraft,
                onValueChange = { noteDraft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("What was this call about?") },
                minLines = 2
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.setCallNote(current.id, noteDraft) },
                enabled = noteDraft != current.note.orEmpty()
            ) { Text("Save note") }
        }

        CallRecordingsSection(
            callId = current.id,
            viewModel = recordingViewModel
        )

        SectionCard(title = "Save as memory") {
            Text(
                "Memories are searchable later — ask the Assistant \"what did they say about…\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = memoryDraft,
                onValueChange = { memoryDraft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Something worth remembering") },
                minLines = 2
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.addMemory(
                        body = memoryDraft,
                        contactPhoneNumber = current.phoneNumber,
                        source = MemorySource.CALL_NOTE,
                        callRecordId = current.id
                    )
                    memoryDraft = ""
                },
                enabled = memoryDraft.isNotBlank()
            ) { Text("Save to Memory") }
        }

        OutlinedButton(
            onClick = { onOpenContact(current.phoneNumber) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open contact") }
    }
}

/**
 * Voice notes filed against this call.
 *
 * Two ways in, because there are two real situations. Recording here covers
 * "say what was agreed while it is fresh", which is what most people actually
 * want call recording for. Importing covers the phone whose own dialer already
 * recorded the call - Sukoon cannot make that recording, but it can be where it
 * lives and be the thing that files it against the right call.
 */
@Composable
private fun CallRecordingsSection(
    callId: Long,
    viewModel: VoiceRecordingViewModel
) {
    val context = LocalContext.current
    // Remembered: recordingsForCall builds a Flow, and a new one per
    // recomposition would resubscribe on every frame.
    val recordingsFlow = remember(callId) { viewModel.recordingsForCall(callId) }
    val recordings by recordingsFlow.collectAsStateWithLifecycle(emptyList())
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val previewingId by viewModel.previewingId.collectAsStateWithLifecycle()

    var label by remember(callId) { mutableStateOf("") }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startRecording(callId) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.import(it, label.ifBlank { "Call recording" }, callId) }
    }

    SectionCard(title = "Voice notes") {
        if (recordings.isEmpty()) {
            Text(
                "Record what was said while it is fresh, or import a recording your " +
                    "phone's own dialer made.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            recordings.forEach { recording ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(recording.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${Formatting.duration(recording.durationMillis / 1000)} · " +
                                Formatting.relativeDateTime(recording.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.togglePreview(recording) }) {
                        Icon(
                            imageVector = if (previewingId == recording.id) Icons.Filled.Stop
                            else Icons.Filled.PlayArrow,
                            contentDescription = if (previewingId == recording.id) "Stop"
                            else "Play ${recording.label}"
                        )
                    }
                    IconButton(onClick = { viewModel.delete(recording) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete ${recording.label}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Name") },
            placeholder = { Text("What this note is about") },
            singleLine = true,
            enabled = !isRecording,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        if (isRecording) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.stopRecording(label) }) {
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
                        viewModel.startRecording(callId)
                    } else {
                        micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                }) {
                    Icon(Icons.Filled.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Record a note")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("audio/*")) }) {
                    Text("Import")
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Sukoon can't record the call itself — Android only allows that for apps " +
                "built into the phone. This records you, after the call, and stays on " +
                "this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
