package com.mar.runtime.core

import android.content.Context
import android.os.Environment
import android.util.Log
import com.mar.agent.sdk.models.ModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

object MultiAgentRuntimeManager {

    private val inferenceMutex = Mutex()
    private const val INFERENCE_TIMEOUT_MS = 120_000L
    private val cpuThreads = (Runtime.getRuntime().availableProcessors().coerceIn(2, 8)).also {
        Log.i("MAR_RuntimeManager", "CPU cores detected: $it")
    }

    @Volatile var isEngineInitialized = false
        private set
    @Volatile var isModelLoaded = false
        private set
    private var loadedModelPath = ""

    fun findLocalModelPath(context: Context, modelId: String? = null): String {
        val repo = ModelRepository(context)
        val fromRepo = repo.findLocalModelPath(modelId)
        if (fromRepo != null) return fromRepo

        val defaultModel = File(context.filesDir, "qwen2.5-0.5b-q4_k_m.gguf")
        if (defaultModel.exists()) return defaultModel.absolutePath

        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir.exists()) {
                val gguf = findGgufRecursive(downloadsDir, 0)
                if (gguf != null) return gguf.absolutePath
            }
        } catch (e: Exception) { Log.e("MAR_RuntimeManager", "findLocalModelPath error: ${e.message}") }

        return defaultModel.absolutePath
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

    suspend fun loadModelOnly(context: Context, modelPath: String): Boolean {
        if (isModelLoaded && modelPath == loadedModelPath) return true
        return withContext(Dispatchers.IO) {
            if (!isEngineInitialized) {
                isEngineInitialized = MarBridge.initialize(maxRamMb = 1024, threads = cpuThreads)
                if (!isEngineInitialized) return@withContext false
            }
            isModelLoaded = MarBridge.loadModel(modelPath)
            if (isModelLoaded) loadedModelPath = modelPath
            isModelLoaded
        }
    }

    suspend fun executeInference(context: Context, prompt: String, modelPathOverride: String? = null): String {
        return inferenceMutex.withLock {
            if (!isEngineInitialized) {
                Log.i("MAR_RuntimeManager", "Initializing native HAL...")
                isEngineInitialized = withContext(Dispatchers.IO) {
                    MarBridge.initialize(maxRamMb = 1024, threads = cpuThreads)
                }
                if (!isEngineInitialized) {
                    return@withLock "{\"error\": \"Failed to init HAL\"}"
                }
            }

            val modelPath = modelPathOverride ?: findLocalModelPath(context)
            val fileObj = File(modelPath)

            if (modelPath != loadedModelPath) {
                isModelLoaded = false
                loadedModelPath = ""
            }

            if (!isModelLoaded && fileObj.exists() && fileObj.length() > 0) {
                Log.i("MAR_RuntimeManager", "Loading model: ${modelPath}")
                isModelLoaded = withContext(Dispatchers.IO) {
                    MarBridge.loadModel(modelPath)
                }
                if (isModelLoaded) loadedModelPath = modelPath
            } else if (!fileObj.exists() || fileObj.length() == 0L) {
                return@withLock "{\"error\": \"Model file missing: $modelPath\"}"
            }

            withTimeout(INFERENCE_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    MarBridge.runInference(prompt)
                }
            }
        }
    }
}
