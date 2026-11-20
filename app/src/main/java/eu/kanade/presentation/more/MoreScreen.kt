package eu.kanade.presentation.more

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.more.DownloadQueueState
import tachiyomi.core.common.Constants
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.yomi.GroupRow
import tachiyomi.presentation.core.components.yomi.SectionHeader
import tachiyomi.presentation.core.components.yomi.ShapeBadge
import tachiyomi.presentation.core.components.yomi.YomiGutter
import tachiyomi.presentation.core.components.yomi.bouncyPress
import tachiyomi.presentation.core.components.yomi.groupPosition
import tachiyomi.presentation.core.components.yomi.reveal
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/** Yomi's "You": reading modes, your tools, your settings. Every entry of Mihon's More stays. */
@Composable
fun MoreScreen(
    downloadQueueStateProvider: () -> DownloadQueueState,
    downloadedOnly: Boolean,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    incognitoMode: Boolean,
    onIncognitoModeChange: (Boolean) -> Unit,
    onClickDownloadQueue: () -> Unit,
    onClickCategories: () -> Unit,
    onClickStats: () -> Unit,
    onClickDataAndStorage: () -> Unit,
    onClickSettings: () -> Unit,
    onClickSupport: () -> Unit,
    onClickAbout: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var showFeedback by remember { mutableStateOf(false) }
    if (showFeedback) FeedbackSheet(onDismissRequest = { showFeedback = false })

    Scaffold { contentPadding ->
        ScrollbarLazyColumn(contentPadding = contentPadding) {
            item(key = "header") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = YomiGutter, end = YomiGutter, top = 20.dp, bottom = 16.dp),
                ) {
                    Text(stringResource(MR.strings.yomi_you), style = MaterialTheme.typography.displayMedium)
                    Text(
                        stringResource(MR.strings.yomi_brand_tagline),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "modes") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).reveal(0),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ModeTile(
                        title = stringResource(MR.strings.label_downloaded_only),
                        icon = Icons.Outlined.CloudOff,
                        polygon = MaterialShapes.Cookie9Sided,
                        checked = downloadedOnly,
                        onCheckedChange = onDownloadedOnlyChange,
                        modifier = Modifier.weight(1f),
                    )
                    ModeTile(
                        title = stringResource(MR.strings.pref_incognito_mode),
                        icon = ImageVector.vectorResource(R.drawable.ic_glasses_24dp),
                        polygon = MaterialShapes.Clover4Leaf,
                        checked = incognitoMode,
                        onCheckedChange = onIncognitoModeChange,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item(key = "settings") {
                Spacer(Modifier.height(12.dp))
                GroupRow(
                    title = stringResource(MR.strings.label_settings),
                    subtitle = stringResource(MR.strings.yomi_preferences),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onClickSettings,
                    leading = {
                        ShapeBadge(
                            Icons.Outlined.Settings,
                            polygon = MaterialShapes.Cookie12Sided,
                            container = MaterialTheme.colorScheme.secondary,
                            content = MaterialTheme.colorScheme.onSecondary,
                        )
                    },
                    trailing = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, null) },
                )
            }

            item(key = "tools-title") { SectionHeader(stringResource(MR.strings.yomi_tools)) }
            item(key = "tools") {
                val queue = downloadQueueStateProvider()
                val queueText = when (queue) {
                    DownloadQueueState.Stopped -> null
                    is DownloadQueueState.Paused -> if (queue.pending == 0) {
                        stringResource(MR.strings.paused)
                    } else {
                        "${stringResource(MR.strings.paused)} • ${
                            pluralStringResource(MR.plurals.download_queue_summary, queue.pending, queue.pending)
                        }"
                    }
                    is DownloadQueueState.Downloading ->
                        pluralStringResource(MR.plurals.download_queue_summary, queue.pending, queue.pending)
                }
                Column(
                    Modifier.padding(horizontal = 16.dp).reveal(1),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ToolTile(
                            stringResource(MR.strings.label_download_queue),
                            queueText,
                            Icons.Outlined.GetApp,
                            MaterialShapes.Pill,
                            onClickDownloadQueue,
                            Modifier.weight(1f),
                        )
                        ToolTile(
                            stringResource(MR.strings.categories),
                            null,
                            Icons.AutoMirrored.Outlined.Label,
                            MaterialShapes.Cookie6Sided,
                            onClickCategories,
                            Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ToolTile(
                            stringResource(MR.strings.label_stats),
                            null,
                            Icons.Outlined.QueryStats,
                            MaterialShapes.Sunny,
                            onClickStats,
                            Modifier.weight(1f),
                        )
                        ToolTile(
                            stringResource(MR.strings.label_data_storage),
                            null,
                            Icons.Outlined.Storage,
                            MaterialShapes.Flower,
                            onClickDataAndStorage,
                            Modifier.weight(1f),
                        )
                    }
                }
            }

            item(key = "about-title") { SectionHeader(stringResource(MR.strings.pref_category_about)) }
            val about = listOf(
                Triple(MR.strings.yomi_feedback, Icons.Outlined.Feedback, { showFeedback = true }),
                Triple(MR.strings.pref_category_about, Icons.Outlined.Info, onClickAbout),
                Triple(MR.strings.label_support_us, Icons.Default.VolunteerActivism, onClickSupport),
                Triple(
                    MR.strings.label_help,
                    Icons.AutoMirrored.Outlined.HelpOutline,
                    { uriHandler.openUri(Constants.URL_HELP) },
                ),
            )
            about.forEachIndexed { index, (title, icon, onClick) ->
                item(key = "about-$index") {
                    GroupRow(
                        title = stringResource(title),
                        position = groupPosition(index, about.size),
                        onClick = onClick,
                        leading = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
                    )
                }
            }
            item(key = "end") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** A reading mode as a large tile; switching it on fills the tile and rounds it further. */
@Composable
private fun ModeTile(
    title: String,
    icon: ImageVector,
    polygon: RoundedPolygon,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val corner by animateDpAsState(if (checked) 40.dp else 24.dp, spring(dampingRatio = 0.6f), label = "mode-corner")
    val container by animateColorAsState(
        if (checked) scheme.primaryContainer else scheme.surfaceContainerHigh,
        label = "mode-container",
    )
    val content: Color = if (checked) scheme.onPrimaryContainer else scheme.onSurface
    Column(
        modifier
            .bouncyPress(interaction)
            .clip(RoundedCornerShape(corner))
            .background(container)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .heightIn(min = 132.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        ShapeBadge(
            icon = icon,
            polygon = polygon,
            size = 44.dp,
            container = if (checked) scheme.primary else scheme.secondaryContainer,
            content = if (checked) scheme.onPrimary else scheme.onSecondaryContainer,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = content,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(if (checked) MR.strings.on else MR.strings.off),
            style = MaterialTheme.typography.labelLarge,
            color = content.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun ToolTile(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    polygon: RoundedPolygon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .bouncyPress(interaction)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick,
            )
            .heightIn(min = 112.dp)
            .padding(16.dp),
    ) {
        ShapeBadge(
            icon = icon,
            polygon = polygon,
            size = 40.dp,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
