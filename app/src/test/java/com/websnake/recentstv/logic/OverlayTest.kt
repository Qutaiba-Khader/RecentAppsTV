package com.websnake.recentstv.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Area OVL — the overlay focus engine and key handling (docs/flow-map/overlay.md). */
class OverlayTest {

    private val a = TaskRef(11, "app.a")
    private val b = TaskRef(12, "app.b")
    private val c = TaskRef(13, "app.c")

    private fun ready(vararg t: TaskRef, pins: List<String> = emptyList(), killAll: Boolean = true, pinRight: Boolean = false) =
        Overlay.loaded(OvState(), t.toList(), pins, killAll, pinRight)
    private fun press(s: OvState, b: Btn, repeat: Int = 0) = Overlay.key(s, KeyIn(b, true, repeat))
    private fun release(s: OvState, b: Btn) = Overlay.key(s, KeyIn(b, false))
    /** A full tap: down then up. Effects of both. */
    private fun tap(s: OvState, b: Btn): Pair<OvState, List<Effect>> {
        val (s1, e1) = press(s, b); val (s2, e2) = release(s1, b); return s2 to (e1 + e2)
    }
    private fun keys(s: OvState) = s.items.map { it.key }
    private fun menu(s: OvState) = Overlay.holdElapsed(press(s, Btn.OK).first)   // hold OK for HOLD_MS

    // ── load and layout ──
    @Test fun `P-OVL-01 load with tasks and Kill All focuses the newest task`() {
        val s = ready(a, b)
        assertEquals(Scene.OVERLAY, s.scene); assertEquals(1, s.focus); assertEquals(Item.Task(a), s.focusedItem)
        assertEquals(listOf("#killall", "app.a", "app.b"), keys(s)); assertTrue(s.hasKillAll); assertFalse(s.emptySlot)
        assertNull(Item.KillAll.taskId); assertNull(Item.Settings.taskId)
    }

    @Test fun `P-OVL-02 load without Kill All focuses the newest, right after the gear`() {
        val s = ready(a, b, killAll = false)
        assertEquals(1, s.focus); assertEquals(listOf("#settings", "app.a", "app.b"), keys(s)); assertFalse(s.hasKillAll)
    }

    @Test fun `P-OVL-03 load with no tasks and no pins shows Empty`() {
        val s = ready()
        assertEquals(Scene.EMPTY, s.scene); assertFalse(s.hasKillAll); assertEquals(1, s.itemCount); assertFalse(s.emptySlot)
        assertNull(s.focusedItem)
    }

    @Test fun `P-OVL-04 a reload clears every transient guard`() {
        val dirty = ready(a, b).copy(busy = true, killing = true, peek = PEEK_DOWN, flying = "app.a", ctxPkg = "app.a",
            longPress = true, pressing = true, selecting = "app.a", closing = true)
        val s = Overlay.loaded(dirty, listOf(a, b), emptyList(), true, false)
        assertFalse(s.busy || s.killing || s.longPress || s.pressing || s.closing)
        assertNull(s.flying); assertNull(s.ctxPkg); assertNull(s.selecting); assertEquals(PEEK_NONE, s.peek)
    }

    @Test fun `P-OVL-05 Shizuku missing routes to the gate, focus on the primary button`() {
        val s = Overlay.needShizuku(ready(a).copy(shzFocus = 1))
        assertEquals(Scene.SHIZUKU, s.scene); assertEquals(0, s.shzFocus); assertNull(s.focusedItem)
    }

    @Test fun `P-OVL-06 pins sit at the far end by default, next to Kill All when right`() {
        assertEquals(listOf("#killall", "app.a", "p.x", "p.y"), keys(ready(a, pins = listOf("p.x", "p.y"))))
        assertEquals(listOf("#killall", "p.x", "p.y", "app.a"), keys(ready(a, pins = listOf("p.x", "p.y"), pinRight = true)))
    }

    @Test fun `P-OVL-07 a pinned app that runs shows once, in the pins, with its task`() {
        val s = ready(a, b, pins = listOf("app.b", "p.x"))
        assertEquals(listOf(a), s.tasks)
        assertEquals(listOf(Item.Pin("app.b", 12), Item.Pin("p.x", null)), s.pins)
        assertTrue(s.pins[0].running); assertFalse(s.pins[1].running)
    }

