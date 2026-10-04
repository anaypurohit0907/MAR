package com.mar.agent.sdk.yaml.parser

import org.yaml.snakeyaml.Yaml
import java.io.InputStream

/**
 * YamlValidator ensures the loaded YAML file contains standard fields,
 * valid schemas, and no malformed structure before saving to the Library.
 */
object YamlValidator {

    fun validate(yamlString: String): ValidationResult {
        val errors = mutableListOf<String>()
        try {
            val yaml = Yaml()
            val rawYaml: Map<String, Any>? = yaml.load(yamlString)
            
            if (rawYaml == null) {
                return ValidationResult(false, listOf("YAML is empty or malformed."))
            }

            val agent = rawYaml["agent"] as? Map<*, *>
            if (agent == null) {
                errors.add("Missing required 'agent' block.")
            } else {
                if (agent["name"]?.toString().isNullOrBlank()) {
                    errors.add("'agent.name' is required.")
                }
            }

            val workflow = rawYaml["workflow"] as? Map<*, *>
            if (workflow == null || workflow.isEmpty()) {
                errors.add("Missing or empty 'workflow' block. The agent must have at least one step.")
            } else {
                // Check if all next steps mentioned actually exist
                val stepKeys = workflow.keys.map { it.toString() }.toSet()
                for ((key, value) in workflow) {
                    val stepMap = value as? Map<*, *>
                    if (stepMap == null) {
                        errors.add("Workflow step '$key' is malformed.")
                        continue
                    }
                    if (stepMap["action"]?.toString().isNullOrBlank()) {
                        errors.add("Workflow step '$key' is missing required 'action'.")
                    }

                    // Validate transitions
                    val onSuccess = stepMap["on_success"]?.toString()
                    if (onSuccess != null && onSuccess !in stepKeys) {
                        errors.add("Workflow step '$key' references unknown on_success step '$onSuccess'.")
                    }
                    val onFailure = stepMap["on_failure"]?.toString()
                    if (onFailure != null && onFailure !in stepKeys) {
                        errors.add("Workflow step '$key' references unknown on_failure step '$onFailure'.")
                    }
                    val onEmpty = stepMap["on_empty"]?.toString()
                    if (onEmpty != null && onEmpty !in stepKeys) {
                        errors.add("Workflow step '$key' references unknown on_empty step '$onEmpty'.")
                    }
                }
            }

            val triggers = rawYaml["triggers"] as? List<*>
            triggers?.forEachIndexed { index, t ->
                val triggerMap = t as? Map<*, *>
                if (triggerMap != null) {
                    val type = triggerMap["type"]?.toString()
                    if (type == "notification") {
                        val notifMap = triggerMap["notification"] as? Map<*, *>
                        if (notifMap == null) {
                            errors.add("Trigger [$index] of type 'notification' is missing 'notification' config block.")
                        } else {
                            val start = notifMap["time_window_start"]?.toString()
                            val end = notifMap["time_window_end"]?.toString()
                            if ((start != null && end == null) || (start == null && end != null)) {
                                errors.add("Trigger [$index] must specify both time_window_start and time_window_end, or neither.")
                            }
                            if (start != null) {
                                try {
                                    java.time.LocalTime.parse(start)
                                } catch (e: Exception) {
                                    errors.add("Trigger [$index] time_window_start '$start' is invalid (expected HH:mm).")
                                }
                            }
                            if (end != null) {
                                try {
                                    java.time.LocalTime.parse(end)
                                } catch (e: Exception) {
                                    errors.add("Trigger [$index] time_window_end '$end' is invalid (expected HH:mm).")
                                }
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            errors.add("YAML Parsing Error: ${e.message}")
        }

        return ValidationResult(errors.isEmpty(), errors)
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>
    )
}
