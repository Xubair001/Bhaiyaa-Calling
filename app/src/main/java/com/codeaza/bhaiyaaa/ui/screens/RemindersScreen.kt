package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.ai.TimeExpressions
import com.codeaza.bhaiyaaa.ui.SukoonViewModel
import com.codeaza.bhaiyaaa.ui.components.EmptyState
import com.codeaza.bhaiyaaa.util.Formatting

/**
 * Reminders, addable in plain language: "call Ali tomorrow at 5pm" is parsed
 * for a time here exactly as it is in the Assistant, so both routes behave the
 * same way.
 */
@Composable
fun RemindersScreen(viewModel: SukoonViewModel) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }

    val parsed = remember(draft) {
        if (draft.isBlank()) null else TimeExpressions.parse(draft, System.currentTimeMillis())
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Call Ali tomorrow at 5pm") },
                singleLine = false,
                maxLines = 3
            )
            // Show the interpreted time before saving, so a misread phrase is
            // visible rather than a surprise later.
            parsed?.dueAt?.let { dueAt ->
                Spacer(Modifier.height(6.dp))
                Text(
                    "Will remind you ${Formatting.relativeDateTime(dueAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val time = parsed
                    val body = TimeExpressions.stripTimePhrase(draft, time?.matchedText)
                    viewModel.addReminder(body.ifBlank { draft }, time?.dueAt)
                    draft = ""
                },
                enabled = draft.isNotBlank()
            ) { Text("Add reminder") }
        }

        if (reminders.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Notifications,
                title = "No pending reminders",
                body = "Add one above, or just tell the Assistant \"remind me to…\"."
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(reminders, key = { it.id }) { reminder ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = reminder.isDone,
                            onCheckedChange = { viewModel.setReminderDone(reminder.id, it) }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                reminder.text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            reminder.dueAt?.let {
                                Text(
                                    Formatting.relativeDateTime(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.deleteReminder(reminder.id) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete reminder",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Reminders fire through Android's alarm scheduler. To avoid asking for " +
                            "the exact-alarm permission, they can land a few minutes late if the " +
                            "phone is in deep sleep.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
