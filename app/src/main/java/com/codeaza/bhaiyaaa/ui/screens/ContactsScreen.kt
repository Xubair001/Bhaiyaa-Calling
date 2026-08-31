package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.ui.SukoonViewModel
import com.codeaza.bhaiyaaa.ui.components.ContactAvatar
import com.codeaza.bhaiyaaa.ui.components.EmptyState
import com.codeaza.bhaiyaaa.ui.components.VipBadge
import com.codeaza.bhaiyaaa.util.Formatting
import com.codeaza.bhaiyaaa.util.PhoneNumbers

@Composable
fun ContactsScreen(
    viewModel: SukoonViewModel,
    onOpenContact: (String) -> Unit
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val stats by viewModel.callStats.collectAsStateWithLifecycle()
    val hasPermissions by viewModel.hasCorePermissions.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }

    val statsByKey = remember(stats) { stats.associateBy { it.matchKey } }

    val visible = remember(contacts, query, selectedTag) {
        contacts.filter { contact ->
            (selectedTag == null || contact.tag == selectedTag) &&
                (query.isBlank() ||
                    contact.name.contains(query, ignoreCase = true) ||
                    contact.phoneNumber.contains(query) ||
                    contact.notes?.contains(query, ignoreCase = true) == true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search contacts and notes") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true
        )

        if (tags.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTag == null,
                    onClick = { selectedTag = null },
                    label = { Text("All") }
                )
                tags.forEach { tag ->
                    FilterChip(
                        selected = selectedTag == tag.name,
                        onClick = { selectedTag = if (selectedTag == tag.name) null else tag.name },
                        label = { Text(tag.name) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (visible.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.People,
                title = if (contacts.isEmpty()) "No contacts yet" else "Nothing matched",
                body = if (!hasPermissions) {
                    "Grant the Contacts permission and Sukoon will pull them in."
                } else if (contacts.isEmpty()) {
                    "Tap refresh to sync your phone's address book."
                } else {
                    "No contact matches that search or tag."
                },
                actionLabel = if (contacts.isEmpty() && hasPermissions) "Sync now" else null,
                onAction = if (contacts.isEmpty() && hasPermissions) {
                    { viewModel.sync() }
                } else {
                    null
                }
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                items(visible, key = { it.phoneNumber }) { contact ->
                    val contactStats = statsByKey[contact.matchKey]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenContact(contact.phoneNumber) }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ContactAvatar(contact.name, size = 42)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    contact.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(Modifier.width(6.dp))
                                VipBadge(VipLevel.from(contact.vipLevel))
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                buildString {
                                    append(PhoneNumbers.forDisplay(contact.phoneNumber))
                                    contact.tag?.let { append(" · $it") }
                                    contactStats?.let {
                                        if (it.totalCalls > 0) {
                                            append(" · ${Formatting.plural(it.totalCalls, "call")}")
                                        }
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
