package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.ai.TimeExpressions
import com.codeaza.bhaiyaaa.data.db.entity.ReminderEntity
import com.codeaza.bhaiyaaa.domain.usecase.ReminderBucket
import com.codeaza.bhaiyaaa.domain.usecase.ReminderGrouping
import com.codeaza.bhaiyaaa.ui.SukoonViewModel
import com.codeaza.bhaiyaaa.ui.components.EmptyState
import com.codeaza.bhaiyaaa.ui.theme.CardShape
import com.codeaza.bhaiyaaa.ui.theme.Motion
import com.codeaza.bhaiyaaa.util.Formatting
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.TimeZone

/**
 * Reminders, grouped by when they are due.
 *
 * Added in plain language - "call Ali tomorrow at 5pm" is parsed for a time
 * here exactly as it is in the Assistant, so both routes behave the same way -
 * and correctable afterwards through the editor, because a misread phrase used
 * to mean deleting and retyping the whole thing.
 */
@Composable
fun RemindersScreen(viewModel: SukoonViewModel) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val done by viewModel.doneReminders.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ReminderEntity?>(null) }
    var showDone by remember { mutableStateOf(false) }

    // Re-read the clock on a timer. Overdue is decided against the current
    // moment, so without this a reminder that comes due while the screen is
    // open stays filed under Today until something else recomposes.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }

    val groups = remember(reminders, now) {
        ReminderGrouping.group(reminders, now) { it.dueAt }
    }

    editing?.let { target ->
        ReminderEditorDialog(
            initialText = target.text,
            initialDueAt = target.dueAt,
            onSave = { text, dueAt ->
                viewModel.editReminder(target.id, text, dueAt)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Composer(
                draft = draft,
                onDraftChange = { draft = it },
                onAdd = {
                    val parsed = TimeExpressions.parse(draft, System.currentTimeMillis())
                    val body = TimeExpressions.stripTimePhrase(draft, parsed.matchedText)
                    viewModel.addReminder(body.ifBlank { draft }, parsed.dueAt)
                    draft = ""
                }
            )
        }

        if (reminders.isEmpty() && done.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Notifications,
                    title = "No reminders yet",
                    body = "Add one above, or just tell the Assistant \"remind me to…\"."
                )
            }
        }

        groups.forEach { group ->
            item(key = "header-${group.bucket.name}") {
                GroupHeader(group.bucket, group.items.size, Modifier.animateItem())
            }
            items(group.items, key = { it.id }) { reminder ->
                ReminderRow(
                    reminder = reminder,
                    overdue = group.bucket == ReminderBucket.OVERDUE,
                    modifier = Modifier.animateItem(),
                    now = now,
                    onToggleDone = { viewModel.setReminderDone(reminder.id, it) },
                    onEdit = { editing = reminder },
                    onSnooze = { until -> viewModel.snoozeReminder(reminder.id, until) },
                    onDelete = { viewModel.deleteReminder(reminder.id) }
                )
            }
        }

        if (done.isNotEmpty()) {
            item(key = "done-header") {
                DoneHeader(count = done.size, expanded = showDone) { showDone = !showDone }
            }
            if (showDone) {
                items(done, key = { "done-${it.id}" }) { reminder ->
                    ReminderRow(
                        reminder = reminder,
                        overdue = false,
                        modifier = Modifier.animateItem(),
                        now = now,
                        onToggleDone = { viewModel.setReminderDone(reminder.id, it) },
                        onEdit = { editing = reminder },
                        onSnooze = { until -> viewModel.snoozeReminder(reminder.id, until) },
                        onDelete = { viewModel.deleteReminder(reminder.id) }
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Reminders fire through Android's alarm scheduler. If exact alarms are not " +
                    "permitted, they can land a few minutes late while the phone is in deep sleep.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Composer(draft: String, onDraftChange: (String) -> Unit, onAdd: () -> Unit) {
    // Parse as the user types so a misread phrase is visible before saving
    // rather than a surprise hours later.
    val parsed = remember(draft) {
        if (draft.isBlank()) null else TimeExpressions.parse(draft, System.currentTimeMillis())
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = CardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Call Ali tomorrow at 5pm") },
                maxLines = 3
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        draft.isBlank() -> "Times are read from what you type."
                        parsed?.dueAt != null -> "Reminds you ${Formatting.whenDue(parsed.dueAt!!)}"
                        else -> "No time found - it will sit under Someday."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (parsed?.dueAt != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Button(onClick = onAdd, enabled = draft.isNotBlank()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add")
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(bucket: ReminderBucket, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = bucket.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            // Overdue is the one group that earns attention colour. Everything
            // else is a neutral label so the section that matters stands out.
            color = if (bucket == ReminderBucket.OVERDUE) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DoneHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "COMPLETED",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        // One icon rotated, not two swapped: the chevron turning is what says
        // the section opened, and a swap at this size just flickers.
        val turn by animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = tween(Motion.STANDARD, easing = Motion.Standard),
            label = "completedChevron"
        )
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = if (expanded) "Hide completed" else "Show completed",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(turn)
        )
    }
}

/** Internal rather than private so the instrumented tests can render one. */
@Composable
internal fun ReminderRow(
    reminder: ReminderEntity,
    overdue: Boolean,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
    onToggleDone: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onSnooze: (Long) -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = CardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (overdue) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = reminder.isDone, onCheckedChange = onToggleDone)
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = reminder.text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (overdue) FontWeight.Medium else FontWeight.Normal,
                    textDecoration = if (reminder.isDone) TextDecoration.LineThrough else null,
                    color = if (reminder.isDone) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                reminder.dueAt?.let { due ->
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (overdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = Formatting.whenDue(due, now),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More actions for ${reminder.text}"
                    )
                }
                RowMenu(
                    expanded = menuOpen,
                    hasTime = reminder.dueAt != null,
                    onDismiss = { menuOpen = false },
                    onEdit = onEdit,
                    onSnooze = onSnooze,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun RowMenu(
    expanded: Boolean,
    hasTime: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSnooze: (Long) -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (hasTime) {
            DropdownMenuItem(
                text = { Text("In 10 minutes") },
                onClick = { onSnooze(System.currentTimeMillis() + 10 * 60_000L); onDismiss() }
            )
            DropdownMenuItem(
                text = { Text("In an hour") },
                onClick = { onSnooze(System.currentTimeMillis() + 60 * 60_000L); onDismiss() }
            )
            DropdownMenuItem(
                text = { Text("Tomorrow morning") },
                onClick = { onSnooze(tomorrowMorning()); onDismiss() }
            )
            HorizontalDivider()
        }
        DropdownMenuItem(
            text = { Text("Edit…") },
            onClick = { onEdit(); onDismiss() }
        )
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            onClick = { onDelete(); onDismiss() }
        )
    }
}

/** 9am tomorrow in the phone's own zone. */
private fun tomorrowMorning(): Long =
    Calendar.getInstance(TimeZone.getDefault()).apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
