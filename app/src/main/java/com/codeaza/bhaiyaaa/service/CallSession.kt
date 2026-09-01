package com.codeaza.bhaiyaaa.service

import android.content.Context

/**
 * Tracks one call across the three broadcasts it arrives as.
 *
 * `PHONE_STATE` is delivered as separate broadcasts - RINGING, then OFFHOOK if
 * the call is answered, then IDLE - and a `BroadcastReceiver` is a fresh object
 * each time, so nothing can be remembered in a field. This holds the little
 * that has to survive between them.
 *
 * It exists to answer one question: was this call *answered*, and by whom.
 * Without the OFFHOOK step a missed call looks exactly like a completed one
 * (both are RINGING then IDLE), and offering to write notes about a call
 * nobody picked up would be the feature's most obvious bug.
 *
 * SharedPreferences rather than a database: two values, written on a broadcast
 * that has ten seconds to live, read microseconds later. A Room transaction
 * here would be the wrong tool.
 */
object CallSession {

    private const val PREFS = "bhaiyaaa_call_session"
    private const val KEY_NUMBER = "ringing_number"
    private const val KEY_ANSWERED = "answered"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A call is coming in from [rawNumber]. Starts a new session. */
    fun onRinging(context: Context, rawNumber: String?) {
        if (rawNumber.isNullOrBlank()) {
            // No number means READ_CALL_LOG was not granted. Nothing useful
            // can be attributed later, so no session is started at all.
            clear(context)
            return
        }
        prefs(context).edit()
            .putString(KEY_NUMBER, rawNumber)
            .putBoolean(KEY_ANSWERED, false)
            .apply()
    }

    /**
     * The call was picked up.
     *
     * Also reached at the start of an *outgoing* call, where no session was
     * started - the guard means that quietly does nothing rather than marking
     * a stale incoming number as answered.
     */
    fun onAnswered(context: Context) {
        val store = prefs(context)
        if (store.getString(KEY_NUMBER, null) == null) return
        store.edit().putBoolean(KEY_ANSWERED, true).apply()
    }

    /**
     * The call is over.
     *
     * @return the number of an incoming call that was actually answered, or
     *   null for a missed call, an outgoing call, or no session at all. Always
     *   clears, so one call can never be reported twice.
     */
    fun onEnded(context: Context): String? {
        val store = prefs(context)
        val number = store.getString(KEY_NUMBER, null)
        val answered = store.getBoolean(KEY_ANSWERED, false)
        clear(context)
        return number?.takeIf { answered }
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_NUMBER).remove(KEY_ANSWERED).apply()
    }
}