    @Test fun `P-OVL-08 only pins - no Kill All, the placeholder shows, focus on the first pin`() {
        val s = ready(pins = listOf("p.x", "p.y"))
        assertEquals(Scene.OVERLAY, s.scene); assertFalse(s.hasKillAll); assertTrue(s.emptySlot)
        assertEquals(1, s.focus); assertEquals("p.x", s.focusedItem?.key)
    }

    @Test fun `P-OVL-09 default focus with nothing at all is 0`() {
        assertEquals(0, Overlay.defaultFocus(OvState()))
    }

    // ── navigation ──
    @Test fun `P-OVL-10 RIGHT moves toward Kill All and stops there (RTL) - without Kill All, at the gear`() {
        var s = ready(a, b).copy(focus = 2)
        s = press(s, Btn.RIGHT).first; assertEquals(1, s.focus)
        s = press(s, Btn.RIGHT).first; assertEquals(Item.KillAll, s.focusedItem)
        s = press(s, Btn.RIGHT).first; assertEquals(0, s.focus)
        var n = ready(a, b, killAll = false)
        n = press(n, Btn.RIGHT).first; assertEquals(Item.Settings, n.focusedItem)
        n = press(n, Btn.RIGHT).first; assertEquals(0, n.focus)
    }

    @Test fun `P-OVL-11 LEFT moves toward older apps and into the pins, stops at the last`() {
        var s = ready(a, pins = listOf("p.x"))
        s = press(s, Btn.LEFT).first; assertEquals("p.x", s.focusedItem?.key)
        s = press(s, Btn.LEFT).first; assertEquals("p.x", s.focusedItem?.key)
    }

    @Test fun `P-OVL-12 moving focus cancels a pending peek`() {
        val s = press(ready(a, b), Btn.DOWN).first
        assertEquals(PEEK_DOWN, s.peek)
        assertEquals(PEEK_NONE, press(s, Btn.LEFT).first.peek)
        assertEquals(PEEK_NONE, press(s, Btn.RIGHT).first.peek)
    }

    // ── dismiss ──
    @Test fun `P-OVL-13 DOWN twice peeks then flies the card down - nothing removed until it lands`() {
        val s1 = press(ready(a, b), Btn.DOWN).first
        val (s2, e) = press(s1, Btn.DOWN)
        assertEquals("app.a", s2.flying); assertEquals(PEEK_DOWN, s2.flyDir); assertEquals(FlyReason.DISMISS, s2.flyReason)
        assertTrue(e.isEmpty()); assertEquals(2, s2.all.size)
    }

    @Test fun `P-OVL-14 UP on an app opens its menu (no hold), on a pin too, peek cleared`() {
        val s = press(ready(a, b).copy(peek = PEEK_DOWN), Btn.UP).first
        assertEquals("app.a", s.ctxPkg); assertEquals(0, s.ctxSel); assertFalse(s.longPress); assertEquals(PEEK_NONE, s.peek)
        assertEquals(listOf(Effect.SetPin("app.a", true)), press(s, Btn.OK).second)     // its first OK runs the row at once
        assertEquals("p.x", press(ready(a, pins = listOf("p.x")).copy(focus = 2), Btn.UP).first.ctxPkg)
    }

    @Test fun `P-OVL-15 DOWN then UP - the menu opens instead of a second peek`() {
        val s = press(press(ready(a, b), Btn.DOWN).first, Btn.UP).first
        assertEquals(PEEK_NONE, s.peek); assertNull(s.flying); assertEquals("app.a", s.ctxPkg)
    }

    @Test fun `P-OVL-16 fly-off landing removes the task, keeps focus in place, then dismisses it`() {
        val s = press(press(ready(a, b, c), Btn.DOWN).first, Btn.DOWN).first
        val (n, e) = Overlay.flyComplete(s, "app.a")
        assertEquals(listOf(b, c), n.all); assertNull(n.flying); assertEquals(1, n.focus); assertEquals("app.b", n.focusedItem?.key)
        assertEquals(listOf(Effect.Dismiss(a)), e)
    }

