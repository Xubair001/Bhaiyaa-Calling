package com.codeaza.bhaiyaaa.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The privacy lock's secrets.
 *
 * The PIN is never stored, in plaintext or otherwise. What is stored is a
 * *stretched* hash of it, inside EncryptedSharedPreferences, whose AES key is
 * generated in and never leaves the Android Keystore.
 *
 * ## Why stretching, and not just a salted SHA-256
 *
 * A salt defeats a precomputed table. It does nothing against brute force, and
 * a PIN is the easiest possible brute force: four to eight digits is at most a
 * hundred million candidates, and a commodity GPU does billions of SHA-256
 * hashes a second. Anyone who ever got hold of the stored hash would recover a
 * four-digit PIN in well under a second.
 *
 * PBKDF2-HMAC-SHA256 at [PBKDF2_ITERATIONS] rounds turns each guess into
 * something that takes a couple of hundred milliseconds, which is unnoticeable
 * once when unlocking and ruinous a hundred million times over. It is in the
 * platform, so this costs no dependency.
 *
 * Deriving takes real time, so [verifyPin] and [setPin] must not be called on
 * the main thread - both are `suspend` to make that impossible to get wrong.
 *
 * ## Upgrading from the old scheme
 *
 * Hashes written by earlier versions have no [KEY_PIN_ALGORITHM] marker and are
 * plain salted SHA-256. Those still verify, and a successful unlock silently
 * rewrites them in the new scheme - so existing users keep their PIN and get
 * the stronger storage without being asked to do anything.
 *
 * ## Rate limiting
 *
 * The attempt counter lives here rather than in the lock screen's state. It
 * used to be a `remember` in the composable, which meant force-stopping the app
 * reset it - an attacker with the phone could simply retry for ever, and the
 * lockout was decoration.
 */
object SecurePrefs {
    private const val FILE_NAME = "bhaiyaaa_secure_prefs"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_PIN_ALGORITHM = "pin_algorithm"
    private const val KEY_LOCK_ENABLED = "lock_enabled"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_LOCKED_UNTIL = "locked_until"

    const val MIN_PIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 8

    /** Wrong tries before the lock starts making the attacker wait. */
    const val MAX_ATTEMPTS_BEFORE_LOCKOUT = 5

    /**
     * How long a lockout lasts, and how fast it grows.
     *
     * Doubling per lockout, capped: five wrong tries costs thirty seconds, the
     * next five a minute, and so on to five minutes. Long enough to make a
     * by-hand search hopeless, short enough that the owner mistyping their own
     * PIN twice is only mildly annoyed.
     */
    private const val BASE_LOCKOUT_MILLIS = 30_000L
    private const val MAX_LOCKOUT_MILLIS = 5 * 60_000L

    /**
     * Tuned so one derivation is roughly a fifth of a second on a mid-range
     * phone: imperceptible when unlocking, and about eight months of CPU to
     * walk a four-digit keyspace.
     */
    private const val PBKDF2_ITERATIONS = 200_000
    private const val PBKDF2_KEY_LENGTH_BITS = 256
    private const val ALGORITHM_PBKDF2 = "pbkdf2-sha256-200000"

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

    /**
     * Test seam, matching the one on AppDatabase.
     *
     * Robolectric has no Android Keystore, so EncryptedSharedPreferences
     * cannot be opened in a unit test - which is why this file had no tests
     * until the hashing was rewritten. Injecting plain prefs lets the
     * *behaviour* be tested (stretching, migration, lockout) while the
     * production path still refuses to run without real encrypted storage.
     */
    internal fun setPrefsForTest(prefs: SharedPreferences?) {
        cached = prefs
    }

    fun isLockEnabled(context: Context): Boolean =
        prefs(context)?.getBoolean(KEY_LOCK_ENABLED, false) ?: false

