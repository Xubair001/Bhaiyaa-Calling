package com.codeaza.bhaiyaaa.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.domain.model.Meridiem
import com.codeaza.bhaiyaaa.ui.theme.NumericTextStyle
import com.codeaza.bhaiyaaa.ui.theme.PillShape

/**
 * A twelve-hour clock entry whose AM/PM is fixed.
 *
 * Material's own `TimeInput` was what this replaces, and the reason is the
 * meridiem toggle: it let Fajr be saved in the afternoon and Asr before dawn,
 * which is not a slightly wrong setting but a prayer silenced twelve hours out.
 * The brief asked for invalid selections to be impossible rather than reported
 * afterwards, and there is no way to reach that with a control that offers the
 * wrong half of the clock and then complains.
 *
 * So the meridiem is shown, and stated, but is not a control. It reads as a
 * fact about the prayer - which is what it is - rather than a choice someone
 * has to get right. Hours and minutes are ordinary numeric fields: precise,
 * large enough to hit, and legible at any font scale, which a dial is not.
 *
 * @param minutesFromMidnight the current value, always inside [meridiem].
 * @param onChange receives a value that is already inside [meridiem]; a caller
 *   never has to re-validate what this emits.
 */
@Composable
fun FixedMeridiemClockField(
    minutesFromMidnight: Int,
    meridiem: Meridiem,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Explains why the meridiem cannot be changed. Announced with it. */
    meridiemNote: String
) {
    // The twelve-hour face: 12 rather than 0 at the top of each half, which is
    // how a clock is read and what every user will type.
    val hour24 = minutesFromMidnight / 60
    val hour12 = when {
        hour24 % 12 == 0 -> 12
        else -> hour24 % 12
    }
    val minute = minutesFromMidnight % 60

    // Held as text so a half-typed value ("1" on the way to "12") is not
    // snapped to something else under the user's fingers. Committed on every
    // keystroke that parses, so there is no separate "apply" step to forget.
    var hourText by remember(minutesFromMidnight) { mutableStateOf(hour12.toString()) }
    var minuteText by remember(minutesFromMidnight) {
        mutableStateOf(minute.toString().padStart(2, '0'))
    }

    fun commit(newHour12: Int, newMinute: Int) {
        val safeHour = newHour12.coerceIn(1, 12)
        val safeMinute = newMinute.coerceIn(0, 59)
        // 12 o'clock is the zero of its half: 12:30 AM is 00:30, 12:30 PM is
        // 12:30. Getting this backwards is the classic clock-arithmetic bug.
        val hourWithinHalf = if (safeHour == 12) 0 else safeHour
        onChange(meridiem.minuteRange.first + hourWithinHalf * 60 + safeMinute)
    }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            OutlinedTextField(
                value = hourText,
                onValueChange = { raw ->
                    val digits = raw.filter(Char::isDigit).take(2)
                    hourText = digits
                    digits.toIntOrNull()?.let { commit(it, minuteText.toIntOrNull() ?: minute) }
                },
                label = { Text("Hour") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.merge(NumericTextStyle)
                    .copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp)
            )
            Text(
                ":",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    // The separator is decoration; the two fields are already
                    // labelled for a screen reader.
                    .clearAndSetSemantics {}
            )
            OutlinedTextField(
                value = minuteText,
                onValueChange = { raw ->
                    val digits = raw.filter(Char::isDigit).take(2)
                    minuteText = digits
                    digits.toIntOrNull()?.let { commit(hourText.toIntOrNull() ?: hour12, it) }
                },
                label = { Text("Minute") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.merge(NumericTextStyle)
                    .copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp)
            )
            Spacer(Modifier.width(12.dp))
            Surface(
                shape = PillShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.semantics {
                    contentDescription = "${meridiem.label}. $meridiemNote"
                }
            ) {
                Box(
                    Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        meridiem.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            meridiemNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Already announced as part of the badge above.
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}