    @Test fun `P-OVL-17 removing the last task leaves Empty, or the pins when there are some`() {
        val s = press(press(ready(a), Btn.DOWN).first, Btn.DOWN).first
        assertEquals(Scene.EMPTY, Overlay.flyComplete(s, "app.a").first.scene)
        val p = press(press(ready(a, pins = listOf("p.x")), Btn.DOWN).first, Btn.DOWN).first
        val n = Overlay.flyComplete(p, "app.a").first
        assertEquals(Scene.OVERLAY, n.scene); assertTrue(n.emptySlot); assertEquals("p.x", n.focusedItem?.key)
    }

    @Test fun `P-OVL-18 a stale or unknown fly-off changes nothing or only clears the flag`() {
        val s = ready(a, b).copy(flying = "app.a")
        assertEquals(s to emptyList<Effect>(), Overlay.flyComplete(s, "app.zz"))
        val gone = ready(a, b).copy(flying = "app.zz")
        val (n, e) = Overlay.flyComplete(gone, "app.zz")
        assertNull(n.flying); assertTrue(e.isEmpty()); assertEquals(2, n.all.size)
    }

    @Test fun `P-OVL-19 DOWN on Kill All, UP DOWN on the in-row gear, DOWN on a pin do nothing`() {
        val k = ready(a).copy(focus = 0)
        assertEquals(k, press(k, Btn.DOWN).first)
        val g = ready(a, killAll = false).copy(focus = 0)
        assertEquals(g, press(g, Btn.UP).first); assertEquals(g, press(g, Btn.DOWN).first)
        val p = ready(a, pins = listOf("p.x")).copy(focus = 2)
        assertEquals(p, press(p, Btn.DOWN).first)
    }

    @Test fun `P-OVL-20 while a card is flying, keys are ignored`() {
        val s = ready(a, b).copy(flying = "app.a")
        for (k in listOf(Btn.DOWN, Btn.UP, Btn.LEFT, Btn.RIGHT)) assertEquals(s, press(s, k).first)
    }

    // ── OK: tap, press-in, select ──
    @Test fun `P-OVL-21 OK down on a card presses it in, the release selects it`() {
        val down = press(ready(a, b), Btn.OK).first
        assertTrue(down.pressing)
        val (n, e) = release(down, Btn.OK)
        assertFalse(n.pressing); assertTrue(n.busy); assertEquals("app.a", n.selecting)
        assertEquals(listOf(Effect.Switch("app.a", 11)), e)
    }

    @Test fun `P-OVL-22 a pinned app that isn't running is launched (no task id)`() {
        val s = ready(a, pins = listOf("p.x")).copy(focus = 2)
        assertEquals(listOf(Effect.ArmHold, Effect.Switch("p.x", null)), tap(s, Btn.OK).second)
        val r = ready(a, pins = listOf("app.a"))
        assertEquals(listOf(Effect.ArmHold, Effect.Switch("app.a", 11)), tap(r, Btn.OK).second)
    }

    @Test fun `P-OVL-23 while selecting every key is ignored`() {
        val s = tap(ready(a, b), Btn.OK).first
        for (k in Btn.entries) assertEquals(s, press(s, k).first)
    }

    @Test fun `P-OVL-24 OK on Kill All clears tasks and running pins, pins stay`() {
        val s = ready(a, b, pins = listOf("app.b", "p.x")).copy(focus = 0)
        assertFalse(press(s, Btn.OK).first.pressing)            // no press-in on Kill All
        val (k, e) = tap(s, Btn.OK)
        assertTrue(k.killing && k.busy)
        assertEquals(listOf(Effect.KillAll(listOf(a, b))), e)
        val done = Overlay.killAllDone(k)
        assertEquals(Scene.OVERLAY, done.scene); assertTrue(done.all.isEmpty()); assertFalse(done.busy || done.killing)
        assertEquals(listOf(Item.Pin("app.b", null), Item.Pin("p.x", null)), done.pins); assertEquals(1, done.focus)
    }

