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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
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
import com.codeaza.bhaiyaaa.domain.model.PrayerMode
import com.codeaza.bhaiyaaa.domain.model.SilenceWindow
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.prayer.RamadanDay
import com.codeaza.bhaiyaaa.prayer.RamadanState
import com.codeaza.bhaiyaaa.prayer.RamadanTimes
import com.codeaza.bhaiyaaa.prayer.SilencePlan
import com.codeaza.bhaiyaaa.ui.prayer.PrayerViewModel
import com.codeaza.bhaiyaaa.ui.theme.CardShape
import com.codeaza.bhaiyaaa.ui.SukoonViewModel
import com.codeaza.bhaiyaaa.domain.model.CallType
import com.codeaza.bhaiyaaa.ui.components.CallTypeIcon
import com.codeaza.bhaiyaaa.ui.components.ContactAvatar
import com.codeaza.bhaiyaaa.ui.components.HadithCard
import com.codeaza.bhaiyaaa.ui.components.InfoBanner
import com.codeaza.bhaiyaaa.ui.components.SectionCard
import com.codeaza.bhaiyaaa.ui.components.StatTile
import com.codeaza.bhaiyaaa.ui.components.VipBadge
import com.codeaza.bhaiyaaa.domain.usecase.ReconnectSuggestion
import com.codeaza.bhaiyaaa.domain.usecase.ReconnectSuggestions
import com.codeaza.bhaiyaaa.util.Formatting
import com.codeaza.bhaiyaaa.util.HijriDate
import com.codeaza.bhaiyaaa.util.PhoneNumbers
import com.codeaza.bhaiyaaa.util.Permissions
import com.codeaza.bhaiyaaa.util.TimeRanges
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * The dashboard. Every figure on this screen is read from the local database -
 * there is no seeded or sample content anywhere, so on a fresh install with no
 * permissions it honestly shows zeroes and explains why.
 */
