package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.UpIcon
import eu.kanade.presentation.components.YomiSearchField
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.yomi.ChoiceChip
import tachiyomi.presentation.core.components.yomi.ConnectedChoice
import tachiyomi.presentation.core.components.yomi.YomiGutter
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The search header: a round back button beside an always-open search pill, a wavy line showing
 * how many sources have answered, and the scope of the search as one connected choice.
 */
@Composable
fun GlobalSearchToolbar(
    searchQuery: String?,
    progress: Int,
    total: Int,
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    hideSourceFilter: Boolean,
    sourceFilter: SourceFilter,
    onChangeSearchFilter: (SourceFilter) -> Unit,
    onlyShowHasResults: Boolean,
    onToggleResults: () -> Unit,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 12.dp, end = YomiGutter, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = navigateUp, shapes = IconButtonDefaults.shapes()) {
                UpIcon()
            }
            Spacer(Modifier.width(10.dp))
            YomiSearchField(
                query = searchQuery.orEmpty(),
                onQueryChange = { onChangeSearchQuery(it) },
                placeholder = stringResource(MR.strings.action_search_hint),
                onSearch = onSearch,
                autoFocus = remember { searchQuery.isNullOrEmpty() },
                modifier = Modifier.weight(1f),
            )
        }

        if (progress in 1..<total) {
            LinearWavyProgressIndicator(
                progress = { progress / total.toFloat() },
                waveSpeed = 0.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = YomiGutter),
            )
        }

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = YomiGutter, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // TODO: make this UX better; it only applies when triggering a new search
            if (!hideSourceFilter) {
                val pinned = stringResource(MR.strings.pinned_sources)
                val all = stringResource(MR.strings.all)
                ConnectedChoice(
                    options = listOf(SourceFilter.PinnedOnly, SourceFilter.All),
                    selected = sourceFilter,
                    label = { if (it == SourceFilter.PinnedOnly) pinned else all },
                    onSelect = onChangeSearchFilter,
                    weighted = false,
                )
            }

            ChoiceChip(
                selected = onlyShowHasResults,
                onClick = onToggleResults,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                label = { Text(text = stringResource(MR.strings.has_results)) },
            )
        }
    }
}
