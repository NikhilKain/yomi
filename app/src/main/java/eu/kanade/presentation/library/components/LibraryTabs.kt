package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.category.visualName
import tachiyomi.domain.category.model.Category
import tachiyomi.presentation.core.components.yomi.SingleToggleShapes
import tachiyomi.presentation.core.components.yomi.YomiGutter

/** The shelf's categories as one connected run of toggle buttons, each with its size. */
@Composable
internal fun LibraryTabs(
    categories: List<Category>,
    pagerState: PagerState,
    getItemCountForCategory: (Category) -> Int?,
    onTabItemClick: (Int) -> Unit,
) {
    val current = pagerState.currentPage.coerceAtMost(categories.lastIndex)
    val row = rememberLazyListState()
    LaunchedEffect(current) { row.animateScrollToItem(current.coerceAtLeast(0)) }
    LazyRow(
        state = row,
        modifier = Modifier.padding(bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = YomiGutter),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        itemsIndexed(categories, key = { _, category -> category.id }) { index, category ->
            ToggleButton(
                checked = index == current,
                onCheckedChange = { onTabItemClick(index) },
                shapes = when {
                    categories.size == 1 -> SingleToggleShapes
                    index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    index == categories.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                contentPadding = PaddingValues(horizontal = 18.dp),
                modifier = Modifier.semantics { role = Role.Tab },
            ) {
                Text(category.visualName, maxLines = 1)
                getItemCountForCategory(category)?.let { count ->
                    Spacer(Modifier.width(6.dp))
                    Text("$count", color = LocalContentColor.current.copy(alpha = 0.7f), maxLines = 1)
                }
            }
        }
    }
}
