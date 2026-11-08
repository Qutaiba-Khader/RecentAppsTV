package com.websnake.recentstv.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/** Area GEO — carousel positions, centring/clamp, keep-in-view, icon size (docs/flow-map/geometry.md). */
class GeometryTest {

    private val eps = 0.001f

    @Test fun `P-GEO-01 layout places items with gap and end padding`() {
        val l = Geometry.layout(listOf(100f, 100f, 134f), gap = 20f, pad = 30f)
        assertEquals(30f, l.lefts[0], eps); assertEquals(150f, l.lefts[1], eps); assertEquals(270f, l.lefts[2], eps)
        assertEquals(30f + 334f + 40f + 30f, l.total, eps)
        assertEquals(337f, l.center(2), eps)
    }

    @Test fun `P-GEO-02 empty layout is just padding - out-of-range centre is the middle`() {
        val l = Geometry.layout(emptyList(), 20f, 30f)
        assertEquals(60f, l.total, eps); assertEquals(30f, l.center(3), eps)
    }

    @Test fun `P-GEO-03 a strip that fits is centred as a whole`() {
        val l = Geometry.layout(listOf(100f, 100f), 20f, 30f)       // total 280
        assertEquals((1000f - 280f) / 2f, Geometry.stripOffset(1000f, l, 0), eps)
    }

    @Test fun `P-GEO-04 a long strip centres the focused item`() {
        val l = Geometry.layout(List(20) { 100f }, 20f, 30f)        // total 2440
        assertEquals(500f - l.center(10), Geometry.stripOffset(1000f, l, 10), eps)
    }

    @Test fun `P-GEO-05 clamp - Kill All on the right never leaves the screen, nor the oldest on the left`() {
        val l = Geometry.layout(List(20) { 100f }, 20f, 30f)
        assertEquals(1000f - l.total, Geometry.stripOffset(1000f, l, 19), eps)    // right end pinned
        assertEquals(0f, Geometry.stripOffset(1000f, l, 0), eps)                  // left end pinned
    }

    @Test fun `P-GEO-06 no viewport yet means no offset`() {
        assertEquals(0f, Geometry.stripOffset(0f, Geometry.layout(listOf(100f), 20f, 30f), 0), eps)
    }

    @Test fun `P-GEO-07 revealDelta - visible needs nothing, above scrolls up, below scrolls down`() {
        assertEquals(0f, Geometry.revealDelta(100f, 200f, 0f, 500f, 20f), eps)
        assertEquals(-30f, Geometry.revealDelta(-10f, 90f, 0f, 500f, 20f), eps)
        assertEquals(30f, Geometry.revealDelta(400f, 510f, 0f, 500f, 20f), eps)
    }

    @Test fun `P-GEO-08 revealDelta - a row taller than the window aligns its top`() {
        assertEquals(80f, Geometry.revealDelta(100f, 900f, 0f, 500f, 20f), eps)
    }

    @Test fun `P-GEO-09 icon raster size covers the largest drawn size, bounded`() {
        assertEquals(198, Geometry.iconRasterPx(115f, 0.66f, 1.3f, 2.0f))
        assertEquals(48, Geometry.iconRasterPx(10f, 0.5f, 1f, 1f))
        assertEquals(512, Geometry.iconRasterPx(500f, 1f, 2f, 4f))
    }

    @Test fun `P-GEO-10 a negative index also falls back to the middle`() {
        val l = Geometry.layout(listOf(100f), 20f, 30f)
        assertEquals(l.total / 2f, l.center(-1), eps)
    }

    @Test fun `P-GEO-11 per-gap layout - the pinned group packs tighter inside, wider at its edges`() {
        val g = Geometry.groupGaps(listOf(true, true, false, false), gap = 20f, inner = 16f, groupPad = 17f)
        assertEquals(listOf(16f, 37f, 20f), g)
        val l = Geometry.layout(listOf(50f, 50f, 100f, 100f), g, 10f)
        assertEquals(10f, l.lefts[0], eps); assertEquals(76f, l.lefts[1], eps); assertEquals(163f, l.lefts[2], eps); assertEquals(283f, l.lefts[3], eps)
        assertEquals(393f, l.total, eps)
        assertEquals(listOf(37f), Geometry.groupGaps(listOf(false, true), 20f, 16f, 17f))
        assertEquals(emptyList<Float>(), Geometry.groupGaps(emptyList(), 20f, 16f, 17f))
    }

    @Test fun `P-GEO-12 a gap list shorter than the items counts the missing gaps as 0`() {
        val l = Geometry.layout(listOf(10f, 10f, 10f), listOf(5f), 0f)
        assertEquals(15f, l.lefts[1], eps); assertEquals(25f, l.lefts[2], eps)
    }

    @Test fun `P-GEO-13 sample size - a huge photo is decoded small but never below the screen`() {
        assertEquals(4, Geometry.sampleSize(8000, 4500, 1920, 1080))
        assertEquals(1, Geometry.sampleSize(1920, 1080, 1920, 1080))
        assertEquals(1, Geometry.sampleSize(400, 300, 1920, 1080))
        assertEquals(2, Geometry.sampleSize(4000, 2200, 1920, 1080))      // the shorter side decides
        assertEquals(1, Geometry.sampleSize(8000, 1500, 1920, 1080))      // a panorama: its height decides
    }

    @Test fun `P-GEO-14 centre crop - wide trims the sides, tall trims top and bottom, both scaled to the screen`() {
        assertEquals(Crop(500, 0, 2000, 1125, 1920, 1080), Geometry.centerCrop(3000, 1125, 1920, 1080))
        val tall = Geometry.centerCrop(1080, 1920, 1920, 1080)          // a portrait photo
        assertEquals(0, tall.left); assertEquals(1080, tall.width); assertEquals(607, tall.height); assertEquals(656, tall.top)
        assertEquals(1080, tall.outW)                                   // smaller than the screen: not scaled up
        assertEquals(Crop(0, 0, 1920, 1080, 1920, 1080), Geometry.centerCrop(1920, 1080, 1920, 1080))
    }
}
