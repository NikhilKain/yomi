package eu.kanade.presentation.manga.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.theme.NumeralFamily
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.yomi.Tag
import tachiyomi.presentation.core.components.yomi.YomiEasing
import tachiyomi.presentation.core.components.yomi.YomiGutter
import tachiyomi.presentation.core.components.yomi.reveal
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.clickableNoIndication
import kotlin.time.Clock
import kotlin.time.Instant

private val BackdropHeight = 250.dp

/**
 * The title's hero: its cover stretched into an atmospheric backdrop that melts into the page,
 * the cover itself standing in front, and the title set large beside it.
 */
@Composable
fun MangaInfoBox(
    @Suppress("UNUSED_PARAMETER") isTabletUi: Boolean,
    appBarPadding: Dp,
    manga: Manga,
    sourceName: String,
    isStubSource: Boolean,
    onCoverClick: () -> Unit,
    doSearch: (query: String, global: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val page = scheme.background
    val cover = remember(manga.id, manga.thumbnailUrl, manga.coverLastModified) {
        ImageRequest.Builder(context).data(manga).crossfade(true).build()
    }
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxWidth().height(appBarPadding + BackdropHeight),
            )
            Box(
                Modifier.fillMaxWidth().height(appBarPadding + BackdropHeight + 1.dp).background(
                    Brush.verticalGradient(
                        0f to page.copy(alpha = 0.78f),
                        0.3f to page.copy(alpha = 0.25f),
                        0.6f to page.copy(alpha = 0.55f),
                        1f to page,
                    ),
                ),
            )
            Row(
                modifier = Modifier
                    .padding(start = YomiGutter, end = YomiGutter, top = appBarPadding + 92.dp)
                    .reveal(),
                verticalAlignment = Alignment.Bottom,
            ) {
                MangaCover.Book(
                    data = cover,
                    modifier = Modifier.width(124.dp),
                    contentDescription = stringResource(MR.strings.manga_cover),
                    shape = MaterialTheme.shapes.large,
                    onClick = onCoverClick,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                    Tag(
                        text = statusLabel(manga.status),
                        container = scheme.secondaryContainer,
                        content = scheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.height(8.dp))
                    val title = manga.title
                    Text(
                        text = title.ifBlank { stringResource(MR.strings.unknown_title) },
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickableNoIndication(
                            onLongClick = { if (title.isNotBlank()) context.copyToClipboard(title, title) },
                            onClick = { if (title.isNotBlank()) doSearch(title, true) },
                        ),
                    )
                    val author = manga.author?.takeIf { it.isNotBlank() }
                    val artist = manga.artist?.takeIf { it.isNotBlank() && it != author }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = author ?: stringResource(MR.strings.unknown_author),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickableNoIndication(
                            onLongClick = { if (author != null) context.copyToClipboard(author, author) },
                            onClick = { if (author != null) doSearch(author, true) },
                        ),
                    )
                    if (artist != null) {
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickableNoIndication(
                                onLongClick = { context.copyToClipboard(artist, artist) },
                                onClick = { doSearch(artist, true) },
                            ),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .padding(start = YomiGutter, end = YomiGutter, top = 16.dp)
                .clickableNoIndication { doSearch(sourceName, false) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isStubSource) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp).size(18.dp),
                    tint = scheme.error,
                )
            }
            Text(
                text = sourceName,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun statusLabel(status: Long): String = when (status) {
    SManga.ONGOING.toLong() -> stringResource(MR.strings.ongoing)
    SManga.COMPLETED.toLong() -> stringResource(MR.strings.completed)
    SManga.LICENSED.toLong() -> stringResource(MR.strings.licensed)
    SManga.PUBLISHING_FINISHED.toLong() -> stringResource(MR.strings.publishing_finished)
    SManga.CANCELLED.toLong() -> stringResource(MR.strings.cancelled)
    SManga.ON_HIATUS.toLong() -> stringResource(MR.strings.on_hiatus)
    else -> stringResource(MR.strings.unknown)
}

/** The title's standing with you (shelf, update rhythm, tracking, web) as one connected group. */
@Composable
fun MangaActionRow(
    favorite: Boolean,
    trackingCount: Int,
    nextUpdate: Instant?,
    isUserIntervalMode: Boolean,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,
    onEditIntervalClicked: (() -> Unit)?,
    onEditCategory: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // TODO: show something better when using custom interval
    val nextUpdateDays = remember(nextUpdate) {
        if (nextUpdate != null) {
            val now = Clock.System.now()
            now.daysUntil(nextUpdate, TimeZone.currentSystemDefault()).coerceAtLeast(0)
        } else {
            null
        }
    }
    val segments = buildList {
        add(
            ActionSegment(
                title = stringResource(if (favorite) MR.strings.in_library else MR.strings.add_to_library),
                icon = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                state = if (favorite) SegmentState.Selected else SegmentState.Idle,
                onClick = onAddToLibraryClicked,
                onLongClick = onEditCategory,
            ),
        )
        add(
            ActionSegment(
                title = when (nextUpdateDays) {
                    null -> stringResource(MR.strings.not_applicable)
                    0 -> stringResource(MR.strings.manga_interval_expected_update_soon)
                    else -> pluralStringResource(MR.plurals.day, count = nextUpdateDays, nextUpdateDays)
                },
                icon = Icons.Outlined.HourglassEmpty,
                state = if (isUserIntervalMode) SegmentState.Active else SegmentState.Idle,
                onClick = { onEditIntervalClicked?.invoke() },
            ),
        )
        add(
            ActionSegment(
                title = if (trackingCount == 0) {
                    stringResource(MR.strings.manga_tracking_tab)
                } else {
                    pluralStringResource(MR.plurals.num_trackers, count = trackingCount, trackingCount)
                },
                icon = if (trackingCount == 0) Icons.Outlined.Sync else Icons.Outlined.Done,
                state = if (trackingCount == 0) SegmentState.Idle else SegmentState.Active,
                onClick = onTrackingClicked,
            ),
        )
        if (onWebViewClicked != null) {
            add(
                ActionSegment(
                    title = stringResource(MR.strings.action_web_view),
                    icon = Icons.Outlined.Public,
                    state = SegmentState.Idle,
                    onClick = onWebViewClicked,
                    onLongClick = onWebViewLongClicked,
                ),
            )
        }
    }
    Row(
        modifier = modifier
            .padding(start = YomiGutter, end = YomiGutter, top = 16.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .reveal(1),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        segments.forEachIndexed { index, segment ->
            val outer = 28.dp
            val inner = 8.dp
            val start = if (index == 0) outer else inner
            val end = if (index == segments.lastIndex) outer else inner
            Segment(
                segment = segment,
                shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

private enum class SegmentState { Idle, Active, Selected }

private class ActionSegment(
    val title: String,
    val icon: ImageVector,
    val state: SegmentState,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
)

@Composable
private fun Segment(segment: ActionSegment, shape: Shape, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container: Color
    val content: Color
    when (segment.state) {
        SegmentState.Selected -> {
            container = scheme.primary
            content = scheme.onPrimary
        }
        SegmentState.Active -> {
            container = scheme.secondaryContainer
            content = scheme.onSecondaryContainer
        }
        SegmentState.Idle -> {
            container = scheme.surfaceContainerHigh
            content = scheme.onSurfaceVariant
        }
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(container)
            .combinedClickable(onClick = segment.onClick, onLongClick = segment.onLongClick)
            .heightIn(min = 68.dp)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(segment.icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = segment.title,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Your place in the series: chapters read against the total in large numerals, a wavy progress
 * line, and the one action that matters here, picking the story back up.
 */
@Composable
fun MangaProgressCard(
    read: Int,
    total: Int,
    onContinueReading: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (total <= 0) return
    val scheme = MaterialTheme.colorScheme
    val fraction by animateFloatAsState(
        read.toFloat() / total,
        tween(700, easing = YomiEasing.Emphasized),
        label = "series-progress",
    )
    Surface(
        color = scheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
            .padding(start = YomiGutter, end = YomiGutter, top = 16.dp)
            .fillMaxWidth()
            .reveal(2),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = read.toString(),
                    fontFamily = NumeralFamily,
                    style = MaterialTheme.typography.displayMedium,
                    color = scheme.primary,
                )
                Text(
                    text = " / $total",
                    fontFamily = NumeralFamily,
                    style = MaterialTheme.typography.headlineSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = pluralStringResource(MR.plurals.manga_num_chapters, count = total, total),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            LinearWavyProgressIndicator(
                progress = { fraction },
                waveSpeed = 0.dp,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Spacer(Modifier.height(16.dp))
            if (read < total) {
                Button(
                    onClick = onContinueReading,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(ButtonDefaults.MediumIconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = stringResource(if (read > 0) MR.strings.action_resume else MR.strings.action_start),
                        style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                        maxLines = 1,
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(scheme.secondaryContainer)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = scheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(MR.strings.yomi_all_caught_up),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
