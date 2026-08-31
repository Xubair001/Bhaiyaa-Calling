package com.codeaza.bhaiyaaa.ai.model

import com.codeaza.bhaiyaaa.data.db.entity.AiModelEntity

/** What a model is for. One active model per purpose at a time. */
enum class ModelPurpose(val storageValue: String, val label: String) {
    SPEECH_RECOGNITION("SPEECH_RECOGNITION", "Speech recognition"),
    TEXT_GENERATION("TEXT_GENERATION", "Text generation"),
    CLASSIFICATION("CLASSIFICATION", "Classification");

    companion object {
        fun from(value: String?): ModelPurpose =
            entries.firstOrNull { it.storageValue == value } ?: SPEECH_RECOGNITION
    }
}

enum class ModelStatus(val storageValue: String, val label: String) {
    NOT_INSTALLED("NOT_INSTALLED", "Not installed"),
    DOWNLOADING("DOWNLOADING", "Downloading"),
    INSTALLED("INSTALLED", "Installed"),
    FAILED("FAILED", "Failed");

    companion object {
        fun from(value: String?): ModelStatus =
            entries.firstOrNull { it.storageValue == value } ?: NOT_INSTALLED
    }
}

/**
 * The models Sukoon can install, all of them free and open-source with
 * licences that permit redistribution and commercial use.
 *
 * Nothing here is bundled in the APK and nothing downloads on its own: each
 * entry starts as NOT_INSTALLED, the size and licence are shown up front, and
 * a download only ever starts from an explicit tap (brief §18).
 *
 * Sizes are the publishers' stated archive sizes and are shown as approximate
 * in the UI; the real figure is taken from the Content-Length at download time.
 */
object ModelCatalog {

    /**
     * Vosk small English. Apache-2.0, runs fully offline via the Vosk Android
     * runtime, and is the reason Sukoon can do speech without Google's
     * network recognizer.
     */
    const val VOSK_EN_SMALL = "vosk-model-small-en-us-0.15"

    /** Vosk small Hindi - closest widely-available model for Urdu/Hindi speech. */
    const val VOSK_HI_SMALL = "vosk-model-small-hi-0.22"

    data class CatalogEntry(
        val id: String,
        val displayName: String,
        val purpose: ModelPurpose,
        val approxSizeBytes: Long,
        val license: String,
        val sourceUrl: String,
        val description: String
    )

    val ENTRIES: List<CatalogEntry> = listOf(
        CatalogEntry(
            id = VOSK_EN_SMALL,
            displayName = "Vosk English (small)",
            purpose = ModelPurpose.SPEECH_RECOGNITION,
            approxSizeBytes = 40L * 1024 * 1024,
            license = "Apache-2.0",
            sourceUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            description = "Offline English speech recognition. Works with no network at all, " +
                "unlike the system recognizer which may fall back to the cloud."
        ),
        CatalogEntry(
            id = VOSK_HI_SMALL,
            displayName = "Vosk Hindi/Urdu (small)",
            purpose = ModelPurpose.SPEECH_RECOGNITION,
            approxSizeBytes = 42L * 1024 * 1024,
            license = "Apache-2.0",
            sourceUrl = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
            description = "Offline Hindi speech recognition. Handles much Roman-Urdu speech, " +
                "since Hindi and Urdu share their spoken core."
        )
    )

    fun seedRows(now: Long): List<AiModelEntity> = ENTRIES.map { e ->
        AiModelEntity(
            id = e.id,
            displayName = e.displayName,
            purpose = e.purpose.storageValue,
            sizeBytes = e.approxSizeBytes,
            license = e.license,
            sourceUrl = e.sourceUrl,
            status = ModelStatus.NOT_INSTALLED.storageValue,
            updatedAt = now
        )
    }

    fun find(id: String): CatalogEntry? = ENTRIES.firstOrNull { it.id == id }
}
