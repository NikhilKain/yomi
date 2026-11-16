package eu.kanade.tachiyomi.ui.reader.model

/**
 * One opening of a book: the two pages visible at rest, and the leaf that joins this opening to the
 * next.
 *
 * A leaf is a physical sheet with a page printed on each side. [first] is the page lying flat on
 * the far side of the spine, [second] is the front of the leaf about to be turned. Turning it
 * carries the *next* opening's [first] — the back of that same sheet — across the spine.
 *
 * Both are in reading order regardless of reading direction; which physical side each occupies is
 * the holder's problem. [first] is null for the very first opening when the cover stands alone,
 * because a closed book has nothing facing its cover.
 */
data class PageSpread(
    val first: ReaderPage?,
    val second: ReaderPage?,
) {

    /**
     * The page this opening reports as the reader's position: the furthest one, since both are
     * equally on screen and treating the nearer as "where you are" would leave the last page of an
     * odd chapter never marked as reached.
     */
    val progressPage: ReaderPage
        get() = second ?: first!!

    operator fun contains(page: ReaderPage): Boolean = first == page || second == page

    val pages: List<ReaderPage>
        get() = listOfNotNull(first, second)

    init {
        require(first != null || second != null) { "A spread must hold at least one page" }
    }
}
