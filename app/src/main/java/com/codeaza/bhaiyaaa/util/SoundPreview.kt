package com.codeaza.bhaiyaaa.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log

/**
 * Plays a sound once, so the user can hear what they have chosen.
 *
 * Deliberately separate from the service that sounds the real adhan. Previewing
 * must never touch the "already played today" record, or testing a sound in the
 * evening would cost you the actual adhan a few minutes later - and it must
 * never take a wake lock or a foreground service for something that lasts as
 * long as the screen is open anyway.
 *
 * Plays on the music stream rather than the alarm stream, for the same reason:
 * a preview is something you asked for while looking at the screen, not an
 * alert that has to cut through a silenced phone.
 */
class SoundPreview {

    private var player: MediaPlayer? = null

    val isPlaying: Boolean get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    /**
     * Starts [uri], stopping whatever was already previewing.
     *
     * @param onFinished called when playback ends or fails, so the UI can drop
     *   its "playing" state rather than showing a stop button forever.
     * @return false when the sound could not be opened.
     */
    fun play(context: Context, uri: Uri, onFinished: () -> Unit = {}): Boolean {
        stop()
        return try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context.applicationContext, uri)
                setOnCompletionListener {
                    stop()
                    onFinished()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    onFinished()
                    true
                }
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Preview failed: ${e.javaClass.simpleName}")
            stop()
            false
        }
    }

    fun stop() {
        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
    }

    private companion object {
        const val TAG = "SukoonPreview"
    }
}
