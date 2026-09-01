package com.codeaza.bhaiyaaa.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The privacy lock.
 *
 * Two properties matter here and neither used to hold:
 *
 *  - the stored hash must be expensive to test a guess against, because a PIN
 *    is at most a hundred million candidates and a fast hash makes the whole
 *    lock decorative;
 *  - the attempt counter must survive the app being force-stopped, or an
 *    attacker simply restarts it and keeps going.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SecurePrefsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * Plain prefs standing in for the Keystore-backed ones.
     *
     * Robolectric has no Android Keystore, so the real store cannot be opened
     * here. What is under test is the behaviour on top of it - stretching,
     * migration, the lockout - all of which is storage-agnostic.
     */
    private lateinit var store: android.content.SharedPreferences

    @Before
    fun setUp() {
        store = context.getSharedPreferences("securePrefsTest", Context.MODE_PRIVATE)
        store.edit().clear().commit()
        SecurePrefs.setPrefsForTest(store)
    }

    @org.junit.After
    fun tearDown() = SecurePrefs.setPrefsForTest(null)

    @Test
    fun `a PIN that was set opens the lock`() = runTest {
        assertThat(SecurePrefs.setPin(context, "1234")).isTrue()

        assertThat(SecurePrefs.verifyPin(context, "1234")).isEqualTo(PinResult.Correct)
    }

    @Test
    fun `a wrong PIN does not`() = runTest {
        SecurePrefs.setPin(context, "1234")

        assertThat(SecurePrefs.verifyPin(context, "9999"))
            .isInstanceOf(PinResult.Wrong::class.java)
    }

    @Test
    fun `the PIN itself is never stored`() = runTest {
        SecurePrefs.setPin(context, "13571")

        // Whatever is on disk, none of it may be the PIN.
        val dumped = store.all.values.joinToString(" ") { it.toString() }
        assertThat(dumped).doesNotContain("13571")
    }

    @Test
    fun `two people with the same PIN get different hashes`() = runTest {
        SecurePrefs.setPin(context, "1234")
        val first = storedHash()

        SecurePrefs.disableLock(context)
        SecurePrefs.setPin(context, "1234")

        // A per-install salt, so one cracked hash says nothing about another.
        assertThat(storedHash()).isNotEqualTo(first)
    }

    @Test
    fun `the lock refuses a PIN that is not four to eight digits`() = runTest {
        assertThat(SecurePrefs.setPin(context, "123")).isFalse()
        assertThat(SecurePrefs.setPin(context, "123456789")).isFalse()
        assertThat(SecurePrefs.setPin(context, "12a4")).isFalse()
        assertThat(SecurePrefs.isWellFormed("1234")).isTrue()
        assertThat(SecurePrefs.isWellFormed("12345678")).isTrue()
    }

    @Test
    fun `enough wrong tries locks the keypad`() = runTest {
        SecurePrefs.setPin(context, "1234")

        repeat(SecurePrefs.MAX_ATTEMPTS_BEFORE_LOCKOUT - 1) {
            assertThat(SecurePrefs.verifyPin(context, "0000"))
                .isInstanceOf(PinResult.Wrong::class.java)
        }
        assertThat(SecurePrefs.verifyPin(context, "0000"))
            .isInstanceOf(PinResult.LockedOut::class.java)
    }

    @Test
    fun `a lockout survives the app being restarted`() = runTest {
        // The whole point. The counter used to live in the lock screen's own
        // state, so force-stopping the app reset it and the lockout was
        // decoration - an attacker could retry for ever at full speed.
        SecurePrefs.setPin(context, "1234")
        repeat(SecurePrefs.MAX_ATTEMPTS_BEFORE_LOCKOUT) {
            SecurePrefs.verifyPin(context, "0000")
        }

        assertThat(SecurePrefs.lockoutRemainingMillis(context)).isGreaterThan(0L)
        // Even the correct PIN is refused while the lockout runs, which is
        // what makes it a rate limit rather than a suggestion.
        assertThat(SecurePrefs.verifyPin(context, "1234"))
            .isInstanceOf(PinResult.LockedOut::class.java)
    }

    @Test
    fun `a correct PIN clears the counter`() = runTest {
        SecurePrefs.setPin(context, "1234")
        SecurePrefs.verifyPin(context, "0000")
        SecurePrefs.verifyPin(context, "0000")

        assertThat(SecurePrefs.verifyPin(context, "1234")).isEqualTo(PinResult.Correct)

        // Back to a full allowance rather than one try from a lockout.
        repeat(SecurePrefs.MAX_ATTEMPTS_BEFORE_LOCKOUT - 1) {
            assertThat(SecurePrefs.verifyPin(context, "0000"))
                .isInstanceOf(PinResult.Wrong::class.java)
        }
    }

    @Test
    fun `a PIN stored by an older version still opens the app and is upgraded`() = runTest {
        // Anyone upgrading has a salted SHA-256 hash on disk. It has to keep
        // working - and quietly become a stretched one.
        writeLegacyPin("4321")

        assertThat(SecurePrefs.verifyPin(context, "4321")).isEqualTo(PinResult.Correct)

        val upgraded = store.getString("pin_algorithm", null)
        assertThat(upgraded).isNotNull()
        // And it still opens the lock after the rewrite.
        assertThat(SecurePrefs.verifyPin(context, "4321")).isEqualTo(PinResult.Correct)
    }

    @Test
    fun `a wrong PIN against an older hash is still wrong`() = runTest {
        writeLegacyPin("4321")

        assertThat(SecurePrefs.verifyPin(context, "1111"))
            .isInstanceOf(PinResult.Wrong::class.java)
    }

    @Test
    fun `turning the lock off forgets everything about it`() = runTest {
        SecurePrefs.setPin(context, "1234")
        SecurePrefs.verifyPin(context, "0000")

        SecurePrefs.disableLock(context)

        assertThat(SecurePrefs.isLockEnabled(context)).isFalse()
        assertThat(SecurePrefs.lockoutRemainingMillis(context)).isEqualTo(0L)
        assertThat(storedHash()).isNull()
    }

    private fun storedHash(): String? = store.getString("pin_hash", null)

    /** Writes a hash in the pre-upgrade format, exactly as v1 did. */
    private fun writeLegacyPin(pin: String) {
        val salt = "0123456789abcdef0123456789abcdef"
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest((salt + pin).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        store.edit()
            .putString("pin_salt", salt)
            .putString("pin_hash", hash)
            .putBoolean("lock_enabled", true)
            .remove("pin_algorithm")
            .apply()
    }
}
