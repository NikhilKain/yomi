package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.view.View
import androidx.viewpager.widget.ViewPager

/**
 * Turns a single leaf of the book, hinged on the spine, rather than sliding a whole opening.
 *
 * The sheet being turned belongs to two openings at once: its front is the current opening's second
 * page, its reverse is the next opening's first. So one swipe drives a 180 degree sweep in two
 * halves — up to vertical the front is still facing the reader, past vertical the reverse takes
 * over and comes down on top of the page that was lying opposite. Both openings are pinned against
 * the pager's own scroll for the duration, so nothing slides; only the leaf moves.
 *
 * Needs `reverseDrawingOrder = true`, so the opening that owns the leaf paints above the one being
 * uncovered.
 */
class BookFlipTransformer(private val isRtl: Boolean) : ViewPager.PageTransformer {

    override fun transformPage(view: View, position: Float) {
        if (view !is PagerSpreadHolder) {
            // Chapter transitions are not openings and have no leaf; let them slide as usual.
            view.translationX = 0f
            view.rotationY = 0f
            view.alpha = 1f
            return
        }

        prepare(view)

        // A right-to-left book reads towards *lower* pager indices, so the opening being left
        // behind drifts positive there and negative otherwise. Normalising first means the sweep
        // below is written once instead of twice with every sign inverted.
        val advance = if (isRtl) position else -position

        when {
            advance <= -1f || advance >= 1f -> {
                // Fully turned past, or not yet reached. Parking a neighbour anywhere inside the
                // viewport leaves it showing through whatever the top view does not paint.
                view.alpha = 0f
                view.translationX = 0f
            }

            advance >= 0f -> {
                // This opening owns the leaf. Sweep runs 0 -> 180 as the drag completes.
                view.alpha = 1f
                view.translationX = -view.width * position
                driveLeaf(view, degrees = advance * SWEEP)
            }

            else -> {
                // The opening being uncovered. It only has to sit still and wait: its first page is
                // currently being drawn as the reverse of the leaf above it, so showing its own
                // copy too would paint the same page twice at different angles.
                view.alpha = 1f
                view.translationX = -view.width * position
                view.staticHalf?.let { it.visibility = View.INVISIBLE }
                view.leafFront?.let {
                    it.visibility = View.VISIBLE
                    it.rotationY = 0f
                }
                view.leafBack?.visibility = View.INVISIBLE
            }
        }
    }

    /**
     * Places the leaf at [degrees] through its sweep.
     *
     * Before vertical only the front exists, after it only the reverse, which is what makes it read
     * as one sheet with two sides rather than two pages crossing over.
     */
    private fun driveLeaf(view: PagerSpreadHolder, degrees: Float) {
        val front = view.leafFront
        val back = view.leafBack
        val static = view.staticHalf

        // The hinge is the spine: the inner edge of whichever half the leaf occupies.
        val frontPivot = if (isRtl) (front?.width ?: 0).toFloat() else 0f
        val backPivot = if (isRtl) 0f else (back?.width ?: 0).toFloat()
        val direction = if (isRtl) 1f else -1f

        if (degrees <= HALF_SWEEP) {
            static?.visibility = View.VISIBLE
            front?.apply {
                visibility = View.VISIBLE
                pivotX = frontPivot
                pivotY = height * 0.5f
                rotationY = direction * degrees
            }
            back?.visibility = View.INVISIBLE
        } else {
            // Past vertical the reverse is what faces the reader, continuing the same sweep from
            // edge-on back down to flat on the opposite half.
            front?.visibility = View.INVISIBLE
            back?.apply {
                visibility = View.VISIBLE
                pivotX = backPivot
                pivotY = height * 0.5f
                rotationY = direction * -(SWEEP - degrees)
            }
            // The page underneath stays exactly where it is and is covered by the descending leaf,
            // which is added after it in the same parent and so already paints over it. Hiding it
            // here instead left the half blank for as long as the leaf was too edge-on to cover
            // anything.
            static?.visibility = View.VISIBLE
        }
    }

    private fun prepare(view: PagerSpreadHolder) {
        val distance = CAMERA_DISTANCE_DP * view.resources.displayMetrics.density
        view.leafFront?.cameraDistance = distance
        view.leafBack?.cameraDistance = distance
    }

    companion object {
        private const val SWEEP = 180f
        private const val HALF_SWEEP = 90f

        /**
         * The default camera sits close enough that a half-turned page balloons into an unreadable
         * wedge. Pushing it back flattens it to something nearer a page seen while reading.
         */
        private const val CAMERA_DISTANCE_DP = 12_000f

        /**
         * Undoes everything the sweep set, for when the animation is switched off while the reader
         * is open. A pager with no transformer simply stops calling it, so without this the last
         * half-turned leaf would keep its angle and its hidden halves would stay hidden.
         */
        fun clear(pager: android.view.ViewGroup) {
            for (i in 0 until pager.childCount) {
                val child = pager.getChildAt(i)
                child.alpha = 1f
                child.translationX = 0f
                child.rotationY = 0f
                if (child is PagerSpreadHolder) {
                    listOfNotNull(child.staticHalf, child.leafFront).forEach {
                        it.visibility = View.VISIBLE
                        it.rotationY = 0f
                    }
                    child.leafBack?.visibility = View.INVISIBLE
                }
            }
        }
    }
}
