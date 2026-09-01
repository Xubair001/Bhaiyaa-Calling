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
import com.codeaza.bhaiyaaa.ui.components.SettingsInfoRow
import com.codeaza.bhaiyaaa.ui.components.SettingsLinkRow
import com.codeaza.bhaiyaaa.ui.components.SettingsSectionHeader

/**
 * About.
 *
 * Says what the app is, who made it, and what it will not do. Library names and
 * build details are not information a user of a phone app has any use for, so
 * they live behind the licences page - where attribution belongs anyway.
 */
@Composable
fun AboutScreen(onOpenLicences: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Sukoon",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Your people. Your quiet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsSectionHeader("Version")
        SettingsInfoRow("Version", BuildConfig.VERSION_NAME)

        SettingsSectionHeader("Made by")
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                "Abdullah Zubair",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Software Engineer",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsSectionHeader("Your privacy")
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                "Everything stays on this phone. There is no account, no cloud and no " +
                    "tracking. Your contacts, call history, notes and prayer times are stored " +
                    "locally and are never uploaded anywhere.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SettingsSectionHeader("What Sukoon will not do")
        Column(Modifier.padding(horizontal = 20.dp)) {
            Limitation(
                "Record the call itself",
                "Capturing call audio needs a permission Android grants only to apps " +
                    "built into the phone — being your default dialer is not enough, and " +
                    "the accessibility workaround has been banned since 2022. Consent " +
                    "rules also differ by country. What Sukoon can do is keep a voice " +
                    "note you record after a call, or a recording your phone's own dialer " +
                    "made and you imported, filed against that call. The microphone opens " +
                    "only while the screen says Recording."
            )
            Limitation(
                "Ship an adhan recording",
                "There is no licence to pass one on, and an unattributed recording in an " +
                    "app about prayer would be worse than none. Choose a sound already on " +
                    "your phone, or record your own — the adhan is off until you do."
            )
            Limitation(
                "Take over your dialer",
                "It works alongside your phone app and never replaces it."
            )
            Limitation(
                "Block anyone",
                "Marking a number as spam is a label for your own reference."
            )
            Limitation(
                "Promise an alert every time",
                "Some phones restrict apps in the background. If VIP alerts or prayer " +
                    "silence are ever late, allow Sukoon unrestricted battery use."
            )
        }

        SettingsSectionHeader("The Hadith shown on your dashboard")
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                "Each narration names its collection and hadith number so you can look " +
                    "it up, and anything outside Sahih al-Bukhari and Sahih Muslim states " +
                    "the grading scholars gave it. Nothing graded weak is included. The " +
                    "English conveys the meaning and is not a claim to be the Arabic " +
                    "wording. Nothing is generated, and nothing is attributed to a prayer " +
                    "it does not mention.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SettingsSectionHeader("Legal")
        SettingsLinkRow(
            title = "Open-source licences",
            subtitle = "Acknowledgements for the components Sukoon is built on",
            onClick = onOpenLicences
        )

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun Limitation(title: String, body: String) {
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