    @Test fun `P-OVL-25 Kill All with no pins ends in Empty`() {
        val k = tap(ready(a, b).copy(focus = 0), Btn.OK).first
        assertEquals(Scene.EMPTY, Overlay.killAllDone(k).scene)
    }

    @Test fun `P-OVL-26 OK with a focus past the end does nothing`() {
        val s = ready(a).copy(focus = 9)
        assertFalse(press(s, Btn.OK).first.pressing)
        assertTrue(tap(s, Btn.OK).second.isEmpty())
        assertEquals(s, press(s, Btn.OTHER).first)
    }

    @Test fun `P-OVL-27 busy, killing or flying - OK does nothing and drops the press`() {
        for (g in listOf(ready(a).copy(busy = true), ready(a).copy(killing = true), ready(a, b).copy(flying = "app.b"))) {
            val p = g.copy(pressing = true)
            assertFalse(press(p, Btn.OK).first.pressing)
            assertTrue(tap(p, Btn.OK).second.isEmpty())
        }
    }

    // ── long-press menu ──
    @Test fun `P-OVL-28 holding OK opens the menu on a task or a pin - the release does not open the app`() {
        val m = menu(press(ready(a, b), Btn.OK).first)
        assertEquals("app.a", m.ctxPkg); assertTrue(m.longPress); assertFalse(m.pressing); assertEquals(0, m.ctxSel)
        val (r, e) = release(m, Btn.OK)
        assertFalse(r.longPress); assertTrue(e.isEmpty()); assertEquals("app.a", r.ctxPkg)
        assertEquals("p.x", menu(ready(a, pins = listOf("p.x")).copy(focus = 2)).ctxPkg)
    }

    @Test fun `P-OVL-29 a hold on Kill All, the gear, a past-the-end focus, or key repeats alone open nothing`() {
        assertNull(menu(ready(a).copy(focus = 0)).ctxPkg)
        assertNull(menu(ready(a).copy(focus = 0, gear = true)).ctxPkg)
        assertNull(menu(ready(a, killAll = false).copy(focus = 0)).ctxPkg)
        assertNull(menu(ready(a).copy(focus = 9)).ctxPkg)
        assertNull(press(ready(a), Btn.OK, 1).first.ctxPkg)
        assertNull(press(ready(a), Btn.OK, 2).first.ctxPkg)
    }

    @Test fun `P-OVL-30 after the menu closes the next OK tap opens the app (no stale hold)`() {
        val closed = press(release(menu(ready(a, b)), Btn.OK).first, Btn.BACK).first
        assertNull(closed.ctxPkg)
        assertEquals(listOf(Effect.ArmHold, Effect.Switch("app.a", 11)), tap(closed, Btn.OK).second)
    }

    @Test fun `P-OVL-31 menu - UP DOWN clamp, LEFT RIGHT do nothing`() {
        var m = release(menu(ready(a)), Btn.OK).first
        m = press(m, Btn.UP).first; assertEquals(0, m.ctxSel)
        repeat(6) { m = press(m, Btn.DOWN).first }; assertEquals(CtxOption.entries.size - 1, m.ctxSel)
        assertEquals(m, press(m, Btn.LEFT).first); assertEquals(m, press(m, Btn.RIGHT).first)
    }

    @Test fun `P-OVL-32 menu - Pin moves a task into the pins and keeps focus on it`() {
        val m = release(menu(ready(a, b)), Btn.OK).first
        val (n, e) = press(m, Btn.OK)
        assertNull(n.ctxPkg); assertEquals(listOf("app.a"), n.pinned)
        assertEquals("app.a", n.focusedItem?.key); assertTrue(n.focusedItem is Item.Pin)
        assertEquals(listOf(Effect.SetPin("app.a", true)), e)
    }

    @Test fun `P-OVL-33 menu - Unpin a running pin returns it to the tasks`() {
        val m = release(menu(ready(a, pins = listOf("app.a"))), Btn.OK).first   // focus = the pinned, running app.a
        assertEquals("app.a", m.ctxPkg)
        val (n, e) = press(m, Btn.OK)
        assertTrue(n.pinned.isEmpty()); assertEquals(Item.Task(a), n.focusedItem)
        assertEquals(listOf(Effect.SetPin("app.a", false)), e)
    }

