package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.data.db.entity.CallRecordEntity
import com.codeaza.bhaiyaaa.domain.model.CallType
import com.codeaza.bhaiyaaa.domain.model.MemorySource
import com.codeaza.bhaiyaaa.ui.BhaiyaaaViewModel
import com.codeaza.bhaiyaaa.ui.components.CallTypeIcon
import com.codeaza.bhaiyaaa.ui.components.ContactAvatar
import com.codeaza.bhaiyaaa.ui.components.SectionCard
import com.codeaza.bhaiyaaa.util.Formatting
import com.codeaza.bhaiyaaa.util.PhoneNumbers

/**
 * One call, with the annotations the user can attach to it.
 *
 * There is deliberately no transcript or recording here. Android blocks
 * non-system apps from capturing call audio on modern versions, and recording
 * consent law varies by country - so BHAIYAAA stores what you write down about
 * a call, and never pretends to have heard it.
 */
@Composable
fun CallDetailScreen(
    callId: Long,
    viewModel: BhaiyaaaViewModel,
    onOpenContact: (String) -> Unit
) {
    val call by produceState<CallRecordEntity?>(initialValue = null, callId) {
        value = viewModel.findCallForDetail(callId)
    }

    val current = call
    if (current == null) {
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

    var noteDraft by remember(current.id) { mutableStateOf(current.note.orEmpty()) }
    var memoryDraft by remember(current.id) { mutableStateOf("") }
    var important by remember(current.id) { mutableStateOf(current.isImportant) }
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
                    checked = important,
                    onCheckedChange = {
                        important = it
                        viewModel.setCallImportant(current.id, it)
                    }
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
