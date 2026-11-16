// Adapted from https://github.com/NikhilKain/yomi
// Copyright Yomi contributors, Apache-2.0

package eu.kanade.tachiyomi.ui.home

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

private data class GlassOptics(
    val blur: Dp,
    val lensHeight: Dp,
    val lensAmount: Dp,
    val saturation: Float,
    val magnification: Float,
)
private val DOCK_OPTICS = GlassOptics(6.dp, 22.dp, 36.dp, 1.5f, 1f)
private val CLEAR_OPTICS = GlassOptics(0.dp, 10.dp, 3.dp, 1f, 1.045f)
private fun glassRim(clearLens: Boolean, tint: Color, dark: Boolean): Pair<Color, Color> =
    if (clearLens) {
        tint.copy(alpha = .8f) to tint.copy(alpha = .32f)
    } else {
        Color.White.copy(alpha = if (dark) .35f else .8f) to Color.White.copy(alpha = if (dark) .06f else .25f)
    }

/** Stride’s default dock surface: vibrancy, light blur, edge refraction and a lit rim. */
@Composable
internal fun Modifier.strideGlass(
    shape: Shape,
    tint: Color,
    backdrop: DockBackdrop,
    clearLens: Boolean = false,
): Modifier {
    val density = LocalDensity.current
    val dark = tint.luminance() < 0.5f
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Only a breath of tint: the look should come from what's behind the bar,
    // bent and softened, not from a frosted overlay painted on top of it.
    val fill = if (clearLens) {
        tint.copy(alpha = 0.065f)
    } else {
        tint.copy(
            alpha = if (!canBlur) {
                0.92f
            } else if (dark) {
                0.14f
            } else {
                0.2f
            },
        )
    }
    val specular = Color.White.copy(alpha = if (dark) 0.22f else 0.55f)
    val (rimTop, rimBottom) = glassRim(clearLens, tint, dark)
    val optics = if (clearLens) CLEAR_OPTICS else DOCK_OPTICS
    val blur = optics.blur
    var origin by remember { mutableStateOf(Offset.Zero) }
    // Created once. Classic's bar changes width while a label opens, and a
    // layer obtained per size would be reallocated on every frame of that.
    val glass = rememberGraphicsLayer()

    return this
        .onGloballyPositioned {
            origin = it.positionInRoot()
        }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val clip = Path().apply { addOutline(outline) }
            // Blur reaches past the outline, so the copy is drawn with a margin
            // and the outline trims it; otherwise the rim would fade to nothing.
            // Margin for the blur only. The lens pulls its image from inside the
            // outline, never outside, so it needs none — the old 32 dp+ margin more
            // than doubled the area blurred on every scroll frame.
            val pad = with(density) { (blur * 1.5f + 2.dp).toPx() }
            glass.renderEffect = if (canBlur) {
                glassEffect(
                    // Plain lens, no rainbow fringe: the dispersive shader samples seven
                    // times per pixel, and the bar re-renders on every scroll frame.
                    density,
                    size,
                    pad,
                    cornerRadii(shape, size, layoutDirection, density),
                    blur = blur,
                    lensHeight = optics.lensHeight,
                    lensAmount = optics.lensAmount,
                    saturation = optics.saturation,
                ).asComposeRenderEffect()
            } else {
                null
            }
            val specBrush = Brush.verticalGradient(
                listOf(specular, Color.Transparent),
                0f,
                with(density) { 3.dp.toPx() },
            )
            val padded = IntSize(ceil(size.width + 2 * pad).toInt(), ceil(size.height + 2 * pad).toInt())
            // The lit rim: bright where light would catch the top edge, fading below.
            val rimBrush = Brush.verticalGradient(listOf(rimTop, rimBottom))
            val rimWidth = with(density) { 1.dp.toPx() }
            // Worked out here: inside a layer recording the draw scope's density
            // resolves through itself, so nothing in [paint] may convert dp.
            val specHeight = with(density) { 3.dp.toPx() }
            val magnificationPivot = Offset(pad + size.width / 2f, pad + size.height / 2f)

            onDrawBehind {
                val shift = backdrop.origin - origin
                glass.record(padded) {
                    // Magnify the actual screen under the selector, preserving its
                    // colours and sharpness. The dock itself is never recorded.
                    scale(optics.magnification, pivot = magnificationPivot) {
                        translate(shift.x + pad, shift.y + pad) { drawLayer(backdrop.layer) }
                    }
                }
                val paint: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                    clipPath(clip) {
                        translate(-pad, -pad) { drawLayer(glass) }
                        drawRect(fill)
                        drawRect(brush = specBrush, size = Size(size.width, specHeight))
                        drawPath(clip, rimBrush, style = Stroke(width = rimWidth * 2))
                    }
                }
                paint()
            }
        }
}

