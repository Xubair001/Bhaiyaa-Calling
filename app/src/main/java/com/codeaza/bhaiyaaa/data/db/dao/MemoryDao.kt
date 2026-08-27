package com.codeaza.bhaiyaaa.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.codeaza.bhaiyaaa.data.db.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    /** Used when the privacy lock is on but the user has not unlocked yet. */
    @Query("SELECT * FROM memories WHERE isPrivate = 0 ORDER BY createdAt DESC")
    fun observeNonPrivate(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE contactPhoneNumber = :phoneNumber ORDER BY createdAt DESC")
    fun observeForContact(phoneNumber: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): MemoryEntity?

    @Insert
    suspend fun insert(memory: MemoryEntity): Long

    @Update
    suspend fun update(memory: MemoryEntity)

    @Delete
    suspend fun delete(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM memories")
    fun observeCount(): Flow<Int>

    /**
     * Full-text search over the FTS4 index. `memories_fts` is an external-content
     * table over `memories`, so this hits the index rather than scanning rows.
     * The caller passes an already-sanitised FTS query (see MemorySearch).
     */
    @Query(
        """
        SELECT m.* FROM memories AS m
        JOIN memories_fts AS f ON f.rowid = m.id
        WHERE memories_fts MATCH :ftsQuery
        ORDER BY m.createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchFts(ftsQuery: String, limit: Int = 50): List<MemoryEntity>

    /** Substring fallback for queries FTS can't express (very short or punctuation-only terms). */
    @Query(
        """
        SELECT * FROM memories
        WHERE body LIKE '%' || :q || '%' OR IFNULL(title,'') LIKE '%' || :q || '%'
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchLike(q: String, limit: Int = 50): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    suspend fun allOnce(): List<MemoryEntity>

    @Query("DELETE FROM memories")
    suspend fun deleteAll()
}
