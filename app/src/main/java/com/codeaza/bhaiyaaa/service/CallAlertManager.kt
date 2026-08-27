package com.codeaza.bhaiyaaa.service

import android.content.Context
import android.hardware.camera2.CameraAccessException
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

    fun triggerAlert(
        context: Context,
        rule: NotificationRuleEntity,
        flashlightGloballyEnabled: Boolean
    ) {
        if (rule.vibrationEnabled) vibrate(context, rule.vibrationPatternCsv)
        if (rule.flashEnabled && flashlightGloballyEnabled) flash(context, rule)
    }

    /** Called when the call stops ringing so the torch never gets stranded on. */
    fun cancelAlerts(context: Context) {
        flashJob?.cancel()
        flashJob = null
        runCatching { torchOff(context) }
    }

    private fun vibrate(context: Context, patternCsv: String) {
        val pattern = parsePattern(patternCsv) ?: return
        try {
            val vibrator = vibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
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
