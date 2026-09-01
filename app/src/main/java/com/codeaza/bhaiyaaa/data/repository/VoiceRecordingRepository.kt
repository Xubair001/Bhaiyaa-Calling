package com.codeaza.bhaiyaaa.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.db.entity.VoiceRecordingEntity
import com.codeaza.bhaiyaaa.data.db.entity.VoiceRecordingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Voice recordings: the files, and the rows that describe them.
 *
 * ## Where the audio lives, and why
 *
 * In the app's private files directory - not the shared media store. Three
 * reasons, all of them the same reason: this is a privacy-first app.
 *  - Nothing else on the phone can read it without root.
 *  - It is covered by the manifest's `allowBackup="false"`, so it is never
 *    copied to anyone's cloud backup.
 *  - Uninstalling removes it, with no orphaned audio left in Music/.
 *
 * An imported file is *copied* in rather than referenced. A content URI can be
 * revoked, and the file behind it can be deleted by the app that owns it - and
 * the moment either happens the adhan stops working, days later, for a reason
 * the user cannot see.
 *
 * ## What this deliberately is not
 *
 * Not call recording. `MediaRecorder.AudioSource.VOICE_CALL` requires
 * `CAPTURE_AUDIO_OUTPUT`, which Android grants only to privileged,
 * pre-installed apps - being the default dialer is not enough, and Play policy
 * has barred the accessibility-service workaround since 2022. Consent law also
 * varies by jurisdiction. Sukoon does not pretend otherwise: it records only
 * what the user deliberately records, with the microphone permission granted at
 * that moment, and it can hold a recording the phone's own dialer produced if
 * the user imports one. A recording can be filed against a call
 * ([addRecorded], [importFrom]), which is the achievable half of the same
 * need - see Settings → About.
 */
class VoiceRecordingRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.getInstance(appContext).voiceRecordingDao()

    val recordings: Flow<List<VoiceRecordingEntity>> = dao.observeAll()

    /** The voice notes filed against one call. */
    fun recordingsForCall(callId: Long): Flow<List<VoiceRecordingEntity>> =
        dao.observeForCall(callId)

    /** The directory the audio lives in, created on first use. */
    fun directory(): File =
        File(appContext.filesDir, DIRECTORY).apply { if (!exists()) mkdirs() }

    fun fileFor(recording: VoiceRecordingEntity): File = File(directory(), recording.fileName)

    /** The URI to hand a MediaPlayer. Never leaves the app, so a file URI is right. */
    fun uriFor(recording: VoiceRecordingEntity): Uri = Uri.fromFile(fileFor(recording))

    /**
     * A fresh, unused file to record into.
     *
     * A UUID rather than a name derived from the label: labels are renameable
     * and can collide, and a file whose name has to change when its label does
     * is a rename that can half-fail.
     */
    fun newRecordingFile(): File = File(directory(), "${UUID.randomUUID()}.$EXTENSION")

    /** @param callId files this against a call; null keeps it standalone. */
    suspend fun addRecorded(
        file: File,
        label: String,
        callId: Long? = null
    ): Long = withContext(Dispatchers.IO) {
        dao.insert(
            VoiceRecordingEntity(
                label = label.trim().ifBlank { DEFAULT_LABEL },
                fileName = file.name,
                durationMillis = durationOf(file),
                createdAt = System.currentTimeMillis(),
                source = VoiceRecordingSource.RECORDED.storageValue,
                callId = callId
            )
        )
    }

    /**
     * Copies an audio file the user picked into private storage.
     *
     * @return the new row's id, or null when the file could not be read - a
     *   revoked grant, or a picker that returned something unreadable.
     */
    suspend fun importFrom(
        uri: Uri,
        label: String,
        callId: Long? = null
    ): Long? = withContext(Dispatchers.IO) {
        val target = newRecordingFile()
        val copied = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyBounded(output) }
            } ?: false
        }.getOrElse {
            Log.w(TAG, "Import failed: ${it.javaClass.simpleName}")
            false
        }

        if (!copied || !target.exists() || target.length() == 0L) {
            // A partial file from an oversized source is deleted rather than
            // left behind as a truncated recording.
            runCatching { target.delete() }
            return@withContext null
        }

        dao.insert(
            VoiceRecordingEntity(
                label = label.trim().ifBlank { DEFAULT_LABEL },
                fileName = target.name,
                durationMillis = durationOf(target),
                createdAt = System.currentTimeMillis(),
                source = VoiceRecordingSource.IMPORTED.storageValue,
                callId = callId
            )
        )
    }

    suspend fun rename(id: Long, label: String) = withContext(Dispatchers.IO) {
        dao.rename(id, label.trim().ifBlank { DEFAULT_LABEL })
    }

    /**
     * Removes the row and the audio.
     *
     * The file goes second: if deleting the row fails there is nothing to
     * clean up, whereas deleting the file first and then failing on the row
     * would leave a recording listed that cannot be played.
     */
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val recording = dao.find(id) ?: return@withContext
        dao.deleteById(id)
        runCatching { fileFor(recording).delete() }
        Unit
    }

    suspend fun find(id: Long): VoiceRecordingEntity? = withContext(Dispatchers.IO) { dao.find(id) }

    /**
     * Deletes audio with no row pointing at it.
     *
     * A recording interrupted by the process dying leaves a file and no row.
     * Without this they accumulate silently in the user's storage, which is
     * exactly the kind of thing a privacy-first app should not do.
     */
    suspend fun removeOrphanedFiles() = withContext(Dispatchers.IO) {
        val known = dao.allOnce().map { it.fileName }.toSet()
        directory().listFiles()?.forEach { file ->
            if (file.name !in known) runCatching { file.delete() }
        }
        Unit
    }

    /**
     * Copies, refusing anything larger than [MAX_IMPORT_BYTES].
     *
     * `copyTo` would happily write a two-gigabyte file into the user's storage
     * because they mis-tapped in the picker, and nothing would tell them why
     * the phone was suddenly full. An adhan is a couple of minutes of audio;
     * the cap is generous for that and ruinous for nothing.
     *
     * @return false if the source was too large, having written no more than
     *   the cap before stopping.
     */
    private fun java.io.InputStream.copyBounded(output: java.io.OutputStream): Boolean {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var total = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) return true
            total += read
            if (total > MAX_IMPORT_BYTES) return false
            output.write(buffer, 0, read)
        }
    }

    /**
     * Length in milliseconds, or zero when it cannot be read.
     *
     * Explicit try/finally rather than `use`: MediaMetadataRetriever only
     * became AutoCloseable at API 29, and this app supports 26. Leaking the
     * retriever would leak a native decoder.
     */
    private fun durationOf(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        const val DIRECTORY = "recordings"
        const val EXTENSION = "m4a"
        const val DEFAULT_LABEL = "Recording"
        const val TAG = "SukoonRecordings"

        /** Comfortably more than any adhan, far less than a phone's storage. */
        const val MAX_IMPORT_BYTES = 25L * 1024 * 1024
        const val COPY_BUFFER_BYTES = 16 * 1024
    }
}
