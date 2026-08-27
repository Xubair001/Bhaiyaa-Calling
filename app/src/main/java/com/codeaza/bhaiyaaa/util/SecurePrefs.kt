package com.codeaza.bhaiyaaa.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The privacy lock's secrets.
 *
 * The PIN is never stored, in plaintext or otherwise. What is stored is a
 * salted SHA-256 hash of it, and even that lives inside
 * EncryptedSharedPreferences, whose AES key is generated in and never leaves
 * the Android Keystore.
 *
 * A per-install random salt means two people with the same PIN get different
 * hashes, so a stolen prefs file can't be attacked with a precomputed table of
 * the ten thousand possible 4-digit PINs.
 */
object SecurePrefs {
    private const val FILE_NAME = "bhaiyaaa_secure_prefs"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_LOCK_ENABLED = "lock_enabled"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

    const val MIN_PIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 8

    @Volatile
    private var cached: SharedPreferences? = null

    /**
     * Keystore-backed prefs can genuinely fail to open - a corrupted keystore
     * entry after a restore-to-new-device is the classic case. Falling back to
     * plain prefs would silently downgrade security, so instead the lock
     * reports itself unavailable and the app stays usable but unlocked.
     */
    private fun prefs(context: Context): SharedPreferences? {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: runCatching {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }.getOrNull()?.also { cached = it }
        }
    }

    fun isAvailable(context: Context): Boolean = prefs(context) != null

    fun isLockEnabled(context: Context): Boolean =
        prefs(context)?.getBoolean(KEY_LOCK_ENABLED, false) ?: false

    fun isBiometricEnabled(context: Context): Boolean =
        prefs(context)?.getBoolean(KEY_BIOMETRIC_ENABLED, false) ?: false

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        prefs(context)?.edit()?.putBoolean(KEY_BIOMETRIC_ENABLED, enabled)?.apply()
    }

    /** @return false if the PIN is the wrong length or secure storage is unavailable. */
    fun setPin(context: Context, pin: String): Boolean {
        if (pin.length !in MIN_PIN_LENGTH..MAX_PIN_LENGTH) return false
        if (!pin.all { it.isDigit() }) return false
        val store = prefs(context) ?: return false
        val salt = newSalt()
        store.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hash(pin, salt))
            .putBoolean(KEY_LOCK_ENABLED, true)
            .apply()
        return true
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val store = prefs(context) ?: return false
        val salt = store.getString(KEY_PIN_SALT, null) ?: return false
        val expected = store.getString(KEY_PIN_HASH, null) ?: return false
        return constantTimeEquals(expected, hash(pin, salt))
    }

    fun disableLock(context: Context) {
        prefs(context)?.edit()
            ?.putBoolean(KEY_LOCK_ENABLED, false)
            ?.putBoolean(KEY_BIOMETRIC_ENABLED, false)
            ?.remove(KEY_PIN_HASH)
            ?.remove(KEY_PIN_SALT)
            ?.apply()
    }

    private fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    private fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((salt + pin).toByteArray(Charsets.UTF_8))
            .toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /**
     * Compares without an early exit. The timing signal from `==` on a local
     * hash is not a realistic attack, but constant-time comparison is the
     * correct habit and costs nothing here.
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
