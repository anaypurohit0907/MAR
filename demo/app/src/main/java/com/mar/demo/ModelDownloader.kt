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

    fun downloadModel(context: Context, cm: CuratedModel): Flow<DownloadState> = flow {
        val modelDir = getModelDir(context)
        modelDir.mkdirs()
        val file = File(modelDir, cm.hfFile)
        
        if (file.exists() && file.length() > 50_000_000) {
            emit(DownloadState.Success(file))
            return@flow
        }

        var urlString = "https://huggingface.co/${cm.hfRepo}/resolve/main/${cm.hfFile}"
        lastDebugLog = "Init: ${cm.label}"
        
        try {
            if (file.exists()) file.delete()
            
            var connection: HttpURLConnection
            var responseCode: Int
            var redirects = 0
            val maxRedirects = 5

            do {
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:100.0) Gecko/100.0 Firefox/100.0")
                
                responseCode = connection.responseCode
                lastDebugLog = "HTTP $responseCode"
                
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == 301 || responseCode == 302 ||
                    responseCode == 307 || responseCode == 308) {
                    
                    val newUrl = connection.getHeaderField("Location")
                    if (newUrl != null) {
                        urlString = newUrl
                        redirects++
                        lastDebugLog = "Redirect $redirects..."
                    } else break
                } else break
            } while (redirects < maxRedirects)

            if (responseCode != HttpURLConnection.HTTP_OK) {
                lastDebugLog = "Error HTTP $responseCode"
                emit(DownloadState.Error("HTTP $responseCode"))
                return@flow
            }

            val totalBytes = connection.contentLength.toLong().let { if (it <= 0) cm.expectedBytes else it }
            val input = connection.inputStream
            val output = file.outputStream()
            val buf = ByteArray(1024 * 32)
            var downloaded = 0L
            var lastUpdate = 0L

            while (true) {
                val read = input.read(buf)
                if (read == -1) break
                output.write(buf, 0, read)
                downloaded += read
                
                val now = System.currentTimeMillis()
                if (now - lastUpdate > 1000) {
                    val pct = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt() else 0
                    emit(DownloadState.Downloading(pct, downloaded / (1024f * 1024f), totalBytes / (1024f * 1024f)))
                    lastDebugLog = "DL: $pct% (${downloaded / (1024*1024)}MB)"
                    lastUpdate = now
                }
            }
            output.close(); input.close()
            
            if (file.exists() && file.length() > 50_000_000) {
                lastDebugLog = "Done: ${file.name}"
                emit(DownloadState.Success(file))
            } else {
                lastDebugLog = "Corrupt: ${file.length()}b"
                emit(DownloadState.Error("File corrupt or too small"))
            }
        } catch (e: Exception) {
            lastDebugLog = "Ex: ${e.message}"
            Log.e("MAR_Downloader", "Download failed", e)
            emit(DownloadState.Error("${e::class.simpleName}: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    fun downloadModel(context: Context): Flow<DownloadState> = downloadModel(context, 
        CuratedModel("Default", "Qwen/Qwen2.5-0.5B-Instruct-GGUF", MODEL_FILENAME, 370_000_000L))
    
    fun getLocalModelPath(context: Context): String? {
        val modelDir = getModelDir(context)
        val files = modelDir.listFiles { f -> f.name.endsWith(".gguf") }
        if (files != null && files.isNotEmpty()) return files.first().absolutePath

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
