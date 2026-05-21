package com.mar.agent.sdk.work

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.util.Log
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import org.json.JSONArray
import com.mar.runtime.core.MarBridge
import com.mar.runtime.core.MultiAgentRuntimeManager
import com.mar.agent.sdk.core.executor.ActionExecutor
import com.mar.agent.sdk.core.executor.PromptBuilder
import com.mar.agent.sdk.yaml.parser.MarYamlParser
import com.mar.agent.sdk.yaml.runner.WorkflowRunner
import com.mar.agent.sdk.ui.AgentNotificationManager
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

        // Always show foreground notification so user can cancel
        try { setForeground(createForegroundInfo(agentId)) } catch (e: Exception) { Log.w("MAR_Worker", "setForeground failed: ${e.message}") }

        val yamlWorkflow = inputData.getString("yaml_workflow")

        // Fast path: YAML-driven workflows
        if (!yamlWorkflow.isNullOrBlank()) {
            Log.i("MAR_Worker", "Yaml workflow detected! Starting Phase-3 Configured Agent execution.")
            val parser = MarYamlParser()
            val config = parser.parse(yamlWorkflow.byteInputStream())
            val actionExecutor = ActionExecutor(context)
            
            val modelPath = MultiAgentRuntimeManager.findLocalModelPath(context, config.hardwareRequirements.model.ifBlank { null })
            val runner = WorkflowRunner(context, actionExecutor)
            runner.executeWorkflow(config, agentId, modelPath)
            
            return@withContext Result.success(workDataOf("native_logs" to "YAML workflow finished."))
        }

        try {
            // User Intent that arrived from UI/Trigger
            val userIntent = inputData.getString("user_intent") ?: "set a timer for 10 minutes"

            var response = ""
            var attempt = 0
            val maxAttempts = 3
            
            // Vector Match Fallback - we try to AVOID THIS by prioritizing LLM Inference first!
            val isVectorMatchFallback = userIntent.contains("flashlight", ignoreCase = true) || userIntent.contains("torch", ignoreCase = true)
            
            // Loop inference execution for Auto-Retries
            while (response.isBlank() && attempt < maxAttempts) {
                if (attempt > 0) {
                     Log.i("MAR_Worker", "Retrying LLM inference... Attempt ${attempt + 1}")
                     AgentNotificationManager.showAgentProgressNotification(context, agentId, "Retrying LLM Reasoning (Attempt ${attempt + 1}/$maxAttempts)...")
                } else {
                     AgentNotificationManager.showAgentProgressNotification(context, agentId, "Starting LLM Inference...")
                }
                
                // Dynamic Prompt Optimization
                val triggerPrompt = PromptBuilder.buildActionPrompt(userIntent)
                val rawResponse = MultiAgentRuntimeManager.executeInference(context, triggerPrompt)
                
                val cleaned = rawResponse.replace("```json", "").replace("```", "").trim()
                if (cleaned.isNotBlank() && !cleaned.contains("{\"error\":")) {
                    response = cleaned
                }
                attempt++
            }
            
            // 5. Hard Fallback to "Static Inference" ONLY if LLM explicitly crashed out entirely after all retries
            if (response.isBlank()) {
                Log.w("MAR_Worker", "LLM collapsed entirely after $maxAttempts attempts.")
                if (isVectorMatchFallback) {
                    Log.w("MAR_Worker", "Exact vector match fallback used as LAST RESORT.")
                    AgentNotificationManager.showAgentProgressNotification(context, agentId, "Falling back to static vector match...")
                    response = """[{"action":"hardware_flashlight","state":"on"}]"""
                } else {
                    AgentNotificationManager.showAgentProgressNotification(context, agentId, "Agent Failed.")
                    return@withContext Result.failure()
                }
            }

            // Log for developer tracing
            Log.i("MAR_Worker", "[$agentId] final response: $response")

            // PARSE AND EXECUTE ACTIONS
            AgentNotificationManager.showAgentProgressNotification(context, agentId, "Executing identified Actions...")
            val actionExecutor = ActionExecutor(context)
            actionExecutor.executeActions(response)
            
            AgentNotificationManager.clearAgentNotification(context, agentId)
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
        val title = "MAR Agent Active"
        val cancel = "Cancel"
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, title, NotificationManager.IMPORTANCE_LOW)
            try {
                notificationManager.createNotificationChannel(channel)
            } catch (e: SecurityException) {
                // POST_NOTIFICATIONS not granted on API 33+, skip channel creation
            }
        }

        val intent = WorkManager.getInstance(context).createCancelPendingIntent(id)

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText("Agent Task: $agentId is calculating state...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, cancel, intent)
            .build()
            
        // For API 34+ specify data bound service types mapping accurately
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
            
        return ForegroundInfo(1001, notification, foregroundServiceType)
    }

    companion object {
        const val KEY_AGENT_ID = "AGENT_ID"
    }
}