    @Test fun `P-OVL-34 menu - Unpin a pin that isn't running removes it, or empties the row`() {
        val s = ready(a, pins = listOf("p.x")).copy(focus = 2)
        val n = press(release(menu(s), Btn.OK).first, Btn.OK).first
        assertEquals(listOf("#killall", "app.a"), keys(n)); assertEquals(1, n.focus)
        val only = press(release(menu(ready(pins = listOf("p.x"))), Btn.OK).first, Btn.OK).first
        assertEquals(Scene.EMPTY, only.scene)
    }

    @Test fun `P-OVL-35 menu - Add to exceptions flies the card up, then it is gone (task or pin)`() {
        val m = press(release(menu(ready(a, b, pins = listOf("p.x"))), Btn.OK).first, Btn.DOWN).first
        val (f, e) = press(m, Btn.OK)
        assertEquals("app.a", f.flying); assertEquals(PEEK_UP, f.flyDir); assertEquals(FlyReason.EXCEPTION, f.flyReason)
        assertEquals(listOf(Effect.AddException("app.a")), e)
        val (n, e2) = Overlay.flyComplete(f, "app.a")
        assertEquals(listOf(b), n.all); assertTrue(e2.isEmpty())
        val p = ready(a, pins = listOf("p.x")).copy(focus = 2)
        val fp = press(press(release(menu(p), Btn.OK).first, Btn.DOWN).first, Btn.OK).first
        assertTrue(Overlay.flyComplete(fp, "p.x").first.pinned.isEmpty())
    }

    @Test fun `P-OVL-36 menu - App info opens it and closes the overlay - Close flies a task off`() {
        var m = release(menu(ready(a)), Btn.OK).first
        repeat(2) { m = press(m, Btn.DOWN).first }
        val (n, e) = press(m, Btn.OK)
        assertTrue(n.closing); assertEquals(listOf(Effect.AppInfo("app.a"), Effect.Close), e)
        val (s, e2) = press(press(m, Btn.DOWN).first, Btn.OK)
        assertNull(s.ctxPkg); assertTrue(e2.isEmpty())
        assertEquals("app.a", s.flying); assertEquals(FlyReason.DISMISS, s.flyReason); assertEquals(PEEK_UP, s.flyDir)
        assertEquals(listOf(Effect.Dismiss(a)), Overlay.flyComplete(s, "app.a").second)
    }

    @Test fun `P-OVL-54 menu - Close on a running pin removes its task and keeps the pin, on an idle pin does nothing`() {
        val close = { st: OvState -> var m = press(st, Btn.UP).first; repeat(3) { m = press(m, Btn.DOWN).first }; press(m, Btn.OK) }
        val (n, e) = close(ready(a, b, pins = listOf("app.b")).copy(focus = 2))                 // focus = pinned, running app.b
        assertEquals(listOf(Effect.Dismiss(b)), e); assertEquals(listOf("app.b"), n.pinned)
        assertEquals(listOf(a), n.all); assertNull(n.ctxPkg); assertNull(n.flying)
        val idle = ready(a, pins = listOf("p.x")).copy(focus = 2)
        val (i, e2) = close(idle)
        assertTrue(e2.isEmpty()); assertNull(i.ctxPkg); assertEquals(listOf("p.x"), i.pinned)
    }

    @Test fun `P-OVL-37 a menu whose app vanished just closes`() {
        val m = ready(a).copy(ctxPkg = "app.zz")
        assertNull(m.ctxItem)
        assertNull(press(m, Btn.DOWN).first.ctxPkg)
    }

    @Test fun `P-OVL-38 OK release with the menu open but no hold, or another key's release, changes nothing`() {
        val m = release(menu(ready(a)), Btn.OK).first
        assertEquals(m, release(m, Btn.OK).first)
        assertEquals(m, release(m, Btn.DOWN).first)
    }

