package com.mar.agent.sdk.yaml.runner

import android.content.Context
import android.util.Log
import com.mar.runtime.core.MultiAgentRuntimeManager
import com.mar.agent.sdk.core.executor.ActionExecutor
import com.mar.agent.sdk.core.executor.PromptBuilder
import com.mar.agent.sdk.yaml.parser.MarAgentConfig
import com.mar.agent.sdk.ui.AgentNotificationManager
import com.mar.demo.ui.viewmodel.ExecutionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WorkflowRunner(
    private val context: Context,
    private val actionExecutor: ActionExecutor
) {
    private val memoryContext = mutableMapOf<String, String>()
    private val MAX_RETRIES = 3
    private val STEP_TIMEOUT_MS = 120_000L

    suspend fun executeWorkflow(config: MarAgentConfig, agentName: String = "Workflow", modelPathOverride: String? = null) {
        if (config.workflow.isEmpty()) {
            Log.w(TAG, "Workflow is empty. Nothing to execute.")
            return
        }
        val agentId = config.agent.name.ifEmpty { agentName }
        var hadError = false
        val modelPath = modelPathOverride ?: resolveModelPath(context, config)
        if (modelPath != null) {
            Log.i(TAG, "Pre-loading model: $modelPath")
            try {
                MultiAgentRuntimeManager.loadModelOnly(context, modelPath)
            } catch (e: Exception) {
                Log.w(TAG, "Model pre-load failed (will retry at inference): ${e.message}")
            }
        }

        val stepKeys = config.workflow.keys.toList()

        val prefs = context.getSharedPreferences("mar_execution", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_executing", true).putString("executing_agent", agentId).apply()

        ExecutionState.start(agentId, stepKeys.size, modelPath != null)

        // Optimization: Use Rust DAG engine for complex workflows if linked
        try {
            Log.i(TAG, "Attempting native Rust DAG execution for $agentId...")
            val rustResult = com.mar.runtime.core.MarBridge.runRustWorkflow(agentId, 50)
            if (rustResult == 0) {
                Log.i(TAG, "Rust DAG engine completed successfully for $agentId.")
                // If Rust handles the full loop, we can skip the Kotlin loop.
                // Note: For now, Rust is pseudo-implementation, so we continue to Kotlin
                // to ensure functional parity until Rust Hal is fully wired.
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Rust DAG engine not linked, falling back to Kotlin WorkflowRunner.")
        } catch (e: Exception) {
            Log.e(TAG, "Rust DAG execution failed: ${e.message}")
        }

        var currentStepKey: String? = config.workflow.keys.firstOrNull { it == "step_1" }
            ?: config.workflow.keys.first()

        var stepIndex = 0

        for ((key, step) in config.workflow) {
            val targets = listOfNotNull(step.onSuccess, step.onFailure, step.onEmpty)
            for (t in targets) {
                if (t != "exit" && t != "log_success" && t !in config.workflow) {
                    Log.w(TAG, "Step '$key' references missing target '$t'")
                }
            }
        }

        try {
            while (currentStepKey != null && currentStepKey != "exit" && currentStepKey != "log_success") {
                val step = config.workflow[currentStepKey]
                if (step == null) {
                    Log.e(TAG, "Step $currentStepKey not found in workflow. Exiting.")
                    break
                }

                Log.i(TAG, "Executing step: $currentStepKey -> action: ${step.action}")
                AgentNotificationManager.showAgentProgressNotification(context, agentName, "Running step: $currentStepKey (${step.action})...")
                ExecutionState.updateStep(stepIndex, currentStepKey, step.action)

                val stepStart = System.currentTimeMillis()
                var success = false
                var isEmptyResult = false

                withTimeout(STEP_TIMEOUT_MS) {
                when (step.action) {
                    "llm_draft_message" -> {
                        val rawPrompt = (step.prompt ?: step.params?.get("prompt")?.toString()) ?: ""
                        val hydratedPrompt = substituteTemplates(rawPrompt)
                        Log.i(TAG, "LLM rawPrompt='$rawPrompt' hydratedPrompt='${hydratedPrompt.take(200)}'")

                        val modelFile = modelPath?.let { File(it) }
                        if (modelFile == null || !modelFile.exists()) {
                            Log.e(TAG, "Model file missing at $modelPath — skipping LLM step")
                            AgentNotificationManager.showAgentProgressNotification(context, agentName, "Model not found — skipping LLM step")
                        } else if (modelFile.length() < 50_000_000) {
                            Log.e(TAG, "Model file too small (${modelFile.length()} bytes) — likely corrupt")
                            AgentNotificationManager.showAgentProgressNotification(context, agentName, "Model file corrupt — skipping LLM step")
                        } else {
                            val chatMl = PromptBuilder.buildSystemPrompt(
                                userQuery = hydratedPrompt,
                                toolsJson = "[]"
                            )

                            var response = ""
                            var attempts = 0

                            while (response.isBlank() && attempts < MAX_RETRIES) {
                                if (attempts > 0) {
                                    AgentNotificationManager.showAgentProgressNotification(context, agentName, "Retrying step: $currentStepKey (Attempt ${attempts + 1}/$MAX_RETRIES)...")
                                    Log.w(TAG, "Retrying LLM inference... Attempt ${attempts + 1}")
                                }
                                try {
                                    val result = MultiAgentRuntimeManager.executeInference(context, chatMl, modelPath)
                                    if (result.isNotBlank() && !result.contains("\"error\":")) response = result
                                    else Log.w(TAG, "LLM returned empty/error: ${result.take(200)}")
                                } catch (e: TimeoutCancellationException) {
                                    Log.e(TAG, "LLM inference timed out: ${e.message}")
                                    throw e
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.e(TAG, "LLM threw: ${e.message}")
                                }
                                attempts++
                            }

                            if (response.isNotBlank()) {
                                memoryContext["$currentStepKey.output.draft_text"] = response
                                memoryContext["$currentStepKey.output.text"] = response
                                success = true
                            } else {
                                AgentNotificationManager.showAgentProgressNotification(context, agentName, "Step $currentStepKey failed after $MAX_RETRIES attempts.")
                                Log.e(TAG, "LLM inference failed completely for $currentStepKey.")
                                isEmptyResult = true
                            }
                        }
                    }
                    else -> {
                        val jsonParams = JSONObject()
                        step.params?.forEach { (k, v) ->
                            val substituted = substituteTemplates(v.toString())
                            jsonParams.put(k, coerceToJson(v, substituted))
                        }

                        Log.i(TAG, "Delegating to ActionExecutor: ${step.action} with params: $jsonParams")
                        val payload = JSONObject().apply {
                            put("action", step.action)
                            jsonParams.keys().forEach { k ->
                                if (k == "action") put("sub_action", jsonParams.get(k))
                                else put(k, jsonParams.get(k))
                            }
                        }

                        var result: Any? = null
                        var attempts = 0

                        while (result == null && attempts < MAX_RETRIES) {
                            if (attempts > 0) {
                                AgentNotificationManager.showAgentProgressNotification(context, agentName, "Retrying tool: ${step.action} (Attempt ${attempts + 1}/$MAX_RETRIES)...")
                            }
                            result = actionExecutor.executeSingleAction(step.action, payload)
                            if (result == false) result = null
                            attempts++
                        }

                        if (result is Map<*, *>) {
                            val jsonObj = org.json.JSONObject()
                            result.forEach { (k, v) ->
                                val key = k?.toString() ?: ""
                                val value = v?.toString() ?: ""
                                jsonObj.put(key, value)
                                memoryContext["$currentStepKey.output.$key"] = value
                            }
                            memoryContext["$currentStepKey.output.json"] = jsonObj.toString()
                            success = true
                        } else if (result is String) {
                            memoryContext["$currentStepKey.output.json"] = result
                            success = true
                        } else if (result == true) {
                            success = true
                        } else if (result == null) {
                            memoryContext["$currentStepKey.output.json"] = """{"info":"No results"}"""
                            isEmptyResult = true
                            success = true
                        } else {
                            isEmptyResult = true
                        }
                    }
                }
                }

                val stepDuration = System.currentTimeMillis() - stepStart
                val stepOutput = memoryContext["$currentStepKey.output.json"] ?: memoryContext["$currentStepKey.output.text"]
                if (success) {
                    ExecutionState.completeStep(stepIndex, stepDuration, stepOutput)
                } else {
                    hadError = true
                    ExecutionState.failStep(stepIndex, stepDuration, step.action)
                }
                stepIndex++

                currentStepKey = if (success) {
                    if (isEmptyResult && step.onEmpty != null) step.onEmpty
                    else step.onSuccess
                } else {
                    hadError = true
                    step.onFailure ?: "exit"
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Step timed out after ${STEP_TIMEOUT_MS / 1000}s: ${currentStepKey}")
            hadError = true
        } catch (e: CancellationException) {
            Log.w(TAG, "Workflow cancelled at ${currentStepKey}, rethrowing")
            prefs.edit().putBoolean("is_executing", false).remove("executing_agent").apply()
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "Workflow crashed: ${e.message}")
            hadError = true
        } finally {
            prefs.edit().putBoolean("is_executing", false).remove("executing_agent").apply()
            ExecutionState.complete(!hadError)
            Log.i(TAG, "Workflow finished. Final Step: $currentStepKey")
            AgentNotificationManager.clearAgentNotification(context, agentName)

            val repo = com.mar.agent.sdk.db.WorkflowRepository(context)
            if (hadError) repo.incrementError(agentId)
            repo.markRun(agentId)

            if (hadError) {
                AgentNotificationManager.showExecutionFailedNotification(context, agentName)
            }
        }
    }

    private fun coerceToJson(original: Any?, substituted: String): Any {
        return when (original) {
            is List<*> -> {
                val arr = org.json.JSONArray()
                original.forEach { elem ->
                    val raw = elem?.toString() ?: ""
                    arr.put(substituteTemplates(raw))
                }
                arr
            }
            is Map<*, *> -> {
                val obj = org.json.JSONObject()
                original.forEach { (k, v) ->
                    val key = k?.toString() ?: ""
                    val value = v?.toString() ?: ""
                    obj.put(key, substituteTemplates(value))
                }
                obj
            }
            else -> substituted
        }
    }

    private val predefinedVars by lazy {
        val now = Date()
        val cal = Calendar.getInstance()
        cal.time = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val todayEnd = cal.timeInMillis

        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val datetimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        mapOf(
            "now" to datetimeFmt.format(now),
            "today" to dateFmt.format(now),
            "today_start" to todayStart.toString(),
            "today_end" to todayEnd.toString()
        )
    }

    private fun resolveModelPath(context: Context, config: MarAgentConfig): String? {
        val modelId = config.hardwareRequirements.model.ifBlank { null } ?: return null
        val repo = com.mar.agent.sdk.models.ModelRepository(context)
        return repo.findLocalModelPath(modelId)
    }

    private fun substituteTemplates(input: String): String {
        var output = input
        val regex = Regex("\\{([^}]+)\\}")
        val matches = regex.findAll(input)

        for (match in matches) {
            val key = match.groupValues[1]
            val replacement = memoryContext[key] ?: predefinedVars[key]
            if (replacement != null) {
                output = output.replace(match.value, replacement)
            } else {
                Log.w(TAG, "Template key '{${key}}' not found in memoryContext or predefinedVars")
            }
        }
        return output
    }

    companion object {
        private const val TAG = "MAR_WorkflowRunner"
    }
}
