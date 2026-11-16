package eu.kanade.presentation.theme

import android.content.Context
import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.ktx.DynamicScheme
import com.materialkolor.toColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val seeds = LruCache<Any, Int>(96)
private val schemes = LruCache<Long, ColorScheme>(24)

/** Covers with no colour worth borrowing are remembered too, so they are read only once. */
private const val NO_SEED = 0

/**
 * Tints everything inside with a colour scheme grown from [cover], a title's artwork, so each
 * title has its own mood: buttons, progress and surfaces pick up the cover's colour. The scheme is
 * computed off the UI thread and applied once the cover has been read; greyscale covers keep the
 * app's own palette.
 */
@Composable
fun AmbientTheme(cover: Any?, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    if (cover == null) {
        content()
        return
    }
    val context = LocalContext.current
    val dark = base.surface.luminance() < 0.5f
    var target by remember(cover, dark, base) {
        mutableStateOf(seeds[cover]?.takeIf { it != NO_SEED }?.let { cached(it, dark) } ?: base)
    }
    LaunchedEffect(cover, dark, base) {
        val seed = seeds[cover] ?: (seedOf(context, cover) ?: NO_SEED).also { seeds.put(cover, it) }
        if (seed != NO_SEED) {
            target = cached(seed, dark) ?: withContext(Dispatchers.Default) {
                DynamicScheme(
                    seedColor = Color(seed),
                    isDark = dark,
                    specVersion = ColorSpec.SpecVersion.SPEC_2025,
                    style = PaletteStyle.TonalSpot,
                ).toColorScheme(isAmoled = false)
            }.also { schemes.put(schemeKey(seed, dark), it) }
        }
    }
    MaterialTheme(colorScheme = target, content = content)
}

private fun schemeKey(seed: Int, dark: Boolean): Long = (seed.toLong() shl 1) or (if (dark) 1L else 0L)

private fun cached(seed: Int, dark: Boolean): ColorScheme? = schemes[schemeKey(seed, dark)]

/** The cover's most characterful colour, read from a thumbnail-sized copy of it. */
private suspend fun seedOf(context: Context, cover: Any): Int? = withContext(Dispatchers.Default) {
    runCatching {
        val request = ImageRequest.Builder(context).data(cover).size(96).allowHardware(false).build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
            ?: return@runCatching null
        characterColor(result.image.toBitmap())
    }.getOrNull()
}

/**
 * Sorts the cover's saturated pixels into hue buckets and returns the average colour of the
 * heaviest one. Greys, near-blacks and near-whites carry no mood and are skipped.
 */
private fun characterColor(bitmap: Bitmap): Int? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val buckets = 24
    val weight = FloatArray(buckets)
    val red = FloatArray(buckets)
    val green = FloatArray(buckets)
    val blue = FloatArray(buckets)
    val hsv = FloatArray(3)
    val step = (pixels.size / 4096).coerceAtLeast(1)
    var index = 0
    while (index < pixels.size) {
        val pixel = pixels[index]
        index += step
        if ((pixel ushr 24) < 128) continue
        android.graphics.Color.colorToHSV(pixel, hsv)
        val saturation = hsv[1]
        val value = hsv[2]
        if (saturation < 0.2f || value < 0.22f) continue
        val bucket = (hsv[0] / (360f / buckets)).toInt().coerceIn(0, buckets - 1)
        val w = saturation * saturation * (0.35f + 0.65f * value)
        weight[bucket] += w
        red[bucket] += w * ((pixel shr 16) and 0xFF)
        green[bucket] += w * ((pixel shr 8) and 0xFF)
        blue[bucket] += w * (pixel and 0xFF)
    }
    var best = -1
    var bestWeight = 0f
    for (i in 0 until buckets) {
        // A hue reads as dominant with its neighbours' support.
        val around = weight[i] + 0.5f * (weight[(i + 1) % buckets] + weight[(i + buckets - 1) % buckets])
        if (around > bestWeight) {
            bestWeight = around
            best = i
        }
    }
    val sampled = pixels.size / step
    if (best < 0 || weight[best] <= 0f || bestWeight < sampled * 0.004f) return null
    val w = weight[best]
    return android.graphics.Color.rgb(
        (red[best] / w).toInt().coerceIn(0, 255),
        (green[best] / w).toInt().coerceIn(0, 255),
        (blue[best] / w).toInt().coerceIn(0, 255),
    )
}
