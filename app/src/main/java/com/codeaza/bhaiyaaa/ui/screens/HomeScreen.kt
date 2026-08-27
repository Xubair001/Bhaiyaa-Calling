package com.codeaza.bhaiyaaa.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.ai.ResourcePhrasebook
import com.codeaza.bhaiyaaa.domain.model.PersonalityMode
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.ui.BhaiyaaaViewModel
import com.codeaza.bhaiyaaa.domain.model.CallType
import com.codeaza.bhaiyaaa.ui.components.CallTypeIcon
import com.codeaza.bhaiyaaa.ui.components.ContactAvatar
import com.codeaza.bhaiyaaa.ui.components.InfoBanner
import com.codeaza.bhaiyaaa.ui.components.SectionCard
import com.codeaza.bhaiyaaa.ui.components.StatTile
import com.codeaza.bhaiyaaa.ui.components.VipBadge
import com.codeaza.bhaiyaaa.util.Formatting
import com.codeaza.bhaiyaaa.util.PhoneNumbers
import com.codeaza.bhaiyaaa.util.Permissions
import com.codeaza.bhaiyaaa.util.TimeRanges
import java.util.Calendar

/**
 * The dashboard. Every figure on this screen is read from the local database -
 * there is no seeded or sample content anywhere, so on a fresh install with no
 * permissions it honestly shows zeroes and explains why.
 */
@Composable
fun HomeScreen(
    viewModel: BhaiyaaaViewModel,
    onOpenCalls: () -> Unit,
    onOpenVip: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenContact: (String) -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val vips by viewModel.vipContacts.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val missedToday by viewModel.missedToday.collectAsStateWithLifecycle()
    val hasPermissions by viewModel.hasCorePermissions.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.onPermissionsChanged() }

    val greeting = rememberGreeting(settings.personality)

    val startOfDay = remember(calls) { TimeRanges.startOfDay(System.currentTimeMillis()) }
    val callsToday = remember(calls, startOfDay) { calls.count { it.timestamp >= startOfDay } }
    val vipCallsToday = remember(calls, vips, startOfDay) {
        val vipKeys = vips.map { it.matchKey }.toSet()
        calls.count { it.timestamp >= startOfDay && it.matchKey in vipKeys }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Apna banda, phone ke andar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isSyncing) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { viewModel.sync() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh from phone")
                    }
                }
            }
        }

        if (!hasPermissions) {
            item {
                val missing = remember(hasPermissions) { Permissions.missingCore(context) }
                InfoBanner(
                    text = "BHAIYAAA needs ${missing.joinToString(", ") { context.getString(it.titleRes) }} " +
                        "to show your real data.",
                    actionLabel = "Allow",
                    onAction = { permissionLauncher.launch(Permissions.coreRequestArray()) }
                )
            }
        }

        item {
            Row(
                // IntrinsicSize.Min sizes the row to its tallest child, and each
                // tile fills it - so a label that wraps to two lines no longer
                // leaves its neighbours visibly shorter. Handles large font
                // scales too, where any label may wrap.
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(
                    value = callsToday.toString(),
                    label = "Calls today",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = missedToday.toString(),
                    label = "Missed today",
                    modifier = Modifier.weight(1f),
                    accent = if (missedToday > 0) MaterialTheme.colorScheme.error else null
                )
                StatTile(
                    value = vips.size.toString(),
                    label = "VIP contacts",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            SectionCard(
                title = "VIP today",
                action = {
                    TextButton(onClick = onOpenVip) { Text("Manage") }
                }
            ) {
                if (vips.isEmpty()) {
                    Text(
                        "No VIPs yet. Open a contact and set their tier to get special alerts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = if (vipCallsToday > 0) {
                            "${Formatting.plural(vipCallsToday, "VIP call")} today"
                        } else {
                            "No VIP calls today"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    vips.take(3).forEach { contact ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenContact(contact.phoneNumber) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ContactAvatar(contact.name, size = 34)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                contact.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            VipBadge(VipLevel.from(contact.vipLevel))
                        }
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "Recent calls",
                action = { TextButton(onClick = onOpenCalls) { Text("See all") } }
            ) {
                if (calls.isEmpty()) {
                    Text(
                        if (hasPermissions) "No calls logged yet."
                        else "Grant the Call log permission to see your history.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    calls.take(4).forEach { call ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CallTypeIcon(CallType.from(call.type))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    call.contactName ?: PhoneNumbers.forDisplay(call.phoneNumber),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    Formatting.relativeDateTime(call.timestamp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (call.durationSeconds > 0) {
                                Text(
                                    Formatting.duration(call.durationSeconds),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (reminders.isNotEmpty()) {
            item {
                SectionCard(title = "Pending reminders") {
                    reminders.take(3).forEach { reminder ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                reminder.text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            reminder.dueAt?.let {
                                Spacer(Modifier.width(8.dp))
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

        if (memories.isNotEmpty()) {
            item {
                SectionCard(
                    title = "Memory",
                    action = { TextButton(onClick = onOpenMemory) { Text("All") } }
                ) {
                    val latest = memories.first()
                    Text(
                        "\"${latest.body}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Saved ${Formatting.relativeDateTime(latest.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SectionCard(title = "Call insights") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenInsights() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Insights,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "See your week: busiest hours, who you talk to most, missed vs answered.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }

        item {
            Text(
                text = "${contacts.size} contacts synced · everything stored on this phone only",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** Greeting text for the current hour, in the user's chosen tone. */
@Composable
private fun rememberGreeting(personality: PersonalityMode): String {
    val context = LocalContext.current
    return remember(personality) {
        val phrasebook = ResourcePhrasebook(context, personality)
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> phrasebook.greetingMorning()
            in 12..16 -> phrasebook.greetingAfternoon()
            else -> phrasebook.greetingEvening()
        }
    }
}