// Light blur so the refraction stays visible; a heavy one flattens it to frost.
private val GLASS_BLUR = 6.dp

// How far in from the rim the glass bends, and how far it pulls the image.
private val LENS_HEIGHT = 22.dp
private val LENS_AMOUNT = 36.dp

/** Vibrancy, then blur, then (Android 13+) the lens — the order Vyxel chains them. */
@RequiresApi(Build.VERSION_CODES.S)
internal fun glassEffect(
    density: Density,
    size: Size,
    pad: Float,
    radii: FloatArray?,
    blur: Dp = GLASS_BLUR,
    lensHeight: Dp = LENS_HEIGHT,
    lensAmount: Dp = LENS_AMOUNT,
    saturation: Float = 1.5f,
): RenderEffect {
    val blurPx = with(density) { blur.toPx() }
    val vibrancy = RenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(saturation) }),
    )
    // Either effect can be turned all the way down in Settings.
    val blurred = if (blurPx >= 0.5f) {
        RenderEffect.createBlurEffect(blurPx, blurPx, vibrancy, Shader.TileMode.CLAMP)
    } else {
        vibrancy
    }
    val lensDisabled = lensHeight.value <= 0f || lensAmount.value <= 0f
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || radii == null || lensDisabled) return blurred
    return RenderEffect.createChainEffect(lens(density, size, pad, radii, lensHeight, lensAmount), blurred)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun lens(
    density: Density,
    size: Size,
    pad: Float,
    radii: FloatArray,
    height: Dp = LENS_HEIGHT,
    amount: Dp = LENS_AMOUNT,
): RenderEffect {
    val shader = RuntimeShader(REFRACTION)
    shader.setFloatUniform("size", size.width, size.height)
    shader.setFloatUniform("offset", -pad, -pad)
    shader.setFloatUniform("cornerRadii", radii)
    shader.setFloatUniform("refractionHeight", with(density) { height.toPx() }.coerceAtMost(size.minDimension / 2f))
    shader.setFloatUniform("refractionAmount", -with(density) { amount.toPx() })
    shader.setFloatUniform("depthEffect", 0f)
    return RenderEffect.createRuntimeShaderEffect(shader, "content")
}

/** Corner radii in the shader's order — top-left, top-right, bottom-right, bottom-left. */
internal fun cornerRadii(shape: Shape, size: Size, direction: LayoutDirection, density: Density): FloatArray? {
    if (shape !is CornerBasedShape) return null
    val ltr = direction == LayoutDirection.Ltr
    val topStart = shape.topStart.toPx(size, density)
    val topEnd = shape.topEnd.toPx(size, density)
    val bottomEnd = shape.bottomEnd.toPx(size, density)
    val bottomStart = shape.bottomStart.toPx(size, density)
    val limit = size.minDimension / 2f
    return floatArrayOf(
        if (ltr) topStart else topEnd,
        if (ltr) topEnd else topStart,
        if (ltr) bottomEnd else bottomStart,
        if (ltr) bottomStart else bottomEnd,
    ).also { r -> for (i in r.indices) r[i] = r[i].coerceIn(0f, limit) }
}

private const val ROUNDED_RECT_SDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}
"""

private const val REFRACTION = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
""" + ROUNDED_RECT_SDF + """
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    return content.eval(refractedCoord);
}
"""
