package com.codeaza.bhaiyaaa.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.prefs.SettingsRepository
import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.codeaza.bhaiyaaa.domain.model.PrayerSettings
import com.codeaza.bhaiyaaa.notifications.Notifier
import com.codeaza.bhaiyaaa.prayer.AdhanPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Plays the adhan when a prayer arrives.
 *
 * A foreground service for the same reason [VipAlertService] is one: an alarm
 * broadcast's `goAsync()` window is about ten seconds, and a backgrounded
 * process can be killed the moment it closes - which would cut the adhan off
 * partway through. The service exists only for the length of one playback and
 * stops itself, so nothing is running between prayers. That, and the fact that
 * the timing comes from `AlarmManager` rather than from anything watching the
 * clock, is what makes this feature cost no battery when it is not sounding.
 *
 * **The alarm stream, deliberately.** Sukoon's own prayer silence has usually
 * put the phone into Do Not Disturb's alarms-only filter three minutes before
 * this fires. Playing on the notification or ring stream would mean the app
 * silenced its own adhan. `USAGE_ALARM` is the one stream that survives its own
 * feature, and it is also what the platform lets through DND.
 *
 * **Nothing plays unless the user asked for it.** The preference is re-read
 * here, at the moment of playing, not trusted from when the alarm was armed -
 * see [AdhanPlayback.shouldPlay].
 */
