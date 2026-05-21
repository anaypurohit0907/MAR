package com.mar.agent.sdk.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkflowDao {
    @Query("SELECT * FROM workflows ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<WorkflowEntity>>

    @Query("SELECT * FROM workflows ORDER BY updatedAt DESC")
    suspend fun getAll(): List<WorkflowEntity>

    @Query("SELECT * FROM workflows WHERE id = :id")
    suspend fun getById(id: String): WorkflowEntity?

    @Query("SELECT * FROM workflows WHERE enabled = 1")
    suspend fun getEnabled(): List<WorkflowEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkflowEntity)

    @Query("UPDATE workflows SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE workflows SET lastRunAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun markRun(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE workflows SET errorCount = errorCount + 1, updatedAt = :now WHERE id = :id")
    suspend fun incrementError(id: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM workflows WHERE id = :id")
    suspend fun delete(id: String)
}
