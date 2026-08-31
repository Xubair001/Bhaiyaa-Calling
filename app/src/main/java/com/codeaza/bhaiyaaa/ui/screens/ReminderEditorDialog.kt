package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.util.Formatting
import java.util.Calendar
import java.util.TimeZone

/**
 * Edits a reminder's wording and when it is due.
 *
 * Reminders are created from plain language ("call Ali tomorrow at 5pm"), and
 * the time phrase is stripped out of the text once parsed. That leaves no way
 * to correct a misread time by retyping, so editing needs real controls rather
 * than another sentence to parse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditorDialog(
    initialText: String,
    initialDueAt: Long?,
    onSave: (String, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val zone = remember { TimeZone.getDefault() }
    var text by remember { mutableStateOf(initialText) }

    // Null means "someday": in the list but never alerting.
    var dayStart by remember { mutableStateOf(initialDueAt?.let { startOfDay(it, zone) }) }
    var pickingDay by remember { mutableStateOf(false) }

    val seed = initialDueAt ?: nextRoundHour(System.currentTimeMillis(), zone)
    val timeState = rememberTimePickerState(
        initialHour = fieldOf(seed, zone, Calendar.HOUR_OF_DAY),
        initialMinute = fieldOf(seed, zone, Calendar.MINUTE),
        is24Hour = false
    )

    val dueAt = dayStart?.let { it + timeState.hour * 3_600_000L + timeState.minute * 60_000L }

    if (pickingDay) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dayStart)
        DatePickerDialog(
            onDismissRequest = { pickingDay = false },
            confirmButton = {
                TextButton(onClick = {
                    // The picker hands back UTC midnight for the chosen day, so
                    // it is read in UTC and rebuilt locally. Using it directly
                    // lands on the previous day for anyone west of Greenwich.
                    pickerState.selectedDateMillis?.let { dayStart = localMidnightOf(it, zone) }
                    pickingDay = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { pickingDay = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit reminder") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reminder") },
                    maxLines = 3
                )
                Spacer(Modifier.height(16.dp))
                Text("When", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val today = startOfDay(System.currentTimeMillis(), zone)
                    val tomorrow = plusDays(today, zone, 1)
                    FilterChip(
                        selected = dayStart == null,
                        onClick = { dayStart = null },
                        label = { Text("Someday") }
                    )
                    FilterChip(
                        selected = dayStart == today,
                        onClick = { dayStart = today },
                        label = { Text("Today") }
                    )
                    FilterChip(
                        selected = dayStart == tomorrow,
                        onClick = { dayStart = tomorrow },
                        label = { Text("Tomorrow") }
                    )
                    FilterChip(
                        selected = dayStart != null && dayStart != today && dayStart != tomorrow,
                        onClick = { pickingDay = true },
                        label = { Text("Pick…") }
                    )
                }
                if (dayStart != null) {
                    Spacer(Modifier.height(12.dp))
                    // TimeInput rather than the dial, matching the prayer
                    // editor: the dial is ~330dp tall and gets clipped inside
                    // an AlertDialog on a normal phone.
                    TimeInput(state = timeState)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dueAt?.let { "Reminds you ${Formatting.whenDue(it)}" }
                        ?: "No time set - it stays in the list but never alerts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text.trim(), dueAt) },
                enabled = text.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun calendarAt(millis: Long, zone: TimeZone): Calendar =
    Calendar.getInstance(zone).apply { timeInMillis = millis }

private fun fieldOf(millis: Long, zone: TimeZone, field: Int): Int =
    calendarAt(millis, zone).get(field)

private fun startOfDay(millis: Long, zone: TimeZone): Long =
    calendarAt(millis, zone).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/** Calendar days, not 24-hour blocks - a DST night is 23 or 25 hours long. */
private fun plusDays(dayStart: Long, zone: TimeZone, days: Int): Long =
    calendarAt(dayStart, zone).apply { add(Calendar.DAY_OF_YEAR, days) }.timeInMillis

private fun localMidnightOf(utcMidnight: Long, zone: TimeZone): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
    return Calendar.getInstance(zone).apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun nextRoundHour(now: Long, zone: TimeZone): Long =
    calendarAt(now, zone).apply {
        add(Calendar.HOUR_OF_DAY, 1)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