class AdhanService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }

        val prayer = Prayer.from(intent?.getStringExtra(EXTRA_PRAYER_KEY))
        val firedAt = intent?.getLongExtra(EXTRA_FIRED_AT, 0L)?.takeIf { it > 0L }
            ?: System.currentTimeMillis()

        // In the foreground before anything slow happens: the platform kills a
        // service that has not shown its notification within a few seconds,
        // and the checks below touch DataStore and Room.
        startInForeground(prayer)

        scope.launch {
            val settings = runCatching { isAllowed(prayer, firedAt) }.getOrNull()
            if (settings == null) {
                stopEverything()
                return@launch
            }
            play(prayer, settings)
        }
        return START_NOT_STICKY
    }

    /**
     * Every reason not to make a sound, checked in one place.
     *
     * @return the settings to play with, or null when it must not play. The
     *   settings are returned rather than re-read by the caller: they were
     *   just fetched, and reading DataStore twice for one adhan is a request
     *   with an identical answer.
     */
    private suspend fun isAllowed(prayer: Prayer, firedAt: Long): PrayerSettings? {
        val settings = SettingsRepository(applicationContext).settings.first().prayer
        val row = AppDatabase.getInstance(applicationContext)
            .prayerDao()
            .find(prayer.storageValue)

        val requestKey = AdhanPlayback.dayKey(prayer, firedAt, settings.zone)
        val allowed = AdhanPlayback.shouldPlay(
            adhanEnabled = settings.adhan.enabled,
            prayerEnabled = row?.enabled ?: false,
            lastPlayedKey = prefs(applicationContext).getString(KEY_LAST_PLAYED, null),
            requestKey = requestKey,
            minutesLate = (System.currentTimeMillis() - firedAt) / 60_000L
        )

        if (!allowed) return null

        // Recorded before playing, not after. If the process dies mid-adhan
        // the correct behaviour is still "this one has been heard" - a second
        // copy starting from a re-delivered alarm would be worse than a
        // truncated first.
        prefs(applicationContext).edit().putString(KEY_LAST_PLAYED, requestKey).apply()
        return settings
    }

    private suspend fun play(prayer: Prayer, settings: PrayerSettings) {
        val uri = settings.adhan.soundUri?.let(Uri::parse) ?: defaultSound()
        if (uri == null) {
            Log.w(TAG, "No adhan sound available on this device")
            stopEverything()
            return
        }

        acquireWakeLock(settings.adhan.maxDurationSeconds)
        Notifier.notifyPrayerTime(applicationContext, prayer, settings.adhan.soundLabel)

        val started = runCatching { start(uri) }.getOrElse {
            Log.w(TAG, "Adhan playback failed: ${it.javaClass.simpleName}")
            false
        }
        if (!started) {
            stopEverything()
            return
        }

        // A hard stop rather than trusting the file to end. A user who picked
        // the wrong file must not lose the wake lock and the audio focus to it.
        delay(settings.adhan.maxDurationSeconds * 1000L)
        stopEverything()
    }

    private fun start(uri: Uri): Boolean {
        val audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        requestFocus(audio, attributes)

        player = MediaPlayer().apply {
            setAudioAttributes(attributes)
            setDataSource(applicationContext, uri)
            isLooping = false
            setOnCompletionListener { stopEverything() }
            setOnErrorListener { _, _, _ ->
                stopEverything()
                true
            }
            prepare()
            start()
        }
        return true
    }

    /**
     * Asks for transient focus so whatever was playing pauses and resumes
     * afterwards, rather than the adhan arriving on top of a podcast.
     */
    private fun requestFocus(audio: AudioManager?, attributes: AudioAttributes) {
        audio ?: return
        runCatching {
            // AudioFocusRequest is API 26, which is this app's minimum, so
            // there is no legacy branch to keep.
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .build()
            focusRequest = request
            audio.requestAudioFocus(request)
        }
    }

    private fun abandonFocus() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching { focusRequest?.let { audio.abandonAudioFocusRequest(it) } }
        focusRequest = null
    }

    /**
     * The device's own alarm tone, used when the user has not chosen a sound.
     *
     * Sukoon ships no adhan recording - it has no licence to redistribute one,
     * and an unattributed recording in an app about prayer would be worse than
     * none at all. The Adhan settings screen says exactly this and offers the
     * ways to supply one.
     */
    private fun defaultSound(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private fun startInForeground(prayer: Prayer) {
        val notification = Notifier.buildAdhanPlayingNotification(applicationContext, prayer)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock(seconds: Int) {
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = runCatching {
            power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                // A timeout as well as an explicit release: if this process is
                // killed before it can release, the platform still takes it
                // back. A leaked wake lock is a flat battery.
                acquire(seconds * 1000L + WAKE_LOCK_GRACE_MILLIS)
            }
        }.getOrNull()
    }

    private fun stopEverything() {
        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
        abandonFocus()
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        runCatching { player?.release() }
        player = null
        abandonFocus()
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
    }

    companion object {
        const val ACTION_PLAY = "com.codeaza.bhaiyaaa.action.PLAY_ADHAN"
        const val ACTION_STOP = "com.codeaza.bhaiyaaa.action.STOP_ADHAN"
        const val EXTRA_PRAYER_KEY = "prayer_key"
        const val EXTRA_FIRED_AT = "fired_at"

        private const val NOTIFICATION_ID = 5001
        private const val TAG = "SukoonAdhan"
        private const val WAKE_LOCK_TAG = "sukoon:adhan"
        private const val WAKE_LOCK_GRACE_MILLIS = 5_000L
        private const val PREFS = "bhaiyaaa_adhan_state"
        private const val KEY_LAST_PLAYED = "last_played_key"

        private fun prefs(context: Context) =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /**
         * @return false when the platform refused to start the service.
         *
         * Android 12+ only lets a background app start a foreground service
         * for a handful of reasons, and one of them is an alarm scheduled with
         * `setAlarmClock` - which is what [com.codeaza.bhaiyaaa.prayer.PrayerScheduler]
         * uses whenever it is allowed to. Where exact alarms have been refused
         * the fallback is an inexact alarm, which carries no such exemption,
         * so this can legitimately fail. The caller degrades to a silent
         * prayer notification rather than the user getting nothing at all.
         */
        fun play(context: Context, prayer: Prayer, firedAt: Long): Boolean {
            val intent = Intent(context, AdhanService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_PRAYER_KEY, prayer.storageValue)
                putExtra(EXTRA_FIRED_AT, firedAt)
            }
            return runCatching { context.startForegroundService(intent) }.isSuccess
        }

        fun stopIntent(context: Context): Intent =
            Intent(context, AdhanService::class.java).setAction(ACTION_STOP)

        /**
         * Forgets what was last played.
         *
         * Used by the "hear it now" preview, so testing the sound never costs
         * the user the real adhan a few minutes later.
         */
        fun clearPlaybackHistory(context: Context) {
            prefs(context).edit().remove(KEY_LAST_PLAYED).apply()
        }
    }
}
