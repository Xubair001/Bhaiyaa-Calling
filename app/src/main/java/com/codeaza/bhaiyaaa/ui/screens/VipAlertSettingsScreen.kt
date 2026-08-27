package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.data.db.entity.NotificationRuleEntity
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.service.CallAlertManager
import com.codeaza.bhaiyaaa.ui.BhaiyaaaViewModel
import com.codeaza.bhaiyaaa.ui.components.SectionCard
import kotlin.math.roundToInt

/**
 * Per-tier alert behaviour, with a Test button that fires the real vibration
 * and flashlight pattern - so what you configure is what you actually get.
 */
@Composable
fun VipAlertSettingsScreen(viewModel: BhaiyaaaViewModel) {
    val context = LocalContext.current
    val rules by viewModel.notificationRules.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hasFlashlight = remember { CallAlertManager.hasFlashlight(context) }

    val byLevel = remember(rules) { rules.associateBy { it.vipLevel } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(VipLevel.assignable.size) { index ->
            val level = VipLevel.assignable[index]
            val rule = byLevel[level.storageValue] ?: NotificationRuleEntity(level.storageValue)
            RuleEditor(
                level = level,
                rule = rule,
                hasFlashlight = hasFlashlight,
                flashlightGloballyOn = settings.flashlightEnabled,
                onSave = { viewModel.saveNotificationRule(it) },
                onTest = {
                    CallAlertManager.triggerAlert(
                        context = context,
                        rule = it,
                        flashlightGloballyEnabled = settings.flashlightEnabled
                    )
                }
            )
        }

        item {
            Text(
                "Notification sound and importance are owned by Android's per-channel settings " +
                    "from Android 8 onward — use Settings → Notifications → Android notification " +
                    "settings to change those.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RuleEditor(
    level: VipLevel,
    rule: NotificationRuleEntity,
    hasFlashlight: Boolean,
    flashlightGloballyOn: Boolean,
    onSave: (NotificationRuleEntity) -> Unit,
    onTest: (NotificationRuleEntity) -> Unit
) {
    var draft by remember(rule) { mutableStateOf(rule) }
    val dirty = draft != rule

    SectionCard(title = "${level.label} alerts") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Notification", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = draft.notificationsEnabled,
                onCheckedChange = { draft = draft.copy(notificationsEnabled = it) }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Vibration", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = draft.vibrationEnabled,
                onCheckedChange = { draft = draft.copy(vibrationEnabled = it) }
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (hasFlashlight) "Flashlight" else "Flashlight (not on this device)",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (hasFlashlight && !flashlightGloballyOn) {
                    Text(
                        "Turned off globally in Notifications",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = draft.flashEnabled && hasFlashlight,
                enabled = hasFlashlight,
                onCheckedChange = { draft = draft.copy(flashEnabled = it) }
            )
        }

        if (hasFlashlight && draft.flashEnabled) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Flashes: ${draft.flashCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = draft.flashCount.toFloat(),
                onValueChange = { draft = draft.copy(flashCount = it.roundToInt()) },
                valueRange = 1f..12f,
                steps = 10
            )
            Text(
                "Flash length: ${draft.flashOnMillis} ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = draft.flashOnMillis.toFloat(),
                onValueChange = {
                    val v = it.roundToInt().toLong()
                    draft = draft.copy(flashOnMillis = v, flashOffMillis = v)
                },
                valueRange = 80f..600f
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "Vibration pattern (ms): ${draft.vibrationPatternCsv}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(draft) }, enabled = dirty) { Text("Save") }
            OutlinedButton(onClick = { onTest(draft) }) { Text("Test alert") }
            if (dirty) {
                OutlinedButton(onClick = { draft = rule }) { Text("Reset") }
            }
        }
    }
}
