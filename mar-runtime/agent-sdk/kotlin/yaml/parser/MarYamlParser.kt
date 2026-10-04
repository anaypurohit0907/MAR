package com.mar.agent.sdk.yaml.parser

import org.yaml.snakeyaml.Yaml
import java.io.InputStream

class MarYamlParser {

    /**
     * Parses a raw YAML InputStream into standard MAR Kotlin structures.
     * Prevents Reflection overhead by mapping raw HashMaps explicitly for speed 
     * and obfuscation safety (R8/Proguard).
     */
    fun parse(yamlStream: InputStream): MarAgentConfig {
        val yaml = Yaml()
        val rawYaml: Map<String, Any> = yaml.load(yamlStream) ?: emptyMap()
        
        return MarAgentConfig(
            agent = parseAgentMetadata(rawYaml["agent"] as? Map<String, Any>),
            hardwareRequirements = parseHardwareRequirements(rawYaml["hardware_requirements"] as? Map<String, Any>),
            triggers = parseTriggers(rawYaml["triggers"] as? List<Map<String, Any>>),
            tools = parseTools(rawYaml["tools"] as? List<Map<String, Any>>),
            workflow = parseWorkflow(rawYaml["workflow"] as? Map<String, Map<String, Any>>)
        )
    }

    private fun parseAgentMetadata(map: Map<String, Any>?): AgentMetadata {
        if (map == null) return AgentMetadata()
        return AgentMetadata(
            name = map["name"]?.toString() ?: "",
            version = map["version"]?.toString() ?: "",
            description = map["description"]?.toString() ?: ""
        )
    }

    private fun parseHardwareRequirements(map: Map<String, Any>?): HardwareRequirements {
        if (map == null) return HardwareRequirements()
        val minRam = (map["min_ram_mb"] as? Number)?.toInt() ?: 0
        return HardwareRequirements(
            minRamMb = minRam,
            model = map["model"]?.toString() ?: ""
        )
    }

    private fun parseTriggers(list: List<Map<String, Any>>?): List<TriggerEntry> {
        if (list == null) return emptyList()
        return list.map {
            val notificationMap = it["notification"] as? Map<String, Any>
            TriggerEntry(
                type = it["type"]?.toString() ?: "",
                schedule = it["schedule"]?.toString(),
                event = it["event"]?.toString(),
                notification = if (notificationMap != null) {
                    NotificationTrigger(
                        packageName = notificationMap["package"]?.toString(),
                        textMatch = notificationMap["text_match"]?.toString(),
                        category = notificationMap["category"]?.toString(),
                        onTrigger = it["on_trigger"]?.toString(),
                        cooldownSeconds = (notificationMap["cooldown_seconds"] as? Number)?.toLong(),
                        timeWindowStart = notificationMap["time_window_start"]?.toString(),
                        timeWindowEnd = notificationMap["time_window_end"]?.toString()
                    )
                } else null
            )
        }
    }

    private fun parseTools(list: List<Map<String, Any>>?): List<ToolEntry> {
        if (list == null) return emptyList()
        return list.map {
            ToolEntry(
                name = it["name"]?.toString() ?: "",
                type = it["type"]?.toString() ?: "",
                uri = it["uri"]?.toString(),
                description = it["description"]?.toString()
            )
        }
    }

    private fun parseWorkflow(map: Map<String, Map<String, Any>>?): Map<String, WorkflowStep> {
        if (map == null) return emptyMap()
        val result = mutableMapOf<String, WorkflowStep>()
        
        for ((key, value) in map) {
            @Suppress("UNCHECKED_CAST")
            result[key] = WorkflowStep(
                action = value["action"]?.toString() ?: "",
                params = value["params"] as? Map<String, Any>,
                prompt = value["prompt"]?.toString(),
                onSuccess = value["on_success"]?.toString(),
                onEmpty = value["on_empty"]?.toString(),
                onFailure = value["on_failure"]?.toString()
            )
        }
        
        return result
    }
}
