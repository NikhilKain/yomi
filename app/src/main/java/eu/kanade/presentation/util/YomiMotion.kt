package eu.kanade.presentation.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
internal fun Modifier.yomiReveal(order: Int = 0): Modifier {
    if (order !in 0..5) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        progress.animateTo(1f, tween(240, order * 25, CubicBezierEasing(.2f, 0f, 0f, 1f)))
    }
    return graphicsLayer {
        val fraction = progress.value
        alpha = fraction
        translationY = 24.dp.toPx() * (1f - fraction)
    }
}

@Composable
internal fun Modifier.yomiPress(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) .965f else 1f,
        spring(dampingRatio = .7f, stiffness = 500f),
        label = "Yomi press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
