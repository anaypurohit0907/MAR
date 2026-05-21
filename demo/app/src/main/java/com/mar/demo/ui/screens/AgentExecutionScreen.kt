package com.mar.demo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mar.demo.ui.components.GlassCard
import com.mar.demo.ui.components.StatusDot
import com.mar.demo.ui.components.WorkflowStepCard
import com.mar.demo.ui.theme.MarColors
import com.mar.demo.ui.viewmodel.AgentExecution
import com.mar.demo.ui.viewmodel.ExecutionState
import com.mar.demo.ui.viewmodel.ExecutionStatus
import com.mar.demo.ui.viewmodel.StepStatus

@Composable
fun AgentExecutionScreen(
    onBack: () -> Unit = {}
) {
    val execution by ExecutionState.current.collectAsState()

    if (execution == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "No Active Execution",
                    style = MaterialTheme.typography.titleLarge,
                    color = MarColors.TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Run an agent from the Library to see live execution",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MarColors.TextTertiary
                )
            }
        }
        return
    }

    val exec = execution!!
    val listState = rememberLazyListState()
    val currentStep = exec.currentStepIndex.coerceIn(0, exec.steps.lastIndex)

    LaunchedEffect(currentStep) {
        if (currentStep > 0) listState.animateScrollToItem(currentStep)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard {
                ExecutionHeader(exec)
            }
        }

        if (exec.steps.isNotEmpty()) {
            itemsIndexed(exec.steps) { i, step ->
                WorkflowStepCard(
                    step = step,
                    index = i,
                    isLast = i == exec.steps.lastIndex,
                    modifier = Modifier
                )
            }
        }
    }
}

@Composable
private fun ExecutionHeader(exec: AgentExecution) {
    val isRunning = exec.status == ExecutionStatus.RUNNING
    val isCompleted = exec.status == ExecutionStatus.COMPLETED
    val isFailed = exec.status == ExecutionStatus.FAILED

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = exec.agentId,
                style = MaterialTheme.typography.headlineMedium,
                color = MarColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        isRunning -> "${exec.currentStepIndex + 1}/${exec.totalSteps} steps"
                        isCompleted -> "${exec.totalSteps}/${exec.totalSteps} steps"
                        else -> "${exec.currentStepIndex}/${exec.totalSteps} steps"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MarColors.TextSecondary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (exec.startedAt > 0) {
                        val elapsed = System.currentTimeMillis() - exec.startedAt
                        formatElapsed(elapsed)
                    } else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MarColors.TextTertiary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (exec.modelLoaded) "Model Ready" else "No Model",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (exec.modelLoaded) MarColors.Green else MarColors.Orange
                )
            }
        }

        when {
            isRunning -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MarColors.Blue,
                    strokeWidth = 3.dp
                )
            }
            isCompleted -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Completed",
                    modifier = Modifier.size(32.dp),
                    tint = MarColors.Green
                )
            }
            isFailed -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Failed",
                    modifier = Modifier.size(32.dp),
                    tint = MarColors.Red
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    val curIdx = exec.currentStepIndex.coerceIn(0, exec.steps.size - 1)
    val curStep = exec.steps[curIdx]
    if (curStep.action.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(curStep.status, size = 8.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Now: ${curStep.action}",
                style = MaterialTheme.typography.bodyMedium,
                color = when (curStep.status) {
                    StepStatus.RUNNING -> MarColors.Blue
                    StepStatus.COMPLETED -> MarColors.Green
                    StepStatus.FAILED -> MarColors.Red
                    else -> MarColors.TextSecondary
                }
            )
        }
    }
}

private fun formatElapsed(ms: Long): String = when {
    ms < 1000 -> "0s"
    ms < 60000 -> "${ms / 1000}s"
    else -> "${ms / 60000}m ${(ms % 60000) / 1000}s"
}