@Composable
fun HomeScreen(
    viewModel: SukoonViewModel,
    prayerViewModel: PrayerViewModel,
    onOpenPrayer: () -> Unit,
    onOpenQibla: () -> Unit,
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

    val prayerSettings by prayerViewModel.settings.collectAsStateWithLifecycle()
    val prayerWindows by prayerViewModel.todayWindows.collectAsStateWithLifecycle()
    val prayerAnchors by prayerViewModel.prayerAnchors.collectAsStateWithLifecycle()
    val callStats by viewModel.callStats.collectAsStateWithLifecycle()

    // Computed from state the dashboard already collects, so this card costs a
    // list walk rather than a query.
    val reconnect = remember(vips, callStats) {
        ReconnectSuggestions.forVips(vips, callStats, System.currentTimeMillis())
    }

    // The Hijri date is a platform conversion with no I/O, but it only changes
    // once a day, so it is remembered rather than recomputed every frame.
    val hijri = remember(settings.personality) { HijriDate.today() }
    val isRamadan = remember(hijri) { HijriDate.isRamadan() }

    // Named off the prayer times rather than computed again - suhoor ends when
    // Fajr comes in and iftar is Maghrib, both of which the app already knows.
    val ramadanDay = remember(prayerAnchors, isRamadan) {
        if (isRamadan) RamadanTimes.forDay(prayerAnchors) else null
    }

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
                        // The Hijri date when the platform can give one, the
                        // tagline when it cannot - the line never goes empty.
                        text = hijri ?: "Your people. Your quiet.",
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
                    text = "Sukoon needs ${missing.joinToString(", ") { context.getString(it.titleRes) }} " +
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
            PrayerCard(
                enabled = prayerSettings.enabled,
                needsLocation = prayerSettings.mode == PrayerMode.AUTOMATIC &&
                    !prayerSettings.hasLocation,
                windows = prayerWindows,
                onOpen = onOpenPrayer,
                onOpenQibla = onOpenQibla.takeIf { prayerSettings.hasLocation }
            )
        }

        // Only during Ramadan, and only once the times it needs are known.
        // For eleven months of the year this item does not exist.
        if (ramadanDay != null) {
            item { RamadanCard(day = ramadanDay) }
        }

        // The other half of "your people, your quiet". Only appears when
        // there is genuinely someone to call, so it is a nudge rather than
        // a permanent fixture.
        if (reconnect.isNotEmpty()) {
            item {
                ReconnectCard(
                    suggestions = reconnect,
                    onOpenContact = onOpenContact
                )
            }
        }

        // Directly under the prayer card, because it is about the prayer
        // period that card names - and nowhere near the top, because it is
        // informational and must not push the day's actual figures down.
        // Renders nothing at all when no prayer times are configured.
        item {
            HadithCard(anchors = prayerAnchors)
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

/**
 * Prayer silence, surfaced on the dashboard.
 *
 * This is the feature people install Sukoon for, and burying it three taps
 * deep in Settings would mean most of them never find it. Until it is set up
 * the card invites; once it is, it earns its place by answering the only
 * question that matters at a glance - when does my phone go quiet next.
 */
@Composable
private fun PrayerCard(
    enabled: Boolean,
    needsLocation: Boolean,
    windows: List<SilenceWindow>,
    onOpen: () -> Unit,
    /** Null hides the qibla link, which is meaningless without a location. */
    onOpenQibla: (() -> Unit)? = null
) {
    val now = System.currentTimeMillis()
    val active = remember(windows, now) { SilencePlan.activeWindow(windows, now) }
    val next = remember(windows, now) { SilencePlan.nextWindow(windows, now) }

    Card(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = CardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            // Sits in the tertiary container so it reads as an invitation
            // rather than as another statistic.
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = when {
                        active != null -> "${active.label} — phone is quiet"
                        !enabled -> "Namaz ka waqt, phone khamosh"
                        needsLocation -> "Prayer silence needs your location"
                        // No windows at all means nothing is configured yet.
                        // Saying "none left today" there reads as if it had run
                        // and finished, when in fact it has never run.
                        windows.isEmpty() -> "Prayer times aren't set yet"
                        next != null -> "Next: ${next.label} at ${Formatting.time(next.anchorMillis)}"
                        else -> "Prayer silence is on"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        active != null ->
                            "Until ${Formatting.time(active.endMillis)}"
                        !enabled ->
                            "Set your prayer times and Sukoon silences your phone for each " +
                                "one, then puts it back exactly as it was."
                        needsLocation ->
                            "Set a location, or enter the times yourself."
                        windows.isEmpty() ->
                            "Tap to set your five prayer times."
                        next != null ->
                            "Quiet from ${Formatting.time(next.startMillis)} to " +
                                "${Formatting.time(next.endMillis)}"
                        else ->
                            "Done for today. Next window is tomorrow at Fajr."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                )
                // Offer the action whenever there is nothing to show, not only
                // when the feature is switched off.
                if (!enabled || windows.isEmpty() || needsLocation) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onOpen) { Text("Set prayer times") }
                }
                if (onOpenQibla != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = onOpenQibla,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Find the qibla",
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            if (enabled && windows.isNotEmpty() && !needsLocation) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

/**
 * Suhoor and iftar, during Ramadan only.
 *
 * Counts down on a one-minute tick, not a one-second one. The last hour before
 * iftar is exactly when the phone is most likely to be sitting on a table with
 * the screen on, and a per-second countdown would recompose sixty times a
 * minute to redraw a line that only ever shows minutes.
 */
@Composable
private fun RamadanCard(day: RamadanDay) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(day) {
        while (true) {
            now = System.currentTimeMillis()
            delay(RAMADAN_TICK_MILLIS)
        }
    }

    val state = remember(day, now) { RamadanTimes.stateAt(day, now) }

    Card(
        Modifier.fillMaxWidth(),
        shape = CardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "RAMADAN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (state) {
                    is RamadanState.BeforeSuhoorEnds ->
                        "Suhoor ends in ${describeRemaining(state.millisRemaining)}"
                    is RamadanState.Fasting ->
                        "Iftar in ${describeRemaining(state.millisUntilIftar)}"
                    is RamadanState.Complete -> "Fast complete"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (state) {
                    is RamadanState.Complete ->
                        "Iftar was at ${Formatting.time(day.iftarAt)}"
                    else ->
                        "Suhoor until ${Formatting.time(day.suhoorEndsAt)} · " +
                            "iftar at ${Formatting.time(day.iftarAt)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
            )
        }
    }
}

/** "4h 12m", or "9 minutes" once it is close enough to count in minutes. */
private fun describeRemaining(millis: Long): String {
    val totalMinutes = (millis / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m"
    else Formatting.plural(minutes.toInt(), "minute")
}

private const val RAMADAN_TICK_MILLIS = 60_000L

/**
 * People you have not spoken to in a while.
 *
 * The counterweight to everything else on this dashboard, which is about calls
 * that already happened. This one is about a call that has not.
 *
 * Deliberately worded as an observation, not an instruction - "it has been
 * three months" rather than "you should call". The app does not know why, and
 * a guilt-trip about a relationship it cannot see would be a bad guess.
 */
@Composable
private fun ReconnectCard(
    suggestions: List<ReconnectSuggestion>,
    onOpenContact: (String) -> Unit
) {
    SectionCard(title = "Been a while") {
        suggestions.forEach { suggestion ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenContact(suggestion.contact.phoneNumber) }
                    .heightIn(min = 48.dp)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ContactAvatar(suggestion.contact.name, size = 34)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        suggestion.contact.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when (val days = suggestion.daysSince) {
                            null -> "No calls on record yet"
                            else -> "Last spoke ${describeGap(days)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                VipBadge(suggestion.level)
            }
        }
    }
}

/**
 * "3 months ago" rather than "94 days ago".
 *
 * Nobody counts in days past a fortnight, and a precise number invites the
 * reader to do arithmetic instead of picking up the phone.
 */
private fun describeGap(days: Long): String = when {
    days < 14 -> Formatting.plural(days.toInt(), "day") + " ago"
    days < 60 -> "${days / 7} weeks ago"
    days < 365 -> "${days / 30} months ago"
    else -> "over a year ago"
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

