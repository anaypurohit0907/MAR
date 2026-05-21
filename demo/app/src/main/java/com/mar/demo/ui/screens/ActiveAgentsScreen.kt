package com.mar.demo.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mar.demo.ui.components.GlassCard
import com.mar.demo.ui.components.GlassCardCompact
import com.mar.demo.ui.components.StatusDot
import com.mar.demo.ui.theme.MarColors
import com.mar.demo.ui.viewmodel.ExecutionState
import com.mar.demo.ui.viewmodel.ExecutionStatus
import com.mar.demo.ui.viewmodel.StepStatus

@Composable
fun ActiveAgentsScreen(
    enabledAgents: List<AgentItem>,
    onRunAgent: (String) -> Unit,
    onViewExecution: () -> Unit,
    onChangeModel: ((workflowId: String) -> Unit)? = null
) {
    val execution by ExecutionState.current.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (execution != null) {
            val exec = execution!!
            item {
                val statusColor = when (exec.status) {
                    ExecutionStatus.RUNNING -> MarColors.Blue
                    ExecutionStatus.COMPLETED -> MarColors.Green
                    ExecutionStatus.FAILED -> MarColors.Red
                    ExecutionStatus.IDLE -> MarColors.TextTertiary
                }
                val statusText = when (exec.status) {
                    ExecutionStatus.RUNNING -> "Step ${exec.currentStepIndex + 1}/${exec.totalSteps}"
                    ExecutionStatus.COMPLETED -> "Completed, ${exec.totalSteps}/${exec.totalSteps} steps"
                    ExecutionStatus.FAILED -> "Failed at step ${exec.currentStepIndex + 1}"
                    ExecutionStatus.IDLE -> ""
                }
                GlassCard(
                    shape = RoundedCornerShape(16.dp),
                    alpha = 1f,
                    modifier = Modifier.clickable { onViewExecution() }
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusDot(
                            when (exec.status) {
                                ExecutionStatus.RUNNING -> StepStatus.RUNNING
                                ExecutionStatus.COMPLETED -> StepStatus.COMPLETED
                                else -> StepStatus.FAILED
                            },
                            size = 14.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = exec.agentId,
                                style = MaterialTheme.typography.titleMedium,
                                color = MarColors.TextPrimary
                            )
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MarColors.TextSecondary
                            )
                            if (exec.steps.isNotEmpty()) {
                            val stepIdx = exec.currentStepIndex.coerceIn(0, exec.steps.size - 1)
                            val cur = exec.steps[stepIdx]
                            if (cur.action.isNotBlank()) {
                                Text(
                                    text = cur.action,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColor
                                )
                            }
                        }
                        IconButton(
                            onClick = { ExecutionState.reset() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                "Dismiss",
                                tint = MarColors.TextTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                                }
                            }
                            }
                        }
            }
        }

        if (enabledAgents.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No enabled agents",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MarColors.TextSecondary
                    )
                }
            }
        }

        items(enabledAgents) { agent ->
            AgentCard(
                agent = agent,
                onRun = { onRunAgent(agent.id) },
                onChangeModel = onChangeModel?.let { { it(agent.id) } }
            )
        }
    }
}

data class AgentItem(
    val id: String,
    val name: String,
    val description: String,
    val modelName: String?,
    val modelReady: Boolean,
    val lastRun: String?,
    val errorCount: Int
)

@Composable
private fun AgentCard(
    agent: AgentItem,
    onRun: () -> Unit,
    onChangeModel: (() -> Unit)? = null
) {
    GlassCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = agent.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MarColors.TextPrimary
                )
                if (agent.description.isNotBlank()) {
                    Text(
                        text = agent.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MarColors.TextSecondary,
                        maxLines = 1
                    )
                }
            }
            Button(
                onClick = onRun,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MarColors.Blue.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = MarColors.Blue)
                Spacer(Modifier.width(4.dp))
                Text("Run", color = MarColors.Blue)
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            agent.modelName?.let { model ->
                GlassCardCompact(
                    modifier = if (onChangeModel != null) Modifier.clickable { onChangeModel() } else Modifier
                ) {
                    Text(
                        text = model,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (agent.modelReady) MarColors.Green else MarColors.Orange
                    )
                }
            }
            agent.lastRun?.let { run ->
                GlassCardCompact {
                    Text(
                        text = run,
                        style = MaterialTheme.typography.bodySmall,
                        color = MarColors.TextSecondary
                    )
                }
            }
            if (agent.errorCount > 0) {
                GlassCardCompact {
                    Text(
                        text = "${agent.errorCount} errors",
                        style = MaterialTheme.typography.bodySmall,
                        color = MarColors.Red
                    )
                }
            }
        }
    }
}
