package com.codeaza.bhaiyaaa.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.assistant.AssistantEngine
import com.codeaza.bhaiyaaa.assistant.AssistantResult
import com.codeaza.bhaiyaaa.ui.AppViewModel
import java.util.Locale

private data class ChatMessage(val text: String, val isUser: Boolean)

@Composable
fun AssistantScreen(viewModel: AppViewModel) {
    val contacts by viewModel.contacts.collectAsState()
    val calls by viewModel.calls.collectAsState()
    val reminders by viewModel.reminders.collectAsState()

    var input by remember { mutableStateOf("") }
    var reminderInput by remember { mutableStateOf("") }
    var messages by remember {
        mutableStateOf(
            listOf(
                ChatMessage(
                    "Assalam o Alaikum, boss. Ask me about missed calls, VIP contacts, " +
                        "recent calls, or say 'remind me to...'.",
                    isUser = false
                )
            )
        )
    }

    fun handleQuery(query: String) {
        if (query.isBlank()) return
        messages = messages + ChatMessage(query, isUser = true)
        when (val result = AssistantEngine.process(query, contacts, calls)) {
            is AssistantResult.Reply -> {
                messages = messages + ChatMessage(result.text, isUser = false)
            }
            is AssistantResult.ReminderCreated -> {
                viewModel.addReminder(result.reminderText)
                messages = messages + ChatMessage(result.reply, isUser = false)
            }
        }
        input = ""
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) handleQuery(text)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            runCatching { speechLauncher.launch(intent) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Assistant", style = MaterialTheme.typography.titleLarge)
        Text(
            "Rule-based, answers only from your real local data - never guesses.",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 8.dp, top = 2.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        message.text,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text("Ask BHAIYAAA") }
            )
            Button(onClick = { handleQuery(input) }) { Text("Send") }
        }
        Button(
            onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("\uD83C\uDFA4 Speak instead")
        }

        Text(
            "Reminders",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = reminderInput,
                onValueChange = { reminderInput = it },
                modifier = Modifier.weight(1f),
                label = { Text("New reminder") }
            )
            Button(onClick = {
                viewModel.addReminder(reminderInput)
                reminderInput = ""
            }) { Text("Add") }
        }

        reminders.take(5).forEach { reminder ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = false,
                    onCheckedChange = { viewModel.markReminderDone(reminder.id) }
                )
                Text(reminder.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
