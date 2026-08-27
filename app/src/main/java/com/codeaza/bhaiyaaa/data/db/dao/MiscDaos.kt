package com.codeaza.bhaiyaaa.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.codeaza.bhaiyaaa.data.db.entity.AiModelEntity
import com.codeaza.bhaiyaaa.data.db.entity.NotificationRuleEntity
import com.codeaza.bhaiyaaa.data.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(tags: List<TagEntity>)

    @Upsert
    suspend fun upsert(tag: TagEntity)

    @Query("DELETE FROM tags WHERE name = :name AND isBuiltIn = 0")
    suspend fun deleteCustom(name: String)

    @Query("SELECT * FROM tags")
    suspend fun allOnce(): List<TagEntity>
}

@Dao
interface AiModelDao {
    @Query("SELECT * FROM ai_models ORDER BY purpose ASC, displayName ASC")
    fun observeAll(): Flow<List<AiModelEntity>>

    @Query("SELECT * FROM ai_models WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AiModelEntity?

    @Query("SELECT * FROM ai_models WHERE purpose = :purpose AND status = 'INSTALLED' AND enabled = 1 LIMIT 1")
    suspend fun activeForPurpose(purpose: String): AiModelEntity?

    @Query("SELECT * FROM ai_models WHERE purpose = :purpose AND status = 'INSTALLED' AND enabled = 1 LIMIT 1")
    fun observeActiveForPurpose(purpose: String): Flow<AiModelEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(models: List<AiModelEntity>)

    @Upsert
    suspend fun upsert(model: AiModelEntity)

    @Query("UPDATE ai_models SET status = :status, lastError = :error, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: String, status: String, error: String?, now: Long)

    @Query("UPDATE ai_models SET downloadedBytes = :bytes WHERE id = :id")
    suspend fun setProgress(id: String, bytes: Long)

    @Query("UPDATE ai_models SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, now: Long)

    @Query("UPDATE ai_models SET status = 'NOT_INSTALLED', installedPath = NULL, enabled = 0, downloadedBytes = 0, updatedAt = :now WHERE id = :id")
    suspend fun markRemoved(id: String, now: Long)

    @Query("SELECT * FROM ai_models WHERE status = 'INSTALLED'")
    suspend fun installedOnce(): List<AiModelEntity>
}

@Dao
interface NotificationRuleDao {
    @Query("SELECT * FROM notification_rules")
    fun observeAll(): Flow<List<NotificationRuleEntity>>

    @Query("SELECT * FROM notification_rules WHERE vipLevel = :vipLevel LIMIT 1")
    suspend fun findForLevel(vipLevel: String): NotificationRuleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(rules: List<NotificationRuleEntity>)

    @Upsert
    suspend fun upsert(rule: NotificationRuleEntity)

    @Query("SELECT * FROM notification_rules")
    suspend fun allOnce(): List<NotificationRuleEntity>
}
