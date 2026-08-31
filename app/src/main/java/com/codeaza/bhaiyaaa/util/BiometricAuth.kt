package com.codeaza.bhaiyaaa.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Result of an unlock attempt, distinguishing "wrong" from "cancelled". */
sealed interface BiometricResult {
    data object Success : BiometricResult
    data object Cancelled : BiometricResult
    data class Error(val message: String) : BiometricResult
}

object BiometricAuth {

    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK

    /** True only when hardware exists AND the user has enrolled something. */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** Why biometrics aren't available, so Settings can explain rather than just grey out. */
    fun unavailableReason(context: Context): String? =
        when (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                "This device has no biometric hardware."
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "Biometric hardware is unavailable right now."
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "No fingerprint or face is enrolled on this device yet."
            else -> "Biometric unlock isn't available on this device."
        }

    fun authenticate(
        activity: FragmentActivity,
        onResult: (BiometricResult) -> Unit
    ) {
        if (!canAuthenticate(activity)) {
            onResult(BiometricResult.Error(unavailableReason(activity) ?: "Unavailable"))
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(BiometricResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Cancelling is a normal choice, not an error to shout about -
                    // the user just falls back to the PIN pad.
                    val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    onResult(
                        if (cancelled) BiometricResult.Cancelled
                        else BiometricResult.Error(errString.toString())
                    )
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Sukoon")
            .setSubtitle("Your VIP list and private notes are locked")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setConfirmationRequired(false)
            .build()

        runCatching { prompt.authenticate(info) }
            .onFailure { onResult(BiometricResult.Error(it.message ?: "Couldn't start biometric prompt")) }
    }
}
