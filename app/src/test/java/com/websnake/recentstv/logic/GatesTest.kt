package com.websnake.recentstv.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Critical gates (docs/FLOW-MAP.md): per atomic condition, one input pair where only
 * that condition changes and the outcome changes. docs/flow-map/mutants.json flips each.
 */
class GatesTest {

    private val a = TaskRef(11, "app.a")
    private val b = TaskRef(12, "app.b")
    private fun ready(vararg t: TaskRef, pins: List<String> = emptyList()) = Overlay.loaded(OvState(), t.toList(), pins, true, false)
    private fun press(s: OvState, btn: Btn, repeat: Int = 0) = Overlay.key(s, KeyIn(btn, true, repeat))
    private fun release(s: OvState, btn: Btn) = Overlay.key(s, KeyIn(btn, false))
    /** OK down, then the HOLD_MS timer fires with it still down. */
    private fun hold(s: OvState) = Overlay.holdElapsed(press(s, Btn.OK).first)

    // ── G-OK: who handles OK (tap on release / hold for the menu) ──
    @Test fun `G-OK-scene only the overlay scene takes the tap-hold path`() {
        assertEquals(listOf(Effect.OpenSettings), press(ready(), Btn.OK).second)        // Empty: acts on press
        assertEquals(listOf(Effect.ArmHold), press(ready(a), Btn.OK).second)            // overlay: arms the hold, opens on release
    }

    @Test fun `G-OK-btn other keys never enter the OK path`() {
        assertEquals(3, press(ready(a, b), Btn.LEFT).first.focus)
    }

    @Test fun `G-OK-busy a hold while switching opens no menu`() {
        assertNull(hold(ready(a).copy(busy = true)).ctxPkg)
        assertEquals("app.a", hold(ready(a)).ctxPkg)
    }

    @Test fun `G-OK-killing a hold during Kill All opens no menu`() {
        assertNull(hold(ready(a).copy(killing = true)).ctxPkg)
    }

    @Test fun `G-OK-flying a hold during a fly-off opens no menu`() {
        assertNull(hold(ready(a, b).copy(flying = "app.b")).ctxPkg)
    }

    // ── G-HOLD: when a held OK opens the context menu ──
    @Test fun `G-HOLD-repeat key repeats of a held OK never open the menu - only the timer does`() {
        val r = press(press(ready(a), Btn.OK).first, Btn.OK, 1)
        assertNull(r.first.ctxPkg); assertTrue(r.second.isEmpty())             // a repeat must not restart the timer
        assertEquals(listOf(Effect.ArmHold), press(ready(a), Btn.OK).second)
    }

    // the TV bug (1.3): the remote repeats OK every ~50 ms; a repeat ran the first menu row (Pin) at once
    @Test fun `G-HOLD-held a key repeat of OK in the menu runs no row, a new press does`() {
        val m = release(hold(ready(a, b)), Btn.OK).first
        assertTrue(press(m, Btn.OK, 3).second.isEmpty())
        assertEquals(listOf(Effect.SetPin("app.a", true)), press(m, Btn.OK, 0).second)
    }

    @Test fun `G-HOLD-same the OK that opened the menu runs no row until it is released`() {
        val open = hold(ready(a, b))
        assertTrue(press(open, Btn.OK, 0).second.isEmpty())
        assertEquals(listOf(Effect.SetPin("app.a", true)), press(release(open, Btn.OK).first, Btn.OK, 0).second)
    }

    @Test fun `G-HOLD-pressing the timer opens the menu only while OK is still down`() {
        val up = release(press(ready(a), Btn.OK).first, Btn.OK).first.copy(busy = false, selecting = null)
        assertNull(Overlay.holdElapsed(up).ctxPkg)
        assertEquals("app.a", hold(ready(a)).ctxPkg)
    }

    @Test fun `G-HOLD-card a hold on Kill All or the gear opens nothing, on a task it does`() {
        assertNull(Overlay.holdElapsed(ready(a).copy(focus = 1, pressing = true)).ctxPkg)
        assertNull(Overlay.holdElapsed(ready(a).copy(focus = 0, pressing = true)).ctxPkg)
        assertEquals("app.a", hold(ready(a)).ctxPkg)
    }

    @Test fun `G-HOLD-pin a hold on a pin opens its menu`() {
        assertEquals("p.x", hold(ready(pins = listOf("p.x"))).ctxPkg)
    }

    @Test fun `G-HOLD-open a timer while a menu is already open changes nothing`() {
        val m = press(release(hold(ready(a, b)), Btn.OK).first, Btn.DOWN).first.copy(pressing = true)
        assertEquals(m, Overlay.holdElapsed(m))
    }

    @Test fun `G-HOLD-gone a timer after the overlay started closing opens nothing`() {
        val p = press(ready(a), Btn.OK).first
        assertNull(Overlay.holdElapsed(p.copy(closing = true)).ctxPkg)
        assertEquals("app.a", Overlay.holdElapsed(p).ctxPkg)
    }

    @Test fun `G-HOLD-chosen a timer after an app was chosen opens nothing`() {
        val p = press(ready(a), Btn.OK).first
        assertNull(Overlay.holdElapsed(p.copy(selecting = "app.a")).ctxPkg)
        assertEquals("app.a", Overlay.holdElapsed(p).ctxPkg)
    }

    // ── the ⚙ tile (1.4): the visible way into Settings ──
    @Test fun `G-ROW-gear the gear tile is always first in the row`() {
        assertEquals(Item.Settings, ready(a).items.first())
        assertEquals(Item.Settings, ready(pins = listOf("p.x")).items.first())
    }

