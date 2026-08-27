package com.codeaza.bhaiyaaa.ai.model

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads and unpacks one model.
 *
 * Runs in WorkManager rather than a bare coroutine so a large download survives
 * the user leaving the screen, retries on a flaky connection, and only runs on
 * a network the system considers usable (brief §28/§29).
 *
 * Uses HttpURLConnection rather than adding an HTTP client dependency - this is
 * the only network call the entire app makes.
 */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID)
            ?: return@withContext Result.failure(errorData("No model id supplied"))
        val entry = ModelCatalog.find(modelId)
            ?: return@withContext Result.failure(errorData("Unknown model: $modelId"))

        val dao = AppDatabase.getInstance(applicationContext).aiModelDao()
        val storage = ModelStorage(applicationContext)
        val now = System.currentTimeMillis()

        dao.setStatus(modelId, ModelStatus.DOWNLOADING.storageValue, null, now)
        dao.setProgress(modelId, 0)

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(entry.sourceUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                dao.setStatus(modelId, ModelStatus.FAILED.storageValue, "HTTP $code", now)
                // Server-side and transport errors are usually transient.
                return@withContext Result.retry()
            }

            connection.inputStream.use { stream ->
                storage.unzipInto(modelId, stream) { written ->
                    // Progress is reported off the download thread cheaply; the
                    // UI polls the DB row rather than this worker's progress.
                    setProgressAsync(workDataOf(KEY_BYTES to written))
                }
            }

            if (!storage.isInstalled(modelId)) {
                storage.delete(modelId)
                dao.setStatus(modelId, ModelStatus.FAILED.storageValue, "Archive was empty", now)
                return@withContext Result.failure(errorData("Archive was empty"))
            }

            val installedSize = storage.sizeOnDisk(modelId)
            dao.upsert(
                requireNotNull(dao.findById(modelId)).copy(
                    status = ModelStatus.INSTALLED.storageValue,
                    installedPath = storage.dirFor(modelId).absolutePath,
                    sizeBytes = installedSize,
                    downloadedBytes = installedSize,
                    // Installing is the user's way of saying they want it used.
                    enabled = true,
                    lastError = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
            Result.success()
        } catch (e: Exception) {
            // Half-written models are worse than none: clear the directory so a
            // retry starts clean and the recogniser never loads a partial model.
            storage.delete(modelId)
            dao.setStatus(
                modelId,
                ModelStatus.FAILED.storageValue,
                e.message ?: e.javaClass.simpleName,
                System.currentTimeMillis()
            )
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry()
            else Result.failure(errorData(e.message ?: "Download failed"))
        } finally {
            connection?.disconnect()
        }
    }

    private fun errorData(message: String): Data = workDataOf(KEY_ERROR to message)

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_BYTES = "bytes"
        const val KEY_ERROR = "error"
        private const val MAX_ATTEMPTS = 3

        fun workName(modelId: String) = "model-download-$modelId"
    }
}
