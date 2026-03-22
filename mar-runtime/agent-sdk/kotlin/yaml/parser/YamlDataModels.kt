package com.mar.agent.sdk.yaml.parser

data class MarAgentConfig(
    val agent: AgentMetadata = AgentMetadata(),
    val hardwareRequirements: HardwareRequirements = HardwareRequirements(),
    val triggers: List<TriggerEntry> = emptyList(),
    val tools: List<ToolEntry> = emptyList(),
    val workflow: Map<String, WorkflowStep> = emptyMap()
)

data class AgentMetadata(
    val name: String = "",
    val version: String = "",
    val description: String = ""
)

data class HardwareRequirements(
    val minRamMb: Int = 0,
    val model: String = ""
)

data class TriggerEntry(
    val type: String = "",
    val schedule: String? = null,
    val event: String? = null
)

data class ToolEntry(
    val name: String = "",
    val type: String = "",
    val uri: String? = null,
    val description: String? = null
)

data class WorkflowStep(
    val action: String = "",
    val params: Map<String, Any>? = null,
    val prompt: String? = null,
    val onSuccess: String? = null,
    val onEmpty: String? = null,
    val onFailure: String? = null
)
