package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.codeaza.bhaiyaaa.util.SecurePrefs

@Composable
fun SettingsScreen(onOpenPrivacyCenter: () -> Unit) {
    val context = LocalContext.current
    var lockEnabled by remember { mutableStateOf(SecurePrefs.isLockEnabled(context)) }
    var showPinSetup by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Text("BHAIYAAA v1.0 \u2014 full build", style = MaterialTheme.typography.bodyMedium)

        Text(
            "VIP alerts, CRM tags, reminders, the assistant, and the privacy " +
                "lock are all live and working on real local data.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Tip: if VIP alerts don't fire when your screen is off, allow " +
                "BHAIYAAA to run unrestricted in your phone's battery settings.",
            style = MaterialTheme.typography.bodyMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Privacy lock (PIN / biometric)", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = lockEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        showPinSetup = true
                    } else {
                        SecurePrefs.disableLock(context)
                        lockEnabled = false
                    }
                }
            )
        }

        if (showPinSetup) {
            OutlinedTextField(
                value = newPin,
                onValueChange = { newPin = it },
                label = { Text("Set a PIN") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    if (newPin.length >= 4) {
                        SecurePrefs.setPin(context, newPin)
                        lockEnabled = true
                        showPinSetup = false
                        newPin = ""
                    }
                }
            ) {
                Text("Save PIN")
            }
        }

        Button(onClick = onOpenPrivacyCenter, modifier = Modifier.fillMaxWidth()) {
            Text("Privacy Center")
        }
    }
}
