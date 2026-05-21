package com.mar.agent.sdk.models

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class HfSearchResult(
    val id: String,
    val author: String,
    val downloads: Long,
    val likes: Long,
    val description: String,
    val ggufFiles: List<HfFileInfo>
)

data class HfFileInfo(
    val name: String,
    val size: Long,
    val quantization: String
)

data class HardwareRating(
    val label: String,
    val color: Int,
    val detail: String
)

object HuggingFaceApi {

    val TRUSTED_AUTHORS = setOf(
        "google", "Qwen", "deepseek-ai", "microsoft", "meta", "meta-llama",
        "mistralai", "NousResearch", "CohereForAI",
        "google-deepmind", "google-research",
        "bartowski", "MaziyarPanahi", "TechxGenus", "unsloth", "lmstudio-ai"
    )

    suspend fun searchModels(query: String, limit: Int = 30): List<HfSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val url = URL("https://huggingface.co/api/models?search=${java.net.URLEncoder.encode(query, "UTF-8")}+gguf&sort=downloads&direction=-1&limit=$limit&full=true")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        if (conn.responseCode != 200) return@withContext emptyList()

        val body = conn.inputStream.bufferedReader().readText()
        val arr = JSONArray(body)
        val results = mutableListOf<HfSearchResult>()

        for (i in 0 until arr.length()) {
            try {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id", "")
                val author = obj.optString("author", "")
                val downloads = obj.optLong("downloads", 0)
                val likes = obj.optLong("likes", 0)
                val pipelineTag = obj.optString("pipeline_tag", "")
                val cardData = obj.optJSONObject("cardData")
                val description = cardData?.optString("model-index", "") ?: ""

                val siblings = obj.optJSONArray("siblings") ?: continue
                val ggufFiles = mutableListOf<HfFileInfo>()
                for (j in 0 until siblings.length()) {
                    val sib = siblings.getJSONObject(j)
                    val rfilename = sib.optString("rfilename", "")
                    val size = sib.optLong("size", 0)
                    if (rfilename.endsWith(".gguf") && !rfilename.contains("/") && !rfilename.contains("-of-") && !rfilename.startsWith("mmproj")) {
                        val quant = extractQuantization(rfilename)
                        ggufFiles.add(HfFileInfo(rfilename, size, quant))
                    }
                }

                if (ggufFiles.isNotEmpty() && author in TRUSTED_AUTHORS) {
                    results.add(HfSearchResult(id, author, downloads, likes, description, ggufFiles.sortedByDescending { it.size }))
                }
            } catch (e: Exception) { Log.w("MAR_HF", "Skipping repo: ${e.message}") }
        }

        results
    }

    fun rateHardware(context: Context, fileSizeBytes: Long): HardwareRating {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val modelMb = fileSizeBytes / (1024 * 1024)

        // LLM needs ~2x GGUF size in RAM during inference
        val neededRam = modelMb * 2

        return when {
            neededRam <= totalRamMb * 0.25 -> HardwareRating("Excellent", android.graphics.Color.rgb(52, 168, 83), "Uses ~${neededRam}MB of ${totalRamMb}MB RAM")
            neededRam <= totalRamMb * 0.50 -> HardwareRating("Good", android.graphics.Color.rgb(66, 133, 244), "Uses ~${neededRam}MB of ${totalRamMb}MB RAM")
            neededRam <= totalRamMb * 0.75 -> HardwareRating("Fair", android.graphics.Color.rgb(251, 188, 4), "Uses ~${neededRam}MB of ${totalRamMb}MB RAM")
            else -> HardwareRating("Heavy", android.graphics.Color.rgb(234, 67, 53), "Needs ~${neededRam}MB of ${totalRamMb}MB RAM")
        }
    }

    private fun extractQuantization(filename: String): String {
        val known = listOf(
            "IQ1_M", "IQ1_S", "IQ2_M", "IQ2_XXS", "IQ3_S", "IQ3_XXS",
            "IQ4_NL", "IQ4_XS", "Q4_K_M", "Q4_K_S", "Q4_0", "Q4_1",
            "Q5_K_M", "Q5_K_S", "Q6_K", "Q8_0", "Q2_K", "Q2_K_L",
            "Q3_K_M", "Q3_K_S", "Q3_K_XL", "Q5_K_XL",
            "MXFP4_MOE", "MXFP4", "BF16", "FP16", "FP32"
        )
        for (p in known) {
            val idx = filename.indexOf(p, ignoreCase = true)
            if (idx >= 0) {
                val before = filename.getOrNull(idx - 1) ?: '-'
                if (before == '-' || before == '_') return p
            }
        }
        val stem = filename.replace(".gguf", "")
        val parts = stem.split("-", "_")
        val quantCandidates = listOf("IQ", "Q", "BF", "FP", "MX")
        for (candidate in parts) {
            val upper = candidate.uppercase()
            if (quantCandidates.any { upper.startsWith(it) } && upper.length in 2..12) {
                if (!upper.any { it in 'a'..'z' } || upper.all { it.isUpperCase() || it.isDigit() || it == '_' || it == '.' }) {
                    return candidate
                }
            }
        }
        return "Unknown"
    }
}
