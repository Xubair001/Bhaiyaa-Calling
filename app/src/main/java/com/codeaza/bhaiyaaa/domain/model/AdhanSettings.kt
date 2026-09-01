package com.codeaza.bhaiyaaa.domain.model

/**
 * The optional adhan that sounds when a prayer time arrives.
 *
 * Three deliberate decisions are encoded here.
 *
 * **Off by default.** Audio that starts on its own is the single most intrusive
 * thing an app can do, so nothing plays until the user turns this on. Every
 * path that could produce sound checks [enabled] first, and there is no code
 * path that plays with it false.
 *
 * **No recording ships in the APK.** Sukoon has no licence to redistribute
 * anyone's adhan, and shipping one of unclear provenance in an app about
 * prayer would be worse than shipping none. [soundUri] therefore points at
 * something the user chose: a tone already on the phone, an audio file they
 * picked, or a recording they made in the app. Null means the device's default
 * alarm tone, which always exists.
 *
 * **A cap, not a fade.** [maxDurationSeconds] stops playback even if the
 * chosen file is long, so a mistakenly selected album track cannot hold a wake
 * lock and the audio focus for nine minutes.
 */
data class AdhanSettings(
    val enabled: Boolean = false,
    /**
     * URI of the sound to play. Null uses the device's default alarm tone.
     *
     * Stored as a string because DataStore has no URI type and because the
     * value has to survive being written by one process and read by another
     * (the alarm receiver) without a parsed object in between.
     */
    val soundUri: String? = null,
    /** What to call the chosen sound in the UI. Never parsed, only displayed. */
    val soundLabel: String = "",
    /**
     * Hard stop for one playback.
     *
     * Long enough for a full adhan, short enough that nothing can run away
     * with the wake lock. Not user-facing: there is no good reason to want
     * this longer, and a setting for it would only be a way to break the app.
     */
    val maxDurationSeconds: Int = DEFAULT_MAX_DURATION_SECONDS,
) {
    /** True when the adhan should actually be armed for a prayer. */
    fun playsFor(prayerEnabled: Boolean): Boolean = enabled && prayerEnabled

    companion object {
        /**
         * Just under three minutes, and that number is not arbitrary.
         *
         * Playback runs in a `shortService` foreground service, which Android
         * 14 gives roughly three minutes before it force-stops it. A cap above
         * that budget would not produce a longer adhan - it would produce a
         * crash at the platform's deadline. A recited adhan runs to about two
         * minutes, so this is comfortably enough for the real case and safely
         * inside the limit for a wrongly chosen file.
         */
        const val DEFAULT_MAX_DURATION_SECONDS = 170
    }
}
