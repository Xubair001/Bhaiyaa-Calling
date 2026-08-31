package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.domain.model.MemorySource
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.ui.SukoonViewModel
import com.codeaza.bhaiyaaa.ui.components.CallRow
import com.codeaza.bhaiyaaa.ui.components.ContactAvatar
import com.codeaza.bhaiyaaa.ui.components.EmptyState
import com.codeaza.bhaiyaaa.util.Formatting
import com.codeaza.bhaiyaaa.util.PhoneNumbers

/** One search across contacts, calls, memories and reminders (brief §30). */
@Composable
fun SearchScreen(
    viewModel: SukoonViewModel,
    onOpenContact: (String) -> Unit,
    onOpenCall: (Long) -> Unit
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .focusRequester(focusRequester),
            placeholder = { Text("Search contacts, calls, memories…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true
        )

        if (query.isBlank()) {
            EmptyState(
                icon = Icons.Filled.Search,
                title = "Search everything",
                body = "Find a person, a call, a note or a reminder. Memories are matched with a " +
                    "full-text index, so it stays fast as they pile up."
            )
            return
        }

        if (results.isEmpty) {
            EmptyState(
                icon = Icons.Filled.Search,
                title = "Nothing matched",
                body = "No contact, call, memory or reminder matches \"$query\"."
            )
            return
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            if (results.contacts.isNotEmpty()) {
                item { SectionLabel("Contacts (${results.contacts.size})") }
                items(results.contacts, key = { "c-${it.phoneNumber}" }) { contact ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenContact(contact.phoneNumber) }
                            .padding(horizontal = 4.dp, vertical = 10.dp)
                    ) {
                        Text(contact.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            PhoneNumbers.forDisplay(contact.phoneNumber),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (results.calls.isNotEmpty()) {
                item { SectionLabel("Calls (${results.calls.size})") }
                items(results.calls, key = { "call-${it.id}" }) { call ->
                    CallRow(call = call, vipLevel = VipLevel.NONE, onClick = { onOpenCall(call.id) })
                }
            }

            if (results.memories.isNotEmpty()) {
                item { SectionLabel("Memories (${results.memories.size})") }
                items(results.memories, key = { "m-${it.id}" }) { memory ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 10.dp)
                    ) {
                        Text(
                            memory.body,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${MemorySource.from(memory.source).label} · " +
                                Formatting.relativeDateTime(memory.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (results.reminders.isNotEmpty()) {
                item { SectionLabel("Reminders (${results.reminders.size})") }
                items(results.reminders, key = { "r-${it.id}" }) { reminder ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 10.dp)
                    ) {
                        Text(reminder.text, style = MaterialTheme.typography.bodyMedium)
                        reminder.dueAt?.let {
                            Text(
                                Formatting.relativeDateTime(it),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 4.dp)
    )
}
