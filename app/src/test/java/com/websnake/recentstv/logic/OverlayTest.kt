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

    private fun ready(vararg t: TaskRef, killAll: Boolean = true) = Overlay.loaded(OvState(), t.toList(), killAll)
    private fun press(s: OvState, b: Btn, repeat: Int = 0) = Overlay.key(s, KeyIn(b, true, repeat))
    private fun release(s: OvState, b: Btn) = Overlay.key(s, KeyIn(b, false))
    /** A full tap: down then up. Effects of both. */
    private fun tap(s: OvState, b: Btn): Pair<OvState, List<Effect>> {
        val (s1, e1) = press(s, b); val (s2, e2) = release(s1, b); return s2 to (e1 + e2)
    }

    @Test fun `P-OVL-01 load with tasks and Kill All focuses the newest task`() {
        val s = ready(a, b)
        assertEquals(Scene.OVERLAY, s.scene); assertEquals(1, s.focus); assertEquals(a, s.focusedTask)
        assertEquals(3, s.itemCount); assertTrue(s.isKillAllAt(0))
    }

    @Test fun `P-OVL-02 load without Kill All focuses index 0 = newest`() {
        val s = ready(a, b, killAll = false)
        assertEquals(0, s.focus); assertEquals(a, s.taskAt(0)); assertFalse(s.isKillAllAt(0))
    }

    @Test fun `P-OVL-03 load with no tasks shows Empty and hides Kill All`() {
        val s = ready()
        assertEquals(Scene.EMPTY, s.scene); assertFalse(s.hasKillAll); assertEquals(0, s.itemCount)
    }

    @Test fun `P-OVL-04 a reload clears every transient guard`() {
        val dirty = ready(a, b).copy(busy = true, killing = true, gear = true, peek = PEEK_DOWN, flying = 11, ctxTaskId = 11, longPress = true, closing = true)
        val s = Overlay.loaded(dirty, listOf(a, b), true)
        assertFalse(s.busy || s.killing || s.gear || s.longPress || s.closing)
        assertNull(s.flying); assertNull(s.ctxTaskId); assertEquals(PEEK_NONE, s.peek)
    }

    @Test fun `P-OVL-05 Shizuku missing routes to the gate, focus on the primary button`() {
        val s = Overlay.needShizuku(ready(a).copy(shzFocus = 1))
        assertEquals(Scene.SHIZUKU, s.scene); assertEquals(0, s.shzFocus)
    }

    @Test fun `P-OVL-06 RIGHT moves toward Kill All and stops there (RTL)`() {
        var s = ready(a, b).copy(focus = 2)
        s = press(s, Btn.RIGHT).first; assertEquals(1, s.focus)
        s = press(s, Btn.RIGHT).first; assertEquals(0, s.focus)
        s = press(s, Btn.RIGHT).first; assertEquals(0, s.focus)
    }

    @Test fun `P-OVL-07 LEFT moves toward older apps and stops at the last`() {
        var s = ready(a, b)
        s = press(s, Btn.LEFT).first; assertEquals(2, s.focus); assertEquals(b, s.focusedTask)
        s = press(s, Btn.LEFT).first; assertEquals(2, s.focus)
    }

    @Test fun `P-OVL-08 moving focus cancels a pending peek`() {
        val s = press(press(ready(a, b), Btn.DOWN).first, Btn.LEFT).first
        assertEquals(PEEK_NONE, s.peek)
    }

    @Test fun `P-OVL-09 DOWN twice peeks then flies the card off - nothing is removed until it lands`() {
        val s1 = press(ready(a, b), Btn.DOWN).first
        assertEquals(PEEK_DOWN, s1.peek); assertNull(s1.flying)
        val (s2, e2) = press(s1, Btn.DOWN)
        assertEquals(11, s2.flying); assertEquals(PEEK_NONE, s2.peek); assertTrue(e2.isEmpty())
        assertEquals(2, s2.tasks.size)
    }

    @Test fun `P-OVL-10 fly-off landing removes the task, keeps focus on a neighbour, then dismisses it`() {
        val flying = press(press(ready(a, b, c).copy(focus = 3), Btn.DOWN).first, Btn.DOWN).first
        assertEquals(13, flying.flying)
        val (s, e) = Overlay.flyComplete(flying, 13)
        assertEquals(listOf(a, b), s.tasks); assertEquals(2, s.focus); assertEquals(b, s.focusedTask)
        assertEquals(listOf(Effect.Dismiss(c)), e); assertNull(s.flying)
    }

    @Test fun `P-OVL-11 removing the last task leaves the Empty scene`() {
        val (s, e) = Overlay.flyComplete(ready(a).copy(flying = 11), 11)
        assertEquals(Scene.EMPTY, s.scene); assertEquals(listOf(Effect.Dismiss(a)), e)
    }

    @Test fun `P-OVL-12 a fly-off for an unknown task only clears the flag`() {
        val (s, e) = Overlay.flyComplete(ready(a).copy(flying = 99), 99)
        assertNull(s.flying); assertTrue(e.isEmpty()); assertEquals(1, s.tasks.size)
    }

    @Test fun `P-OVL-13 DOWN on Kill All does nothing`() {
        val s = press(ready(a).copy(focus = 0), Btn.DOWN).first
        assertEquals(PEEK_NONE, s.peek)
    }

    @Test fun `P-OVL-14 while a card is flying, DOWN and L-R are ignored`() {
        val f = ready(a, b).copy(flying = 11)
        assertEquals(f, press(f, Btn.DOWN).first)
        assertEquals(f, press(f, Btn.LEFT).first)
        assertEquals(f, press(f, Btn.UP).first)          // no jump to the gear mid-flight
    }

    @Test fun `P-OVL-15 UP cancels a peek, otherwise focuses the gear`() {
        val peeked = press(ready(a), Btn.DOWN).first
        val s1 = press(peeked, Btn.UP).first
        assertEquals(PEEK_NONE, s1.peek); assertFalse(s1.gear)
        val s2 = press(s1, Btn.UP).first
        assertTrue(s2.gear); assertNull(s2.focusedTask)
    }

    @Test fun `P-OVL-16 gear focused - DOWN LEFT RIGHT leave it, OK opens settings`() {
        val g = press(ready(a), Btn.UP).first
        for (b in listOf(Btn.DOWN, Btn.LEFT, Btn.RIGHT)) assertFalse(press(g, b).first.gear)
        assertEquals(listOf(Effect.OpenSettings), press(g, Btn.OK).second)
        assertEquals(g, press(g, Btn.OTHER).first)
    }

    @Test fun `P-OVL-17 OK tap on a card switches to it on release and blocks further input`() {
        val (s, e) = tap(ready(a, b), Btn.OK)
        assertEquals(listOf(Effect.Switch(a)), e); assertTrue(s.busy)
        assertEquals(s, press(s, Btn.LEFT).first)
    }

    @Test fun `P-OVL-18 OK on Kill All clears everything, then the Empty scene`() {
        val (s, e) = tap(ready(a, b).copy(focus = 0), Btn.OK)
        assertEquals(listOf(Effect.KillAll(listOf(a, b))), e); assertTrue(s.killing && s.busy)
        val done = Overlay.killAllDone(s)
        assertEquals(Scene.EMPTY, done.scene); assertTrue(done.tasks.isEmpty()); assertFalse(done.busy || done.killing)
    }

    @Test fun `P-OVL-19 holding OK opens the context menu - the release does not switch`() {
        val (s1, _) = press(ready(a, b), Btn.OK)
        val (s2, _) = press(s1, Btn.OK, repeat = 1)
        assertEquals(11, s2.ctxTaskId); assertTrue(s2.longPress); assertEquals(a, s2.ctxTask)
        val (s3, e3) = release(s2, Btn.OK)
        assertTrue(e3.isEmpty()); assertEquals(11, s3.ctxTaskId)
    }

    @Test fun `P-OVL-20 after the menu closes, the next OK tap works (stale long-press flag)`() {
        // v1.0 defect: the release after the long-press landed with the menu open and was dropped,
        // so the flag stayed and ate the next real OK tap (found on the A50).
        val opened = press(press(ready(a, b), Btn.OK).first, Btn.OK, 1).first
        val released = release(opened, Btn.OK).first
        assertFalse(released.longPress)
        val closed = press(released, Btn.BACK).first
        assertNull(closed.ctxTaskId)
        assertEquals(listOf(Effect.Switch(a)), tap(closed, Btn.OK).second)
    }

    @Test fun `P-OVL-21 holding OK on Kill All never opens a menu`() {
        val s = press(press(ready(a).copy(focus = 0), Btn.OK).first, Btn.OK, 1).first
        assertNull(s.ctxTaskId)
    }

    @Test fun `P-OVL-22 context menu - UP DOWN clamp, LEFT closes`() {
        val m = ready(a).copy(ctxTaskId = 11)
        assertEquals(0, press(m, Btn.UP).first.ctxSel)
        assertEquals(2, press(press(press(m, Btn.DOWN).first, Btn.DOWN).first, Btn.DOWN).first.ctxSel)
        assertNull(press(m, Btn.LEFT).first.ctxTaskId)
        assertNull(press(m, Btn.RIGHT).first.ctxTaskId)
        assertEquals(m, press(m, Btn.OTHER).first)
        assertEquals(m, release(m, Btn.LEFT).first)
    }

    @Test fun `P-OVL-23 context menu - add to exceptions hides the app at once`() {
        val (s, e) = press(ready(a, b).copy(ctxTaskId = 11, ctxSel = 0), Btn.OK)
        assertEquals(listOf(Effect.AddException(a)), e); assertEquals(listOf(b), s.tasks); assertNull(s.ctxTaskId)
    }

    @Test fun `P-OVL-24 context menu - kill app removes it and kills it`() {
        val (s, e) = press(ready(a, b).copy(ctxTaskId = 12, ctxSel = 1), Btn.OK)
        assertEquals(listOf(Effect.KillApp(b)), e); assertEquals(listOf(a), s.tasks)
    }

    @Test fun `P-OVL-25 context menu - app info opens settings and closes the overlay`() {
        val (s, e) = press(ready(a).copy(ctxTaskId = 11, ctxSel = 2), Btn.OK)
        assertEquals(listOf(Effect.AppInfo(a), Effect.Close), e); assertTrue(s.closing)
    }

    @Test fun `P-OVL-26 a menu whose task vanished just closes`() {
        val (s, e) = press(ready(a).copy(ctxTaskId = 77), Btn.DOWN)
        assertNull(s.ctxTaskId); assertTrue(e.isEmpty())
    }

    @Test fun `P-OVL-27 BACK closes the menu first, then the overlay - closing ignores keys`() {
        val m = ready(a).copy(ctxTaskId = 11)
        assertNull(press(m, Btn.BACK).first.ctxTaskId)
        val (c, e) = press(ready(a), Btn.BACK)
        assertEquals(listOf(Effect.Close), e); assertTrue(c.closing)
        assertEquals(c to emptyList<Effect>(), press(c, Btn.OK))
    }

    @Test fun `P-OVL-28 MENU opens settings from any scene`() {
        assertEquals(listOf(Effect.OpenSettings), press(ready(a), Btn.MENU).second)
        assertEquals(listOf(Effect.OpenSettings), press(ready(), Btn.MENU).second)
    }

    @Test fun `P-OVL-29 Empty - OK or UP opens settings, other keys nothing`() {
        val e = ready()
        assertEquals(listOf(Effect.OpenSettings), tap(e, Btn.OK).second)
        assertEquals(listOf(Effect.OpenSettings), press(e, Btn.UP).second)
        assertTrue(press(e, Btn.LEFT).second.isEmpty())
    }

    @Test fun `P-OVL-30 Loading ignores keys - key releases do nothing`() {
        val l = OvState()
        assertEquals(l to emptyList<Effect>(), press(l, Btn.OK))
        assertEquals(ready(a) to emptyList<Effect>(), release(ready(a), Btn.LEFT))
    }

    @Test fun `P-OVL-31 Shizuku gate - LEFT to Retry, RIGHT back, OK acts from either`() {
        val g = Overlay.needShizuku(OvState())
        val r = press(g, Btn.LEFT).first; assertEquals(1, r.shzFocus)
        assertEquals(1, press(r, Btn.LEFT).first.shzFocus)
        assertEquals(0, press(press(r, Btn.RIGHT).first, Btn.RIGHT).first.shzFocus)
        assertEquals(listOf(Effect.GateAction), tap(g, Btn.OK).second)
        assertEquals(listOf(Effect.GateAction), tap(r, Btn.OK).second)
        assertTrue(press(g, Btn.UP).second.isEmpty())
    }

    @Test fun `P-OVL-32 busy or killing overlay ignores navigation and OK`() {
        val b1 = ready(a, b).copy(busy = true)
        assertEquals(b1, press(b1, Btn.LEFT).first)
        assertTrue(tap(b1, Btn.OK).second.isEmpty())
        val k = ready(a, b).copy(killing = true)
        assertEquals(k, press(k, Btn.DOWN).first)
    }

    @Test fun `P-OVL-33 OK and other keys with a focus past the end do nothing`() {
        val s = ready(a).copy(focus = 5)
        assertTrue(tap(s, Btn.OK).second.isEmpty())
        assertEquals(s, press(s, Btn.DOWN).first)
        assertEquals(s, press(s, Btn.OTHER).first)
    }

    @Test fun `P-OVL-34 focusedTask is null outside the overlay scene - ctxTask null without a menu`() {
        assertNull(ready().focusedTask)
        assertNull(Overlay.needShizuku(OvState()).focusedTask)
        assertNull(ready(a).ctxTask)
    }

    @Test fun `P-OVL-35 OK release with the menu open but no hold changes nothing`() {
        val m = ready(a).copy(ctxTaskId = 11, longPress = false)
        assertEquals(m to emptyList<Effect>(), release(m, Btn.OK))
    }

    @Test fun `P-OVL-36 OK while a card flies or Kill All runs is ignored`() {
        val f = ready(a, b).copy(flying = 11)
        assertTrue(tap(f, Btn.OK).second.isEmpty())
        val k = ready(a, b).copy(killing = true, busy = false)
        assertTrue(tap(k, Btn.OK).second.isEmpty())
    }

    @Test fun `P-OVL-37 a hold on a focus past the end, or a longer hold, opens no menu`() {
        assertNull(press(ready(a).copy(focus = 5), Btn.OK, repeat = 1).first.ctxTaskId)
        assertNull(press(ready(a), Btn.OK, repeat = 2).first.ctxTaskId)
    }

    @Test fun `P-OVL-38 BACK while still holding OK closes the menu - the release then does nothing`() {
        val opened = press(press(ready(a, b), Btn.OK).first, Btn.OK, 1).first
        val closed = press(opened, Btn.BACK).first
        assertNull(closed.ctxTaskId); assertTrue(closed.longPress)
        val (s, e) = release(closed, Btn.OK)
        assertTrue(e.isEmpty()); assertFalse(s.longPress)
    }
}
