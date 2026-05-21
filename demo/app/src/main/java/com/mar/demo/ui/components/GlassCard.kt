package com.mar.demo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mar.demo.ui.theme.MarColors

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    alpha: Float = 1f,
    content: @Composable ColumnScope.() -> Unit
) {
    val animatedAlpha by animateFloatAsState(
        targetValue = alpha,
        animationSpec = tween(300),
        label = "cardAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = MarColors.BlueGlow, spotColor = MarColors.BlueGlow)
            .clip(shape)
            .background(MarColors.GlassSurface)
            .border(1.dp, MarColors.GlassBorder, shape)
            .alpha(animatedAlpha)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun GlassCardCompact(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MarColors.GlassWhite)
            .border(0.5.dp, MarColors.GlassBorder, shape)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            content = content
        )
    }
}

fun Modifier.glassBorder(shape: RoundedCornerShape = RoundedCornerShape(12.dp)): Modifier =
    this.then(
        Modifier.border(0.5.dp, MarColors.GlassBorder, shape)
    )
