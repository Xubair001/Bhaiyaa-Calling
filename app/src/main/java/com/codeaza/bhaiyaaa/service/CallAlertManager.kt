package com.codeaza.bhaiyaaa.service

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.provider.Settings
import android.os.VibrationAttributes
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.codeaza.bhaiyaaa.data.db.entity.NotificationRuleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Fires the physical alert for a VIP call: vibration pattern and flashlight.
 *
 * Every hardware interaction is defensive. A device with no vibrator, a torch
 * already held by the camera app, or a manufacturer that restricts torch access
 * while ringing must all degrade to "the other alert still happens" rather than
 * to a crash inside a broadcast receiver.
 */
object CallAlertManager {

    /** Held so a new call, or the call ending, can cancel an in-flight flash pattern. */
    @Volatile
    private var flashJob: Job? = null

    /** The ring-through-silent player, held so the call being answered stops it. */
    @Volatile
    private var ringtonePlayer: MediaPlayer? = null

    @Volatile
    private var ringtoneStopJob: Job? = null

    /** Safety net: never let a ringtone outlast a call that stopped ringing. */
    const val MAX_RING_MILLIS = 45_000L

    /** Long enough to hear it, short enough not to have to silence it. */
    const val TEST_RING_MILLIS = 6_000L

    /**
     * @param ringMillis how long the ring-through-silent tone may last. A real
     *   call gets the full window; a test from Settings gets a few seconds, so
     *   trying it out never means hunting for a way to shut it up.
     */
    fun triggerAlert(
        context: Context,
        rule: NotificationRuleEntity,
        flashlightGloballyEnabled: Boolean,
        ringMillis: Long = MAX_RING_MILLIS
    ) {
        if (rule.vibrationEnabled) vibrate(context, rule.vibrationPatternCsv)
        if (rule.flashEnabled && flashlightGloballyEnabled) flash(context, rule)
        if (rule.bypassDnd) ringThroughSilent(context, ringMillis)
    }

    /**
     * Plays the ringtone on the alarm stream so a VIP call is audible even with
     * the ringer on silent.
     *
     * A notification channel's bypassDnd flag overrides Do Not Disturb, and only
     * that. Silent mode is a separate mechanism - the ringer mute silences
     * notification audio outright, and no channel setting beats it. The one
     * stream silent mode leaves alone is the alarm stream, which is why an alarm
     * clock still wakes you, so that is what this uses.
     *
     * This is deliberately loud behaviour, so it runs only for a tier the user
     * explicitly switched "ring through silent" on for, and it stops the moment
     * the call is answered or ends.
     */
    private fun ringThroughSilent(context: Context, ringMillis: Long) {
        stopRingtone()
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                ?: Settings.System.DEFAULT_RINGTONE_URI
                ?: return

            val player = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setOnErrorListener { _, _, _ -> stopRingtone(); true }
                prepare()
                start()
            }
            ringtonePlayer = player

            // A missed call never produces an "answered" or "idle" edge on some
            // OEM builds, so the ringtone gets its own deadline rather than
            // trusting the broadcast to arrive.
            ringtoneStopJob = CoroutineScope(Dispatchers.IO).launch {
                delay(ringMillis)
                stopRingtone()
            }
        } catch (t: Throwable) {
            // No ringtone configured, storage unavailable, or the codec refused
            // it. Vibration and the flashlight have already fired.
            stopRingtone()
        }
    }

    private fun stopRingtone() {
        ringtoneStopJob?.cancel()
        ringtoneStopJob = null
        val player = ringtonePlayer
        ringtonePlayer = null
        if (player != null) {
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
    }

    /** Called when the call stops ringing so the torch never gets stranded on. */
    fun cancelAlerts(context: Context) {
        flashJob?.cancel()
        flashJob = null
        stopRingtone()
        runCatching { torchOff(context) }
    }

    /**
     * Vibrates, declaring the buzz as a ringtone rather than a generic one.
     *
     * This matters more than it looks. A vibration posted with no usage
     * attributes is classified as an ordinary notification, and silent mode and
     * Do Not Disturb suppress it - which is why the flashlight would fire for a
     * VIP call while the phone stayed completely still. Tagging it USAGE_RINGTONE
     * puts it in the same class as an incoming call, so it is allowed through
     * wherever the user has permitted calls through.
     */
    private fun vibrate(context: Context, patternCsv: String) {
        val pattern = parsePattern(patternCsv) ?: return
        try {
            val vibrator = vibrator(context) ?: return
            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
                return
            }

            val effect = VibrationEffect.createWaveform(pattern, -1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(
                    effect,
                    VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_RINGTONE)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        } catch (e: Exception) {
            // No vibrator, or permission revoked mid-session. Notification still fires.
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /**
     * Parses "0,400,200,400" into a waveform. Returns null on anything malformed
     * rather than vibrating with a garbage pattern.
     */
    internal fun parsePattern(csv: String): LongArray? {
        val parts = csv.split(",").mapNotNull { it.trim().toLongOrNull() }
        if (parts.isEmpty() || parts.size > 40) return null
        if (parts.any { it < 0 || it > 10_000 }) return null
        return parts.toLongArray()
    }

    private fun flash(context: Context, rule: NotificationRuleEntity) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        val cameraId = torchCameraId(cameraManager) ?: return

        flashJob?.cancel()
        // Coroutine on the IO dispatcher, not a raw Thread: it is cancellable, so
        // the torch is guaranteed to be switched off when the call is answered.
        flashJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                repeat(rule.flashCount.coerceIn(1, 20)) {
                    if (!isActive) return@launch
                    cameraManager.setTorchMode(cameraId, true)
                    delay(rule.flashOnMillis.coerceIn(50, 2000))
                    cameraManager.setTorchMode(cameraId, false)
                    delay(rule.flashOffMillis.coerceIn(50, 2000))
                }
            } catch (e: CameraAccessException) {
                // Torch busy or restricted by the OEM while ringing.
            } catch (e: Exception) {
                // Any other hardware refusal - vibration and the notification stand.
            } finally {
                runCatching { cameraManager.setTorchMode(cameraId, false) }
            }
        }
    }

    private fun torchOff(context: Context) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        val id = torchCameraId(cameraManager) ?: return
        runCatching { cameraManager.setTorchMode(id, false) }
    }

    private fun torchCameraId(cameraManager: CameraManager): String? = try {
        cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (e: Exception) {
        null
    }

    /** True when this device has a torch at all - used to hide the setting if not. */
    fun hasFlashlight(context: Context): Boolean {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        return torchCameraId(cameraManager) != null
    }
}
