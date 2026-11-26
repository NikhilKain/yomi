package eu.kanade.domain.chapter.interactor

import eu.kanade.domain.download.interactor.DeleteDownload
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

class SetReadStatus(
    private val downloadPreferences: DownloadPreferences,
    private val deleteDownload: DeleteDownload,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val libraryPreferences: LibraryPreferences,
) {

    private val mapper = { chapter: Chapter, read: Boolean ->
        ChapterUpdate(
            read = read,
            lastPageRead = if (!read) 0 else null,
            id = chapter.id,
        )
    }

    suspend fun await(read: Boolean, vararg chapters: Chapter): Result = withNonCancellableContext {
        val chaptersToUpdate = chapters.filter {
            when (read) {
                true -> !it.read
                false -> it.read || it.lastPageRead > 0
            }
        }.let { if (read) it + unreadDuplicatesOf(it) else it }
        if (chaptersToUpdate.isEmpty()) {
            return@withNonCancellableContext Result.NoChapters
        }

        try {
            chapterRepository.updateAll(
                chaptersToUpdate.map { mapper(it, read) },
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        if (read && downloadPreferences.removeAfterMarkedAsRead.get()) {
            chaptersToUpdate
                .groupBy { it.mangaId }
                .forEach { (mangaId, chapters) ->
                    deleteDownload.awaitAll(
                        manga = mangaRepository.getMangaById(mangaId),
                        chapters = chapters.toTypedArray(),
                    )
                }
        }

        Result.Success
    }

    /**
     * Yomi: other uploads of the same chapter number (another scanlator, a re-upload) count as read
     * too, wherever a chapter is marked read, not only on finishing it in the reader.
     */
    private suspend fun unreadDuplicatesOf(chapters: List<Chapter>): List<Chapter> {
        if (chapters.isEmpty()) return emptyList()
        val enabled = libraryPreferences.markDuplicateReadChapterAsRead.get()
            .contains(LibraryPreferences.MARK_DUPLICATE_CHAPTER_READ_EXISTING)
        if (!enabled) return emptyList()
        val ids = chapters.mapTo(HashSet()) { it.id }
        return chapters.filter { it.isRecognizedNumber }
            .groupBy { it.mangaId }
            .flatMap { (mangaId, marked) ->
                val numbers = marked.mapTo(HashSet()) { it.chapterNumber }
                chapterRepository.getChapterByMangaId(mangaId).filter {
                    !it.read && it.id !in ids && it.isRecognizedNumber && it.chapterNumber in numbers
                }
            }
    }

    suspend fun await(mangaId: Long, read: Boolean): Result = withNonCancellableContext {
        await(
            read = read,
            chapters = chapterRepository
                .getChapterByMangaId(mangaId)
                .toTypedArray(),
        )
    }

    suspend fun await(manga: Manga, read: Boolean) =
        await(manga.id, read)

    sealed interface Result {
        data object Success : Result
        data object NoChapters : Result
        data class InternalError(val error: Throwable) : Result
    }
}
