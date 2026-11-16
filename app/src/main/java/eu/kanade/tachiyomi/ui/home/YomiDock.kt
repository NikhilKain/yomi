// Adapted from https://github.com/NikhilKain/yomi
// Copyright Yomi contributors, Apache-2.0

package eu.kanade.tachiyomi.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.Tab
import kotlin.math.roundToInt

internal class DockBackdrop(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
}

internal fun Modifier.dockBackdrop(backdrop: DockBackdrop) = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

@Composable
internal fun YomiDock(
    tabs: List<Tab>,
    selected: cafe.adriel.voyager.navigator.tab.Tab,
    backdrop: DockBackdrop,
    onSelect: (Tab) -> Unit,
    icon: @Composable (Tab) -> Unit,
) {
    val shape = RoundedCornerShape(34.dp)
    val scheme = MaterialTheme.colorScheme
    val position by animateFloatAsState(
        tabs.indexOfFirst { it == selected }.coerceAtLeast(0).toFloat(),
        spring(dampingRatio = .9f, stiffness = 800f),
        label = "Dock selection",
    )
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clip(shape)
                .strideGlass(shape, scheme.surfaceContainer, backdrop)
                .selectableGroup()
                .padding(horizontal = 5.dp, vertical = 5.dp),
        ) {
            BoxWithConstraints(Modifier.matchParentSize()) {
                val itemWidth = maxWidth / tabs.size
                Box(
                    Modifier
                        .offset { IntOffset((itemWidth.toPx() * position).roundToInt(), 0) }
                        .width(itemWidth)
                        .fillMaxHeight()
                        .strideGlass(RoundedCornerShape(28.dp), scheme.primary, backdrop, clearLens = true),
                )
            }
            Row(Modifier.fillMaxWidth()) {
                tabs.forEach { tab ->
                    val active = tab == selected
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .selectable(active, role = Role.Tab, onClick = { onSelect(tab) })
                            .heightIn(min = 54.dp)
                            .padding(horizontal = 2.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                    ) {
                        icon(tab)
                        Text(
                            tab.options.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) scheme.onSurface else scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
