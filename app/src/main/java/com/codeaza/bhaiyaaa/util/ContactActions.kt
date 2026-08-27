package com.codeaza.bhaiyaaa.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Hands a phone number off to the phone's own dialer or messaging app.
 *
 * Uses ACTION_DIAL rather than ACTION_CALL on purpose. ACTION_CALL places the
 * call immediately but needs the CALL_PHONE permission, and asking for the
 * ability to dial without confirmation is a big ask for an app whose whole
 * pitch is restraint. ACTION_DIAL opens the dialer with the number already
 * filled in - one tap to connect, no extra permission, and the user is never
 * surprised by a call they didn't intend.
 */
object ContactActions {

    /** @return false when the device has no app that can handle the action. */
    fun dial(context: Context, phoneNumber: String): Boolean {
        val number = PhoneNumbers.normalize(phoneNumber)
        if (number.isBlank()) return false
        return launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")))
    }

    fun message(context: Context, phoneNumber: String): Boolean {
        val number = PhoneNumbers.normalize(phoneNumber)
        if (number.isBlank()) return false
        return launch(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")))
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        // A tablet with no dialer, or no SMS app installed.
        false
    } catch (e: SecurityException) {
        false
    }
}
