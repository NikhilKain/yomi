package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.presentation.browse.components.BrowseSourceLoadingItem
import eu.kanade.presentation.browse.components.InLibraryBadge
import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.presentation.core.components.FastScrollLazyVerticalGrid
import tachiyomi.presentation.core.util.plus

/**
 * Yomi's bento layout. Rows repeat as one wide feature tile, then two halves, then three
 * thirds, so a shelf reads like a magazine spread instead of a uniform wall of covers. Wide
 * screens use a 12-unit grid with rows of two, three and four.
 */
internal class BentoLayout(private val wide: Boolean) {
    val units = if (wide) 12 else 6
    private val cycle = if (wide) intArrayOf(6, 6, 4, 4, 4, 3, 3, 3, 3) else intArrayOf(6, 3, 3, 2, 2, 2)

    fun span(index: Int): Int = cycle[index % cycle.size]

    /** Every tile in a row shares a ratio, so rows line up. */
    fun ratio(span: Int): Float = when {
        span == units -> 1.6f
        wide && span == 6 -> 1.25f
        span * 2 == units -> 0.78f
        else -> 2f / 3f
    }

    fun featured(span: Int): Boolean = span * 2 >= units
}

@Composable
internal fun rememberBentoLayout(): BentoLayout {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    return androidx.compose.runtime.remember(wide) { BentoLayout(wide) }
}

@Composable
internal fun LibraryBentoGrid(
    items: List<LibraryItem>,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    val bento = rememberBentoLayout()
    FastScrollLazyVerticalGrid(
        columns = GridCells.Fixed(bento.units),
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridVerticalSpacer),
        horizontalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridHorizontalSpacer),
    ) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)
        itemsIndexed(
            items = items,
            span = { index, _ -> GridItemSpan(bento.span(index)) },
            contentType = { index, _ -> "library_bento_${bento.span(index)}" },
        ) { index, libraryItem ->
            val span = bento.span(index)
            val manga = libraryItem.libraryManga.manga
            MangaCompactGridItem(
                isSelected = manga.id in selection,
                title = manga.title,
                coverData = MangaCover(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    url = manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                ),
                coverBadgeStart = {
                    DownloadsBadge(count = libraryItem.badges.downloadCount)
                    UnreadBadge(count = libraryItem.badges.unreadCount)
                },
                coverBadgeEnd = {
                    LanguageBadge(
                        isLocal = libraryItem.badges.isLocal,
                        sourceLanguage = libraryItem.badges.sourceLanguage,
                    )
                },
                onLongClick = { onLongClick(libraryItem.libraryManga) },
                onClick = { onClick(libraryItem.libraryManga) },
                onClickContinueReading = if (onClickContinueReading != null && libraryItem.unreadCount > 0) {
                    { onClickContinueReading(libraryItem.libraryManga) }
                } else {
                    null
                },
                ratio = bento.ratio(span),
                featured = bento.featured(span),
            )
        }
    }
}

@Composable
fun BrowseSourceBentoGrid(
    mangaList: LazyPagingItems<StateFlow<Manga>>,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
) {
    val bento = rememberBentoLayout()
    LazyVerticalGrid(
        columns = GridCells.Fixed(bento.units),
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridVerticalSpacer),
        horizontalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridHorizontalSpacer),
    ) {
        loadingRow(mangaList.loadState.prepend is LoadState.Loading)
        items(
            count = mangaList.itemCount,
            span = { GridItemSpan(bento.span(it)) },
            contentType = { "source_bento_${bento.span(it)}" },
        ) { index ->
            val manga by mangaList[index]?.collectAsState() ?: return@items
            val span = bento.span(index)
            MangaCompactGridItem(
                title = manga.title,
                coverData = MangaCover(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    url = manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                ),
                coverAlpha = if (manga.favorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
                coverBadgeStart = { InLibraryBadge(enabled = manga.favorite) },
                onLongClick = { onMangaLongClick(manga) },
                onClick = { onMangaClick(manga) },
                ratio = bento.ratio(span),
                featured = bento.featured(span),
            )
        }
        loadingRow(
            mangaList.loadState.refresh is LoadState.Loading || mangaList.loadState.append is LoadState.Loading,
        )
    }
}

private fun LazyGridScope.loadingRow(show: Boolean) {
    if (show) item(span = { GridItemSpan(maxLineSpan) }) { BrowseSourceLoadingItem() }
}
