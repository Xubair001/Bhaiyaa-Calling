package com.codeaza.bhaiyaaa.ui.models

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.codeaza.bhaiyaaa.ai.model.ModelDownloadWorker
import com.codeaza.bhaiyaaa.ai.model.ModelStorage
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.db.entity.AiModelEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ModelManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).aiModelDao()
    private val storage = ModelStorage(application)

    val models: StateFlow<List<AiModelEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _diskUsage = MutableStateFlow(0L)
    val diskUsage: StateFlow<Long> = _diskUsage.asStateFlow()

    init {
        refreshDiskUsage()
    }

    fun refreshDiskUsage() {
        viewModelScope.launch { _diskUsage.value = storage.totalSizeOnDisk() }
    }

    /**
     * Starts a download. Only ever called from an explicit confirmation dialog
     * that has already shown the user the size and licence.
     */
    fun download(modelId: String) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to modelId))
            .setConstraints(
                Constraints.Builder()
                    // A ~40 MB model has no business running on a metered link
                    // the user didn't choose.
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(getApplication())
            .enqueueUniqueWork(
                ModelDownloadWorker.workName(modelId),
                ExistingWorkPolicy.KEEP,
                request
            )
        _message.value = "Download queued. It'll run on Wi-Fi."
    }

    fun cancelDownload(modelId: String) {
        WorkManager.getInstance(getApplication())
            .cancelUniqueWork(ModelDownloadWorker.workName(modelId))
        viewModelScope.launch {
            dao.markRemoved(modelId, System.currentTimeMillis())
            storage.delete(modelId)
            refreshDiskUsage()
        }
    }

    fun delete(modelId: String) {
        viewModelScope.launch {
            storage.delete(modelId)
            dao.markRemoved(modelId, System.currentTimeMillis())
            refreshDiskUsage()
            _message.value = "Model deleted."
        }
    }

    fun setEnabled(modelId: String, enabled: Boolean) {
        viewModelScope.launch {
            dao.setEnabled(modelId, enabled, System.currentTimeMillis())
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