    // ── BACK / MENU / other scenes ──
    @Test fun `P-OVL-39 BACK closes the menu first, then the overlay - closing ignores keys`() {
        val m = menu(ready(a))
        val n = press(m, Btn.BACK).first
        assertNull(n.ctxPkg); assertTrue(n.longPress)               // still held: the release clears it
        assertFalse(release(n, Btn.OK).first.longPress)
        val (c, e) = press(ready(a).copy(pressing = true), Btn.BACK)
        assertTrue(c.closing); assertFalse(c.pressing); assertEquals(listOf(Effect.Close), e)
        assertEquals(c to emptyList<Effect>(), press(c, Btn.OK))
    }

    @Test fun `P-OVL-40 MENU opens settings from any scene`() {
        for (s in listOf(ready(a), ready(), Overlay.needShizuku(OvState()), OvState()))
            assertEquals(listOf(Effect.OpenSettings), press(s, Btn.MENU).second)
    }

    @Test fun `P-OVL-41 Empty - OK or UP opens settings, other keys nothing`() {
        val s = ready()
        assertEquals(listOf(Effect.OpenSettings), press(s, Btn.OK).second)
        assertEquals(listOf(Effect.OpenSettings), press(s, Btn.UP).second)
        assertTrue(press(s, Btn.LEFT).second.isEmpty())
    }

    @Test fun `P-OVL-42 Loading ignores keys - releases do nothing`() {
        val s = OvState()
        assertEquals(s to emptyList<Effect>(), press(s, Btn.OK))
        val r = ready(a)
        assertEquals(r to emptyList<Effect>(), release(r, Btn.LEFT))
    }

    @Test fun `P-OVL-43 Shizuku gate - LEFT to Retry, RIGHT back, OK acts from either`() {
        var s = Overlay.needShizuku(OvState())
        s = press(s, Btn.LEFT).first; assertEquals(1, s.shzFocus)
        s = press(s, Btn.LEFT).first; assertEquals(1, s.shzFocus)
        assertEquals(listOf(Effect.GateAction), press(s, Btn.OK).second)
        s = press(s, Btn.RIGHT).first; assertEquals(0, s.shzFocus)
        s = press(s, Btn.RIGHT).first; assertEquals(0, s.shzFocus)
        assertEquals(s, press(s, Btn.DOWN).first)
    }

    @Test fun `P-OVL-44 busy or killing overlay ignores navigation`() {
        for (g in listOf(ready(a, b).copy(busy = true), ready(a, b).copy(killing = true)))
            for (k in listOf(Btn.LEFT, Btn.RIGHT, Btn.UP, Btn.DOWN)) assertEquals(g, press(g, k).first)
    }

    @Test fun `P-OVL-45 item keys and lookups`() {
        assertEquals("#killall", Item.KillAll.key)
        val s = ready(a, pins = listOf("p.x"))
        assertEquals(2, s.indexOf("p.x")); assertEquals(-1, s.indexOf("nope")); assertNull(s.itemAt(7))
        assertEquals("#settings", Item.Settings.key)
        assertNull(s.ctxItem)
    }

    @Test fun `P-OVL-46 the release of an OK that ran a menu row opens nothing (A50 find)`() {
        for (sel in 0..3) {
            var m = release(menu(ready(a, b)), Btn.OK).first
            repeat(sel) { m = press(m, Btn.DOWN).first }
            val ran = press(m, Btn.OK).first
            val (n, e) = release(ran, Btn.OK)
            assertTrue(e.isEmpty()); assertNull(n.selecting); assertTrue(n.closing || !n.longPress)   // App info closes: keys ignored
        }
    }

    @Test fun `P-OVL-47 an OK press while busy keeps a hold, its release ends it`() {
        val held = ready(a).copy(busy = true, longPress = true)
        assertTrue(press(held, Btn.OK).first.longPress)
        assertFalse(release(held, Btn.OK).first.longPress)
    }

    // the TV bug (1.3): a held OK repeats every ~50 ms; the 2nd repeat used to run "Pin" and close the menu
    @Test fun `P-OVL-48 a real remote hold - repeats keep the balloon open with nothing run, release keeps it`() {
        var s = press(ready(a, b), Btn.OK).first
        for (r in 1..8) s = press(s, Btn.OK, r).first          // ~400 ms of repeats before the timer
        assertNull(s.ctxPkg); assertTrue(s.pressing)
        s = Overlay.holdElapsed(s)                              // 550 ms
        for (r in 9..30) { val (n, e) = press(s, Btn.OK, r); assertTrue(e.isEmpty()); s = n }
        assertEquals("app.a", s.ctxPkg); assertEquals(0, s.ctxSel); assertTrue(s.pinned.isEmpty())
        val (up, e) = release(s, Btn.OK)
        assertEquals("app.a", up.ctxPkg); assertFalse(up.longPress); assertTrue(e.isEmpty())
    }

