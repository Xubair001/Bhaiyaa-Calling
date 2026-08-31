package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.data.db.entity.SilenceScheduleEntity
import com.codeaza.bhaiyaaa.domain.model.PrayerSilenceMode
import com.codeaza.bhaiyaaa.domain.model.Weekdays
import java.util.Calendar

/** What the editor returns. */
data class ScheduleDraft(
    val id: Long?,
    val label: String,
    val startMinutes: Int,
    val durationMinutes: Int,
    val daysMask: Int,
    val mode: PrayerSilenceMode
)

/**
 * Create or edit a custom quiet period.
 *
 * A new schedule opens at the current time, because the overwhelmingly common
 * case is "quiet from about now" - a meeting starting, a class beginning. It
 * also means the meridiem is already right, which a fixed default never is more
 * than half the time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorDialog(
    existing: SilenceScheduleEntity?,
    onDismiss: () -> Unit,
    onSave: (ScheduleDraft) -> Unit,
    onDelete: ((Long) -> Unit)? = null
) {
    val nowMinutes = remember {
        val c = Calendar.getInstance()
        c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }
    val start = existing?.startMinutesFromMidnight ?: nowMinutes

    val timeState = rememberTimePickerState(
        initialHour = start / 60,
        initialMinute = start % 60,
        is24Hour = false
    )
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var duration by remember { mutableIntStateOf(existing?.durationMinutes ?: 30) }
    var days by remember { mutableIntStateOf(existing?.daysMask ?: Weekdays.EVERY_DAY) }
    var mode by remember {
        mutableStateOf(PrayerSilenceMode.from(existing?.silenceMode))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New quiet time" else "Edit quiet time") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name") },
                    placeholder = { Text("Meeting, class, sleep…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))
                Text("Starts at", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                TimeInput(state = timeState)

                Spacer(Modifier.height(8.dp))
                Text("Lasts $duration minutes", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = duration.toFloat(),
                    onValueChange = { duration = it.toInt() },
                    valueRange = 5f..240f,
                    steps = 46
                )
                Text(
                    endsAtLabel(timeState.hour, timeState.minute, duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))
                Text("Repeats", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Weekdays.labels.forEachIndexed { index, day ->
                        FilterChip(
                            selected = Weekdays.isSet(days, index),
                            onClick = { days = Weekdays.toggle(days, index) },
                            label = { Text(day.take(1)) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Every day" to Weekdays.EVERY_DAY,
                        "Weekdays" to Weekdays.WEEKDAYS,
                        "Weekends" to Weekdays.WEEKENDS
                    ).forEach { (name, mask) ->
                        TextButton(onClick = { days = mask }) { Text(name) }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("How quiet", style = MaterialTheme.typography.titleSmall)
                PrayerSilenceMode.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == option, onClick = { mode = option })
                        Spacer(Modifier.width(8.dp))
                        Text(option.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    ScheduleDraft(
                        id = existing?.id,
                        label = label,
                        startMinutes = timeState.hour * 60 + timeState.minute,
                        durationMinutes = duration,
                        daysMask = days,
                        mode = mode
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null && onDelete != null) {
                    TextButton(onClick = { onDelete(existing.id) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** "Quiet until 2:45 PM" - or names the next day when it runs past midnight. */
private fun endsAtLabel(hour: Int, minute: Int, duration: Int): String {
    val total = hour * 60 + minute + duration
    val endHour = (total / 60) % 24
    val endMinute = total % 60
    val crossesMidnight = total >= 24 * 60
    val suffix = if (endHour < 12) "AM" else "PM"
    val display = when {
        endHour == 0 -> 12
        endHour > 12 -> endHour - 12
        else -> endHour
    }
    val time = "%d:%02d %s".format(display, endMinute, suffix)
    return if (crossesMidnight) "Quiet until $time the next day" else "Quiet until $time"
}
