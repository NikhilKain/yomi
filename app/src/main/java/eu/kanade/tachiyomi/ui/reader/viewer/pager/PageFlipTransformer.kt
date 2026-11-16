package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.ViewPager

/**
 * Turns pages on a hinge instead of sliding them, so a swipe reads as lifting a leaf rather than
 * scrolling a filmstrip.
 *
 * The trick is that neither page moves horizontally. Both are pinned against the pager's own scroll
 * with an equal and opposite translation, which leaves the outgoing page rotating away on its spine
 * edge while the incoming one lies flat underneath and is uncovered as that happens. Letting the
 * incoming page slide in as usual would read as two unrelated animations at once.
 *
 * Drawing order matters as much as the transform: the leaf being turned has to be painted above the
 * one beneath it, which is why this is installed with `reverseDrawingOrder = true`.
 *
 * Horizontal pagers only. A vertical pager already owns its transformer — that is how it scrolls
 * vertically at all — so replacing it there would break paging rather than decorate it.
 */
class PageFlipTransformer(private val isRtl: Boolean = false) : ViewPager.PageTransformer {

    override fun transformPage(view: View, position: Float) {
        // The default camera sits close enough to the view that a near-90 degree turn on a
        // full-screen page balloons into an unreadable wedge. Pushing it back flattens the
        // perspective to something closer to a page seen from normal reading distance.
        view.cameraDistance = CAMERA_DISTANCE_DP * view.resources.displayMetrics.density

        // A manga's items are reversed so reading forward walks down the pager, which sends the
        // page being left behind positive instead of negative. Normalising first means the turn is
        // written once rather than twice with every sign inverted.
        val advance = if (isRtl) position else -position

        when {
            // At rest, a neighbour must be genuinely gone rather than parked under the current
            // page. Pinning it at any offset leaves it sitting in the viewport, where it shows
            // through anything the top page does not paint — a blank half of a cover spread, or
            // the bands beside a page that does not fill its slot.
            advance <= -1f || advance >= 1f -> {
                view.alpha = 0f
                view.translationX = 0f
                view.rotationY = 0f
            }

            advance >= 0f -> {
                // The page being turned. It holds its place and swings away on its hinge, which is
                // the edge it departs towards: the inner edge in either reading direction.
                view.alpha = 1f
                view.translationX = -view.width * position
                view.pivotX = if (isRtl) view.width.toFloat() else 0f
                view.pivotY = view.height * 0.5f
                view.rotationY = (if (isRtl) 1f else -1f) * MAX_ANGLE * advance
            }

            else -> {
                // The page underneath: flat and unrotated, waiting to be uncovered.
                view.alpha = 1f
                view.translationX = -view.width * position
                view.rotationY = 0f
            }
        }
    }

    companion object {
        /**
         * Stops a page short of edge-on. At a full 90 degrees it collapses to a zero-width line for
         * a frame, which flickers.
         */
        private const val MAX_ANGLE = 88f

        private const val CAMERA_DISTANCE_DP = 12_000f

        /**
         * Undoes everything [transformPage] set, for when the animation is switched off while the
         * reader is open. A pager with no transformer simply stops calling it, so without this the
         * last partially turned page would keep its rotation for good.
         */
        fun clear(pager: ViewGroup) {
            for (i in 0 until pager.childCount) {
                pager.getChildAt(i).apply {
                    alpha = 1f
                    translationX = 0f
                    rotationY = 0f
                    pivotX = width * 0.5f
                    pivotY = height * 0.5f
                }
            }
        }
    }
}