    fun isBiometricEnabled(context: Context): Boolean =
        prefs(context)?.getBoolean(KEY_BIOMETRIC_ENABLED, false) ?: false

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        prefs(context)?.edit()?.putBoolean(KEY_BIOMETRIC_ENABLED, enabled)?.apply()
    }

    // -------------------------------------------------------------- the PIN

    /**
     * @return false if the PIN is the wrong length, not digits, or secure
     *   storage is unavailable.
     *
     * Suspending because deriving the hash is deliberately slow.
     */
    suspend fun setPin(context: Context, pin: String): Boolean {
        if (!isWellFormed(pin)) return false
        val store = prefs(context) ?: return false
        val salt = newSalt()
        val derived = derive(pin, salt)
        store.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, derived)
            .putString(KEY_PIN_ALGORITHM, ALGORITHM_PBKDF2)
            .putBoolean(KEY_LOCK_ENABLED, true)
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKED_UNTIL)
            .apply()
        return true
    }

    /**
     * Checks a PIN, and enforces the lockout.
     *
     * @return [PinResult.Correct], [PinResult.Wrong] with how many tries are
     *   left, or [PinResult.LockedOut] with when it may be tried again.
     */
    suspend fun verifyPin(context: Context, pin: String): PinResult {
        val store = prefs(context) ?: return PinResult.Wrong(remainingAttempts = 0)

        val lockedUntil = store.getLong(KEY_LOCKED_UNTIL, 0L)
        val now = System.currentTimeMillis()
        if (now < lockedUntil) return PinResult.LockedOut(lockedUntil - now)

        val salt = store.getString(KEY_PIN_SALT, null)
        val expected = store.getString(KEY_PIN_HASH, null)
        if (salt == null || expected == null) return PinResult.Wrong(remainingAttempts = 0)

        val legacy = store.getString(KEY_PIN_ALGORITHM, null) != ALGORITHM_PBKDF2
        val actual = if (legacy) legacySha256(pin, salt) else derive(pin, salt)

        if (!constantTimeEquals(expected, actual)) {
            return registerFailure(store, now)
        }

        // Correct. Clear the counter, and quietly re-stretch a hash still
        // stored under the old scheme so the next unlock uses the new one.
        store.edit().remove(KEY_FAILED_ATTEMPTS).remove(KEY_LOCKED_UNTIL).apply()
        if (legacy) {
            val upgradedSalt = newSalt()
            store.edit()
                .putString(KEY_PIN_SALT, upgradedSalt)
                .putString(KEY_PIN_HASH, derive(pin, upgradedSalt))
                .putString(KEY_PIN_ALGORITHM, ALGORITHM_PBKDF2)
                .apply()
        }
        return PinResult.Correct
    }

    private fun registerFailure(store: SharedPreferences, now: Long): PinResult {
        val attempts = store.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val editor = store.edit().putInt(KEY_FAILED_ATTEMPTS, attempts)

        if (attempts % MAX_ATTEMPTS_BEFORE_LOCKOUT != 0) {
            editor.apply()
            return PinResult.Wrong(
                remainingAttempts = MAX_ATTEMPTS_BEFORE_LOCKOUT - (attempts % MAX_ATTEMPTS_BEFORE_LOCKOUT)
            )
        }

        // Every further block of wrong tries doubles the wait, up to the cap.
        val lockouts = attempts / MAX_ATTEMPTS_BEFORE_LOCKOUT
        val wait = (BASE_LOCKOUT_MILLIS shl (lockouts - 1).coerceAtMost(MAX_LOCKOUT_SHIFT))
            .coerceAtMost(MAX_LOCKOUT_MILLIS)
        editor.putLong(KEY_LOCKED_UNTIL, now + wait).apply()
        return PinResult.LockedOut(wait)
    }

    /** How long the lock is still shut for, or zero. Cheap enough to poll. */
    fun lockoutRemainingMillis(context: Context): Long {
        val store = prefs(context) ?: return 0L
        return (store.getLong(KEY_LOCKED_UNTIL, 0L) - System.currentTimeMillis())
            .coerceAtLeast(0L)
    }

    fun disableLock(context: Context) {
        prefs(context)?.edit()
            ?.putBoolean(KEY_LOCK_ENABLED, false)
            ?.putBoolean(KEY_BIOMETRIC_ENABLED, false)
            ?.remove(KEY_PIN_HASH)
            ?.remove(KEY_PIN_SALT)
            ?.remove(KEY_PIN_ALGORITHM)
            ?.remove(KEY_FAILED_ATTEMPTS)
            ?.remove(KEY_LOCKED_UNTIL)
            ?.apply()
    }

    fun isWellFormed(pin: String): Boolean =
        pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it.isDigit() }

    // ------------------------------------------------------------- internals

    private fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    private fun derive(pin: String, salt: String): String {
        val spec = PBEKeySpec(
            pin.toCharArray(),
            salt.toByteArray(Charsets.UTF_8),
            PBKDF2_ITERATIONS,
            PBKDF2_KEY_LENGTH_BITS
        )
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
                .toHex()
        } finally {
            // The spec holds a copy of the PIN; clear it rather than leaving
            // it for the garbage collector to get round to.
            spec.clearPassword()
        }
    }

    /** The pre-v2 scheme, kept only so an existing PIN still opens the app. */
    private fun legacySha256(pin: String, salt: String): String =
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

    private const val SALT_BYTES = 16

    /** Caps the doubling before it overflows, independently of the time cap. */
    private const val MAX_LOCKOUT_SHIFT = 8
}

/** The outcome of offering a PIN. */
sealed interface PinResult {
    data object Correct : PinResult

    /** @param remainingAttempts tries left before the next lockout. */
    data class Wrong(val remainingAttempts: Int) : PinResult

    /** @param waitMillis how long until another attempt is accepted. */
    data class LockedOut(val waitMillis: Long) : PinResult
}
