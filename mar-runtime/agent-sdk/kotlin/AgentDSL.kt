package com.mar.agent.sdk

// --- MAR Agent Abstraction Layer (Layer 3) ---

/**
 * Base classes representing tools in the framework
 */
abstract class MarTool(val name: String) {
    abstract fun call(params: Map<String, Any>): String
}

// Removing mock classes now that we have real tools under `tools/` package.
// We keep MarTool base class here.

/**
 * Models
 */
abstract class LlmModel(val id: String)
class Qwen(id: String = "qwen3.5-0.8b-q4f16") : LlmModel(id)

/**
 * Agent DSL Builder
 */
class WorkflowBuilder {
    private val steps = mutableMapOf<String, () -> Unit>()

    fun on(event: String, block: () -> Unit) {
        steps[event] = block
    }
}

class AgentBuilder(val name: String) {
    var model: LlmModel? = null
    val tools = mutableListOf<MarTool>()
    private var workflowBlock: WorkflowBuilder.() -> Unit = {}

    fun workflow(block: WorkflowBuilder.() -> Unit) {
        workflowBlock = block
    }

    operator fun MarTool.plus(other: MarTool): List<MarTool> = listOf(this, other)
    operator fun List<MarTool>.plus(other: MarTool): List<MarTool> = this.toMutableList().apply { add(other) }

    fun build(): MarAgent {
        val builder = WorkflowBuilder().apply(workflowBlock)
        return MarAgent(name, model, tools, builder)
    }
}

class MarAgent(
    val name: String, 
    val model: LlmModel?,
    val tools: List<MarTool>,
    val workflow: WorkflowBuilder
)

// The exposed Entrypoint DSL
// Enables viral growth by making Agent development syntax feel exactly like Gradle.
// Any standard Android Dev can spin up a localized SLM agent pipeline in <20 lines of code.
fun agent(name: String, init: AgentBuilder.() -> Unit): MarAgent {
    val builder = AgentBuilder(name)
    builder.init()
    return builder.build()
}

import com.mar.agent.sdk.tools.*

/**
 * Example definition:
 */
// In reality context would be passed. Mocking null for structural syntax demonstration.
val birthdayAgent = agent("BirthdayGreeter") {
    model = Qwen()
    // Using real tools (simulating context injection)
    // tools.addAll(listOf(LocalSearchTool(context), CalendarQueryTool(context), UITapTool()))
    
    workflow {
        on("new_contact_event") {
            // Pseudo DAG linking
            println("Triggering Birthday pipeline")
        }
    }
}
