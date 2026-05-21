package com.mar.demo.ui.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class StepStatus { PENDING, RUNNING, COMPLETED, FAILED }
enum class ExecutionStatus { IDLE, RUNNING, COMPLETED, FAILED }

data class ExecutionStep(
    val stepKey: String,
    val action: String,
    val status: StepStatus = StepStatus.PENDING,
    val durationMs: Long = 0L,
    val output: String? = null,
    val error: String? = null
)

data class AgentExecution(
    val agentId: String,
    val status: ExecutionStatus = ExecutionStatus.IDLE,
    val totalSteps: Int = 0,
    val currentStepIndex: Int = 0,
    val steps: List<ExecutionStep> = emptyList(),
    val startedAt: Long = 0L,
    val modelLoaded: Boolean = false
)

object ExecutionState {
    private val _current = MutableStateFlow<AgentExecution?>(null)
    val current: StateFlow<AgentExecution?> = _current.asStateFlow()

    fun start(agentId: String, totalSteps: Int, modelLoaded: Boolean) {
        val steps = (1..totalSteps).map { i ->
            ExecutionStep(stepKey = "step_$i", action = "", status = StepStatus.PENDING)
        }
        _current.value = AgentExecution(
            agentId = agentId,
            status = ExecutionStatus.RUNNING,
            totalSteps = totalSteps,
            steps = steps,
            startedAt = System.currentTimeMillis(),
            modelLoaded = modelLoaded
        )
    }

    fun updateStep(stepIndex: Int, stepKey: String, action: String) {
        val exec = _current.value ?: return
        val steps = exec.steps.toMutableList()
        if (stepIndex < steps.size) {
            steps[stepIndex] = steps[stepIndex].copy(
                stepKey = stepKey, action = action, status = StepStatus.RUNNING
            )
        }
        _current.value = exec.copy(steps = steps, currentStepIndex = stepIndex)
    }

    fun completeStep(stepIndex: Int, durationMs: Long, output: String?) {
        val exec = _current.value ?: return
        val steps = exec.steps.toMutableList()
        if (stepIndex < steps.size) {
            steps[stepIndex] = steps[stepIndex].copy(
                status = StepStatus.COMPLETED, durationMs = durationMs, output = output
            )
        }
        _current.value = exec.copy(steps = steps, currentStepIndex = stepIndex + 1)
    }

    fun failStep(stepIndex: Int, durationMs: Long, error: String) {
        val exec = _current.value ?: return
        val steps = exec.steps.toMutableList()
        if (stepIndex < steps.size) {
            steps[stepIndex] = steps[stepIndex].copy(
                status = StepStatus.FAILED, durationMs = durationMs, error = error
            )
        }
        _current.value = exec.copy(steps = steps, status = ExecutionStatus.FAILED)
    }

    fun complete(success: Boolean) {
        val exec = _current.value ?: return
        _current.value = exec.copy(
            status = if (success) ExecutionStatus.COMPLETED else ExecutionStatus.FAILED
        )
    }

    fun reset() { _current.value = null }
}