    @Test fun `G-OK-gear OK on the gear opens Settings, on a task it opens the app`() {
        assertEquals(listOf(Effect.OpenSettings), release(ready(a).copy(focus = 0), Btn.OK).second)
        assertEquals(listOf(Effect.Switch("app.a", 11)), release(ready(a), Btn.OK).second)
    }

    // ── G-INPUT: the overlay ignores input while it is changing ──
    @Test fun `G-INPUT-busy no focus move while switching`() {
        assertEquals(2, press(ready(a, b).copy(busy = true), Btn.LEFT).first.focus)
    }

    @Test fun `G-INPUT-killing no peek during Kill All`() {
        assertEquals(PEEK_NONE, press(ready(a, b).copy(killing = true), Btn.DOWN).first.peek)
    }

    @Test fun `G-INPUT-flying no peek during a fly-off`() {
        assertEquals(PEEK_NONE, press(ready(a, b).copy(flying = "app.b"), Btn.UP).first.peek)
        assertEquals(PEEK_UP, press(ready(a, b), Btn.UP).first.peek)
    }

    @Test fun `G-INPUT-selecting the select zoom swallows every key`() {
        val s = ready(a, b).copy(selecting = "app.a")
        assertEquals(s, press(s, Btn.LEFT).first)
        assertEquals(3, press(ready(a, b), Btn.LEFT).first.focus)
    }

    // ── G-DISMISS: only a running task can be swiped away ──
    @Test fun `G-DISMISS-task a pin never peeks, a task does`() {
        assertEquals(PEEK_NONE, press(ready(a, pins = listOf("p.x")).copy(focus = 3), Btn.DOWN).first.peek)
        assertEquals(PEEK_DOWN, press(ready(a, pins = listOf("p.x")), Btn.DOWN).first.peek)
    }

    @Test fun `G-DISMISS-same only the same direction twice flies the card`() {
        assertNull(press(press(ready(a), Btn.UP).first, Btn.DOWN).first.flying)
        assertEquals("app.a", press(press(ready(a), Btn.DOWN).first, Btn.DOWN).first.flying)
    }

    // ── G-ROW: who sits in the row ──
    @Test fun `G-ROW-pinned a pinned running app leaves the tasks`() {
        assertEquals(listOf(b), ready(a, b, pins = listOf("app.a")).tasks)
    }

    @Test fun `G-ROW-killall Kill All needs the setting and at least one task`() {
        assertFalse(Overlay.loaded(OvState(), listOf(a), emptyList(), false, false).hasKillAll)
        assertFalse(ready(pins = listOf("p.x")).hasKillAll)
        assertTrue(ready(a).hasKillAll)
    }

    @Test fun `G-ROW-empty Empty only when there are neither tasks nor pins`() {
        assertEquals(Scene.OVERLAY, ready(pins = listOf("p.x")).scene)
        assertEquals(Scene.OVERLAY, ready(a).scene)
        assertEquals(Scene.EMPTY, ready().scene)
    }

    // ── G-SELECT: which tasks the user sees ──
    @Test fun `G-SELECT-id a task without a valid id is dropped`() {
        assertEquals(emptyList<TaskRef>(), TaskList.select(listOf(TaskRef(0, "x")), { false }))
    }

    @Test fun `G-SELECT-blank a task without a package is dropped`() {
        assertEquals(emptyList<TaskRef>(), TaskList.select(listOf(TaskRef(4, " ")), { false }))
    }

    @Test fun `G-SELECT-seen a package shows once`() {
        assertEquals(1, TaskList.select(listOf(TaskRef(1, "p"), TaskRef(2, "p")), { false }).size)
    }

    @Test fun `G-SELECT-excluded an excluded package is hidden`() {
        assertEquals(emptyList<TaskRef>(), TaskList.select(listOf(TaskRef(1, "p")), { it == "p" }))
    }

    // ── G-READ: which source is trusted ──
    @Test fun `G-READ-null a failed binder read falls through`() {
        assertEquals(TaskSource.STACK_LIST, TaskList.read({ null }, { "taskId=1: a.b/.C\ntaskId=2: c.d/.E" }, { "" }).second)
    }

    @Test fun `G-READ-size a one-task binder list is not enough`() {
        assertEquals(TaskSource.STACK_LIST, TaskList.read({ listOf(TaskRef(9, "home")) }, { "taskId=1: a.b/.C\ntaskId=2: c.d/.E" }, { "" }).second)
    }

    // ── G-CLAMP: the row stays on screen ──
    @Test fun `G-CLAMP-fits a strip that fits is centred whole, whatever the focus`() {
        val l = Geometry.layout(listOf(100f, 100f), 20f, 30f)
        assertEquals(Geometry.stripOffset(1000f, l, 0), Geometry.stripOffset(1000f, l, 1), 0.001f)
    }

    @Test fun `G-CLAMP-ends the long strip is pinned at both ends`() {
        val l = Geometry.layout(List(20) { 100f }, 20f, 30f)
        assertTrue(Geometry.stripOffset(1000f, l, 19) >= 1000f - l.total - 0.001f)
        assertTrue(Geometry.stripOffset(1000f, l, 0) <= 0.001f)
    }

    // the release that ends a hold must not open the app
    @Test fun `G-HOLD-release the release after a hold opens nothing`() {
        val held = hold(ready(a))
        assertTrue(release(held, Btn.OK).second.isEmpty())
    }
}