    @Test fun `P-OVL-49 the hold timer after a release, a move or on a non-card opens nothing`() {
        val p = press(ready(a, b), Btn.OK).first
        assertNull(Overlay.holdElapsed(release(p, Btn.OK).first.copy(busy = false, selecting = null)).ctxPkg)
        val moved = press(p, Btn.LEFT).first
        assertFalse(moved.pressing); assertNull(Overlay.holdElapsed(moved).ctxPkg)
        assertNull(Overlay.holdElapsed(ready(a).copy(focus = 0, pressing = true)).ctxPkg)
        assertNull(Overlay.holdElapsed(ready().copy(pressing = true)).ctxPkg)
    }

    @Test fun `P-OVL-50 the gear opens Settings on release, no press-in, no hold`() {
        val g = press(ready(a, b).copy(focus = 0), Btn.UP).first
        val (d, e1) = press(g, Btn.OK)
        assertFalse(d.pressing); assertTrue(e1.isEmpty())
        val (n, e2) = release(d, Btn.OK)
        assertEquals(listOf(Effect.OpenSettings), e2); assertFalse(n.busy); assertNull(n.selecting)
        assertEquals(Item.Settings, ready(pins = listOf("p.x")).items.first())
    }

    // 1.5: the ⚙ sits above Kill All — ↑ from Kill All, ↓ back, ← goes on to the apps
    @Test fun `P-OVL-51 Kill All up focuses the gear, down returns, left goes to the newest app, right stays`() {
        val k = ready(a, b).copy(focus = 0)
        val g = press(k, Btn.UP).first
        assertTrue(g.gearFocused); assertEquals(Item.Settings, g.focusedItem); assertEquals(PEEK_NONE, g.peek)
        assertEquals(g, press(g, Btn.UP).first); assertEquals(g, press(g, Btn.RIGHT).first)
        val d = press(g, Btn.DOWN).first
        assertFalse(d.gearFocused); assertEquals(Item.KillAll, d.focusedItem)
        val l = press(g, Btn.LEFT).first
        assertFalse(l.gear); assertEquals(Item.Task(a), l.focusedItem)
    }

    @Test fun `P-OVL-52 the gear above Kill All goes away with Kill All - a reload or the last task leaving resets it`() {
        val g = press(ready(a).copy(focus = 0), Btn.UP).first
        assertTrue(g.gearFocused)
        assertFalse(Overlay.loaded(g, listOf(a), emptyList(), true, false).gear)
        assertFalse(g.copy(showKillAll = false).gearFocused)                        // stale flag: not counted
        assertFalse(g.copy(focus = 1).gearFocused)
        assertEquals(Item.Settings, ready(a, killAll = false).items.first())
        assertFalse(ready(a).items.contains(Item.Settings))
    }

    // 1.8: launching the app again while the overlay shows closes it (the mapped button is a toggle)
    @Test fun `P-OVL-53 a second launch closes the overlay, menu or not, but not while closing or opening an app`() {
        val (c, e) = Overlay.relaunched(ready(a, b))
        assertTrue(c.closing); assertEquals(listOf(Effect.Close), e)
        val m = Overlay.relaunched(ready(a).copy(ctxPkg = "app.a", pressing = true)).first
        assertNull(m.ctxPkg); assertFalse(m.pressing); assertTrue(m.closing)
        val closing = c
        assertEquals(closing to emptyList<Effect>(), Overlay.relaunched(closing))
        val sel = ready(a).copy(selecting = "app.a")
        assertEquals(sel to emptyList<Effect>(), Overlay.relaunched(sel))
        val gate = Overlay.relaunched(Overlay.needShizuku(ready(a)))
        assertEquals(listOf(Effect.Close), gate.second)                              // the gate closes too
    }
}
