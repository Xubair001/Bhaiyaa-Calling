package com.codeaza.bhaiyaaa.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Records a short clip from the microphone.
 *
 * AAC in an MP4 container: it is the one combination every Android device
 * since API 16 can both record and play back, which matters because whatever
 * is recorded here is later handed to a MediaPlayer inside an alarm-driven
 * service where a codec failure would be invisible.
 *
 * A thin wrapper on purpose. MediaRecorder is a state machine that throws
 * `IllegalStateException` for any out-of-order call, and the value of putting
 * it behind three methods is that the rest of the app cannot get the order
 * wrong. Every entry point is safe to call twice.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var target: File? = null

    val isRecording: Boolean get() = recorder != null

    /** @return false when the microphone could not be opened at all. */
    fun start(file: File): Boolean {
        if (isRecording) return false
        return try {
            val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                // Enough for speech and for a recited adhan without producing
                // a file that is large for what it is.
                setAudioEncodingBitRate(BIT_RATE)
                setAudioSamplingRate(SAMPLE_RATE)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = created
            target = file
            true
        } catch (e: Exception) {
            // A denied permission, a microphone held by a call, an OEM refusing
            // the encoder. None of these should crash a settings screen.
            Log.w(TAG, "Could not start recording: ${e.javaClass.simpleName}")
            releaseQuietly()
            runCatching { file.delete() }
            false
        }
    }

    /**
     * @return the file written, or null when nothing usable was captured -
     *   which is the normal outcome of stopping within a fraction of a second
     *   of starting, because MediaRecorder throws rather than writing a
     *   zero-length clip.
     */
    fun stop(): File? {
        val active = recorder ?: return null
        val file = target
        val ok = runCatching { active.stop() }.isSuccess
        releaseQuietly()
        if (!ok || file == null || !file.exists() || file.length() == 0L) {
            file?.let { runCatching { it.delete() } }
            return null
        }
        return file
    }

    /** Abandons the recording and removes the partial file. */
    fun cancel() {
        val file = target
        runCatching { recorder?.stop() }
        releaseQuietly()
        file?.let { runCatching { it.delete() } }
    }

    private fun releaseQuietly() {
        runCatching { recorder?.release() }
        recorder = null
        target = null
    }

    private companion object {
        const val BIT_RATE = 96_000
        const val SAMPLE_RATE = 44_100
        const val TAG = "SukoonRecorder"
    }
}
