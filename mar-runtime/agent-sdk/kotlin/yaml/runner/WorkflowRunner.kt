package com.mar.agent.sdk.yaml.runner

import android.content.Context
import android.util.Log
import com.mar.runtime.core.MarBridge
import com.mar.runtime.core.MultiAgentRuntimeManager
import com.mar.agent.sdk.core.executor.ActionExecutor
import com.mar.agent.sdk.core.executor.PromptBuilder
import com.mar.agent.sdk.yaml.parser.MarAgentConfig
import com.mar.agent.sdk.ui.AgentNotificationManager
import org.json.JSONObject

class WorkflowRunner(
    private val context: Context,
    private val actionExecutor: ActionExecutor
) {
    // Memory context state to support template substitution like {step_1.output.name}
    private val memoryContext = mutableMapOf<String, String>()
    private val MAX_RETRIES = 3

    suspend fun executeWorkflow(config: MarAgentConfig, agentName: String = "Workflow") {
        if (config.workflow.isEmpty()) {
            Log.w("MAR_WorkflowRunner", "Workflow is empty. Nothing to execute.")
            return
        }

        // Start with the first step chronologically (or explicitly named "step_1")
        var currentStepKey: String? = config.workflow.keys.firstOrNull { it == "step_1" } 
            ?: config.workflow.keys.first()

        while (currentStepKey != null && currentStepKey != "exit" && currentStepKey != "log_success") {
            val step = config.workflow[currentStepKey]
            if (step == null) {
                Log.e("MAR_WorkflowRunner", "Step $currentStepKey not found in workflow. Exiting.")
                break
            }

            Log.i("MAR_WorkflowRunner", "Executing step: $currentStepKey -> action: ${step.action}")
            AgentNotificationManager.showAgentProgressNotification(context, agentName, "Running step: $currentStepKey (${step.action})...")
            
            var success = false
            var isEmptyResult = false

            when (step.action) {
                "llm_draft_message" -> {
                    // It's a direct LLM call
                    val rawPrompt = step.prompt ?: ""
                    val hydratedPrompt = substituteTemplates(rawPrompt)
                    
                    // Generate specialized prompt using ChatML structured builder
                    val chatMl = PromptBuilder.buildSystemPrompt(
                        userQuery = hydratedPrompt,
                        toolsJson = "[]" // We can inject available dynamic tools here later 
                    )
                    
                    var response = ""
                    var attempts = 0
                    
                    while (response.isBlank() && attempts < MAX_RETRIES) {
                        if (attempts > 0) {
                            AgentNotificationManager.showAgentProgressNotification(context, agentName, "Retrying step: $currentStepKey (Attempt ${attempts + 1}/$MAX_RETRIES)...")
                            Log.w("MAR_WorkflowRunner", "Retrying LLM inference... Attempt ${attempts + 1}")
                        }
                        
                        response = MultiAgentRuntimeManager.executeInference(context, chatMl)
                        
                        if (response.isBlank() || response.contains("{\"error\":")) {
                            response = "" // Treat as failure
                        }
                        attempts++
                    }
                    
                    if (response.isNotBlank()) {
                        memoryContext["$currentStepKey.output.draft_text"] = response
                        success = true
                    } else {
                        AgentNotificationManager.showAgentProgressNotification(context, agentName, "Step $currentStepKey failed after $MAX_RETRIES attempts.")
                        Log.e("MAR_WorkflowRunner", "LLM inference failed completely for $currentStepKey.")
                        isEmptyResult = true
                    }
                }
                else -> {
                    // It's a local tool/SDK action
                    val jsonParams = JSONObject()
                    step.params?.forEach { (k, v) ->
                        // Substitute templates inside parameters too!
                        val valueStr = v.toString()
                        val substituted = substituteTemplates(valueStr)
                        jsonParams.put(k, substituted)
                    }
                    
                    Log.i("MAR_WorkflowRunner", "Delegating to ActionExecutor: ${step.action} with params: $jsonParams")
                    // Execute dynamically via our ActionExecutor or Tool bindings
                    val payload = JSONObject().apply {
                        put("action", step.action)
                        step.params?.keys?.forEach { k -> put(k, jsonParams.get(k)) }
                    }
                    
                    var result: Any? = null
                    var attempts = 0
                    
                    while (result == null && attempts < MAX_RETRIES) {
                        if (attempts > 0) {
                            AgentNotificationManager.showAgentProgressNotification(context, agentName, "Retrying tool: ${step.action} (Attempt ${attempts + 1}/$MAX_RETRIES)...")
                        }
                        result = actionExecutor.executeSingleAction(step.action, payload)
                        if (result == false) result = null // Retry on explicit tool false return
                        attempts++
                    }
                    
                    if (result is Map<*, *>) {
                        result.forEach { (k, v) -> 
                            memoryContext["$currentStepKey.output.$k"] = v.toString() 
                        }
                        success = true
                    } else if (result == true) {
                        success = true
                    } else {
                        isEmptyResult = true
                    }
                }
            }

            currentStepKey = if (success) {
                if (isEmptyResult && step.onEmpty != null) step.onEmpty
                else step.onSuccess
            } else {
                step.onFailure ?: "exit"
            }
        }
        
        Log.i("MAR_WorkflowRunner", "Workflow finished. Final Step: $currentStepKey")
        AgentNotificationManager.clearAgentNotification(context, agentName)
    }

    /**
     * Replaces `{step_x.output.y}` with the respective value from the `memoryContext`.
     */
    private fun substituteTemplates(input: String): String {
        var output = input
        val regex = Regex("\\{([^}]+)\\}")
        val matches = regex.findAll(input)
        
        for (match in matches) {
            val key = match.groupValues[1]
            val replacement = memoryContext[key] ?: ""
            output = output.replace(match.value, replacement)
        }
        return output
    }
}
