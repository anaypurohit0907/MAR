package com.mar.demo.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mar.demo.ui.theme.MarColors
import com.mar.demo.ui.viewmodel.ExecutionStep
import com.mar.demo.ui.viewmodel.StepStatus

@Composable
fun WorkflowStepCard(
    step: ExecutionStep,
    index: Int,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = when (step.status) {
            StepStatus.RUNNING -> 0.7f
            StepStatus.COMPLETED, StepStatus.FAILED -> 1f
            StepStatus.PENDING -> 0f
        },
        animationSpec = tween(600), label = "stepProgress"
    )

    Row(modifier = modifier.fillMaxWidth().animateContentSize().padding(start = 8.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            StatusDot(step.status, size = 12.dp)
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(40.dp)
                ) {
                    val lineColor = when {
                        step.status == StepStatus.COMPLETED || step.status == StepStatus.RUNNING ->
                            MarColors.StepLineActive
                        else -> MarColors.StepLine
                    }
                    Canvas(Modifier.fillMaxHeight().fillMaxWidth()) {
                        drawLine(
                            color = lineColor,
                            start = Offset(size.width / 2, 0f),
                            end = Offset(size.width / 2, size.height),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (step.action.isNotBlank()) step.action else step.stepKey,
                    style = MaterialTheme.typography.bodyLarge,
                    color = when (step.status) {
                        StepStatus.PENDING -> MarColors.TextTertiary
                        else -> MarColors.TextPrimary
                    }
                )
                if (step.durationMs > 0) {
                    Text(
                        text = formatDuration(step.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MarColors.TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            if (step.status == StepStatus.RUNNING) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MarColors.Blue,
                    trackColor = MarColors.TextTertiary.copy(alpha = 0.2f)
                )
            } else if (step.status == StepStatus.COMPLETED || step.status == StepStatus.FAILED) {
                LinearProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = if (step.status == StepStatus.COMPLETED) MarColors.Green else MarColors.Red,
                    trackColor = MarColors.TextTertiary.copy(alpha = 0.2f)
                )
            }

            if (step.output != null || step.error != null) {
                Column(Modifier.padding(top = 8.dp)) {
                    GlassCardCompact {
                        Text(
                            text = step.error ?: step.output ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = if (step.error != null) MarColors.Red else MarColors.TextSecondary,
                            maxLines = 10
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60000 -> "${ms / 1000}.${(ms % 1000) / 100}s"
    else -> "${ms / 60000}m ${(ms % 60000) / 1000}s"
}
