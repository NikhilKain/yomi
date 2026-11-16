package tachiyomi.source.local.image

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.source.local.io.LocalSourceFileSystem
import java.io.InputStream

private const val DEFAULT_COVER_NAME = "cover.jpg"
private const val LOOSE_COVER_SUFFIX = ".cover.jpg"

class LocalCoverManager(
    private val context: Context,
    private val fileSystem: LocalSourceFileSystem,
) {

    fun find(mangaUrl: String): UniFile? {
        val insideFolder = fileSystem.getFilesInMangaDirectory(mangaUrl)
            // Get all file whose names start with "cover"
            .filter { it.isFile && it.nameWithoutExtension.equals("cover", ignoreCase = true) }
            // Get the first actual image
            .firstOrNull { ImageUtil.isImage(it.name) { it.openInputStream() } }

        return insideFolder
            ?: looseCover(mangaUrl)?.takeIf { it.isFile && ImageUtil.isImage(it.name) { it.openInputStream() } }
    }

    fun update(
        manga: SManga,
        inputStream: InputStream,
    ): UniFile? {
        val directory = fileSystem.getMangaDirectory(manga.url) ?: fileSystem.getBaseDirectory()
        if (directory == null) {
            inputStream.close()
            return null
        }

        val existing = find(manga.url)
        val targetFile = when {
            existing != null -> existing
            // A single-file entry has no folder of its own, so its cover goes beside it in the root
            // under the file's own name. Without this a dropped-in PDF would show as a blank tile
            // for good: the cover could be rendered but never had anywhere to live.
            fileSystem.getMangaDirectory(manga.url) == null ->
                directory.createFile(looseCoverName(manga.url))
            else -> directory.createFile(DEFAULT_COVER_NAME)
        }

        if (targetFile == null) {
            inputStream.close()
            return null
        }

        inputStream.use { input ->
            targetFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }

        DiskUtil.createNoMediaFile(directory, context)

        manga.thumbnail_url = targetFile.uri.toString()
        return targetFile
    }

    private fun looseCover(mangaUrl: String): UniFile? =
        fileSystem.getBaseDirectory()?.findFile(looseCoverName(mangaUrl))

    /** Named after the file so two loose entries in the same folder cannot collide. */
    private fun looseCoverName(mangaUrl: String): String =
        "${mangaUrl.substringBeforeLast('.')}$LOOSE_COVER_SUFFIX"
}
