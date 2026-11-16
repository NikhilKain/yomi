package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.PageSpread
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.viewer.calculateChapterGap
import eu.kanade.tachiyomi.util.system.createReaderThemeContext
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import tachiyomi.core.common.util.system.logcat

/**
 * Pager adapter used by this [viewer] to where [ViewerChapters] updates are posted.
 */
class PagerViewerAdapter(private val viewer: PagerViewer) : ViewPagerAdapter() {

    /**
     * List of currently set items.
     */
    var items: MutableList<Any> = mutableListOf()
        private set

    /**
     * Holds preprocessed items so they don't get removed when changing chapter
     */
    private var preprocessed: MutableMap<Int, InsertPage> = mutableMapOf()

    var nextTransition: ChapterTransition.Next? = null
        private set

    var currentChapter: ReaderChapter? = null

    /**
     * Context that has been wrapped to use the correct theme values based on the
     * current app theme and reader background color
     */
    private var readerThemedContext = viewer.activity.createReaderThemeContext()

    /**
     * Updates this adapter with the given [chapters]. It handles setting a few pages of the
     * next/previous chapter to allow seamless transitions and inverting the pages if the viewer
     * has R2L direction.
     */
    fun setChapters(chapters: ViewerChapters, forceTransition: Boolean) {
        val newItems = mutableListOf<Any>()

        // Forces chapter transition if there is missing chapters
        val prevHasMissingChapters = calculateChapterGap(chapters.currChapter, chapters.prevChapter) > 0
        val nextHasMissingChapters = calculateChapterGap(chapters.nextChapter, chapters.currChapter) > 0

        // Add previous chapter pages and transition
        chapters.prevChapter?.pages?.let(newItems::addAll)

        // Skip transition page if the chapter is loaded & current page is not a transition page
        if (prevHasMissingChapters || forceTransition || chapters.prevChapter?.state !is ReaderChapter.State.Loaded) {
            newItems.add(ChapterTransition.Prev(chapters.currChapter, chapters.prevChapter))
        }

        var insertPageLastPage: InsertPage? = null

        // Add current chapter.
        val currPages = chapters.currChapter.pages
        if (currPages != null) {
            val pages = currPages.toMutableList()

            val lastPage = pages.last()

            // Insert preprocessed pages into current page list
            preprocessed.keys.sortedDescending()
                .forEach { key ->
                    if (lastPage.index == key) {
                        insertPageLastPage = preprocessed[key]
                    }
                    preprocessed[key]?.let { pages.add(key + 1, it) }
                }

            newItems.addAll(pages)
        }

        currentChapter = chapters.currChapter

        // Add next chapter transition and pages.
        nextTransition = ChapterTransition.Next(chapters.currChapter, chapters.nextChapter)
            .also {
                if (
                    nextHasMissingChapters ||
                    forceTransition ||
                    chapters.nextChapter?.state !is ReaderChapter.State.Loaded
                ) {
                    newItems.add(it)
                }
            }

        chapters.nextChapter?.pages?.let(newItems::addAll)

        // Resets double-page splits, else insert pages get misplaced
        items.filterIsInstance<InsertPage>().also { items.removeAll(it) }

        // Group before the reversal, so pairing follows reading order rather than screen order.
        val grouped = if (viewer.isBookMode) groupIntoSpreads(newItems) else newItems

        if (viewer is R2LPagerViewer) {
            grouped.reverse()
        }

        preprocessed = mutableMapOf()
        items = grouped
        notifyDataSetChanged()

        // Will skip insert page otherwise
        if (insertPageLastPage != null) {
            viewer.moveToPage(insertPageLastPage)
        }
    }

