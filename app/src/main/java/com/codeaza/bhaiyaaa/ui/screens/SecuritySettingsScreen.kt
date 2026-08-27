package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.codeaza.bhaiyaaa.ui.BhaiyaaaViewModel
import com.codeaza.bhaiyaaa.ui.components.InfoBanner
import com.codeaza.bhaiyaaa.ui.components.SettingsSectionHeader
import com.codeaza.bhaiyaaa.ui.components.SettingsSwitchRow
import com.codeaza.bhaiyaaa.util.BiometricAuth
import com.codeaza.bhaiyaaa.util.SecurePrefs

@Composable
fun SecuritySettingsScreen(viewModel: BhaiyaaaViewModel) {
    val context = LocalContext.current

    // Bumped after each change so the rows re-read the Keystore-backed prefs.
    var version by remember { mutableIntStateOf(0) }
    val lockEnabled = remember(version) { SecurePrefs.isLockEnabled(context) }
    val biometricEnabled = remember(version) { SecurePrefs.isBiometricEnabled(context) }
    val secureStorageAvailable = remember { SecurePrefs.isAvailable(context) }
    val biometricReason = remember(version) { BiometricAuth.unavailableReason(context) }

    var showPinDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (!secureStorageAvailable) {
            InfoBanner(
                text = "Secure storage isn't available on this device, so the privacy lock " +
                    "can't be switched on. BHAIYAAA won't fall back to storing a PIN unprotected.",
                modifier = Modifier.padding(16.dp)
            )
        }

        SettingsSectionHeader("Privacy lock")
        SettingsSwitchRow(
            title = "Require a PIN",
            subtitle = if (lockEnabled) {
                "BHAIYAAA locks when you leave the app"
            } else {
                "Your VIP list and private notes are visible to anyone holding the phone"
            },
            checked = lockEnabled,
            enabled = secureStorageAvailable,
            onCheckedChange = { wantOn ->
                if (wantOn) {
                    showPinDialog = true
                } else {
                    viewModel.disableLock()
                    version++
                }
            }
        )

        if (lockEnabled) {
            SettingsSwitchRow(
                title = "Unlock with biometrics",
                subtitle = biometricReason ?: "Use your fingerprint or face instead of the PIN",
                checked = biometricEnabled,
                enabled = biometricReason == null,
                onCheckedChange = {
                    viewModel.setBiometricEnabled(it)
                    version++
                }
            )
            TextButton(
                onClick = { showPinDialog = true },
                modifier = Modifier.padding(horizontal = 12.dp)
            ) { Text("Change PIN") }
        }

        Text(
            "Your PIN is never stored. BHAIYAAA keeps a salted SHA-256 hash of it inside " +
                "encrypted storage whose key lives in the Android Keystore and never leaves it. " +
                "This protects your data from someone picking up your unlocked phone — it is " +
                "not full-disk encryption.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }

    if (showPinDialog) {
        SetPinDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = { pin ->
                if (viewModel.setPin(pin)) {
                    version++
                    showPinDialog = false
                    true
                } else {
                    false
                }
            }
        )
    }
}

@Composable
private fun SetPinDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set a PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.all(Char::isDigit)) pin = it },
                    label = { Text("PIN (${SecurePrefs.MIN_PIN_LENGTH}–${SecurePrefs.MAX_PIN_LENGTH} digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { if (it.all(Char::isDigit)) confirm = it },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    error = when {
                        pin.length !in SecurePrefs.MIN_PIN_LENGTH..SecurePrefs.MAX_PIN_LENGTH ->
                            "PIN must be ${SecurePrefs.MIN_PIN_LENGTH}–${SecurePrefs.MAX_PIN_LENGTH} digits."
                        pin != confirm -> "The two PINs don't match."
                        else -> {
                            if (onConfirm(pin)) null else "Couldn't save the PIN on this device."
                        }
                    }
                },
                enabled = pin.isNotBlank() && confirm.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
