package com.mar.demo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mar.demo.ui.theme.MarColors
import com.mar.demo.ui.viewmodel.StepStatus

@Composable
fun StatusDot(
    status: StepStatus,
    size: Dp = 10.dp,
    modifier: Modifier = Modifier
) {
    val color by animateColorAsState(
        targetValue = when (status) {
            StepStatus.PENDING -> MarColors.TextTertiary
            StepStatus.RUNNING -> MarColors.Blue
            StepStatus.COMPLETED -> MarColors.Green
            StepStatus.FAILED -> MarColors.Red
        },
        animationSpec = tween(300),
        label = "dotColor"
    )

    if (status == StepStatus.RUNNING) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        Box(modifier = modifier.size(size)) {
            Box(
                modifier = Modifier
                    .size(size)
                    .scale(pulseScale)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(color)
            )
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun StatusLabel(status: StepStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(status)
        Spacer(Modifier.width(6.dp))
        Text(
            text = when (status) {
                StepStatus.PENDING -> "Pending"
                StepStatus.RUNNING -> "Running"
                StepStatus.COMPLETED -> "Completed"
                StepStatus.FAILED -> "Failed"
            },
            style = MaterialTheme.typography.bodySmall,
            color = when (status) {
                StepStatus.PENDING -> MarColors.TextTertiary
                StepStatus.RUNNING -> MarColors.Blue
                StepStatus.COMPLETED -> MarColors.Green
                StepStatus.FAILED -> MarColors.Red
            }
        )
    }
}