    /**
     * Pairs consecutive pages into facing spreads, leaving everything else untouched.
     *
     * Pairing restarts at each chapter, and never runs across a chapter transition, because a
     * spread made of the last page of one chapter and the first of the next is not a spread — it is
     * two unrelated drawings sharing a screen, and it would knock every following pair out of step.
     */
    private fun groupIntoSpreads(source: List<Any>): MutableList<Any> {
        val result = mutableListOf<Any>()
        val run = mutableListOf<ReaderPage>()

        fun flushRun() {
            if (run.isEmpty()) return

            var index = 0
            if (viewer.config.bookModeCoverAlone) {
                // A closed book shows its cover on the turnable side with nothing facing it, so the
                // cover goes in the second slot. Putting it first would leave the opening with no
                // leaf to turn and knock every later pair out of step.
                result.add(PageSpread(null, run[0]))
                index = 1
            }
            while (index < run.size) {
                result.add(PageSpread(run[index], run.getOrNull(index + 1)))
                index += 2
            }
            run.clear()
        }

        source.forEach { item ->
            if (item is ReaderPage && (run.isEmpty() || run.last().chapter == item.chapter)) {
                run.add(item)
            } else {
                flushRun()
                if (item is ReaderPage) run.add(item) else result.add(item)
            }
        }
        flushRun()

        return result
    }

    /**
     * Returns the amount of items of the adapter.
     */
    override fun getCount(): Int {
        return items.size
    }

    /**
     * Position of the item holding [page], whether that is the page itself or the spread it is
     * paired into.
     */
    fun positionOf(page: ReaderPage): Int = items.indexOfFirst {
        it === page || (it is PageSpread && page in it)
    }

    /**
     * The page printed on the reverse of the leaf that [position]'s opening is turned by, which is
     * the first page of the opening after it in reading order.
     *
     * Reading order is not list order in a right-to-left book: the items are reversed so that the
     * pager's own idea of "forward" runs the other way, which puts the next opening one index
     * *back*. Null at a chapter boundary, where the leaf has nothing meaningful on its reverse.
     */
    private fun nextLeafPage(position: Int): ReaderPage? {
        val step = if (viewer is R2LPagerViewer) -1 else 1
        return (items.getOrNull(position + step) as? PageSpread)?.first
    }

    /** Every page on screen at [position], in reading order. */
    fun pagesAt(position: Int): List<ReaderPage> = when (val item = items.getOrNull(position)) {
        is PageSpread -> item.pages
        is ReaderPage -> listOf(item)
        else -> emptyList()
    }

    /**
     * Creates a new view for the item at the given [position].
     */
    override fun createView(container: ViewGroup, position: Int): View {
        return when (val item = items[position]) {
            is ReaderPage -> PagerPageHolder(readerThemedContext, viewer, item)
            is PageSpread -> PagerSpreadHolder(readerThemedContext, viewer, item, nextLeafPage(position))
            is ChapterTransition -> PagerTransitionHolder(readerThemedContext, viewer, item)
            else -> throw NotImplementedError("Holder for ${item.javaClass} not implemented")
        }
    }

    /**
     * Returns the current position of the given [view] on the adapter.
     */
    override fun getItemPosition(view: Any): Int {
        if (view is PositionableView) {
            val position = items.indexOf(view.item)
            if (position != -1) {
                return position
            } else {
                logcat { "Position for ${view.item} not found" }
            }
        }
        return POSITION_NONE
    }

    fun onPageSplit(currentPage: Any?, newPage: InsertPage) {
        // Splitting a wide page in two and pairing pages into spreads are opposite operations on
        // the same list. Letting a split fire while spreads are built would insert a bare page into
        // a list of spreads, which desynchronises every index after it.
        if (viewer.isBookMode) return
        if (currentPage !is ReaderPage) return

        val currentIndex = items.indexOf(currentPage)

        // Put aside preprocessed pages for next chapter so they don't get removed when changing chapter
        if (currentPage.chapter.chapter.id != currentChapter?.chapter?.id) {
            preprocessed[newPage.index] = newPage
            return
        }

        val placeAtIndex = when (viewer) {
            is L2RPagerViewer,
            is VerticalPagerViewer,
            -> currentIndex + 1
            else -> currentIndex
        }

        // It will enter a endless cycle of insert pages
        if (viewer is R2LPagerViewer && placeAtIndex - 1 >= 0 && items[placeAtIndex - 1] is InsertPage) {
            return
        }

        // Same here it will enter a endless cycle of insert pages
        if (items[placeAtIndex] is InsertPage) {
            return
        }

        items.add(placeAtIndex, newPage)

        notifyDataSetChanged()
    }

    fun cleanupPageSplit() {
        val insertPages = items.filterIsInstance<InsertPage>()
        items.removeAll(insertPages)
        notifyDataSetChanged()
    }

    fun refresh() {
        readerThemedContext = viewer.activity.createReaderThemeContext()
    }
}
