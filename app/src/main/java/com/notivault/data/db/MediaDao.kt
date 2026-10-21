package com.notivault.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(m: MediaEntity): Long

    @Query("SELECT sourceUri FROM media WHERE treeUri = :tree AND removedFromSourceAt IS NULL")
    suspend fun liveUris(tree: String): List<String>

    @Query("SELECT sourceUri FROM media WHERE treeUri = :tree")
    suspend fun knownUris(tree: String): List<String>

    @Query("UPDATE media SET removedFromSourceAt = :at WHERE sourceUri IN (:uris) AND removedFromSourceAt IS NULL")
    suspend fun markRemoved(uris: List<String>, at: Long): Int

    @Query("SELECT * FROM media WHERE (:removedOnly = 0 OR removedFromSourceAt IS NOT NULL) ORDER BY sourceModified DESC")
    fun page(removedOnly: Boolean): PagingSource<Int, MediaEntity>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun get(id: Long): MediaEntity?

    @Query("SELECT (SELECT COUNT(*) FROM media) AS total, (SELECT COUNT(*) FROM media WHERE removedFromSourceAt IS NOT NULL) AS removed")
    fun stats(): Flow<MediaStats>

    @Query("SELECT vaultPath FROM media WHERE vaultPath IS NOT NULL")
    suspend fun allPaths(): List<String>

    @Query("SELECT vaultPath FROM media WHERE capturedAt < :cutoff AND vaultPath IS NOT NULL")
    suspend fun pathsOlderThan(cutoff: Long): List<String>

    @Query("DELETE FROM media WHERE capturedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM media")
    suspend fun wipe()
}
