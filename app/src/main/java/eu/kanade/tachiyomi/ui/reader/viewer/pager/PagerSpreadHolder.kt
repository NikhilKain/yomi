package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.ui.reader.model.PageSpread
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.widget.ViewPagerAdapter

/**
 * One opening of the book: two pages side by side, plus the hidden reverse of the leaf between this
 * opening and the next.
 *
 * Laid out by hand rather than with a LinearLayout because the three slots are not a row. Two of
 * them share the same half of the screen: [leafBack] sits directly on top of [staticHalf] and is
 * uncovered as the leaf swings past vertical. That overlap is the whole point — a turning page has
 * to pass *over* the page it is covering, which is only possible while both live in the same view,
 * since the pager decides draw order between its children and knows nothing about halves.
 */
@SuppressLint("ViewConstructor")
class PagerSpreadHolder(
    readerThemedContext: Context,
    val viewer: PagerViewer,
    val spread: PageSpread,
    nextLeafPage: ReaderPage?,
) : ViewGroup(readerThemedContext), ViewPagerAdapter.PositionableView {

    override val item get() = spread

    /** In a right-to-left book the leaf is on the left and swings the other way. */
    private val isRtl = viewer is R2LPagerViewer

    /** The page lying flat on the far side of the spine. Never moves. */
    val staticHalf: View?

    /** The front of the leaf: the page the reader is about to turn. */
    val leafFront: View?

    /**
     * The reverse of that same leaf, which is the next opening's first page. Hidden until the leaf
     * passes vertical, then swept down over [staticHalf].
     */
    val leafBack: View?

    private val pageHolders: List<PagerPageHolder>

    init {
        // Each page is opaque, but the opening as a whole is not. Backing the whole view instead
        // would paint over the opening underneath, which is the one thing that must show through
        // once the leaf lifts off it.
        val paper = TypedValue().let { value ->
            readerThemedContext.theme.resolveAttribute(android.R.attr.colorBackground, value, true)
            value.data
        }

        val holders = mutableListOf<PagerPageHolder>()
        fun holderFor(page: ReaderPage?): View? = page?.let {
            PagerPageHolder(readerThemedContext, viewer, it)
                .also { holder -> holder.setBackgroundColor(paper) }
                .also(holders::add)
        }

        staticHalf = holderFor(spread.first)
        leafFront = holderFor(spread.second)
        leafBack = holderFor(nextLeafPage)
        pageHolders = holders

        // Added in painting order: the static page first, the leaf's reverse above it, and the
        // leaf's front last so it covers its own half until it is turned away.
        staticHalf?.let(::addView)
        leafBack?.let {
            addView(it)
            it.visibility = INVISIBLE
        }
        leafFront?.let(::addView)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        val halfSpec = MeasureSpec.makeMeasureSpec(width / 2, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        children().forEach { it.measure(halfSpec, heightSpec) }

        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val half = (r - l) / 2
        val height = b - t

        // The leaf occupies the side the reader turns from: the right in a left-to-right book, the
        // left in a manga. Its reverse lands on the opposite side, which is where the static page
        // already is.
        val leafOnRight = !isRtl
        val leafLeft = if (leafOnRight) half else 0
        val staticLeft = if (leafOnRight) 0 else half

        staticHalf?.layout(staticLeft, 0, staticLeft + half, height)
        leafBack?.layout(staticLeft, 0, staticLeft + half, height)
        leafFront?.layout(leafLeft, 0, leafLeft + half, height)
    }

    private fun children(): Sequence<View> = sequence {
        for (i in 0 until childCount) yield(getChildAt(i))
    }

    fun holderFor(page: ReaderPage): PagerPageHolder? = pageHolders.firstOrNull { it.page == page }

    fun onPageSelected(forward: Boolean) {
        // Only the pages actually part of this opening; the leaf's reverse belongs to the next one
        // and will be told when the reader gets there.
        pageHolders.filter { it.page in spread }.forEach { it.onPageSelected(forward) }
    }
}
