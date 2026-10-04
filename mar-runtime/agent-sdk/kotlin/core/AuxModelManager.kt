package com.mar.agent.sdk.core

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * AuxModelManager: Manages auto-download and lifecycle of small auxiliary ML models.
 *
 * These micro-models (~12MB total) form the "Tier 0/1" inference layer — they run
 * in <10ms and handle routing, slot extraction, and semantic matching so the
 * 350MB Qwen SLM is only invoked for genuinely non-deterministic tasks.
 *
 * All paths degrade gracefully: if a model isn't downloaded yet, callers fall
 * through to the next tier (SLM). No crashes, no blocking.
 */
object AuxModelManager {

    private const val TAG = "MAR_AuxModelManager"
    private const val AUX_DIR = "models/aux"

    /**
     * Catalog of all auxiliary models MAR needs.
     * URLs should point to the hosted model files (update before shipping).
     */
    val INTENT_CLASSIFIER = AuxModel(
        name = "intent_classifier.tflite",
        // Replace with your hosted URL — e.g. a GitHub release asset or GCS bucket
        downloadUrl = "https://github.com/your-org/mar-models/releases/download/v1.0/intent_classifier.tflite",
        sizeBytes = 4_000_000L,
        description = "TFLite intent classifier — routes user commands to action buckets"
    )

    val USE_LITE = AuxModel(
        name = "use_lite.tflite",
        downloadUrl = "https://tfhub.dev/google/lite-model/universal-sentence-encoder-qa-ondevice/1/lite/3?lite-format=tflite",
        sizeBytes = 6_000_000L,
        description = "Universal Sentence Encoder Lite — semantic similarity for notification triggers"
    )

    /** All models that should be available on the device. */
    private val ALL_MODELS = listOf(INTENT_CLASSIFIER, USE_LITE)

    /**
     * Returns the local file for a model, or null if not yet downloaded.
     * Always check this before using a model — never block on download here.
     */
    fun getModelFile(context: Context, model: AuxModel): File? {
        val file = File(context.filesDir, "$AUX_DIR/${model.name}")
        return if (file.exists() && file.length() > model.sizeBytes / 2) file else null
    }

    /**
     * Call once from MainActivity.onCreate() (or equivalent app start).
     * Checks which models are missing and enqueues background downloads for them.
     * Does not block. Does not download on metered networks by default.
     */
    fun ensureModelsReady(context: Context) {
        val missing = ALL_MODELS.filter { getModelFile(context, it) == null }
        if (missing.isEmpty()) {
            Log.i(TAG, "All aux models present — no download needed")
            return
        }
        Log.i(TAG, "Scheduling download for ${missing.size} aux model(s): ${missing.map { it.name }}")
        missing.forEach { model -> scheduleDownload(context, model) }
    }

    /**
     * Returns a human-readable status string suitable for display in Settings.
     * e.g. "Smart models: 1/2 ready (downloading...)"
     */
    fun getStatusString(context: Context): String {
        val ready = ALL_MODELS.count { getModelFile(context, it) != null }
        val total = ALL_MODELS.size
        return if (ready == total) {
            "Smart routing models: ready ✓"
        } else {
            "Smart routing models: $ready/$total ready (downloading in background...)"
        }
    }

    private fun scheduleDownload(context: Context, model: AuxModel) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // Prefer WiFi; falls back to any network
            .setRequiresBatteryNotLow(true)
            .build()

        val inputData = workDataOf(
            "model_name" to model.name,
            "model_url" to model.downloadUrl,
            "model_size" to model.sizeBytes
        )

        val request = OneTimeWorkRequestBuilder<AuxModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag("mar_aux_model_download")
            .addTag("mar_aux_${model.name}")
            .build()

        WorkManager.getInstance(context).enqueue(request)
        Log.i(TAG, "Enqueued download for ${model.name}")
    }
}

data class AuxModel(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val description: String = ""
)

/**
 * WorkManager worker that downloads a single aux model file to filesDir/models/aux/.
 * Runs in the background, respects battery and network constraints.
 */
class AuxModelDownloadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val name = inputData.getString("model_name") ?: return@withContext Result.failure()
        val url = inputData.getString("model_url") ?: return@withContext Result.failure()

        val auxDir = File(context.filesDir, "models/aux").also { it.mkdirs() }
        val destFile = File(auxDir, name)

        // Skip if already downloaded and non-empty
        if (destFile.exists() && destFile.length() > 1000) {
            Log.i(TAG, "$name already exists, skipping download")
            return@withContext Result.success()
        }

        Log.i(TAG, "Downloading aux model: $name from $url")
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 120_000
            conn.setRequestProperty("User-Agent", "MAR-AuxModelManager/1.0")
            conn.connect()

            if (conn.responseCode != 200) {
                Log.w(TAG, "Download failed for $name: HTTP ${conn.responseCode}")
                return@withContext Result.retry()
            }

            val tempFile = File(auxDir, "$name.tmp")
            conn.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
            // Atomic rename — avoids using a partial file
            tempFile.renameTo(destFile)
            Log.i(TAG, "Downloaded $name successfully (${destFile.length()} bytes)")

            // Eagerly load the classifier now that it's available
            if (name == AuxModelManager.INTENT_CLASSIFIER.name) {
                IntentClassifier.load(context)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download $name: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MAR_AuxModelDownload"
    }
}
