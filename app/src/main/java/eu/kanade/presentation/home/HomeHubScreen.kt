package eu.kanade.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.library.components.YomiReadingDeck
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.ui.home.HomeHubViewModel
import eu.kanade.tachiyomi.util.lang.toTimestampString
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.yomi.EmptyState
import tachiyomi.presentation.core.components.yomi.GroupRow
import tachiyomi.presentation.core.components.yomi.SectionHeader
import tachiyomi.presentation.core.components.yomi.ShapeBadge
import tachiyomi.presentation.core.components.yomi.ShapeBadgeBox
import tachiyomi.presentation.core.components.yomi.StatTile
import tachiyomi.presentation.core.components.yomi.YomiGutter
import tachiyomi.presentation.core.components.yomi.bouncyPress
import tachiyomi.presentation.core.components.yomi.groupPosition
import tachiyomi.presentation.core.components.yomi.reveal
import tachiyomi.presentation.core.i18n.stringResource
import java.time.LocalTime

/**
 * Yomi's Home: the first thing you see. It answers "what do I read now?" before anything else,
 * then shows what arrived and what you read last, each with a way into Mihon's full screen.
 */
@Composable
fun HomeHubScreen(
    state: HomeHubViewModel.State,
    snackbarHostState: SnackbarHostState,
    onOpenManga: (Long) -> Unit,
    onRead: (Long) -> Unit,
    onResumeHistory: (HistoryWithRelations) -> Unit,
    onSeeUpdates: () -> Unit,
    onSeeHistory: () -> Unit,
    onSeeLibrary: () -> Unit,
    onDiscover: () -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onDownloads: () -> Unit,
    onStats: () -> Unit,
) {
    val greeting: @Composable () -> Unit = {
        Greeting(
            subtitle = when {
                state.loading -> null
                state.freshChapters > 0 -> stringResource(MR.strings.yomi_unread_waiting, state.freshChapters)
                state.libraryCount > 0 -> stringResource(MR.strings.yomi_nothing_new)
                else -> null
            },
            onSearch = onSearch,
            onRefresh = onRefresh.takeIf { state.libraryCount > 0 },
        )
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                LoadingIndicator(Modifier.size(72.dp))
            }
            state.isEmpty -> Column(Modifier.fillMaxSize().padding(padding)) {
                greeting()
                Spacer(Modifier.weight(1f))
                EmptyState(
                    icon = Icons.Outlined.Explore,
                    title = stringResource(MR.strings.yomi_home_empty_title),
                    body = stringResource(MR.strings.yomi_home_empty_body),
                    polygon = MaterialShapes.Cookie12Sided,
                    action = {
                        Button(onClick = onDiscover, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(MR.strings.yomi_go_discover))
                        }
                    },
                )
                Spacer(Modifier.weight(1.4f))
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                item(key = "greeting", contentType = "greeting") { greeting() }
                item(key = "stats", contentType = "stats") {
                    Stats(state, onSeeLibrary, Modifier.reveal(0))
                }
                if (state.reading.isNotEmpty()) {
                    item(key = "deck-title", contentType = "header") {
                        SectionHeader(stringResource(MR.strings.yomi_continue_reading))
                    }
                    item(key = "deck", contentType = "deck") {
                        YomiReadingDeck(
                            books = state.reading,
                            onOpen = { onOpenManga(it.id) },
                            onRead = { onRead(it.id) },
                        )
                    }
                }
                item(key = "shortcuts", contentType = "shortcuts") {
                    Shortcuts(
                        onUpdates = onSeeUpdates,
                        onHistory = onSeeHistory,
                        onDownloads = onDownloads,
                        onStats = onStats,
                        modifier = Modifier.reveal(1),
                    )
                }
                item(key = "fresh-title", contentType = "header") {
                    SectionHeader(
                        stringResource(MR.strings.yomi_new_chapters),
                        trailing = stringResource(MR.strings.yomi_see_all),
                        onTrailing = onSeeUpdates,
                    )
                }
                item(key = "fresh", contentType = "shelf") {
                    if (state.fresh.isEmpty()) {
                        GroupRow(
                            title = stringResource(MR.strings.yomi_nothing_new),
                            leading = {
                                ShapeBadge(Icons.Rounded.DoneAll, polygon = MaterialShapes.Sunny, size = 40.dp)
                            },
                            onClick = onSeeUpdates,
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = YomiGutter),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(state.fresh, key = { _, it -> it.mangaId }) { index, title ->
                                ShelfCard(
                                    cover = title.cover,
                                    title = title.title,
                                    caption = relativeTimeSpanString(title.fetchedAt),
                                    badge = title.count,
                                    onClick = { onOpenManga(title.mangaId) },
                                    modifier = Modifier.reveal(index),
                                )
                            }
                        }
                    }
                }
                if (state.recent.isNotEmpty()) {
                    item(key = "recent-title", contentType = "header") {
                        SectionHeader(
                            stringResource(MR.strings.yomi_recently_read),
                            trailing = stringResource(MR.strings.yomi_see_all),
                            onTrailing = onSeeHistory,
                        )
                    }
                    itemsIndexed(state.recent, key = { _, it -> "recent-${it.id}" }, contentType = { _, _ -> "row" }) {
                            index,
                            entry,
                        ->
                        RecentRow(
                            entry = entry,
                            index = index,
                            count = state.recent.size,
                            onOpen = { onOpenManga(entry.mangaId) },
                            onResume = { onResumeHistory(entry) },
                        )
                    }
                }
                if (state.waiting.isNotEmpty()) {
                    item(key = "waiting-title", contentType = "header") {
                        SectionHeader(
                            stringResource(MR.strings.yomi_not_started),
                            trailing = stringResource(MR.strings.yomi_see_all),
                            onTrailing = onSeeLibrary,
                        )
                    }
                    item(key = "waiting", contentType = "shelf") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = YomiGutter),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(state.waiting, key = { _, it -> it.id }) { index, book ->
                                WaitingCard(book, { onOpenManga(book.id) }, Modifier.reveal(index))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Greeting(
    subtitle: String?,
    onSearch: () -> Unit,
    onRefresh: (() -> Unit)?,
) {
    val hour = remember { LocalTime.now().hour }
    val greeting = stringResource(
        when (hour) {
            in 5..11 -> MR.strings.yomi_good_morning
            in 12..16 -> MR.strings.yomi_good_afternoon
            in 17..22 -> MR.strings.yomi_good_evening
            else -> MR.strings.yomi_good_night
        },
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = YomiGutter, end = 12.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                greeting,
                style = MaterialTheme.typography.displayMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (onRefresh != null) {
            FilledTonalIconButton(onClick = onRefresh, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Outlined.Refresh, stringResource(MR.strings.action_update_library))
            }
        }
        FilledTonalIconButton(onClick = onSearch, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Outlined.Search, stringResource(MR.strings.yomi_search_everywhere))
        }
    }
}

