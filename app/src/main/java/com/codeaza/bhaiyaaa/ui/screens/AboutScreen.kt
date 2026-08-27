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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.BuildConfig
import com.codeaza.bhaiyaaa.ui.components.SectionCard
import com.codeaza.bhaiyaaa.ui.components.SettingsInfoRow
import com.codeaza.bhaiyaaa.ui.components.SettingsSectionHeader

/**
 * About, including a plain statement of what BHAIYAAA deliberately does NOT do.
 * Being explicit about the limits is the point - it is what stops a user
 * assuming the app hears their calls.
 */
@Composable
fun AboutScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("BHAIYAAA", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Apna banda, phone ke andar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsSectionHeader("Version")
        SettingsInfoRow("App version", BuildConfig.VERSION_NAME)
        SettingsInfoRow("Build", if (BuildConfig.DEBUG) "Debug" else "Release")

        SettingsSectionHeader("What it does")
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                "Everything runs on this phone. No account, no cloud, no API key, and no paid " +
                    "service. Your contacts, call history, notes and memories are stored in a " +
                    "local database and never uploaded.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SettingsSectionHeader("What it deliberately doesn't do")
        Column(Modifier.padding(horizontal = 20.dp)) {
            LimitationText(
                "Record or transcribe calls",
                "Modern Android blocks non-system apps from capturing call audio, and consent " +
                    "law for recording calls varies by country. BHAIYAAA stores what you write " +
                    "down about a call and never claims to have heard it."
            )
            LimitationText(
                "Replace your dialer or screen calls",
                "It listens for the system's ringing broadcast — the same mechanism caller-ID " +
                    "apps use — and never takes over the default dialer or call-screening role."
            )
            LimitationText(
                "Block calls",
                "Marking someone as spam is a label for your own reference. BHAIYAAA does not " +
                    "intercept or reject calls."
            )
            LimitationText(
                "Guarantee alerts when the screen is off",
                "Some manufacturers delay or drop background broadcasts. Allowing unrestricted " +
                    "battery use helps, but this is a platform limitation."
            )
        }

        SettingsSectionHeader("Open-source components")
        SettingsInfoRow("Vosk speech recognition", "Apache-2.0")
        SettingsInfoRow("AndroidX / Jetpack Compose", "Apache-2.0")
        SettingsInfoRow("Room, WorkManager, DataStore", "Apache-2.0")
        SettingsInfoRow("Kotlin & coroutines", "Apache-2.0")

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LimitationText(title: String, body: String) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
