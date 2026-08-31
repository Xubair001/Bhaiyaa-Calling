package com.codeaza.bhaiyaaa.data.export

import android.content.Context
import android.net.Uri
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.db.entity.ContactEntity
import com.codeaza.bhaiyaaa.data.db.entity.MemoryEntity
import com.codeaza.bhaiyaaa.data.db.entity.NotificationRuleEntity
import com.codeaza.bhaiyaaa.data.db.entity.ReminderEntity
import com.codeaza.bhaiyaaa.data.db.entity.TagEntity
import com.codeaza.bhaiyaaa.data.prefs.SettingsRepository
import com.codeaza.bhaiyaaa.domain.model.PersonalityMode
import com.codeaza.bhaiyaaa.domain.model.ThemeMode
import com.codeaza.bhaiyaaa.util.PhoneNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed interface TransferResult {
    data class Success(val summary: String) : TransferResult
    data class Failure(val message: String) : TransferResult
}

/**
 * Export and import of everything Sukoon knows, as plain JSON.
 *
 * Design decisions worth stating:
 *  - Only ever runs from an explicit user action through the system file picker
 *    (SAF), so the app never writes to shared storage on its own.
 *  - Call *history* is excluded by default. It is the bulkiest and most
 *    sensitive data in the app, and it is re-derivable from the device call log,
 *    so exporting it by default would leak more than the user expects from a
 *    "back up my settings" action. It is opt-in via [includeCallHistory].
 *  - This is a portable JSON document, not an encrypted backup format. The
 *    Privacy Center says so in as many words, because a user who thinks this
 *    file is encrypted might store it somewhere they shouldn't.
 */
class DataTransfer(private val context: Context) {

    private val db get() = AppDatabase.getInstance(context)
    private val settings get() = SettingsRepository(context)

    suspend fun export(
        uri: Uri,
        includeCallHistory: Boolean = false
    ): TransferResult = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject().apply {
                put(KEY_FORMAT, FORMAT_NAME)
                put(KEY_VERSION, FORMAT_VERSION)
                put(KEY_EXPORTED_AT, System.currentTimeMillis())
                put(KEY_CONTACTS, contactsJson(db.contactDao().allOnce()))
                put(KEY_MEMORIES, memoriesJson(db.memoryDao().allOnce()))
                put(KEY_REMINDERS, remindersJson(db.reminderDao().allOnce()))
                put(KEY_TAGS, tagsJson(db.tagDao().allOnce()))
                put(KEY_RULES, rulesJson(db.notificationRuleDao().allOnce()))
                put(KEY_SETTINGS, settingsJson())
                if (includeCallHistory) {
                    put(KEY_CALLS, callsJson())
                }
            }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return@withContext TransferResult.Failure("Couldn't open that file for writing")

