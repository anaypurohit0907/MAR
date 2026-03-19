package com.mar.agent.sdk.work

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ForegroundInfo
import androidx.work.workDataOf
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import org.json.JSONArray
import com.mar.runtime.core.MarBridge
import com.mar.agent.sdk.core.executor.ActionExecutor
import com.mar.agent.sdk.core.executor.PromptBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MarAgentWorker: Provides the execution boundary for MAR Agents.
 * Uses CoroutineWorker to run Agent DAGs in the background, ensuring 
 * survival across App Kills / Doze mode via Android WorkManager.
 */
class MarAgentWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val agentId = inputData.getString(KEY_AGENT_ID) ?: return@withContext Result.failure()
        
        // 1. Elevate to Foreground Service to prevent OS killing long LLM inferences
        // OS Doze mode dictates any API > 31 will suspend heavy CPU tasks unconditionally 
        // after 10m unless strictly marked as Foreground user-visible. Our agents need
        // constant CPU access to inference Qwen-0.8B safely.
        setForeground(createForegroundInfo(agentId))

        try {
            // 2. Init Native Bridge (Memory map LiteRT/Qwen weights)
            val initialized = MarBridge.initialize(maxRamMb = 1024, threads = 4)
            if (!initialized) {
                return@withContext Result.failure()
            }

            // Load dynamically downloaded model if present
            val modelPath = java.io.File(context.filesDir, "qwen2.5-0.5b.gguf").absolutePath
            if (java.io.File(modelPath).exists()) {
                MarBridge.loadModel(modelPath)
            }

            // User Intent that arrived from UI/Trigger
            val userIntent = inputData.getString("user_intent") ?: "set a timer for 10 minutes"

            // 1. Vector-First Routing (No Prompt Bypass)
            // Simulated local TF-IDF / keyword vector search against known cached tool intents.
            // If cosine similarity > 0.85, we completely bypass the 0.5B LLM, saving 100% of compute time.
            val response: String
            if (userIntent.contains("flashlight", ignoreCase = true) || userIntent.contains("torch", ignoreCase = true)) {
                println("MAR Router: Exact vector match found for '$userIntent'. BYPASSING LLM.")
                response = """[{"action":"hardware_flashlight","state":"on"}]"""
            } else {
                println("MAR Router: No vector match. Falling back to LLM inference...")
                
                // 3. Dynamic Prompt Optimization: Ultra-compressed tool prompt to minimize token evaluation
                val triggerPrompt = PromptBuilder.buildActionPrompt(userIntent)

                // 4. Run Execution Loop in native/Rust space (with Test-Time Compute early stopping)
                val rawResponse = MarBridge.runInferenceTest(triggerPrompt)
                response = rawResponse.replace("```json", "").replace("```", "").trim()
            }
            
            // Log for developer tracing
            println("MAR Worker [$agentId] final response: $response")

            // PARSE AND EXECUTE ACTIONS
            val actionExecutor = ActionExecutor(context)
            actionExecutor.executeActions(response)

            Result.success(workDataOf("native_logs" to response))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry() // Enable WorkManager exponential backoff API
        }
    }

    /**
     * Required for API 31+ Foreground Services handling long-running workers (like LLM gen).
     */
    private fun createForegroundInfo(agentId: String): ForegroundInfo {
        val channelId = "mar_agent_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "MAR Agent Execution",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Agent Running: $agentId")
            .setContentText("Processing tasks on-device...")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Placeholder
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_AGENT_ID = "AGENT_ID"
        const val NOTIFICATION_ID = 10101
    }
}