@Composable
private fun Stats(state: HomeHubViewModel.State, onLibrary: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onLibrary),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile(compact(state.libraryCount), stringResource(MR.strings.yomi_in_library), Modifier.weight(1f))
        StatTile(compact(state.readingCount), stringResource(MR.strings.yomi_reading_now), Modifier.weight(1f))
        StatTile(compact(state.unreadCount), stringResource(MR.strings.yomi_unread), Modifier.weight(1f))
    }
}

@Composable
private fun Shortcuts(
    onUpdates: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Shortcut(Icons.Outlined.NewReleases, stringResource(MR.strings.label_recent_updates), MaterialShapes.Cookie9Sided, onUpdates)
        Shortcut(Icons.Outlined.History, stringResource(MR.strings.history), MaterialShapes.Clover4Leaf, onHistory)
        Shortcut(Icons.Outlined.Download, stringResource(MR.strings.label_download_queue), MaterialShapes.Pill, onDownloads)
        Shortcut(Icons.Outlined.QueryStats, stringResource(MR.strings.label_stats), MaterialShapes.Sunny, onStats)
    }
}

@Composable
private fun Shortcut(icon: ImageVector, label: String, polygon: RoundedPolygon, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .width(80.dp)
            .bouncyPress(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShapeBadge(
            icon = icon,
            polygon = polygon,
            size = 60.dp,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShelfCard(
    cover: Any,
    title: String,
    caption: String?,
    badge: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .width(116.dp)
            .bouncyPress(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box {
            MangaCover.Book(
                data = cover,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            )
            if (badge > 0) {
                ShapeBadgeBox(
                    polygon = MaterialShapes.Sunny,
                    size = 36.dp,
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                ) {
                    Text(
                        if (badge > 99) "99+" else badge.toString(),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!caption.isNullOrBlank()) {
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WaitingCard(book: LibraryManga, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ShelfCard(
        cover = book.manga.asMangaCover(),
        title = book.manga.title,
        caption = null,
        badge = book.unreadCount.toInt(),
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun RecentRow(
    entry: HistoryWithRelations,
    index: Int,
    count: Int,
    onOpen: () -> Unit,
    onResume: () -> Unit,
) {
    val readAt = remember(entry.readAt) { entry.readAt?.toTimestampString().orEmpty() }
    GroupRow(
        title = entry.title,
        subtitle = if (entry.chapterNumber > -1) {
            stringResource(MR.strings.recent_manga_time, formatChapterNumber(entry.chapterNumber), readAt)
        } else {
            readAt
        },
        position = groupPosition(index, count),
        titleMaxLines = 1,
        subtitleMaxLines = 1,
        onClick = onOpen,
        leading = {
            MangaCover.Square(
                data = entry.coverData,
                modifier = Modifier.size(52.dp),
                shape = MaterialShapes.Cookie9Sided.toShape(),
            )
        },
        trailing = {
            FilledTonalIconButton(onClick = onResume, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.PlayArrow, stringResource(MR.strings.action_resume))
            }
        },
    )
}

/** Counts that fit a tile: 9999, then 10.6k, then 1.2M. */
private fun compact(value: Int): String = when {
    value < 10_000 -> value.toString()
    value < 1_000_000 -> "%.1fk".format(java.util.Locale.ROOT, value / 1000f).replace(".0k", "k")
    else -> "%.1fM".format(java.util.Locale.ROOT, value / 1_000_000f).replace(".0M", "M")
}
