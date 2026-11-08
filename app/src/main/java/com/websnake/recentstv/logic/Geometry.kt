package com.websnake.recentstv.logic

/* Layout arithmetic, computed instead of measured: no layout → composition
   feedback, so a focus move costs one draw-phase transform. All values in px. */

/** A centre crop: the source rectangle to keep, and the size to scale it to. */
data class Crop(val left: Int, val top: Int, val width: Int, val height: Int, val outW: Int, val outH: Int)

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
     * Strip translation that puts item [focus] in the middle of a [viewport]-wide window — always, at either end of
     * the row and in a short row too (1.8, owner: the focused app sits in the centre; empty space beside the row is fine).
     */
    fun stripOffset(viewport: Float, strip: StripLayout, focus: Int): Float {
        if (viewport <= 0f) return 0f
        return viewport / 2f - strip.center(focus)
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

    /**
     * Largest power-of-two sub-sample that still leaves the decoded picture at least
     * [tw]×[th] — so a huge photo is never decoded at full size.
     */
    fun sampleSize(w: Int, h: Int, tw: Int, th: Int): Int {
        var s = 1
        while (w / (s * 2) >= tw && h / (s * 2) >= th) s *= 2
        return s
    }

    /**
     * Centre crop of a [w]×[h] picture to the [tw]:[th] shape, scaled down to [tw]×[th]
     * (never up: a small picture stays small and is stretched only when drawn).
     */
    fun centerCrop(w: Int, h: Int, tw: Int, th: Int): Crop {
        val wide = w.toLong() * th > h.toLong() * tw          // wider than the screen: trim the sides
        val cw = if (wide) (h.toLong() * tw / th).toInt().coerceIn(1, w) else w
        val ch = if (wide) h else (w.toLong() * th / tw).toInt().coerceIn(1, h)
        val scale = minOf(1f, tw.toFloat() / cw)
        return Crop(
            (w - cw) / 2, (h - ch) / 2, cw, ch,
            (cw * scale).toInt().coerceAtLeast(1), (ch * scale).toInt().coerceAtLeast(1),
        )
    }
}
