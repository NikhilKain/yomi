package tachiyomi.source.local.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import com.hippo.unifile.UniFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Draws the first page of a PDF so it can stand in as a cover.
 *
 * Every other local format stores its cover as a file that can simply be copied out. A PDF has no
 * such thing — the first page only becomes an image once something rasterises it — so this is the
 * cheapest honest substitute.
 */
object PdfCover {

    fun renderFirstPage(context: Context, file: UniFile): InputStream? {
        val descriptor = context.contentResolver.openFileDescriptor(file.uri, "r") ?: return null

        return descriptor.use {
            PdfRenderer(it).use { renderer ->
                if (renderer.pageCount == 0) return@use null

                renderer.openPage(0).use { page ->
                    val scale = (TARGET_WIDTH_PX.toFloat() / page.width).coerceIn(MIN_SCALE, MAX_SCALE)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    // Unpainted areas come back transparent, which would show as black.
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    bitmap.recycle()
                    ByteArrayInputStream(out.toByteArray())
                }
            }
        }
    }

    private const val TARGET_WIDTH_PX = 1000
    private const val MIN_SCALE = 1f
    private const val MAX_SCALE = 4f
    private const val JPEG_QUALITY = 90
}
