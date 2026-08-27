package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.ui.AppViewModel
import com.codeaza.bhaiyaaa.util.ContactTag
import com.codeaza.bhaiyaaa.util.VipLevel

@Composable
fun ContactDetailScreen(viewModel: AppViewModel, phoneNumber: String, onBack: () -> Unit) {
    val contacts by viewModel.contacts.collectAsState()
    val contact = contacts.find { it.phoneNumber == phoneNumber }

    var notesText by remember(phoneNumber) { mutableStateOf(contact?.notes ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("\u2190 Back") }

        if (contact == null) {
            Text("Contact not found.")
        } else {
            Text(contact.name, style = MaterialTheme.typography.titleLarge)
            Text(contact.phoneNumber, style = MaterialTheme.typography.bodyMedium)

            Text("VIP level", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VipLevel.ALL.forEach { level ->
                    FilterChip(
                        selected = contact.vipLevel == level,
                        onClick = { viewModel.setVipLevel(phoneNumber, level) },
                        label = { Text(VipLevel.label(level)) }
                    )
                }
            }

            Text("Category", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ContactTag.ALL.forEach { tag ->
                    FilterChip(
                        selected = contact.tag == tag,
                        onClick = { viewModel.setTag(phoneNumber, if (contact.tag == tag) null else tag) },
                        label = { Text(tag) }
                    )
                }
            }

            Text("Notes", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Button(onClick = { viewModel.setNotes(phoneNumber, notesText) }) {
                Text("Save notes")
            }
        }
    }
}
