package com.mar.agent.sdk.db

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.yaml.snakeyaml.Yaml
import java.io.File

class WorkflowRepository(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).workflowDao()
    private val yamlParser = Yaml()

    fun getAllFlow(): Flow<List<WorkflowEntity>> = dao.getAllFlow()

    suspend fun getAll(): List<WorkflowEntity> = dao.getAll()

    suspend fun getById(id: String): WorkflowEntity? = dao.getById(id)

    suspend fun getEnabled(): List<WorkflowEntity> = dao.getEnabled()

    fun getEnabledBlocking(): List<WorkflowEntity> = runBlocking { dao.getEnabled() }

    suspend fun save(id: String, name: String, description: String, yaml: String) {
        val existing = dao.getById(id)
        dao.upsert(
            WorkflowEntity(
                id = id,
                name = name,
                description = description,
                yaml = yaml,
                enabled = existing?.enabled ?: true,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        // Also write to filesDir for backward compat with TermuxCliReceiver
        File(context.filesDir, "$id.yaml").writeText(yaml)
    }

    suspend fun delete(id: String) {
        dao.delete(id)
        File(context.filesDir, "$id.yaml").delete()
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        dao.setEnabled(id, enabled)
    }

    suspend fun markRun(id: String) {
        dao.markRun(id)
    }

    suspend fun incrementError(id: String) {
        dao.incrementError(id)
    }

    suspend fun loadYaml(id: String): String? {
        val entity = dao.getById(id)
        if (entity != null) return entity.yaml
        // Fallback to filesDir
        val file = File(context.filesDir, "$id.yaml")
        return if (file.exists()) file.readText() else null
    }

    suspend fun importFromFilesDir() {
        val files = context.filesDir.listFiles { f -> f.name.endsWith(".yaml") } ?: return
        for (file in files) {
            val id = file.nameWithoutExtension
            if (dao.getById(id) != null) continue
            val text = file.readText()
            var name = id
            var description = ""
            try {
                val map = yamlParser.load<Map<String, Any>>(text)
                val agent = map["agent"] as? Map<*, *>
                if (agent != null) {
                    agent["name"]?.toString()?.let { name = it }
                    agent["description"]?.toString()?.let { description = it }
                }
            } catch (e: Exception) { Log.w("MAR_WFRepo", "Failed to parse YAML metadata for $id: ${e.message}") }
            dao.upsert(
                WorkflowEntity(
                    id = id, name = name, description = description,
                    yaml = text, createdAt = file.lastModified(), updatedAt = file.lastModified()
                )
            )
        }
    }

    suspend fun yamlExists(id: String): Boolean {
        return dao.getById(id) != null || File(context.filesDir, "$id.yaml").exists()
    }
}
