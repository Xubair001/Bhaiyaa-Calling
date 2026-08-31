package com.codeaza.bhaiyaaa.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.ui.theme.CardShape
import com.codeaza.bhaiyaaa.util.BackgroundReliability

/**
 * The two device settings that decide whether alerts work with the app closed.
 *
 * Shown wherever a feature depends on running in the background. Neither can be
 * granted from code, so the honest thing is to say what is missing and open the
 * right screen - rather than let the feature look broken for a reason the user
 * has no way of seeing.
 */
@Composable
fun ReliabilityCard(
    /** Bump to re-read the settings, e.g. after returning from a system screen. */
    refreshKey: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val batteryOk = BackgroundReliability.isIgnoringBatteryOptimizations(context)
    val needsAutostart = BackgroundReliability.hasAggressiveBackgroundPolicy()

    // Nothing to nag about on a stock device that is already unrestricted.
    if (batteryOk && !needsAutostart) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (batteryOk) MaterialTheme.colorScheme.surfaceContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (batteryOk) Icons.Filled.Check else Icons.Filled.PriorityHigh,
                    contentDescription = null,
                    tint = if (batteryOk) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Working when the app is closed",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (batteryOk) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(10.dp))

            if (!batteryOk) {
                Text(
                    "Android won't let Sukoon start its alert while it's in the background " +
                        "unless battery optimisation is off for it. Alerts will be cut short " +
                        "or missed entirely until this is allowed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(BackgroundReliability.batteryOptimizationIntent(context))
                    }.onFailure {
                        runCatching {
                            context.startActivity(BackgroundReliability.batteryOptimizationListIntent())
                        }
                    }
                }) { Text("Allow unrestricted battery") }
            }

            if (needsAutostart) {
                if (!batteryOk) Spacer(Modifier.height(14.dp))
                Text(
                    BackgroundReliability.autostartInstruction(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (batteryOk) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    val intent = BackgroundReliability.autostartIntent(context)
                    runCatching {
                        // No autostart screen found on this build - the app's own
                        // settings page is the nearest useful place to land.
                        context.startActivity(
                            intent ?: com.codeaza.bhaiyaaa.util.Permissions.appSettingsIntent(context)
                        )
                    }
                }) { Text("Open autostart settings") }
            }
        }
    }
}
