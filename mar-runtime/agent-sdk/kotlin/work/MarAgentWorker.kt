package com.mar.agent.sdk.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ForegroundInfo
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.app.NotificationChannel
import android.os.Build
import com.mar.runtime.core.MarBridge
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

            // 3. Construct EAP Message for trigger (Mock)
            val triggerPrompt = """
                {"task_id": "trigger_$agentId", "tools": [{"name": "calendar_query", "params": {"today": true}}]}
            """.trimIndent()

            // 4. Run Execution Loop in native/Rust space
            val response = MarBridge.runInferenceTest(triggerPrompt)
            
            // Log for developer tracing
            println("MAR Worker [$agentId] native response: $response")

            Result.success()
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

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_AGENT_ID = "AGENT_ID"
        const val NOTIFICATION_ID = 10101
    }
}
