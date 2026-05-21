package com.mar.agent.sdk.models

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ModelRepository(private val context: Context) {

    private val dao = com.mar.agent.sdk.db.AppDatabase.getInstance(context).modelDao()

    fun getAllFlow(): Flow<List<ModelEntity>> = dao.getAllFlow()

    suspend fun getAll(): List<ModelEntity> = dao.getAll()

    suspend fun getById(id: String): ModelEntity? = dao.getById(id)

    fun getByIdBlocking(id: String): ModelEntity? = runBlocking { dao.getById(id) }

    suspend fun save(model: ModelEntity) {
        dao.upsert(model)
    }

    suspend fun delete(id: String) {
        val entity = dao.getById(id)
        if (entity != null) {
            File(entity.filePath).delete()
        }
        dao.delete(id)
    }

    fun getModelDir(): File {
        val dir = context.getExternalFilesDir("models") ?: context.filesDir
        dir.mkdirs()
        return dir
    }

    fun findLocalModelPath(modelId: String? = null): String? {
        val modelDir = getModelDir()

        if (modelId != null) {
            val entity = getByIdBlocking(modelId)
            if (entity != null && File(entity.filePath).exists()) {
                return entity.filePath
            }
            val partial = runBlocking { dao.getAll().firstOrNull { it.id.contains(modelId, ignoreCase = true) } }
            if (partial != null && File(partial.filePath).exists()) {
                return partial.filePath
            }
            val file = File(modelDir, modelId)
            if (file.name.endsWith(".gguf") && file.exists()) return file.absolutePath
        }

        val ggufFiles = modelDir.listFiles { f -> f.name.endsWith(".gguf") }
        if (ggufFiles != null && ggufFiles.isNotEmpty()) {
            return ggufFiles.first().absolutePath
        }

        val oldFile = File(context.filesDir, "qwen2.5-0.5b.gguf")
        if (oldFile.exists()) return oldFile.absolutePath

        val oldQ4 = File(context.filesDir, "qwen2.5-0.5b-q4_k_m.gguf")
        if (oldQ4.exists()) return oldQ4.absolutePath

        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        if (downloadsDir.exists()) {
            val found = findGgufRecursive(downloadsDir, 0)
            if (found != null) return found.absolutePath
        }

        return null
    }

    private fun downloadWithRetry(url: URL, outFile: File, maxRetries: Int = 3): Boolean {
        var lastError: String? = null
        for (attempt in 1..maxRetries) {
            try {
                if (outFile.exists()) outFile.delete()
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 30000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "MAR-Downloader/1.0")
                conn.connect()
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    lastError = "HTTP ${conn.responseCode}"
                    Log.w("MAR_ModelRepo", "Attempt $attempt/$maxRetries: $lastError")
                    if (attempt < maxRetries) Thread.sleep(1000L * attempt)
                    continue
                }
                val input = conn.inputStream
                val output = FileOutputStream(outFile)
                val buf = ByteArray(8192)
                while (true) {
                    val read = input.read(buf)
                    if (read == -1) break
                    output.write(buf, 0, read)
                }
                output.close(); input.close()
                if (outFile.exists() && outFile.length() > 0) return true
            } catch (e: Exception) {
                lastError = "${e::class.simpleName}: ${e.message}"
                Log.w("MAR_ModelRepo", "Attempt $attempt/$maxRetries failed: $lastError")
                if (attempt < maxRetries) Thread.sleep(1000L * attempt)
            }
        }
        Log.e("MAR_ModelRepo", "All $maxRetries attempts failed: $lastError")
        return false
    }

    fun downloadModel(hfModelId: String, hfFile: HfFileInfo): Flow<DownloadProgress> = flow {
        val modelDir = getModelDir()
        val modelFile = File(modelDir, hfFile.name)

        if (modelFile.exists()) {
            val entity = registrationEntity(hfModelId, hfFile, modelFile.absolutePath)
            dao.upsert(entity)
            emit(DownloadProgress.Done(modelFile))
            return@flow
        }

        val downloadUrl = URL("https://huggingface.co/$hfModelId/resolve/main/${hfFile.name}")
        val success = downloadWithRetry(downloadUrl, modelFile)

        if (success) {
            val entity = registrationEntity(hfModelId, hfFile, modelFile.absolutePath)
            dao.upsert(entity)
            emit(DownloadProgress.Done(modelFile))
        } else {
            modelFile.delete()
            emit(DownloadProgress.Error("Download failed after retries"))
        }
    }.flowOn(Dispatchers.IO)

    private fun registrationEntity(hfModelId: String, hfFile: HfFileInfo, filePath: String): ModelEntity {
        return ModelEntity(
            id = hfFile.name.replace(".gguf", ""),
            name = hfModelId.split("/").lastOrNull() ?: hfModelId,
            description = "${hfModelId} · ${hfFile.quantization}",
            url = "https://huggingface.co/$hfModelId/resolve/main/${hfFile.name}",
            sizeMb = hfFile.size / (1024 * 1024),
            quantization = hfFile.quantization,
            family = hfModelId.split("/").firstOrNull() ?: "",
            parameters = "",
            filePath = filePath
        )
    }

    /**
     * Scan the model directory and register any .gguf files not yet in Room.
     * Call on startup to auto-discover models placed in the folder manually.
     */
    suspend fun scanAndRegisterModels() {
        val modelDir = getModelDir()
        val allEntities = dao.getAll()
        val validFileNames = mutableSetOf<String>()

        val files = modelDir.listFiles { f -> f.name.endsWith(".gguf") } ?: emptyArray()
        for (file in files) {
            if (file.length() < 50_000_000) {
                file.delete()
                continue
            }
            if (!isValidGguf(file)) {
                Log.w("MAR_ModelRepo", "Corrupt GGUF header in ${file.name}, skipping")
                continue
            }
            validFileNames.add(file.name)
            val id = file.name.replace(".gguf", "")
            if (allEntities.any { it.id == id }) continue
            val entity = ModelEntity(
                id = id,
                name = id,
                description = "Discovered: ${file.name}",
                url = "",
                sizeMb = file.length() / (1024 * 1024),
                quantization = "",
                family = "",
                parameters = "",
                filePath = file.absolutePath
            )
            dao.upsert(entity)
        }

        for (entity in allEntities) {
            val fileName = File(entity.filePath).name
            if (fileName !in validFileNames && !File(entity.filePath).exists()) {
                dao.delete(entity.id)
            }
        }
    }

    private fun isValidGguf(file: File): Boolean {
        return try {
            file.inputStream().use { s ->
                val hdr = ByteArray(4)
                s.read(hdr) == 4 && hdr[0] == 0x47.toByte() && hdr[1] == 0x47.toByte()
                        && hdr[2] == 0x55.toByte() && hdr[3] == 0x46.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun findGgufRecursive(dir: File, depth: Int): File? {
        if (depth > 3) return null
        val files = dir.listFiles() ?: return null
        val gguf = files.firstOrNull { it.isFile && it.name.endsWith(".gguf") }
        if (gguf != null) return gguf
        for (f in files) {
            if (f.isDirectory) {
                val found = findGgufRecursive(f, depth + 1)
                if (found != null) return found
            }
        }
        return null
    }
}

sealed class DownloadProgress {
    data class Progress(val pct: Int, val downloadedMb: Float, val totalMb: Float) : DownloadProgress()
    data class Done(val file: File) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}
