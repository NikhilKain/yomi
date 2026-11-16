package tachiyomi.presentation.core.components.yomi

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

object YomiEasing {
    /** Material 3 "emphasized": quick start, long gentle landing. Used for anything that travels. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/** Fast compression followed by a short spring release, drawn without relaying out the content. */
@Composable
fun Modifier.bouncyPress(interaction: InteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = if (pressed) 1f else 0.55f, stiffness = 650f),
        label = "press-bounce",
    )
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** Rise-and-fade entrance, played once per screen visit. [order] staggers neighbouring sections. */
@Composable
fun Modifier.reveal(order: Int = 0): Modifier {
    var revealed by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (revealed) 1f else 0f) }
    val distance = with(LocalDensity.current) { 20.dp.toPx() }
    LaunchedEffect(Unit) {
        revealed = true
        if (progress.value < 1f) {
            delay(order.coerceIn(0, 6) * 25L)
            progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 900f))
        }
    }
    return graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = distance * (1f - progress.value)
    }
}

/** A soft sweep across placeholder blocks while content loads, calmer than a spinner. */
@Composable
fun Modifier.shimmer(shape: Shape = MaterialTheme.shapes.medium): Modifier {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val glint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f).compositeOver(base)
    val t by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    return this.clip(shape).drawWithCache {
        val brush = Brush.linearGradient(
            colors = listOf(base, glint, base),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
        onDrawBehind {
            drawRect(base)
            translate(left = t * size.width) { drawRect(brush) }
        }
    }
}
