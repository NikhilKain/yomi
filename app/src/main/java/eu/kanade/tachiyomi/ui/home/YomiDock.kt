// Adapted from https://github.com/NikhilKain/yomi
// Copyright Yomi contributors, Apache-2.0

package eu.kanade.tachiyomi.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.util.Tab
import eu.kanade.presentation.util.yomiPress
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
    icon: @Composable (Tab, Color) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val surfaceLayer = rememberGraphicsLayer()
    val dockSurface = remember(surfaceLayer) { DockBackdrop(surfaceLayer) }
    val motion = spring<Float>(dampingRatio = .72f, stiffness = 420f)
    val position by animateFloatAsState(
        tabs.indexOf(selected).coerceAtLeast(0).toFloat(),
        motion,
        label = "Dock selection",
    )
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 18.dp)) {
        Box(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .shadow(8.dp, CircleShape, clip = false)
                .strideGlass(CircleShape, scheme.surfaceContainerLowest, backdrop, output = dockSurface)
                .selectableGroup()
                .padding(8.dp),
        ) {
            BoxWithConstraints(Modifier.matchParentSize()) {
                val itemWidth = maxWidth / tabs.size
                Box(
                    Modifier
                        .offset { IntOffset((itemWidth.toPx() * position).roundToInt(), 0) }
                        .width(itemWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 1.dp)
                        .strideGlass(CircleShape, scheme.primary, dockSurface, clearLens = true),
                )
            }
            Row(Modifier.fillMaxWidth()) {
                tabs.forEach { tab ->
                    val active = tab == selected
                    val interaction = remember { MutableInteractionSource() }
                    val content by animateColorAsState(
                        if (active) scheme.primary else scheme.onSurfaceVariant,
                        label = "Dock tab color",
                    )
                    val magnification by animateFloatAsState(
                        if (active) DockLensMagnification else 1f,
                        motion,
                        label = "Dock tab magnification",
                    )
                    val labelStyle = MaterialTheme.typography.labelSmall
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .selectable(
                                selected = active,
                                interactionSource = interaction,
                                indication = null,
                                role = Role.Tab,
                                onClick = { onSelect(tab) },
                            )
                            .heightIn(min = 48.dp)
                            .yomiPress(interaction)
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                            .graphicsLayer {
                                scaleX = magnification
                                scaleY = magnification
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    ) {
                        icon(tab, content)
                        BasicText(
                            text = tab.options.title,
                            style = labelStyle.copy(
                                color = content,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = labelStyle.fontSize),
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
