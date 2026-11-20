package eu.kanade.tachiyomi.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.home.HomeHubScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.stats.StatsScreen
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/** Yomi's first tab: the reading hub. Updates and History open from here as full screens. */
data object HomeHubTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val selected = LocalTabNavigator.current.current.key == key
            return TabOptions(
                index = 0u,
                title = stringResource(MR.strings.yomi_home),
                icon = rememberVectorPainter(if (selected) Icons.Rounded.Home else Icons.Outlined.Home),
            )
        }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<HomeHubViewModel>()
        val state by viewModel.state.collectAsState()
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(state.loading) {
            if (!state.loading) (context as? MainActivity)?.ready = true
        }

        fun read(chapter: Chapter?) {
            if (chapter != null) {
                context.startActivity(ReaderActivity.newIntent(context, chapter.mangaId, chapter.id))
            } else {
                scope.launch { snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter)) }
            }
        }

        HomeHubScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onOpenManga = { navigator.push(MangaScreen(it)) },
            onRead = { id -> scope.launch { read(viewModel.nextChapter(id)) } },
            onResumeHistory = { entry ->
                scope.launch { read(viewModel.nextChapter(entry.mangaId, entry.chapterId)) }
            },
            onSeeUpdates = { navigator.push(UpdatesTab) },
            onSeeHistory = { navigator.push(HistoryTab) },
            onSeeLibrary = { scope.launch { HomeScreen.openTab(HomeScreen.Tab.Library()) } },
            onDiscover = { scope.launch { HomeScreen.openTab(HomeScreen.Tab.Browse()) } },
            onSearch = { navigator.push(GlobalSearchScreen()) },
            onRefresh = {
                val started = LibraryUpdateJob.startNow(context)
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.stringResource(
                            if (started) MR.strings.updating_library else MR.strings.update_already_running,
                        ),
                    )
                }
            },
            onDownloads = { navigator.push(DownloadQueueScreen) },
            onStats = { navigator.push(StatsScreen()) },
        )
    }
}
