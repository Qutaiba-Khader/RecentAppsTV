package com.websnake.recentstv.logic

/* Layout arithmetic, computed instead of measured: no layout → composition
   feedback, so a focus move costs one draw-phase transform. All values in px. */

class StripLayout(val lefts: FloatArray, val widths: FloatArray, val total: Float) {
    fun center(i: Int): Float = if (i in lefts.indices) lefts[i] + widths[i] / 2f else total / 2f
}

object Geometry {

    /** Items laid left→right with [gap] between them and [pad] at both ends. */
    fun layout(widths: List<Float>, gap: Float, pad: Float): StripLayout =
        layout(widths, List((widths.size - 1).coerceAtLeast(0)) { gap }, pad)

    /** Items laid left→right; [gaps]`[i]` sits between item i and i+1 (the pinned group packs tighter). */
    fun layout(widths: List<Float>, gaps: List<Float>, pad: Float): StripLayout {
        val lefts = FloatArray(widths.size)
        var x = pad
        widths.forEachIndexed { i, w -> lefts[i] = x; x += w + if (i < widths.lastIndex) (gaps.getOrNull(i) ?: 0f) else 0f }
        return StripLayout(lefts, widths.toFloatArray(), x + pad)
    }

    /**
     * Gaps for a row whose items are flagged [grouped] (pinned): [inner] between two grouped
     * items, [gap] + [groupPad] where the group meets a neighbour (room for the group's frame),
     * [gap] elsewhere.
     */
    fun groupGaps(grouped: List<Boolean>, gap: Float, inner: Float, groupPad: Float): List<Float> =
        (0 until (grouped.size - 1).coerceAtLeast(0)).map { i ->
            val a = grouped[i]; val b = grouped[i + 1]
            when {
                a && b -> inner
                a || b -> gap + groupPad
                else -> gap
            }
        }

    /**
     * Strip translation that centres item [focus] in a [viewport]-wide window.
     * A strip that fits is centred as a whole; a longer one is clamped so neither
     * end (Kill All on the right) ever leaves the screen.
     */
    fun stripOffset(viewport: Float, strip: StripLayout, focus: Int): Float {
        if (viewport <= 0f) return 0f
        if (strip.total <= viewport) return (viewport - strip.total) / 2f
        return (viewport / 2f - strip.center(focus)).coerceIn(viewport - strip.total, 0f)
    }

    /**
     * Scroll needed to bring [itemTop, itemBottom] inside [viewTop + margin, viewBottom - margin].
     * 0 when already visible; negative scrolls up. An item taller than the window aligns its top.
     */
    fun revealDelta(itemTop: Float, itemBottom: Float, viewTop: Float, viewBottom: Float, margin: Float): Float {
        val top = viewTop + margin
        val bottom = viewBottom - margin
        return when {
            itemTop < top -> itemTop - top
            itemBottom > bottom -> minOf(itemBottom - bottom, itemTop - top)
            else -> 0f
        }
    }

    /** Pixel size an icon is rasterised at: the largest it is ever drawn (Large card, focused). */
    fun iconRasterPx(largeCardDp: Float, iconFraction: Float, focusScale: Float, density: Float): Int =
        kotlin.math.ceil(largeCardDp * iconFraction * focusScale * density).toInt().coerceIn(48, 512)
}
