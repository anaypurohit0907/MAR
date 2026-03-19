package com.mar.agent.sdk.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "agent_state")
data class AgentStateEntity(
    @PrimaryKey val taskId: String,
    @ColumnInfo(name = "agent_id") val agentId: String,
    @ColumnInfo(name = "state_blob") val stateBlob: ByteArray,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long
)

@Dao
interface AgentStateDao {
    @Query("SELECT * FROM agent_state WHERE agent_id = :agentId")
    fun getStateForAgent(agentId: String): Flow<AgentStateEntity?>

    @Query("SELECT * FROM agent_state WHERE taskId = :taskId")
    suspend fun getStateByTaskId(taskId: String): AgentStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveState(state: AgentStateEntity)

    @Query("DELETE FROM agent_state WHERE taskId = :taskId")
    suspend fun deleteState(taskId: String)
}

@Database(entities = [AgentStateEntity::class], version = 1, exportSchema = false)
abstract class MarAppDatabase : RoomDatabase() {
    abstract fun agentStateDao(): AgentStateDao

    companion object {
        @Volatile
        private var INSTANCE: MarAppDatabase? = null

        fun getDatabase(context: android.content.Context): MarAppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MarAppDatabase::class.java,
                    "mar_agent_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
