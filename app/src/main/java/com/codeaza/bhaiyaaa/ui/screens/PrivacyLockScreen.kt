package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.codeaza.bhaiyaaa.util.BiometricAuth
import com.codeaza.bhaiyaaa.util.SecurePrefs

@Composable
fun PrivacyLockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val activity = context as? FragmentActivity
    val biometricAvailable = remember { BiometricAuth.canAuthenticate(context) }

    LaunchedEffect(Unit) {
        if (biometricAvailable && activity != null) {
            BiometricAuth.authenticate(
                activity,
                onSuccess = { onUnlocked() },
                onError = { /* fall back to PIN entry below, no need to surface every cancel */ }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Private Space \uD83D\uDD10", style = MaterialTheme.typography.titleLarge)
        Text("Enter your PIN to continue.", style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it; error = null },
            label = { Text("PIN") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                if (SecurePrefs.verifyPin(context, pin)) {
                    onUnlocked()
                } else {
                    error = "Wrong PIN, try again."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Unlock")
        }

        if (biometricAvailable && activity != null) {
            TextButton(
                onClick = {
                    BiometricAuth.authenticate(
                        activity,
                        onSuccess = { onUnlocked() },
                        onError = { error = "Biometric failed - use your PIN." }
                    )
                }
            ) {
                Text("Use biometric instead")
            }
        }
    }
}
