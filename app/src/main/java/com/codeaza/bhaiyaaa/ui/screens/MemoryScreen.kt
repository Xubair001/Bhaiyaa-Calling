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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.data.db.entity.MemoryEntity
import com.codeaza.bhaiyaaa.domain.model.MemorySource
import com.codeaza.bhaiyaaa.ui.SukoonViewModel
import com.codeaza.bhaiyaaa.ui.components.EmptyState
import com.codeaza.bhaiyaaa.util.Formatting

/**
 * Everything Sukoon remembers, all of it typed by the user.
 *
 * The empty state says so explicitly - it is the single most likely place for
 * someone to assume the app has been listening to their calls, and it hasn't.
 */
@Composable
fun MemoryScreen(viewModel: SukoonViewModel) {
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<MemoryEntity?>(null) }
    var searchHits by remember { mutableStateOf<List<MemoryEntity>?>(null) }

    val nameByNumber = remember(contacts) { contacts.associate { it.phoneNumber to it.name } }

    // Search goes through the FTS index rather than filtering the list in memory,
    // so it still works once there are thousands of notes.
    LaunchedEffect(query, memories.size) {
        searchHits = if (query.isBlank()) null else viewModel.searchMemoriesNow(query)
    }

    val visible = searchHits ?: memories

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New memory") }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search memories") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true
            )

            if (visible.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Lightbulb,
                    title = if (query.isBlank()) "No memories yet" else "Nothing matched",
                    body = if (query.isBlank()) {
                        "Save a note after a call and Sukoon can find it later. " +
                            "It only ever remembers what you write down — it can't hear your calls."
                    } else {
                        "No memory matches \"$query\"."
                    }
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.id }) { memory ->
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                memory.title?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(4.dp))
                                }
                                Text(memory.body, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        buildString {
                                            append(MemorySource.from(memory.source).label)
                                            memory.contactPhoneNumber
                                                ?.let { nameByNumber[it] }
                                                ?.let { append(" · $it") }
                                            append(" · ")
                                            append(Formatting.relativeDateTime(memory.createdAt))
                                            if (memory.isPrivate) append(" · 🔐 Private")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { pendingDelete = memory }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Delete memory",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddMemoryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { body, isPrivate ->
                viewModel.addMemory(body = body, isPrivate = isPrivate)
                showAddDialog = false
            }
        )
    }

    pendingDelete?.let { memory ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this memory?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMemory(memory.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit
) {
    var body by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New memory") },
        text = {
            Column {
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("What should Sukoon remember?") },
                    minLines = 3
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Private", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Hidden until you unlock the app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(body, isPrivate) }, enabled = body.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
