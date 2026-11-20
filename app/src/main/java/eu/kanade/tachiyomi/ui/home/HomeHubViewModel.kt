package eu.kanade.tachiyomi.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mihon.core.viewmodel.StateViewModel
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.updates.interactor.GetUpdates
import tachiyomi.domain.updates.model.UpdatesWithRelations
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

/**
 * Feeds Yomi's Home: what you are in the middle of, what arrived, what you read last and what
 * is still waiting on the shelf. Everything is derived from Mihon's own library, updates and
 * history, so the full Updates and History screens stay one tap away and nothing is duplicated.
 */
class HomeHubViewModel(
    getLibraryManga: GetLibraryManga = Injekt.get(),
    getUpdates: GetUpdates = Injekt.get(),
    getHistory: GetHistory = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
) : StateViewModel<HomeHubViewModel.State>(State()) {

    init {
        val after = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(UPDATES_WINDOW_DAYS)
        combine(
            getLibraryManga.subscribe(),
            getUpdates.subscribe(read = false, after = after),
            getHistory.subscribe(""),
            ::build,
        )
            .flowOn(Dispatchers.Default)
            .onEach { mutableState.value = it }
            .launchIn(viewModelScope)
    }

    /** The next unread chapter of a title, or its next chapter when everything is read. */
    suspend fun nextChapter(mangaId: Long): Chapter? =
        getNextChapters.await(mangaId, onlyUnread = true).firstOrNull()

    /** The chapter to resume from a history entry, the same rule Mihon's History uses. */
    suspend fun nextChapter(mangaId: Long, chapterId: Long): Chapter? =
        getNextChapters.await(mangaId, chapterId, onlyUnread = false).firstOrNull()

    private fun build(
        library: List<LibraryManga>,
        updates: List<UpdatesWithRelations>,
        history: List<HistoryWithRelations>,
    ): State {
        val reading = library.asSequence()
            .filter { it.lastRead > 0 }
            .sortedByDescending { it.lastRead }
            .take(DECK_SIZE)
            .toList()
        val fresh = updates.groupBy { it.mangaId }.values.asSequence()
            .map { group ->
                val newest = group.maxBy { it.dateFetch }
                FreshTitle(
                    mangaId = newest.mangaId,
                    title = newest.mangaTitle,
                    cover = newest.coverData,
                    count = group.size,
                    fetchedAt = newest.dateFetch,
                )
            }
            .sortedByDescending { it.fetchedAt }
            .take(SHELF_SIZE)
            .toList()
        val recent = history.asSequence()
            .distinctBy { it.mangaId }
            .take(RECENT_SIZE)
            .toList()
        val waiting = library.asSequence()
            .filter { !it.hasStarted && it.unreadCount > 0 }
            .sortedByDescending { it.manga.dateAdded }
            .take(SHELF_SIZE)
            .toList()
        return State(
            loading = false,
            reading = reading,
            fresh = fresh,
            freshChapters = updates.size,
            recent = recent,
            waiting = waiting,
            libraryCount = library.size,
            unreadCount = library.sumOf { it.unreadCount }.toInt(),
            readingCount = library.count { it.hasStarted && it.unreadCount > 0 },
        )
    }

    @Immutable
    data class State(
        val loading: Boolean = true,
        val reading: List<LibraryManga> = emptyList(),
        val fresh: List<FreshTitle> = emptyList(),
        val freshChapters: Int = 0,
        val recent: List<HistoryWithRelations> = emptyList(),
        val waiting: List<LibraryManga> = emptyList(),
        val libraryCount: Int = 0,
        val unreadCount: Int = 0,
        val readingCount: Int = 0,
    ) {
        val isEmpty: Boolean
            get() = !loading && libraryCount == 0 && recent.isEmpty()
    }

    @Immutable
    data class FreshTitle(
        val mangaId: Long,
        val title: String,
        val cover: MangaCover,
        val count: Int,
        val fetchedAt: Long,
    )

    private companion object {
        const val UPDATES_WINDOW_DAYS = 14L
        const val DECK_SIZE = 5
        const val SHELF_SIZE = 12
        const val RECENT_SIZE = 5
    }
}
