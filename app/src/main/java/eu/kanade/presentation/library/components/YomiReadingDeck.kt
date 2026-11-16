package eu.kanade.presentation.library.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.theme.AmbientTheme
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.yomi.YomiEasing
import tachiyomi.presentation.core.components.yomi.reveal
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The shelf's spotlight: the titles you are most likely to open next, one card each, tinted by
 * their own cover. The card carries the cover as a backdrop, your place in the series and one
 * large action to pick it back up.
 */
@Composable
internal fun YomiReadingDeck(
    books: List<LibraryManga>,
    onOpen: (LibraryManga) -> Unit,
    onRead: (LibraryManga) -> Unit,
) {
    val pager = rememberPagerState { books.size }
    HorizontalPager(
        state = pager,
        pageSpacing = 12.dp,
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.padding(bottom = 12.dp),
        key = { books[it].id },
    ) { index ->
        val book = books[index]
        val cover = book.manga.asMangaCover()
        AmbientTheme(cover) {
            Spotlight(book, cover, { onOpen(book) }, { onRead(book) }, Modifier.reveal(index))
        }
    }
}

@Composable
private fun Spotlight(
    book: LibraryManga,
    cover: Any,
    onOpen: () -> Unit,
    onRead: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container = scheme.surfaceContainer
    val total = book.totalChapters.toInt()
    val read = book.readCount.toInt()
    val fraction by animateFloatAsState(
        if (total > 0) read.toFloat() / total else 0f,
        tween(700, easing = YomiEasing.Emphasized),
        label = "spotlight-progress",
    )
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(container)
            .clickable(onClick = onOpen),
    ) {
        // The cover again, wide and faded, so the card has the title's atmosphere.
        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxWidth().height(210.dp),
        )
        Box(
            Modifier.fillMaxWidth().height(211.dp).background(
                Brush.verticalGradient(
                    0f to container.copy(alpha = 0.35f),
                    0.45f to container.copy(alpha = 0.7f),
                    1f to container,
                ),
            ),
        )
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 84.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                MangaCover.Book(
                    data = cover,
                    modifier = Modifier.width(96.dp),
                    shape = MaterialTheme.shapes.large,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            if (book.lastRead > 0) MR.strings.yomi_continue_reading else MR.strings.yomi_up_next,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        book.manga.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (total > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (read >= total) {
                                stringResource(MR.strings.yomi_all_caught_up)
                            } else {
                                stringResource(MR.strings.yomi_chapters_progress, read, total)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (total > 0) {
                LinearWavyProgressIndicator(
                    progress = { fraction },
                    waveSpeed = 0.dp,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onRead != null && read < total) {
                    Button(
                        onClick = onRead,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f).height(ButtonDefaults.MediumContainerHeight),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(ButtonDefaults.MediumIconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(
                            stringResource(
                                if (read > 0) MR.strings.action_resume else MR.strings.yomi_start_reading,
                            ),
                            style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                            maxLines = 1,
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onOpen,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.size(ButtonDefaults.MediumContainerHeight),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = stringResource(MR.strings.yomi_details),
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = onOpen,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f).height(ButtonDefaults.MediumContainerHeight),
                    ) {
                        Text(
                            stringResource(MR.strings.yomi_details),
                            style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            null,
                            Modifier.size(ButtonDefaults.MediumIconSize),
                        )
                    }
                }
            }
        }
    }
}
