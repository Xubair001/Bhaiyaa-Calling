package com.codeaza.bhaiyaaa.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.data.db.projection.ContactStats
import com.codeaza.bhaiyaaa.domain.model.Importance
import com.codeaza.bhaiyaaa.domain.model.Lookup
import com.codeaza.bhaiyaaa.domain.model.MemorySource
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.ui.BhaiyaaaViewModel
import com.codeaza.bhaiyaaa.ui.components.CallRow
import com.codeaza.bhaiyaaa.ui.components.ContactAvatar
import com.codeaza.bhaiyaaa.ui.components.LoadingState
import com.codeaza.bhaiyaaa.ui.components.SectionCard
import com.codeaza.bhaiyaaa.ui.components.StatTile
import com.codeaza.bhaiyaaa.ui.components.VipBadge
import androidx.compose.ui.platform.LocalContext
import com.codeaza.bhaiyaaa.util.ContactActions
import com.codeaza.bhaiyaaa.util.Formatting
import com.codeaza.bhaiyaaa.util.PhoneNumbers
import kotlinx.coroutines.flow.map

/**
 * The per-contact CRM record (brief §8): tier, category, importance, notes,
 * saved memories, and call statistics computed live from the call log.
 */
@Composable
fun ContactDetailScreen(
    phoneNumber: String,
    viewModel: BhaiyaaaViewModel,
    onOpenCall: (Long) -> Unit
) {
    val context = LocalContext.current
    val lookup by remember(phoneNumber) { viewModel.observeContactLookup(phoneNumber) }
        .collectAsStateWithLifecycle(initialValue = Lookup.Loading)
    val tags by viewModel.tags.collectAsStateWithLifecycle()

    // Loading and missing are rendered differently. Treating them the same
    // flashes a "not found" error on every open, before the query answers.
    when (val state = lookup) {
        is Lookup.Loading -> {
            LoadingState(label = "Opening contact…")
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
                Text("Contact not found", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "It may have been removed from your phone since the last sync. " +
                        "Pull a refresh on Home to re-sync.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }
        is Lookup.Found -> Unit
    }
    val current = (lookup as Lookup.Found<com.codeaza.bhaiyaaa.data.db.entity.ContactEntity>).value

    val calls by remember(current.matchKey) { viewModel.callsForContact(current.matchKey) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val memories by remember(phoneNumber) { viewModel.memoriesForContact(phoneNumber) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Aggregates come straight from the database as a Flow, so they stay in
    // step with the call list above without a manual refresh.
    val stats by remember(current.matchKey) { viewModel.observeStatsFor(current.matchKey) }
        .collectAsStateWithLifecycle(initialValue = null)

    var notesDraft by remember(current.phoneNumber) { mutableStateOf(current.notes.orEmpty()) }
    var newMemory by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ContactAvatar(current.name, size = 64)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        current.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        PhoneNumbers.forDisplay(current.phoneNumber),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    VipBadge(VipLevel.from(current.vipLevel))
                }
            }
        }

        item {
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
                    Text("Call")
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
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = (stats?.totalCalls ?: 0).toString(),
                    label = "Total calls",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = (stats?.missedCalls ?: 0).toString(),
                    label = "Missed",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = Formatting.duration(stats?.averageDurationSeconds ?: 0L),
                    label = "Avg length",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            SectionCard(title = "VIP tier") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (listOf(VipLevel.NONE) + VipLevel.assignable).forEach { level ->
                        FilterChip(
                            selected = VipLevel.from(current.vipLevel) == level,
                            onClick = { viewModel.setVipLevel(current.phoneNumber, level) },
                            label = { Text(level.label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (VipLevel.from(current.vipLevel)) {
                        VipLevel.NONE -> "Normal alerts."
                        VipLevel.VIP -> "Distinct vibration, a short flash pattern and a heads-up alert."
                        VipLevel.SUPER_VIP -> "Longer vibration, more flashes, high-priority alert."
                        VipLevel.EMERGENCY -> "Most insistent pattern this device allows."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = "Category") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag ->
                        FilterChip(
                            selected = current.tag == tag.name,
                            onClick = {
                                viewModel.setTag(
                                    current.phoneNumber,
                                    if (current.tag == tag.name) null else tag.name
                                )
                            },
                            label = { Text(tag.name) }
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "Importance") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Importance.entries.forEach { level ->
                        FilterChip(
                            selected = Importance.from(current.importance) == level,
                            onClick = { viewModel.setImportance(current.phoneNumber, level.storageValue) },
                            label = { Text(level.label) }
                        )
                    }
                }
            }
        }

        item {
            SectionCard(title = "Alerts for this contact") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("VIP alerts", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Mute just this person without changing their tier.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = current.notificationsEnabled,
                        onCheckedChange = {
                            viewModel.setContactNotifications(current.phoneNumber, it)
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Mark as spam", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Hides them from suggestions. BHAIYAAA never blocks calls itself.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = current.isSpam,
                        onCheckedChange = { viewModel.setSpam(current.phoneNumber, it) }
                    )
                }
            }
        }

        item {
            SectionCard(title = "Private notes") {
                OutlinedTextField(
                    value = notesDraft,
                    onValueChange = { notesDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Anything worth remembering about them") },
                    minLines = 3
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.setNotes(current.phoneNumber, notesDraft) },
                        enabled = notesDraft != current.notes.orEmpty()
                    ) { Text("Save note") }
                    if (notesDraft != current.notes.orEmpty()) {
                        TextButton(onClick = { notesDraft = current.notes.orEmpty() }) {
                            Text("Discard")
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Memories") {
                OutlinedTextField(
                    value = newMemory,
                    onValueChange = { newMemory = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Wants the deployment done by Friday") },
                    minLines = 2
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.addMemory(
                            body = newMemory,
                            contactPhoneNumber = current.phoneNumber,
                            source = MemorySource.MANUAL
                        )
                        newMemory = ""
                    },
                    enabled = newMemory.isNotBlank()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, Modifier.width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save memory")
                }

                if (memories.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    memories.take(5).forEach { memory ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text("\"${memory.body}\"", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${MemorySource.from(memory.source).label} · " +
                                    Formatting.relativeDateTime(memory.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Call history",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (calls.isEmpty()) {
            item {
                Text(
                    "No calls logged with this contact yet — use Call or Message above to " +
                        "start one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(calls.size, key = { calls[it].id }) { index ->
                val call = calls[index]
                CallRow(
                    call = call,
                    vipLevel = VipLevel.from(current.vipLevel),
                    onClick = { onOpenCall(call.id) }
                )
            }
        }
    }
}
