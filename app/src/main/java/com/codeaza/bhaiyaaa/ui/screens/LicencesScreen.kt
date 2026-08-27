package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.ui.components.SettingsInfoRow
import com.codeaza.bhaiyaaa.ui.components.SettingsSectionHeader

/**
 * Third-party acknowledgements.
 *
 * Separated from About because these licences are an obligation to the people
 * whose work this is built on, not a feature list for the user - but they are a
 * real obligation, so they are stated in full rather than dropped.
 */
@Composable
fun LicencesScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "BHAIYAAA is built on open-source work by others. " +
                    "Each component is used under the licence shown.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsSectionHeader("Apache License 2.0")
        SettingsInfoRow("Android Jetpack & Compose", "Google")
        SettingsInfoRow("Kotlin & Coroutines", "JetBrains")
        SettingsInfoRow("Vosk speech recognition", "Alpha Cephei")

        SettingsSectionHeader("MIT License")
        SettingsInfoRow("Adhan prayer times", "Batoul Apps")

        SettingsSectionHeader("SIL Open Font License 1.1")
        SettingsInfoRow("Manrope", "Mikhail Sharanda")

        Column(Modifier.padding(20.dp)) {
            Text(
                "Full licence texts are available from each project. Copies are included " +
                    "with the application source.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
