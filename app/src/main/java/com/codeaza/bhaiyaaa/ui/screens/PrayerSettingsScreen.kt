package com.codeaza.bhaiyaaa.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.data.db.entity.PrayerEntity
import com.codeaza.bhaiyaaa.data.db.entity.SilenceScheduleEntity
import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.codeaza.bhaiyaaa.domain.model.PrayerMadhab
import com.codeaza.bhaiyaaa.domain.model.PrayerMethod
import com.codeaza.bhaiyaaa.domain.model.PrayerMode
import com.codeaza.bhaiyaaa.domain.model.PrayerSettings
import com.codeaza.bhaiyaaa.domain.model.PrayerSilenceMode
import com.codeaza.bhaiyaaa.domain.model.SilenceWindow
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.domain.model.Weekdays
import com.codeaza.bhaiyaaa.prayer.PrayerScheduler
import com.codeaza.bhaiyaaa.prayer.SilenceController
import com.codeaza.bhaiyaaa.ui.components.FixedMeridiemClockField
import com.codeaza.bhaiyaaa.ui.components.InfoBanner
import com.codeaza.bhaiyaaa.ui.components.ReliabilityCard
import com.codeaza.bhaiyaaa.ui.components.SectionCard
import com.codeaza.bhaiyaaa.ui.components.SettingsSwitchRow
import com.codeaza.bhaiyaaa.ui.prayer.PrayerViewModel
import com.codeaza.bhaiyaaa.ui.prayer.QuietTimesFocus
import com.codeaza.bhaiyaaa.ui.prayer.QuietTimesSection
import com.codeaza.bhaiyaaa.ui.prayer.QuietTimesLayout
import com.codeaza.bhaiyaaa.util.BackgroundReliability
import com.codeaza.bhaiyaaa.util.Formatting

/**
 * Quiet times: the user's own quiet periods, prayer silence, and the adhan.
 *
 * The screen is arranged around one honest idea: this feature can only work if
 * the platform lets it, so anything missing is stated at the top with the way
 * to fix it, rather than the toggles quietly doing nothing.
 *
 * Its sections reorder around what the user is doing - see [QuietTimesLayout]
 * for the rules and why they are rules rather than three separate screens. The
 * mechanism is a `LazyColumn` keyed by section, so a change in order moves the
 * existing items rather than recomposing new ones, and nothing jumps.
 */
