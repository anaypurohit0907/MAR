package com.mar.agent.sdk.models

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY downloadedAt DESC")
    fun getAllFlow(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models ORDER BY downloadedAt DESC")
    suspend fun getAll(): List<ModelEntity>

    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun getById(id: String): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(model: ModelEntity)

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM models")
    suspend fun count(): Int
}
