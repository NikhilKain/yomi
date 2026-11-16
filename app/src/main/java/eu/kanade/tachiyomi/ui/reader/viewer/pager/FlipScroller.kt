package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.Context
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Scroller
import tachiyomi.core.common.util.system.logcat

/**
 * Scroller that scales how long a page takes to settle, so the turn can be sped up or slowed down.
 *
 * The pager works out its own settle duration from drag distance and fling velocity, which is worth
 * keeping — a flick should still finish sooner than a slow drag. So this scales that number rather
 * than replacing it, and the setting reads as a speed multiplier instead of a fixed duration.
 */
class FlipScroller(context: Context) : Scroller(context, DecelerateInterpolator(DECELERATION)) {

    /** Percent of normal speed. 100 leaves the pager's own timing untouched; 200 is twice as fast. */
    var speedPercent: Int = DEFAULT_SPEED

    override fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int, duration: Int) {
        val scaled = if (speedPercent <= 0) duration else duration * DEFAULT_SPEED / speedPercent
        super.startScroll(startX, startY, dx, dy, scaled.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS))
    }

    companion object {
        const val DEFAULT_SPEED = 100
        const val MIN_SPEED = 25
        const val MAX_SPEED = 300

        private const val DECELERATION = 1.4f

        /** Floor and ceiling so neither extreme of the slider makes the reader feel broken. */
        private const val MIN_DURATION_MS = 60
        private const val MAX_DURATION_MS = 2_500

        /**
         * Swaps the pager's scroller for one whose duration can be scaled.
         *
         * The field is private with no setter, so it has to be reached reflectively. It is found by
         * type rather than by name because the release build is minified and `mScroller` will not
         * survive under that name — a field's type does survive, and there is exactly one
         * [Scroller] on a pager.
         *
         * Returns null if the field cannot be found, in which case the pager keeps its own scroller
         * and the speed setting simply does nothing. A missing animation preference is not worth
         * crashing a reader over.
         */
        fun install(pager: ViewGroup): FlipScroller? {
            return try {
                var cls: Class<*>? = pager.javaClass
                while (cls != null) {
                    val field = cls.declaredFields.firstOrNull { Scroller::class.java.isAssignableFrom(it.type) }
                    if (field != null) {
                        field.isAccessible = true
                        return FlipScroller(pager.context).also { field.set(pager, it) }
                    }
                    cls = cls.superclass
                }
                logcat { "No Scroller field on ${pager.javaClass.name}; flip speed unavailable" }
                null
            } catch (e: Exception) {
                logcat { "Could not install flip scroller: ${e.message}" }
                null
            }
        }
    }
}
