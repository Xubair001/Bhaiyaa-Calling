package com.codeaza.bhaiyaaa.call

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Fires the vibration + flashlight pattern for a VIP-level incoming call.
 * Every hardware call is wrapped so a missing/unavailable flashlight (in use
 * by another app, no flash hardware, etc.) never crashes the alert - it just
 * silently skips that part and the vibration still fires.
 */
object CallAlertManager {

    fun triggerVipAlert(context: Context, level: String) {
        vibrate(context, level)
        flashTorch(context, level)
    }

    private fun vibrate(context: Context, level: String) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = when (level) {
                "SUPER_VIP" -> longArrayOf(0, 500, 150, 500, 150, 500)
                "EMERGENCY" -> longArrayOf(0, 400, 150, 400, 150, 400, 150, 400, 150, 400)
                else -> longArrayOf(0, 400, 200, 400)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            // No vibrator hardware or permission revoked mid-session - skip silently.
        }
    }

    private fun flashTorch(context: Context, level: String) {
        val flashCount = when (level) {
            "SUPER_VIP" -> 5
            "EMERGENCY" -> 8
            else -> 3
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return

        val cameraId = try {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            null
        } ?: return

        Thread {
            try {
                repeat(flashCount) {
                    cameraManager.setTorchMode(cameraId, true)
                    Thread.sleep(200)
                    cameraManager.setTorchMode(cameraId, false)
                    Thread.sleep(200)
                }
            } catch (e: Exception) {
                // Camera busy (e.g. another app holds it) - fail silently, vibration already alerted.
            }
        }.start()
    }
}
