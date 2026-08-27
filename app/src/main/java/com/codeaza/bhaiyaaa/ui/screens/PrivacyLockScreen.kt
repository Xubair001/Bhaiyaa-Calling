package com.codeaza.bhaiyaaa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.codeaza.bhaiyaaa.util.BiometricAuth
import com.codeaza.bhaiyaaa.util.BiometricResult
import com.codeaza.bhaiyaaa.util.SecurePrefs

/**
 * The gate in front of the whole app when the privacy lock is on.
 *
 * PIN entry is rate-limited after repeated failures. This is a local device
 * lock, not a network login, so the goal is only to make shoulder-surfing and
 * idle guessing tedious - it is not claimed to resist an attacker with the
 * unlocked phone and unlimited time.
 */
@Composable
fun PrivacyLockScreen(
    onUnlocked: () -> Unit,
    verifyPin: (String) -> Boolean
) {
    val context = LocalContext.current
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var failedAttempts by remember { mutableStateOf(0) }
    var lockedUntil by remember { mutableStateOf(0L) }

    val biometricEnabled = remember { SecurePrefs.isBiometricEnabled(context) }
    val canUseBiometric = remember { biometricEnabled && BiometricAuth.canAuthenticate(context) }

    fun attemptBiometric() {
        val activity = context as? FragmentActivity ?: return
        BiometricAuth.authenticate(activity) { result ->
            when (result) {
                is BiometricResult.Success -> onUnlocked()
                is BiometricResult.Cancelled -> Unit
                is BiometricResult.Error -> error = result.message
            }
        }
    }

    // Offer the fingerprint prompt straight away when it's enabled, so the
    // common case is a single tap rather than typing a PIN.
    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric) attemptBiometric()
    }

    fun submit(pin: String) {
        if (System.currentTimeMillis() < lockedUntil) return
        if (verifyPin(pin)) {
            onUnlocked()
        } else {
            failedAttempts++
            entered = ""
            if (failedAttempts >= MAX_ATTEMPTS_BEFORE_DELAY) {
                lockedUntil = System.currentTimeMillis() + LOCKOUT_MILLIS
                error = "Too many tries. Wait ${LOCKOUT_MILLIS / 1000}s."
            } else {
                error = "Wrong PIN"
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Private Space 🔐",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Enter your PIN to unlock BHAIYAAA",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.Center) {
            repeat(SecurePrefs.MAX_PIN_LENGTH) { index ->
                val filled = index < entered.length
                Box(
                    Modifier
                        .padding(horizontal = 6.dp)
                        .size(if (filled) 14.dp else 12.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = error ?: " ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(16.dp))

        Keypad(
            onDigit = { digit ->
                if (entered.length < SecurePrefs.MAX_PIN_LENGTH &&
                    System.currentTimeMillis() >= lockedUntil
                ) {
                    error = null
                    entered += digit
                    if (entered.length >= SecurePrefs.MIN_PIN_LENGTH) {
                        // Auto-submit at the minimum length, then on each extra
                        // digit, so both 4- and 6-digit PINs feel natural.
                        submit(entered)
                    }
                }
            },
            onBackspace = { entered = entered.dropLast(1) }
        )

        if (canUseBiometric) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { attemptBiometric() }) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Use fingerprint")
            }
        }
    }
}

@Composable
private fun Keypad(onDigit: (Char) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf(' ', '0', '<')
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row {
                row.forEach { ch ->
                    when (ch) {
                        ' ' -> Spacer(Modifier.size(72.dp))
                        '<' -> IconButton(
                            onClick = onBackspace,
                            modifier = Modifier
                                .size(72.dp)
                                .semantics { contentDescription = "Delete last digit" }
                        ) {
                            Icon(Icons.Filled.Backspace, contentDescription = null)
                        }
                        else -> TextButton(
                            onClick = { onDigit(ch) },
                            modifier = Modifier.size(72.dp)
                        ) {
                            Text(
                                ch.toString(),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_ATTEMPTS_BEFORE_DELAY = 5
private const val LOCKOUT_MILLIS = 30_000L