@Composable
fun PrayerSettingsScreen(
    viewModel: PrayerViewModel,
    /** Where the screen was opened from, if that said anything about intent. */
    initialFocus: QuietTimesFocus = QuietTimesFocus.NONE,
    onOpenRecordings: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val prayers by viewModel.prayers.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val windows by viewModel.todayWindows.collectAsStateWithLifecycle()
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()

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

    // Asking the platform is a system call, so it happens on the two occasions
    // the answer can change rather than on every recomposition.
    val blockedReason by viewModel.blockedReason.collectAsStateWithLifecycle()
    LaunchedEffect(settings.silenceMode, grantVersion) { viewModel.refreshBlockedReason() }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.useCurrentLocation() }

    /**
     * Picks any audio the user already has. `OpenDocument` with a persistable
     * grant rather than `GetContent`: the URI has to survive a reboot, because
     * it will be read by an alarm days from now, and a one-shot grant would be
     * gone by then.
     */
    val soundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.setAdhanSound(uri.toString(), uri.lastPathSegment ?: "Chosen sound")
        }
    }

    var focus by remember { mutableStateOf(initialFocus) }
    var editing by remember { mutableStateOf<Prayer?>(null) }
    var showCoordinates by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<SilenceScheduleEntity?>(null) }
    var creatingSchedule by remember { mutableStateOf(false) }
    var showTimeZones by remember { mutableStateOf(false) }

    val byName = remember(prayers) { prayers.associateBy { it.name } }
    // Keyed by the window's stable key, since the list also holds custom
    // schedules, which have no Prayer at all.
    val windowByKey = remember(windows) { windows.associateBy { it.key } }

    /**
     * Whether there is actually a warning to show.
     *
     * Computed here rather than inside the section, because a section that
     * renders nothing still occupies a slot in the list - and with
     * `spacedBy` that is a visible band of dead space at the top of the
     * screen. An empty section is dropped rather than drawn empty.
     */
    //
    // Remembered against grantVersion like the other two: both of these are
    // binder calls into PowerManager, and reading them straight from the
    // composition ran them on every single recomposition of the screen.
    val backgroundRestricted = remember(grantVersion) {
        !BackgroundReliability.isIgnoringBatteryOptimizations(context) ||
            BackgroundReliability.hasAggressiveBackgroundPolicy()
    }
    val hasWarnings = settings.enabled && (!hasDnd || !hasExact || backgroundRestricted)

    val sections = remember(focus, settings.enabled, settings.mode, hasWarnings) {
        QuietTimesLayout.order(
            focus = focus,
            prayerEnabled = settings.enabled,
            automatic = settings.mode == PrayerMode.AUTOMATIC
        ).filterNot { it == QuietTimesSection.WARNINGS && !hasWarnings }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(
            count = sections.size,
            // Keyed by section, so reordering moves the existing item rather
            // than tearing it down and building a new one somewhere else -
            // which is what would make the list flicker and lose scroll.
            key = { sections[it].name }
        ) { index ->
            val section = sections[index]
            // animateItem covers the move. Without it a reorder is a jump;
            // with it the section visibly travels to its new place, which is
            // what makes the rearrangement legible rather than startling.
            Column(Modifier.animateItem()) {
                when (section) {
                    QuietTimesSection.WARNINGS -> WarningsSection(
                        enabled = settings.enabled,
                        hasDnd = hasDnd,
                        hasExact = hasExact,
                        grantVersion = grantVersion
                    )

                    QuietTimesSection.CUSTOM_QUIET -> CustomQuietSection(
                        schedules = schedules,
                        onAdd = {
                            focus = QuietTimesFocus.QUIET_TIME
                            creatingSchedule = true
                        },
                        onEdit = {
                            focus = QuietTimesFocus.QUIET_TIME
                            editingSchedule = it
                        },
                        onToggle = viewModel::setScheduleEnabled
                    )

                    QuietTimesSection.PRAYER_SWITCH -> SettingsSwitchRow(
                        title = "Silence during prayer",
                        subtitle = "Turns on Do Not Disturb for each prayer, then puts your " +
                            "phone back exactly as it was",
                        checked = settings.enabled,
                        onCheckedChange = {
                            focus = QuietTimesFocus.PRAYER_SWITCH
                            viewModel.setEnabled(it)
                        }
                    )

                    QuietTimesSection.HOW_QUIET -> HowQuietSection(
                        selected = settings.silenceMode,
                        blockedReason = blockedReason,
                        onSelect = viewModel::setSilenceMode,
                        onTest = viewModel::testSilenceNow
                    )

                    QuietTimesSection.TIME_SOURCE -> TimeSourceSection(
                        selected = settings.mode,
                        onSelect = viewModel::setMode
                    )

                    QuietTimesSection.LOCATION -> LocationSection(
                        settings = settings,
                        onUseLocation = {
                            if (viewModel.hasLocationPermission()) viewModel.useCurrentLocation()
                            else locationLauncher.launch(
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        },
                        onTypeCoordinates = { showCoordinates = true }
                    )

                    QuietTimesSection.METHOD -> MethodSection(
                        settings = settings,
                        onMethod = viewModel::setMethod,
                        onMadhab = viewModel::setMadhab
                    )

                    QuietTimesSection.PRAYER_TIMES -> PrayerTimesSection(
                        settings = settings,
                        byName = byName,
                        windowByKey = windowByKey,
                        anyWindows = windows.isNotEmpty(),
                        onEdit = {
                            focus = QuietTimesFocus.PRAYER_TIMES
                            editing = it
                        },
                        onToggle = { prayer, on ->
                            focus = QuietTimesFocus.PRAYER_TIMES
                            viewModel.setPrayerEnabled(prayer, on)
                        }
                    )

                    QuietTimesSection.ADHAN -> AdhanSection(
                        settings = settings,
                        onEnabled = viewModel::setAdhanEnabled,
                        onPickFile = { soundPicker.launch(arrayOf("audio/*")) },
                        onUseDefault = { viewModel.setAdhanSound(null, "") },
                        onOpenRecordings = onOpenRecordings
                    )

                    QuietTimesSection.WHO_RINGS -> WhoRingsSection(
                        rules = rules,
                        onChange = viewModel::setRingsDuringPrayer
                    )

                    QuietTimesSection.TIME_ZONE -> TimeZoneSection(
                        settings = settings,
                        onChange = { showTimeZones = true },
                        onFollowPhone = { viewModel.setTimeZone(null) }
                    )

                    QuietTimesSection.FOOTNOTE -> {
                        Text(
                            "Alarms still ring during a prayer window — Sukoon uses Do Not " +
                                "Disturb's alarms-only mode, never total silence.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (creatingSchedule || editingSchedule != null) {
        ScheduleEditorDialog(
            existing = editingSchedule,
            onDismiss = { creatingSchedule = false; editingSchedule = null },
            onSave = { draft ->
                viewModel.saveSchedule(
                    id = draft.id,
                    label = draft.label,
                    startMinutes = draft.startMinutes,
                    durationMinutes = draft.durationMinutes,
                    daysMask = draft.daysMask,
                    mode = draft.mode
                )
                creatingSchedule = false; editingSchedule = null
            },
            onDelete = { id ->
                viewModel.deleteSchedule(id)
                creatingSchedule = false; editingSchedule = null
            }
        )
    }

    if (showTimeZones) {
        TimeZoneDialog(
            options = viewModel.timeZoneOptions(),
            selected = settings.timeZoneId,
            onDismiss = { showTimeZones = false },
            onPick = { id -> viewModel.setTimeZone(id); showTimeZones = false }
        )
    }

    editing?.let { prayer ->
        val row = byName[prayer.storageValue]
        EditPrayerDialog(
            prayer = prayer,
            currentMinutes = row?.manualMinutesFromMidnight,
            currentSilence = row?.silenceMinutes ?: DEFAULT_SILENCE_MINUTES,
            currentOffset = row?.startOffsetMinutes ?: DEFAULT_START_OFFSET_MINUTES,
            canClearOverride = settings.mode == PrayerMode.AUTOMATIC,
            calculatedTime = windowByKey[SilenceWindow.prayerKey(prayer)]
                ?.takeIf { !it.isOverridden }
                ?.let { Formatting.time(it.anchorMillis) },
            onDismiss = { editing = null },
            onSave = { minutes, silence, offset ->
                // One call, one transaction, one rearm. This used to be three
                // separate view-model calls and therefore three of everything.
                viewModel.savePrayerEdit(prayer, minutes, silence, offset)
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

/** Matches the seeded default, so a missing row and a default row look alike. */
private const val DEFAULT_SILENCE_MINUTES = 15
private const val DEFAULT_START_OFFSET_MINUTES = -3

// ---------------------------------------------------------------- sections

/**
 * Platform grants that decide whether any of this works.
 *
 * Always first in the order, whatever the focus: a missing Do Not Disturb
 * grant is the reason the whole feature would silently do nothing, and no
 * intent is a good enough reason to push that below the fold.
 */
@Composable
private fun WarningsSection(
    enabled: Boolean,
    hasDnd: Boolean,
    hasExact: Boolean,
    grantVersion: Int
) {
    if (!enabled) return
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ReliabilityCard(refreshKey = grantVersion)

        if (!hasDnd) {
            InfoBanner(
                text = "Sukoon can't silence your phone without Do Not Disturb access.",
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

        if (!hasExact) {
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
}

@Composable
private fun CustomQuietSection(
    schedules: List<SilenceScheduleEntity>,
    onAdd: () -> Unit,
    onEdit: (SilenceScheduleEntity) -> Unit,
    onToggle: (Long, Boolean) -> Unit
) {
    SectionCard(
        title = "Your own quiet times",
        action = { TextButton(onClick = onAdd) { Text("Add") } }
    ) {
        if (schedules.isEmpty()) {
            Text(
                "Set a quiet period for anything — a meeting, a class, sleep. These " +
                    "work whether or not prayer silence is on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAdd) { Text("Add a quiet time") }
        } else {
            schedules.forEach { schedule ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(schedule) }
                        // 48dp minimum: the row is the target, and a two-line
                        // row is already taller, but a one-line one would not be.
                        .heightIn(min = MIN_TOUCH_TARGET_DP.dp)
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(schedule.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            buildString {
                                append(formatClock(schedule.startMinutesFromMidnight))
                                append(" · ${schedule.durationMinutes} min")
                                append(" · ${Weekdays.describe(schedule.daysMask)}")
                                append(" · ${PrayerSilenceMode.from(schedule.silenceMode).label}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = schedule.enabled,
                        onCheckedChange = { onToggle(schedule.id, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HowQuietSection(
    selected: PrayerSilenceMode,
    blockedReason: String?,
    onSelect: (PrayerSilenceMode) -> Unit,
    onTest: () -> Unit
) {
    SectionCard(title = "How quiet") {
        PrayerSilenceMode.entries.forEach { mode ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == mode,
                        role = Role.RadioButton,
                        onClick = { onSelect(mode) }
                    )
                    .heightIn(min = MIN_TOUCH_TARGET_DP.dp)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected == mode, onClick = null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        mode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (blockedReason != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                blockedReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onTest) { Text("Test for one minute") }
        Spacer(Modifier.height(6.dp))
        Text(
            "Applies it right now so you can check it works, and undoes it after a minute.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TimeSourceSection(selected: PrayerMode, onSelect: (PrayerMode) -> Unit) {
    SectionCard(title = "Where times come from") {
        PrayerMode.entries.forEach { mode ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == mode,
                        role = Role.RadioButton,
                        onClick = { onSelect(mode) }
                    )
                    .heightIn(min = MIN_TOUCH_TARGET_DP.dp)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected == mode, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(mode.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "A time you enter yourself always wins. Changing your location never " +
                "overwrites one — it only fills in the prayers you haven't set.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LocationSection(
    settings: PrayerSettings,
    onUseLocation: () -> Unit,
    onTypeCoordinates: () -> Unit
) {
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
            Button(onClick = onUseLocation) { Text("Use my location") }
            OutlinedButton(onClick = onTypeCoordinates) { Text("Type coordinates") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Coordinates stay on this phone. Nothing is sent anywhere — the times are " +
                "worked out on the device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MethodSection(
    settings: PrayerSettings,
    onMethod: (PrayerMethod) -> Unit,
    onMadhab: (PrayerMadhab) -> Unit
) {
    SectionCard(title = "Calculation method") {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PrayerMethod.entries.forEach { method ->
                FilterChip(
                    selected = settings.method == method,
                    onClick = { onMethod(method) },
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
                    onClick = { onMadhab(madhab) },
                    label = { Text(madhab.label) }
                )
            }
        }
    }
}

@Composable
private fun PrayerTimesSection(
    settings: PrayerSettings,
    byName: Map<String, PrayerEntity>,
    windowByKey: Map<String, SilenceWindow>,
    anyWindows: Boolean,
    onEdit: (Prayer) -> Unit,
    onToggle: (Prayer, Boolean) -> Unit
) {
    SectionCard(title = "Today") {
        if (!anyWindows) {
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
            val window = windowByKey[SilenceWindow.prayerKey(prayer)]
            Row(
                Modifier
                    .fillMaxWidth()
                    // The whole row is the target, not just the small text
                    // button beside it.
                    .clickable { onEdit(prayer) }
                    .heightIn(min = MIN_TOUCH_TARGET_DP.dp)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(prayer.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        buildString {
                            if (window != null) {
                                append(Formatting.time(window.anchorMillis))
                                if (window.isOverridden) append(" · your time")
                                append("\nQuiet ")
                                append(Formatting.time(window.startMillis))
                                append(" – ")
                                append(Formatting.time(window.endMillis))
                            } else {
                                append("No time set")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onEdit(prayer) }) {
                    Text(
                        if (settings.mode == PrayerMode.MANUAL &&
                            row?.manualMinutesFromMidnight == null
                        ) "Set" else "Edit"
                    )
                }
                Switch(
                    checked = row?.enabled ?: true,
                    onCheckedChange = { onToggle(prayer, it) }
                )
            }
        }
    }
}

/**
 * The adhan.
 *
 * The honesty here matters as much as the switch: Sukoon has no licence to
 * redistribute anyone's recording, so it ships none, and the section says so
 * rather than leaving the user to work out why the default sounds like an
 * alarm clock.
 */
@Composable
private fun AdhanSection(
    settings: PrayerSettings,
    onEnabled: (Boolean) -> Unit,
    onPickFile: () -> Unit,
    onUseDefault: () -> Unit,
    onOpenRecordings: () -> Unit
) {
    SectionCard(title = "Adhan") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Play a sound at each prayer", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Sounds at the prayer time itself, on the alarm stream — so it is " +
                        "heard even while Sukoon has the phone silent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = settings.adhan.enabled, onCheckedChange = onEnabled)
        }

        if (settings.adhan.enabled) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Sound: " + settings.adhan.soundLabel.ifBlank { "this phone's alarm tone" },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickFile) { Text("Choose a file") }
                OutlinedButton(onClick = onOpenRecordings) { Text("Recordings") }
            }
            if (settings.adhan.soundUri != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onUseDefault) { Text("Use the phone's alarm tone") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "No adhan recording ships with Sukoon — there is no licence to pass one " +
                    "on, and an unattributed recording in an app about prayer would be " +
                    "worse than none. Choose a file, or record your own.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WhoRingsSection(
    rules: List<com.codeaza.bhaiyaaa.data.db.entity.NotificationRuleEntity>,
    onChange: (VipLevel, Boolean) -> Unit
) {
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
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_TOUCH_TARGET_DP.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${level.label} rings through",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = rule?.ringsDuringPrayer ?: false,
                    onCheckedChange = { onChange(level, it) }
                )
            }
        }
    }
}

@Composable
private fun TimeZoneSection(
    settings: PrayerSettings,
    onChange: () -> Unit,
    onFollowPhone: () -> Unit
) {
    SectionCard(title = "Time zone") {
        Text(
            settings.timeZoneId
                ?: "Following this phone (${java.util.TimeZone.getDefault().id})",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Every prayer time, quiet period and adhan is worked out against this. Worth " +
                "setting if you travel and want to keep the times of home.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onChange) { Text("Change") }
            if (settings.timeZoneId != null) {
                TextButton(onClick = onFollowPhone) { Text("Follow phone") }
            }
        }
    }
}

// ----------------------------------------------------------------- dialogs

/**
 * One prayer's editor.
 *
 * The time control cannot express the wrong half of the clock at all - see
 * [FixedMeridiemClockField]. That is the whole answer to "Fajr must not be
 * settable to PM": not a validation message after the fact, but a control that
 * has no way to say it.
 */
@Composable
private fun EditPrayerDialog(
    prayer: Prayer,
    currentMinutes: Int?,
    currentSilence: Int,
    currentOffset: Int,
    canClearOverride: Boolean,
    calculatedTime: String?,
    onDismiss: () -> Unit,
    onSave: (Int?, Int, Int) -> Unit
) {
    // Opens on the prayer's own default when nothing is set, and that default
    // is guaranteed to be in the right half of the clock by Prayer itself.
    var minutes by remember(prayer, currentMinutes) {
        mutableIntStateOf(currentMinutes ?: prayer.defaultClockMinutes)
    }
    var silence by remember(prayer) { mutableIntStateOf(currentSilence) }
    var earlyBy by remember(prayer) { mutableIntStateOf(-currentOffset) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(prayer.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FixedMeridiemClockField(
                    minutesFromMidnight = minutes,
                    meridiem = prayer.meridiem,
                    onChange = { minutes = it },
                    meridiemNote = "${prayer.label} is always ${prayer.meridiem.label}."
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    "Go quiet $earlyBy minutes early",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = earlyBy.toFloat(),
                    onValueChange = { earlyBy = it.toInt() },
                    valueRange = 0f..20f,
                    steps = 19
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Stay quiet for $silence minutes in total",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = silence.toFloat(),
                    onValueChange = { silence = it.toInt() },
                    valueRange = 5f..60f,
                    steps = 10
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "So the phone is quiet from $earlyBy minutes before the adhan until " +
                        "${silence - earlyBy} minutes after it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canClearOverride) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (calculatedTime != null) {
                            "Calculated for your location: $calculatedTime. Setting a time " +
                                "here overrides it for this prayer only, and a later change " +
                                "of location will not undo it."
                        } else {
                            "Setting a time here overrides the calculation for this prayer."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(minutes, silence, -earlyBy) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (canClearOverride && currentMinutes != null) {
                    TextButton(onClick = { onSave(null, silence, -earlyBy) }) {
                        Text("Use calculated")
                    }
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

/** Minutes past midnight as a 12-hour clock time. */
private fun formatClock(minutesFromMidnight: Int): String {
    val hour24 = (minutesFromMidnight / 60).coerceIn(0, 23)
    val minute = minutesFromMidnight % 60
    val suffix = if (hour24 < 12) "AM" else "PM"
    val hour12 = when {
        hour24 == 0 -> 12
        hour24 > 12 -> hour24 - 12
        else -> hour24
    }
    return "%d:%02d %s".format(hour12, minute, suffix)
}

/**
 * Time-zone picker.
 *
 * A short curated list plus whatever the phone is on, rather than all six
 * hundred IANA zones - a searchable list of every zone is a worse experience
 * than eleven that cover almost everyone who will use this.
 */
@Composable
private fun TimeZoneDialog(
    options: List<String>,
    selected: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time zone") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { id ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == id,
                                role = Role.RadioButton,
                                onClick = { onPick(id) }
                            )
                            .heightIn(min = MIN_TOUCH_TARGET_DP.dp)
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == id, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                id.substringAfter('/').replace('_', ' '),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                id,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

/** Material's minimum touch target. Named so the number is not a mystery. */
private const val MIN_TOUCH_TARGET_DP = 48
