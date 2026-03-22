package com.mar.runtime.core

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import java.io.File

/**
 * MultiAgentRuntimeManager:
 * Safely queues multiple agent requests to prevent SIGSEGV crashes 
 * resulting from concurrent access to llama.cpp/JNI inference state.
 * Reduces redundant model loading overhead.
 */
object MultiAgentRuntimeManager {
    
    private val inferenceMutex = Mutex()
    private var isEngineInitialized = false
    private var isModelLoaded = false

    private fun findLocalModelPath(context: Context): String {
        val defaultModel = File(context.filesDir, "qwen2.5-0.5b.gguf")
        if (defaultModel.exists()) return defaultModel.absolutePath

        // Check if there is any gguf in filesDir
        val localGguf = context.filesDir.listFiles()?.firstOrNull { it.name.endsWith(".gguf") }
        if (localGguf != null) return localGguf.absolutePath

        // Search in external Downloads directory up to 3 levels deep
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && downloadsDir.exists()) {
                val gguf = findGgufRecursive(downloadsDir, 0)
                if (gguf != null) {
                    Log.i("MAR_RuntimeManager", "Found existing model in Downloads: ${gguf.absolutePath}")
                    return gguf.absolutePath
                }
            }
        } catch (e: Exception) {
            Log.e("MAR_RuntimeManager", "Error searching for models: ${e.message}")
        }
        
        return localGguf?.absolutePath ?: defaultModel.absolutePath
    }

    private fun findGgufRecursive(dir: File, depth: Int): File? {
        if (depth > 3) return null
        val files = dir.listFiles() ?: return null
        
        val ggufFile = files.firstOrNull { it.isFile && it.name.endsWith(".gguf") }
        if (ggufFile != null) return ggufFile
        
        for (f in files) {
            if (f.isDirectory) {
                val found = findGgufRecursive(f, depth + 1)
                if (found != null) return found
            }
        }
        return null
    }

    suspend fun executeInference(context: Context, prompt: String, modelPathOverride: String? = null): String {
        return inferenceMutex.withLock {
            withContext(Dispatchers.IO) {
                // Initialize engine if off
                if (!isEngineInitialized) {
                    Log.i("MAR_RuntimeManager", "Initializing native HAL...")
                    isEngineInitialized = MarBridge.initialize(maxRamMb = 1024, threads = 4)
                    if (!isEngineInitialized) {
                        return@withContext "{\"error\": \"Failed to init HAL\"}"
                    }
                }

                val modelPath = modelPathOverride ?: findLocalModelPath(context)
                
                // If model changed or not loaded, load it.
                val fileObj = java.io.File(modelPath)
                if (!isModelLoaded && fileObj.exists() && fileObj.length() > 0) {
                    Log.i("MAR_RuntimeManager", "Loading memory-mapped model weights from ${modelPath}...")
                    isModelLoaded = MarBridge.loadModel(modelPath)
                } else if (!fileObj.exists() || fileObj.length() == 0L) {
                    Log.e("MAR_RuntimeManager", "ABORT: Model file is missing or corrupted at $modelPath")
                    return@withContext "{\"error\": \"Model file missing\"}"
                }

                Log.i("MAR_RuntimeManager", "Generating offline inference...")
                return@withContext MarBridge.runInferenceTest(prompt)
            }
        }
    }
}
