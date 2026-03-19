package com.mar.demo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloader {
    private const val MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf"
    private const val MODEL_FILENAME = "qwen2.5-0.5b.gguf"

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val progressPct: Int, val downloadedMb: Float, val totalMb: Float) : DownloadState()
        data class Success(val file: File) : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    fun downloadModel(context: Context): Flow<DownloadState> = flow {
        val modelFile = File(context.filesDir, MODEL_FILENAME)
        
        // If it already exists and has a reasonable size (> 100MB), skip download
        if (modelFile.exists() && modelFile.length() > 100 * 1024 * 1024) {
            emit(DownloadState.Success(modelFile))
            return@flow
        }

        try {
            val url = URL(MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                emit(DownloadState.Error("HTTP Error: ${connection.responseCode}"))
                return@flow
            }

            val fileLength = connection.contentLength
            val inputStream = connection.inputStream
            val outputStream = modelFile.outputStream()

            val data = ByteArray(8192)
            var total: Long = 0
            var count: Int
            
            var lastEmitTime = System.currentTimeMillis()

            while (inputStream.read(data).also { count = it } != -1) {
                total += count
                outputStream.write(data, 0, count)
                
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastEmitTime > 300) { // Throttle UI updates
                    val progress = (total * 100 / fileLength).toInt()
                    emit(DownloadState.Downloading(
                        progressPct = progress,
                        downloadedMb = total / (1024f * 1024f),
                        totalMb = fileLength / (1024f * 1024f)
                    ))
                    lastEmitTime = currentTime
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            emit(DownloadState.Success(modelFile))

        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "Unknown download error"))
        }
    }.flowOn(Dispatchers.IO)
    
    fun getLocalModelPath(context: Context): String? {
        val file = File(context.filesDir, MODEL_FILENAME)
        return if (file.exists()) file.absolutePath else null
    }
}
