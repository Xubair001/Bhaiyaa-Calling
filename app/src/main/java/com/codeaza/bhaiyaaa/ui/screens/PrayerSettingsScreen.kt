package com.codeaza.bhaiyaaa.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.codeaza.bhaiyaaa.domain.model.PrayerMadhab
import com.codeaza.bhaiyaaa.domain.model.PrayerMethod
import com.codeaza.bhaiyaaa.domain.model.PrayerMode
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.prayer.PrayerScheduler
import com.codeaza.bhaiyaaa.prayer.SilenceController
import com.codeaza.bhaiyaaa.ui.components.InfoBanner
import com.codeaza.bhaiyaaa.ui.components.SectionCard
import com.codeaza.bhaiyaaa.ui.components.SettingsSwitchRow
import com.codeaza.bhaiyaaa.ui.prayer.PrayerViewModel
import com.codeaza.bhaiyaaa.util.Formatting

/**
 * Prayer silence settings.
 *
 * The whole screen is arranged around one honest idea: this feature can only
 * work if the platform lets it, so anything missing is stated at the top with
 * the way to fix it, rather than the toggles quietly doing nothing.
 */
@Composable
fun PrayerSettingsScreen(viewModel: PrayerViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val prayers by viewModel.prayers.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val windows by viewModel.todayWindows.collectAsStateWithLifecycle()

    // Re-read platform grants on resume: both are changed in system settings,
    // so the only reliable moment to check is coming back from them.
    var grantVersion by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) grantVersion++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasDnd = remember(grantVersion) { SilenceController.hasDndAccess(context) }
    val hasExact = remember(grantVersion) { PrayerScheduler.canScheduleExact(context) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.useCurrentLocation() }

    var editing by remember { mutableStateOf<Prayer?>(null) }
    var showCoordinates by remember { mutableStateOf(false) }

    val byName = remember(prayers) { prayers.associateBy { it.name } }
    val windowByPrayer = remember(windows) { windows.associateBy { it.prayer } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsSwitchRow(
                title = "Silence during prayer",
                subtitle = "Turns on Do Not Disturb for each prayer, then puts your phone " +
                    "back exactly as it was",
                checked = settings.enabled,
                onCheckedChange = { viewModel.setEnabled(it) }
            )
        }

        if (settings.enabled && !hasDnd) {
            item {
                InfoBanner(
                    text = "BHAIYAAA can't silence your phone without Do Not Disturb access.",
                    actionLabel = "Allow",
                    onAction = {
                        runCatching {
                            context.startActivity(
                                com.codeaza.bhaiyaaa.notifications.NotificationChannels.dndAccessIntent()
                            )
                        }
                    }
                )
            }
        }

        if (settings.enabled && !hasExact) {
            item {
                InfoBanner(
                    text = "Without exact alarms, a prayer window can start a few minutes late.",
                    actionLabel = "Fix",
                    onAction = {
                        PrayerScheduler.exactAlarmSettingsIntent(context)?.let {
                            runCatching { context.startActivity(it) }
                        }
                    }
                )
            }
        }

        if (settings.enabled) {
            item {
                SectionCard(title = "Where times come from") {
                    PrayerMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = settings.mode == mode,
                                    role = Role.RadioButton,
                                    onClick = { viewModel.setMode(mode) }
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = settings.mode == mode, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            if (settings.mode == PrayerMode.AUTOMATIC) {
                item {
                    SectionCard(title = "Location") {
                        Text(
                            if (settings.hasLocation) settings.locationLabel
                            else "Not set — times can't be calculated yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (settings.hasLocation) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (viewModel.hasLocationPermission()) {
                                    viewModel.useCurrentLocation()
                                } else {
                                    locationLauncher.launch(
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                }
                            }) { Text("Use my location") }
                            OutlinedButton(onClick = { showCoordinates = true }) {
                                Text("Type coordinates")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Coordinates stay on this phone. Nothing is sent anywhere — the " +
                                "times are worked out on the device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    SectionCard(title = "Calculation method") {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PrayerMethod.entries.forEach { method ->
                                FilterChip(
                                    selected = settings.method == method,
                                    onClick = { viewModel.setMethod(method) },
                                    label = { Text(method.label) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            settings.method.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        Text("Asr calculation", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrayerMadhab.entries.forEach { madhab ->
                                FilterChip(
                                    selected = settings.madhab == madhab,
                                    onClick = { viewModel.setMadhab(madhab) },
                                    label = { Text(madhab.label) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Today") {
                    if (windows.isEmpty()) {
                        Text(
                            if (settings.mode == PrayerMode.AUTOMATIC && !settings.hasLocation) {
                                "Set your location and the five times appear here."
                            } else {
                                "No times set yet. Tap a prayer below to enter one."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Prayer.entries.forEach { prayer ->
                        val row = byName[prayer.storageValue]
                        val window = windowByPrayer[prayer]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(prayer.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    buildString {
                                        if (window != null) {
                                            append(Formatting.time(window.startMillis))
                                            if (window.isOverridden) append(" · your time")
                                            append(" · silent ${window.silenceMinutes} min")
                                        } else {
                                            append("No time set")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { editing = prayer }) { Text("Edit") }
                            Switch(
                                checked = row?.enabled ?: true,
                                onCheckedChange = { viewModel.setPrayerEnabled(prayer, it) }
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Who still gets through") {
                    Text(
                        "Prayer silence outranks a VIP tier unless you allow it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    VipLevel.assignable.forEach { level ->
                        val rule = rules.firstOrNull { it.vipLevel == level.storageValue }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${level.label} rings through",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = rule?.ringsDuringPrayer ?: false,
                                onCheckedChange = { viewModel.setRingsDuringPrayer(level, it) }
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Alarms still ring during a prayer window — BHAIYAAA uses Do Not Disturb's " +
                        "alarms-only mode, never total silence.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    editing?.let { prayer ->
        val row = byName[prayer.storageValue]
        EditPrayerDialog(
            prayerLabel = prayer.label,
            currentMinutes = row?.manualMinutesFromMidnight,
            currentSilence = row?.silenceMinutes ?: 20,
            canClearOverride = settings.mode == PrayerMode.AUTOMATIC,
            onDismiss = { editing = null },
            onSave = { minutes, silence ->
                viewModel.setManualTime(prayer, minutes)
                viewModel.setSilenceMinutes(prayer, silence)
                editing = null
            }
        )
    }

    if (showCoordinates) {
        CoordinatesDialog(
            onDismiss = { showCoordinates = false },
            onSave = { lat, lng ->
                viewModel.setManualLocation(lat, lng)
                showCoordinates = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPrayerDialog(
    prayerLabel: String,
    currentMinutes: Int?,
    currentSilence: Int,
    canClearOverride: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int?, Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = (currentMinutes ?: 0) / 60,
        initialMinute = (currentMinutes ?: 0) % 60,
        is24Hour = false
    )
    var silence by remember { mutableIntStateOf(currentSilence) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(prayerLabel) },
        text = {
            Column {
                TimePicker(state = state)
                Spacer(Modifier.height(12.dp))
                Text("Stay silent for $silence minutes", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = silence.toFloat(),
                    onValueChange = { silence = it.toInt() },
                    valueRange = 5f..60f,
                    steps = 10
                )
                if (canClearOverride) {
                    Text(
                        "Setting a time here overrides the calculation for this prayer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(state.hour * 60 + state.minute, silence) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (canClearOverride && currentMinutes != null) {
                    TextButton(onClick = { onSave(null, silence) }) { Text("Use calculated") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun CoordinatesDialog(
    onDismiss: () -> Unit,
    onSave: (Double, Double) -> Unit
) {
    var lat by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }
    val valid = lat.toDoubleOrNull() != null && lng.toDoubleOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter coordinates") },
        text = {
            Column {
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it },
                    label = { Text("Latitude") },
                    placeholder = { Text("31.5204") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lng,
                    onValueChange = { lng = it },
                    label = { Text("Longitude") },
                    placeholder = { Text("74.3587") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(lat.toDouble(), lng.toDouble()) },
                enabled = valid
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
