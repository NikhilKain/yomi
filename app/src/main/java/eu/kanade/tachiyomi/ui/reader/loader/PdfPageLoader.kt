package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.InputStream

/**
 * Loader for a PDF, rasterising each page on demand.
 *
 * A PDF is not a bundle of pictures the way a CBZ is; a page is a program that has to be drawn
 * before there is an image at all. So rather than handing the reader a stream straight from the
 * file, every page is rendered to a bitmap, written to a scratch file, and served from there. The
 * scratch file matters: the reader opens a page's stream more than once — to sniff the format, then
 * to decode it — and re-rendering each time would pay the cost twice for no reason.
 */
internal class PdfPageLoader(
    private val context: Context,
    file: UniFile,
) : PageLoader() {

    override var isLocal: Boolean = true

    private val descriptor = context.contentResolver.openFileDescriptor(file.uri, "r")
        ?: error("Could not open ${file.filePath ?: file.uri}")

    private val renderer = PdfRenderer(descriptor)

    /**
     * PdfRenderer allows exactly one open page per document and throws if that is violated, so
     * every render is serialised — including the ones the reader kicks off in parallel while
     * preloading ahead.
     */
    private val renderLock = Any()

    private val cacheDir = File(context.cacheDir, "pdf_pages/${file.uri.hashCode()}").apply {
        deleteRecursively()
        mkdirs()
    }

    override suspend fun getPages(): List<ReaderPage> {
        return (0 until renderer.pageCount).map { index ->
            ReaderPage(index).apply {
                stream = { pageStream(index) }
                status = Page.State.Ready
            }
        }
    }

    private fun pageStream(index: Int): InputStream {
        val cached = File(cacheDir, "page_%04d.jpg".format(index))
        if (!cached.exists()) {
            synchronized(renderLock) {
                // Re-check inside the lock: a preload and the page the user just turned to can ask
                // for the same page at once, and the loser would otherwise render it again.
                if (!cached.exists()) render(index, cached)
            }
        }
        return cached.inputStream()
    }

    private fun render(index: Int, target: File) {
        renderer.openPage(index).use { page ->
            val scale = (TARGET_WIDTH_PX.toFloat() / page.width).coerceIn(MIN_SCALE, MAX_SCALE)
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // PdfRenderer draws only what the page paints and leaves the rest transparent, which
            // decodes to black in the reader. Paper is white.
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            try {
                val tmp = File(target.parentFile, "${target.name}.part")
                tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                // Rename only once the bytes are down, so a kill mid-render cannot leave a
                // truncated file that would be served forever as a valid cache hit.
                tmp.renameTo(target)
            } finally {
                bitmap.recycle()
            }
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }

    override fun recycle() {
        super.recycle()
        runCatching { renderer.close() }
            .onFailure { logcat { "Could not close PDF renderer: ${it.message}" } }
        runCatching { descriptor.close() }
        cacheDir.deleteRecursively()
    }

    private companion object {
        /**
         * Rasterisation width in pixels. Comfortably above a phone's screen so pinch-zoom still has
         * detail to show, and short of the point where a long PDF fills the cache directory.
         */
        const val TARGET_WIDTH_PX = 1600

        /** A PDF page is measured in points, so these bound how far a page may be scaled. */
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 4f

        const val JPEG_QUALITY = 90
    }
}