            val counts = listOf(
                "${db.contactDao().allOnce().size} contacts",
                "${db.memoryDao().allOnce().size} memories",
                "${db.reminderDao().allOnce().size} reminders"
            )
            TransferResult.Success("Exported ${counts.joinToString(", ")}")
        } catch (e: Exception) {
            TransferResult.Failure(e.message ?: "Export failed")
        }
    }

    /**
     * Merges a previously exported file back in.
     *
     * Import is additive and never destructive: contacts are matched by number
     * and only their Sukoon-owned fields are restored, so importing an old
     * backup can't delete contacts or wipe newer notes with blank ones.
     */
    suspend fun import(uri: Uri): TransferResult = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext TransferResult.Failure("Couldn't open that file")

            val root = try {
                JSONObject(text)
            } catch (e: Exception) {
                return@withContext TransferResult.Failure("That file isn't valid JSON")
            }

            if (root.optString(KEY_FORMAT) != FORMAT_NAME) {
                return@withContext TransferResult.Failure("That doesn't look like a Sukoon export")
            }
            val version = root.optInt(KEY_VERSION, 0)
            if (version > FORMAT_VERSION) {
                return@withContext TransferResult.Failure(
                    "That file was written by a newer version of Sukoon (format $version)"
                )
            }

            val now = System.currentTimeMillis()
            var contactsRestored = 0
            var memoriesAdded = 0
            var remindersAdded = 0

            root.optJSONArray(KEY_CONTACTS)?.let { array ->
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val number = PhoneNumbers.normalize(o.optString("phoneNumber"))
                    if (number.isBlank()) continue
                    val existing = db.contactDao().findByPhoneNumber(number)
                    val merged = (existing ?: ContactEntity(
                        phoneNumber = number,
                        matchKey = PhoneNumbers.matchKey(number),
                        name = o.optString("name").ifBlank { number },
                        createdAt = now,
                        updatedAt = now
                    )).copy(
                        vipLevel = o.optString("vipLevel", "NONE"),
                        tag = o.optStringOrNull("tag"),
                        relationship = o.optStringOrNull("relationship"),
                        importance = o.optInt("importance", 1),
                        notes = o.optStringOrNull("notes") ?: existing?.notes,
                        isSpam = o.optBoolean("isSpam", false),
                        notificationsEnabled = o.optBoolean("notificationsEnabled", true),
                        updatedAt = now
                    )
                    db.contactDao().upsert(merged)
                    contactsRestored++
                }
            }

            root.optJSONArray(KEY_MEMORIES)?.let { array ->
                // De-duplicate against what's already stored so importing the
                // same file twice doesn't double every note.
                val existing = db.memoryDao().allOnce()
                    .map { it.body.trim() to it.createdAt }
                    .toSet()
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val body = o.optString("body").trim()
                    if (body.isBlank()) continue
                    val createdAt = o.optLong("createdAt", now)
                    if ((body to createdAt) in existing) continue
                    db.memoryDao().insert(
                        MemoryEntity(
                            contactPhoneNumber = o.optStringOrNull("contactPhoneNumber")
                                ?.let { PhoneNumbers.normalize(it) }
                                ?.takeIf { n -> db.contactDao().findByPhoneNumber(n) != null },
                            title = o.optStringOrNull("title"),
                            body = body,
                            source = o.optString("source", "MANUAL"),
                            isPrivate = o.optBoolean("isPrivate", false),
                            createdAt = createdAt,
                            updatedAt = o.optLong("updatedAt", createdAt)
                        )
                    )
                    memoriesAdded++
                }
            }

            root.optJSONArray(KEY_REMINDERS)?.let { array ->
                val existing = db.reminderDao().allOnce().map { it.text.trim() to it.createdAt }.toSet()
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val text2 = o.optString("text").trim()
                    if (text2.isBlank()) continue
                    val createdAt = o.optLong("createdAt", now)
                    if ((text2 to createdAt) in existing) continue
                    db.reminderDao().insert(
                        ReminderEntity(
                            text = text2,
                            createdAt = createdAt,
                            dueAt = if (o.isNull("dueAt")) null else o.optLong("dueAt"),
                            isDone = o.optBoolean("isDone", false),
                            // Anything already past keeps its notified flag so
                            // an import doesn't fire a burst of stale alerts.
                            notified = o.optBoolean("notified", false)
                        )
                    )
                    remindersAdded++
                }
            }

            root.optJSONArray(KEY_TAGS)?.let { array ->
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val name = o.optString("name")
                    if (name.isBlank()) continue
                    db.tagDao().upsert(
                        TagEntity(
                            name = name,
                            colorArgb = o.optInt("colorArgb", 0xFF5F6368.toInt()),
                            isBuiltIn = o.optBoolean("isBuiltIn", false),
                            sortOrder = o.optInt("sortOrder", 0)
                        )
                    )
                }
            }

            root.optJSONArray(KEY_RULES)?.let { array ->
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val level = o.optString("vipLevel")
                    if (level.isBlank()) continue
                    db.notificationRuleDao().upsert(
                        NotificationRuleEntity(
                            vipLevel = level,
                            notificationsEnabled = o.optBoolean("notificationsEnabled", true),
                            vibrationEnabled = o.optBoolean("vibrationEnabled", true),
                            vibrationPatternCsv = o.optString("vibrationPatternCsv", "0,400,200,400"),
                            flashEnabled = o.optBoolean("flashEnabled", true),
                            flashCount = o.optInt("flashCount", 3),
                            flashOnMillis = o.optLong("flashOnMillis", 180),
                            flashOffMillis = o.optLong("flashOffMillis", 180),
                            customSoundUri = o.optStringOrNull("customSoundUri"),
                            bypassDnd = o.optBoolean("bypassDnd", false)
                        )
                    )
                }
            }

            root.optJSONObject(KEY_SETTINGS)?.let { o ->
                settings.setThemeMode(ThemeMode.from(o.optString("themeMode")))
                settings.setPersonality(PersonalityMode.from(o.optString("personality")))
                settings.setDynamicColor(o.optBoolean("dynamicColor", true))
                settings.setNotificationsEnabled(o.optBoolean("notificationsEnabled", true))
                settings.setFlashlightEnabled(o.optBoolean("flashlightEnabled", true))
                settings.setMissedCallNudge(o.optBoolean("missedCallNudgeEnabled", true))
                settings.setAutoSync(o.optBoolean("autoSyncEnabled", true))
            }

            TransferResult.Success(
                "Restored $contactsRestored contacts, $memoriesAdded memories, $remindersAdded reminders"
            )
        } catch (e: Exception) {
            TransferResult.Failure(e.message ?: "Import failed")
        }
    }

    // ------------------------------------------------------------ serialisers

    private fun contactsJson(rows: List<ContactEntity>) = JSONArray().apply {
        rows.forEach { c ->
            put(
                JSONObject().apply {
                    put("phoneNumber", c.phoneNumber)
                    put("name", c.name)
                    put("vipLevel", c.vipLevel)
                    putOpt("tag", c.tag)
                    putOpt("relationship", c.relationship)
                    put("importance", c.importance)
                    putOpt("notes", c.notes)
                    put("isSpam", c.isSpam)
                    put("notificationsEnabled", c.notificationsEnabled)
                }
            )
        }
    }

    private fun memoriesJson(rows: List<MemoryEntity>) = JSONArray().apply {
        rows.forEach { m ->
            put(
                JSONObject().apply {
                    putOpt("contactPhoneNumber", m.contactPhoneNumber)
                    putOpt("title", m.title)
                    put("body", m.body)
                    put("source", m.source)
                    put("isPrivate", m.isPrivate)
                    put("createdAt", m.createdAt)
                    put("updatedAt", m.updatedAt)
                }
            )
        }
    }

    private fun remindersJson(rows: List<ReminderEntity>) = JSONArray().apply {
        rows.forEach { r ->
            put(
                JSONObject().apply {
                    put("text", r.text)
                    putOpt("contactPhoneNumber", r.contactPhoneNumber)
                    put("createdAt", r.createdAt)
                    if (r.dueAt != null) put("dueAt", r.dueAt) else put("dueAt", JSONObject.NULL)
                    put("isDone", r.isDone)
                    put("notified", r.notified)
                }
            )
        }
    }

    private fun tagsJson(rows: List<TagEntity>) = JSONArray().apply {
        rows.forEach { t ->
            put(
                JSONObject().apply {
                    put("name", t.name)
                    put("colorArgb", t.colorArgb)
                    put("isBuiltIn", t.isBuiltIn)
                    put("sortOrder", t.sortOrder)
                }
            )
        }
    }

    private fun rulesJson(rows: List<NotificationRuleEntity>) = JSONArray().apply {
        rows.forEach { r ->
            put(
                JSONObject().apply {
                    put("vipLevel", r.vipLevel)
                    put("notificationsEnabled", r.notificationsEnabled)
                    put("vibrationEnabled", r.vibrationEnabled)
                    put("vibrationPatternCsv", r.vibrationPatternCsv)
                    put("flashEnabled", r.flashEnabled)
                    put("flashCount", r.flashCount)
                    put("flashOnMillis", r.flashOnMillis)
                    put("flashOffMillis", r.flashOffMillis)
                    putOpt("customSoundUri", r.customSoundUri)
                    put("bypassDnd", r.bypassDnd)
                }
            )
        }
    }

    private suspend fun callsJson() = JSONArray().apply {
        db.callRecordDao().allOnce().forEach { c ->
            put(
                JSONObject().apply {
                    put("phoneNumber", c.phoneNumber)
                    putOpt("contactName", c.contactName)
                    put("type", c.type)
                    put("timestamp", c.timestamp)
                    put("durationSeconds", c.durationSeconds)
                    put("isImportant", c.isImportant)
                    putOpt("note", c.note)
                }
            )
        }
    }

    private suspend fun settingsJson(): JSONObject {
        val s = settings.settings.first()
        return JSONObject().apply {
            put("themeMode", s.themeMode.storageValue)
            put("personality", s.personality.storageValue)
            put("dynamicColor", s.dynamicColor)
            put("notificationsEnabled", s.notificationsEnabled)
            put("flashlightEnabled", s.flashlightEnabled)
            put("missedCallNudgeEnabled", s.missedCallNudgeEnabled)
            put("autoSyncEnabled", s.autoSyncEnabled)
        }
    }

    /** org.json turns absent values into the literal string "null"; this doesn't. */
    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    companion object {
        const val FORMAT_NAME = "bhaiyaaa-export"
        const val FORMAT_VERSION = 1
        const val DEFAULT_FILE_NAME = "bhaiyaaa-backup.json"

        private const val KEY_FORMAT = "format"
        private const val KEY_VERSION = "version"
        private const val KEY_EXPORTED_AT = "exportedAt"
        private const val KEY_CONTACTS = "contacts"
        private const val KEY_MEMORIES = "memories"
        private const val KEY_REMINDERS = "reminders"
        private const val KEY_TAGS = "tags"
        private const val KEY_RULES = "notificationRules"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_CALLS = "callHistory"
    }
}
