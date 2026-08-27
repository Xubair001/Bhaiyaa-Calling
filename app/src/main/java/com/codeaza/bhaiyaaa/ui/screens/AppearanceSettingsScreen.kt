package com.codeaza.bhaiyaaa.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeaza.bhaiyaaa.domain.model.ThemeMode
import com.codeaza.bhaiyaaa.ui.BhaiyaaaViewModel
import com.codeaza.bhaiyaaa.ui.components.SettingsSectionHeader
import com.codeaza.bhaiyaaa.ui.components.SettingsSwitchRow

@Composable
fun AppearanceSettingsScreen(viewModel: BhaiyaaaViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SettingsSectionHeader("Theme")
        ThemeMode.entries.forEach { mode ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = settings.themeMode == mode,
                        role = Role.RadioButton,
                        onClick = { viewModel.setThemeMode(mode) }
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = settings.themeMode == mode, onClick = null)
                Spacer(Modifier.width(16.dp))
                Text(mode.label, style = MaterialTheme.typography.bodyLarge)
            }
        }

        SettingsSectionHeader("Colour")
        SettingsSwitchRow(
            title = "Use wallpaper colours",
            subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "Match BHAIYAAA to your Android theme"
            } else {
                "Needs Android 12 or newer — this device uses BHAIYAAA's own palette"
            },
            checked = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            // Hidden capability, not a dead switch: below API 31 the platform
            // simply has no wallpaper palette to read.
            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            onCheckedChange = { viewModel.setDynamicColor(it) }
        )

        Text(
            "Dark mode also follows your system setting when Theme is set to Follow system.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }
}
