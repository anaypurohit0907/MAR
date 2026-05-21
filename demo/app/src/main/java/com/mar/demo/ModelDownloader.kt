package com.mar.demo

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloader {
    const val MODEL_FILENAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
    val MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/$MODEL_FILENAME"

    data class CuratedModel(val label: String, val hfRepo: String, val hfFile: String, val expectedBytes: Long = 0L)

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val progressPct: Int, val downloadedMb: Float, val totalMb: Float) : DownloadState()
        data class Success(val file: File) : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    private fun getModelDir(context: Context): File {
        return context.getExternalFilesDir("models") ?: context.filesDir
    }

    private fun downloadWithRetry(url: URL, file: File, timeoutMs: Int = 30000, maxRetries: Int = 3): Long? {
        var lastError: String? = null
        for (attempt in 1..maxRetries) {
            try {
                if (file.exists()) file.delete()
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.setRequestProperty("User-Agent", "MAR-Downloader/1.0")
                conn.connect()
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    lastError = "HTTP ${conn.responseCode}"
                    Log.w("MAR_Downloader", "Attempt $attempt/$maxRetries: $lastError")
                    if (attempt < maxRetries) Thread.sleep(1000L * attempt)
                    continue
                }
                val input = conn.inputStream
                val output = file.outputStream()
                val buf = ByteArray(8192)
                var total = 0L
                while (true) {
                    val read = input.read(buf)
                    if (read == -1) break
                    output.write(buf, 0, read)
                    total += read
                }
                output.close(); input.close()
                if (file.exists() && file.length() > 0) return total
            } catch (e: Exception) {
                lastError = "${e::class.simpleName}: ${e.message}"
                Log.w("MAR_Downloader", "Attempt $attempt/$maxRetries failed: $lastError")
                if (attempt < maxRetries) Thread.sleep(1000L * attempt)
            }
        }
        Log.e("MAR_Downloader", "All $maxRetries attempts failed: $lastError")
        return null
    }

    fun downloadModel(context: Context, cm: CuratedModel): Flow<DownloadState> = flow {
        val modelDir = getModelDir(context)
        modelDir.mkdirs()
        val file = File(modelDir, cm.hfFile)
        if (file.exists() && file.length() > cm.expectedBytes * 9 / 10) {
            emit(DownloadState.Success(file))
            return@flow
        }
        val url = URL("https://huggingface.co/${cm.hfRepo}/resolve/main/${cm.hfFile}")
        val totalLen = try {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.requestMethod = "HEAD"
            conn.connect()
            if (conn.responseCode == 200) conn.contentLength else cm.expectedBytes
        } catch (e: Exception) { cm.expectedBytes }
        val result = downloadWithRetry(url, file)
        if (result != null) {
            emit(DownloadState.Success(file))
        } else {
            if (file.exists()) file.delete()
            emit(DownloadState.Error("Download failed after retries"))
        }
    }.flowOn(Dispatchers.IO)

    fun downloadModel(context: Context): Flow<DownloadState> = flow {
        val modelDir = getModelDir(context)
        modelDir.mkdirs()
        val modelFile = File(modelDir, MODEL_FILENAME)

        val oldFile = File(context.filesDir, MODEL_FILENAME)
        if (!modelFile.exists() && oldFile.exists() && oldFile.length() > 100 * 1024 * 1024) {
            oldFile.renameTo(modelFile)
        }

        if (modelFile.exists() && modelFile.length() > 100 * 1024 * 1024) {
            emit(DownloadState.Success(modelFile))
            return@flow
        }

        val url = URL(MODEL_URL)
        val result = downloadWithRetry(url, modelFile)
        if (result != null) {
            emit(DownloadState.Success(modelFile))
        } else {
            if (modelFile.exists()) modelFile.delete()
            emit(DownloadState.Error("Download failed after retries"))
        }
    }.flowOn(Dispatchers.IO)
    
    fun getLocalModelPath(context: Context): String? {
        val modelDir = getModelDir(context)
        val file = File(modelDir, MODEL_FILENAME)
        if (file.exists()) return file.absolutePath

        // Fallback: old internal filesDir location or Downloads
        val oldFile = File(context.filesDir, MODEL_FILENAME)
        if (oldFile.exists()) return oldFile.absolutePath

        try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && downloadsDir.exists()) {
                val gguf = findGgufRecursive(downloadsDir, 0)
                if (gguf != null) return gguf.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return null
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
}
