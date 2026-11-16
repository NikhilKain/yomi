package tachiyomi.presentation.core.components.material

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tachiyomi.presentation.core.components.yomi.bouncyPress

/**
 * A choice shown as a tile: the icon above its name. The chosen tile fills with the accent and
 * tightens its corners, the expressive way of saying "this one".
 */
@Composable
fun IconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    imageVector: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val container by animateColorAsState(
        if (checked) scheme.primaryContainer else scheme.surfaceContainerHighest,
        label = "tile-container",
    )
    val content by animateColorAsState(
        if (checked) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        label = "tile-content",
    )
    val corner by animateDpAsState(if (checked) 14.dp else 24.dp, label = "tile-corner")
    Column(
        modifier = modifier
            .bouncyPress(interaction)
            .clip(RoundedCornerShape(corner))
            .background(container)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                role = Role.RadioButton,
                onValueChange = onCheckedChange,
            )
            .heightIn(min = 88.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Icon(imageVector = imageVector, contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
