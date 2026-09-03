package dev.tmdbrows.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ListConfigDao {
    @Query("SELECT * FROM list_config ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<ListConfig>>

    @Query("SELECT * FROM list_config ORDER BY sortOrder, id")
    suspend fun getAll(): List<ListConfig>

    @Query("SELECT * FROM list_config WHERE id = :id")
    suspend fun get(id: Long): ListConfig?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) + 1 FROM list_config")
    suspend fun nextSortOrder(): Int

    @Insert suspend fun insert(c: ListConfig): Long
    @Update suspend fun update(c: ListConfig)
    @Delete suspend fun delete(c: ListConfig)
}

@Dao
interface CachedItemDao {
    @Query("SELECT * FROM cached_item WHERE configId = :configId")
    suspend fun forConfig(configId: Long): List<CachedItem>

    @Query("SELECT * FROM cached_item WHERE configId = :configId LIMIT :limit")
    fun observePreview(configId: Long, limit: Int): Flow<List<CachedItem>>

    @Query("SELECT COUNT(*) FROM cached_item WHERE configId = :configId")
    fun observeCount(configId: Long): Flow<Int>

    @Query("SELECT * FROM cached_item WHERE configId = :configId AND tmdbId = :tmdbId AND mediaType = :type")
    suspend fun find(configId: Long, tmdbId: Long, type: String): CachedItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedItem>)

    @Query("DELETE FROM cached_item WHERE configId = :configId")
    suspend fun deleteForConfig(configId: Long)

    @Query("DELETE FROM cached_item WHERE configId = :configId AND tmdbId NOT IN (:keep)")
    suspend fun deleteNotIn(configId: Long, keep: List<Long>)
}

@Dao
interface CustomTargetDao {
    @Query("SELECT * FROM custom_target ORDER BY label")
    fun observeAll(): Flow<List<CustomTarget>>

    @Query("SELECT * FROM custom_target ORDER BY label")
    suspend fun getAll(): List<CustomTarget>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(t: CustomTarget): Long

    @Delete suspend fun delete(t: CustomTarget)
}
