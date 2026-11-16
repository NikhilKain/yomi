package eu.kanade.presentation.more.stats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import eu.kanade.presentation.theme.NumeralFamily
import tachiyomi.presentation.core.components.yomi.ShapeBadge

/** A headline figure: a shaped icon, the number set large, and what it counts. */
@Composable
fun RowScope.StatsOverviewItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
) {
    StatTile(
        title = title,
        titleStyle = MaterialTheme.typography.headlineLarge,
        subtitle = subtitle,
        icon = icon,
        polygon = polygon,
    )
}

@Composable
fun RowScope.StatsItem(
    title: String,
    subtitle: String,
) {
    StatTile(
        title = title,
        titleStyle = MaterialTheme.typography.headlineSmall,
        subtitle = subtitle,
    )
}

@Composable
private fun RowScope.StatTile(
    title: String,
    titleStyle: TextStyle,
    subtitle: String,
    icon: ImageVector? = null,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            ShapeBadge(icon = icon, polygon = polygon, size = 40.dp)
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = title,
            fontFamily = NumeralFamily,
            style = titleStyle,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
