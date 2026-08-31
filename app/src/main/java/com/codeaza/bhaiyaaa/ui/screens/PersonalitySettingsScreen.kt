package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.ai.ResourcePhrasebook
import com.codeaza.bhaiyaaa.domain.model.PersonalityMode
import com.codeaza.bhaiyaaa.ui.SukoonViewModel
import com.codeaza.bhaiyaaa.ui.components.SettingsSectionHeader

/**
 * Personality picker with a live preview, so the difference between the tones
 * is visible before choosing rather than described.
 */
@Composable
fun PersonalitySettingsScreen(viewModel: SukoonViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SettingsSectionHeader("How Sukoon talks")

        PersonalityMode.entries.forEach { mode ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = settings.personality == mode,
                        role = Role.RadioButton,
                        onClick = { viewModel.setPersonality(mode) }
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = settings.personality == mode, onClick = null)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        mode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val preview = remember(settings.personality) {
            val phrasebook = ResourcePhrasebook(context, settings.personality)
            listOf(
                phrasebook.greetingMorning(),
                phrasebook.vipCalling("Ahmed"),
                phrasebook.reminderCreated(),
                phrasebook.noMissedCalls()
            )
        }

        SettingsSectionHeader("Preview")
        Card(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                preview.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        Text(
            "Tone only changes the wording. The facts Sukoon reports come from your data " +
                "either way.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }
}
