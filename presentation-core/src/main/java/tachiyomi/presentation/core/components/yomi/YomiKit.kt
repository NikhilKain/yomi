package tachiyomi.presentation.core.components.yomi

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon

/** Horizontal page gutter shared by every Yomi screen. */
val YomiGutter = 20.dp

/** A lone toggle has no neighbours to connect to, so it is simply a pill. */
val SingleToggleShapes = ToggleButtonShapes(
    shape = CircleShape,
    pressedShape = RoundedCornerShape(12.dp),
    checkedShape = CircleShape,
)

/** An icon set inside one of the Material expressive shapes (cookie, clover, burst). */
@Composable
fun ShapeBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    size: Dp = 48.dp,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Box(
        modifier.size(size).clip(polygon.toShape()).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
fun ShapeBadge(
    painter: Painter,
    modifier: Modifier = Modifier,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    size: Dp = 48.dp,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Box(
        modifier.size(size).clip(polygon.toShape()).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.46f))
    }
}

/** A badge whose content is arbitrary (a number, a monogram), set in an expressive shape. */
@Composable
fun ShapeBadgeBox(
    modifier: Modifier = Modifier,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    size: Dp = 48.dp,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    body: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.size(size).clip(polygon.toShape()).background(container),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) { body() }
    }
}

/** The handful of shapes list rows cycle through, so neighbouring rows do not all look alike. */
val YomiRowShapes: List<RoundedPolygon>
    get() = listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Flower,
        MaterialShapes.Sunny,
        MaterialShapes.Cookie12Sided,
        MaterialShapes.Clover8Leaf,
        MaterialShapes.Cookie7Sided,
    )

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onTrailing: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = YomiGutter, end = 8.dp, top = 8.dp, bottom = 4.dp)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null && onTrailing != null) {
            TextButton(onClick = onTrailing) { Text(trailing) }
        }
    }
}

/** Material 3 expressive connected button group, used as a single-choice segmented control. */
@Composable
fun <T> ConnectedChoice(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    weighted: Boolean = true,
) {
    Row(
        modifier.then(if (weighted) Modifier.fillMaxWidth() else Modifier),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val interaction = remember { MutableInteractionSource() }
            ToggleButton(
                interactionSource = interaction,
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                shapes = when {
                    options.size == 1 -> SingleToggleShapes
                    index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                contentPadding = PaddingValues(horizontal = if (weighted) 6.dp else 18.dp),
                modifier = (if (weighted) Modifier.weight(1f) else Modifier)
                    .bouncyPress(interaction)
                    .semantics { role = Role.RadioButton },
            ) {
                Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Small rounded label: "Ongoing", "EN", "84". */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// ---- Grouped rows: a stack of tonal cards that reads as one rounded block ----------------------

private val GroupOuter = 28.dp
private val GroupInner = 6.dp

/** Vertical gap between rows of one group. */
val GroupGap = 3.dp

enum class GroupPosition { Single, First, Middle, Last }

fun groupPosition(index: Int, count: Int): GroupPosition = when {
    count <= 1 -> GroupPosition.Single
    index == 0 -> GroupPosition.First
    index == count - 1 -> GroupPosition.Last
    else -> GroupPosition.Middle
}

fun GroupPosition.shape(): Shape {
    val top = if (this == GroupPosition.First || this == GroupPosition.Single) GroupOuter else GroupInner
    val bottom = if (this == GroupPosition.Last || this == GroupPosition.Single) GroupOuter else GroupInner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** Where a row sits inside its group; rows read it to pick their corner radii. */
val LocalGroupPosition = compositionLocalOf { GroupPosition.Single }

/**
 * One row of a grouped list: tonal card, optional leading slot, title, supporting text and a
 * trailing slot. Corners follow [position] so consecutive rows merge into one rounded block.
 */
@Composable
fun GroupRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    position: GroupPosition = LocalGroupPosition.current,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    titleMaxLines: Int = 2,
    subtitleMaxLines: Int = 3,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    GroupSurface(
        modifier = modifier,
        position = position,
        container = container,
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.72f),
                        maxLines = subtitleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
    }
}

/** The bare grouped card, for rows whose content does not fit [GroupRow]. */
@Composable
fun GroupSurface(
    modifier: Modifier = Modifier,
    position: GroupPosition = LocalGroupPosition.current,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = position.shape()
    val color = if (selected) MaterialTheme.colorScheme.secondaryContainer else container
    Box(
        modifier
            .padding(horizontal = 16.dp, vertical = GroupGap / 2)
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.bouncyPress(interaction) else Modifier)
            .clip(shape)
            .background(color)
            .then(
                when {
                    onClick != null && onLongClick != null -> Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                    onClick != null -> Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = onClick,
                    )
                    else -> Modifier
                },
            ),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides
                if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            content = content,
        )
    }
}

/** A calm empty state: a large shaped badge, a headline and one line of guidance. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    polygon: RoundedPolygon = MaterialShapes.Flower,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShapeBadge(icon = icon, polygon = polygon, size = 96.dp, modifier = Modifier.reveal())
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.reveal(1),
        )
        if (!body.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.reveal(2),
            )
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.reveal(3)) { action() }
        }
    }
}

/** A number that should read at a glance, with a quiet caption under it. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

/**
 * Position of the row at [index] in a mixed list, where rows are interrupted by headers or other
 * non-row entries: a run of consecutive rows forms one group.
 */
fun <T> groupPositionOf(items: List<T>, index: Int, isRow: (T) -> Boolean): GroupPosition {
    val opens = index == 0 || !isRow(items[index - 1])
    val closes = index == items.lastIndex || !isRow(items[index + 1])
    return when {
        opens && closes -> GroupPosition.Single
        opens -> GroupPosition.First
        closes -> GroupPosition.Last
        else -> GroupPosition.Middle
    }
}

/** A lazy list entry that knows where it sits in its group, so neighbours merge into one block. */
fun androidx.compose.foundation.lazy.LazyListScope.groupItem(
    position: GroupPosition,
    key: Any? = null,
    content: @Composable androidx.compose.foundation.lazy.LazyItemScope.() -> Unit,
) {
    item(key = key) {
        CompositionLocalProvider(LocalGroupPosition provides position) { content() }
    }
}

/**
 * One option in a set of choices, as an expressive toggle: a pill that squares off when chosen.
 * Takes the same arguments a filter chip does, so it drops in wherever one was used.
 */
@Composable
fun ChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        enabled = enabled,
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(8.dp))
        }
        label()
    }
}

/** Where Mihon drew a divider line, Yomi leaves breathing room: groups are told apart by space. */
@Composable
fun SoftGap(modifier: Modifier = Modifier, height: Dp = 12.dp) {
    Spacer(modifier.height(height))
}
