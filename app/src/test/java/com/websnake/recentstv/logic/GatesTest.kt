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
        assertEquals(2, press(ready(a, b), Btn.LEFT).first.focus)
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
        assertNull(Overlay.holdElapsed(ready(a).copy(focus = 0, pressing = true)).ctxPkg)
        assertNull(Overlay.holdElapsed(ready(a).copy(focus = 0, gear = true, pressing = true)).ctxPkg)
        assertEquals("app.a", hold(ready(a)).ctxPkg)
    }

    @Test fun `G-HOLD-pin a hold on a pin opens its menu`() {
        assertEquals("p.x", hold(ready(pins = listOf("p.x"))).ctxPkg)
    }

    @Test fun `G-HOLD-open a timer while a menu is already open changes nothing`() {
        val m = press(release(hold(ready(a, b)), Btn.OK).first, Btn.UP).first.copy(pressing = true)
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
    @Test fun `G-ROW-gear the gear is in the row only when Kill All is not shown`() {
        assertFalse(ready(a).items.contains(Item.Settings))
        assertEquals(Item.Settings, ready(pins = listOf("p.x")).items.first())
    }

    @Test fun `G-GEAR-up UP from Kill All focuses the gear, from a task it opens the menu`() {
        assertTrue(press(ready(a).copy(focus = 0), Btn.UP).first.gearFocused)
        assertEquals("app.a", press(ready(a), Btn.UP).first.ctxPkg)
    }

    @Test fun `G-GEAR-down DOWN from the gear returns to Kill All, from a task it peeks`() {
        val g = press(ready(a).copy(focus = 0), Btn.UP).first
        assertEquals(Item.KillAll, press(g, Btn.DOWN).first.focusedItem)
        assertEquals(PEEK_DOWN, press(ready(a), Btn.DOWN).first.peek)
    }

    @Test fun `G-GEAR-right RIGHT on the gear stays, on a task it moves`() {
        val g = press(ready(a).copy(focus = 0), Btn.UP).first
        assertTrue(press(g, Btn.RIGHT).first.gearFocused)
        assertEquals(0, press(ready(a), Btn.RIGHT).first.focus)
    }

    private fun gearUp() = press(ready(a).copy(focus = 0), Btn.UP).first

    @Test fun `G-GEAR-flag the gear is focused only after UP`() {
        assertTrue(gearUp().gearFocused); assertFalse(gearUp().copy(gear = false).gearFocused)
    }

    @Test fun `G-GEAR-scene the gear counts only in the overlay scene`() {
        assertTrue(gearUp().gearFocused); assertFalse(gearUp().copy(scene = Scene.EMPTY).gearFocused)
    }

    @Test fun `G-GEAR-shown the gear above Kill All counts only while Kill All is shown`() {
        assertTrue(gearUp().gearFocused); assertFalse(gearUp().copy(showKillAll = false).gearFocused)
    }

    @Test fun `G-GEAR-focus the gear counts only while focus is on Kill All`() {
        assertTrue(gearUp().gearFocused); assertFalse(gearUp().copy(focus = 1).gearFocused)
    }

    @Test fun `G-OK-gear OK on the gear opens Settings, on a task it opens the app`() {
        assertEquals(listOf(Effect.OpenSettings), release(press(ready(a).copy(focus = 0), Btn.UP).first, Btn.OK).second)
        assertEquals(listOf(Effect.Switch("app.a", 11)), release(ready(a), Btn.OK).second)
    }

    // ── G-INPUT: the overlay ignores input while it is changing ──
    @Test fun `G-INPUT-busy no focus move while switching`() {
        assertEquals(1, press(ready(a, b).copy(busy = true), Btn.LEFT).first.focus)
    }

    @Test fun `G-INPUT-killing no peek during Kill All`() {
        assertEquals(PEEK_NONE, press(ready(a, b).copy(killing = true), Btn.DOWN).first.peek)
    }

    @Test fun `G-INPUT-flying no peek and no menu during a fly-off`() {
        assertEquals(PEEK_NONE, press(ready(a, b).copy(flying = "app.b"), Btn.DOWN).first.peek)
        assertNull(press(ready(a, b).copy(flying = "app.b"), Btn.UP).first.ctxPkg)
        assertEquals(PEEK_DOWN, press(ready(a, b), Btn.DOWN).first.peek)
    }

    @Test fun `G-INPUT-selecting the select zoom swallows every key`() {
        val s = ready(a, b).copy(selecting = "app.a")
        assertEquals(s, press(s, Btn.LEFT).first)
        assertEquals(2, press(ready(a, b), Btn.LEFT).first.focus)
    }

    // ── G-DISMISS: only a running task can be swiped away ──
    @Test fun `G-DISMISS-task a pin never peeks, a task does`() {
        assertEquals(PEEK_NONE, press(ready(a, pins = listOf("p.x")).copy(focus = 2), Btn.DOWN).first.peek)
        assertEquals(PEEK_DOWN, press(ready(a, pins = listOf("p.x")), Btn.DOWN).first.peek)
    }

    @Test fun `G-DISMISS-same only the same direction twice flies the card`() {
        assertNull(press(ready(a).copy(peek = PEEK_UP), Btn.DOWN).first.flying)
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

    @Test fun `G-READ-recents a non-empty system recents list is used, an empty one is not`() {
        assertEquals(TaskSource.RECENTS, TaskList.read({ listOf(TaskRef(1, "a"), TaskRef(2, "b")) }, { "" }, { "" }, recents = { listOf(TaskRef(5, "x")) }).second)
        assertEquals(TaskSource.BINDER, TaskList.read({ listOf(TaskRef(1, "a"), TaskRef(2, "b")) }, { "" }, { "" }, recents = { emptyList() }).second)
    }

    @Test fun `G-READ-size a one-task binder list is not enough`() {
        assertEquals(TaskSource.STACK_LIST, TaskList.read({ listOf(TaskRef(9, "home")) }, { "taskId=1: a.b/.C\ntaskId=2: c.d/.E" }, { "" }).second)
    }

    // ── G-CENTER: the focused app is always in the middle (1.8) ──
    @Test fun `G-CENTER-short in a strip that fits, each focus moves the strip`() {
        val l = Geometry.layout(listOf(100f, 100f), 20f, 30f)
        assertEquals(500f, Geometry.stripOffset(1000f, l, 1) + l.center(1), 0.001f)
    }

    @Test fun `G-CENTER-ends the end items of a long strip are centred, not pinned`() {
        val l = Geometry.layout(List(20) { 100f }, 20f, 30f)
        assertEquals(500f, Geometry.stripOffset(1000f, l, 19) + l.center(19), 0.001f)
        assertEquals(500f, Geometry.stripOffset(1000f, l, 0) + l.center(0), 0.001f)
    }

    // the release that ends a hold must not open the app
    @Test fun `G-HOLD-release the release after a hold opens nothing`() {
        val held = hold(ready(a))
        assertTrue(release(held, Btn.OK).second.isEmpty())
    }

    // ── G-ROW: the 1.7 row (stored recents + usage + removal ledger) ──
    private val st = { pkg: String, home: Boolean, excl: Boolean, avail: Boolean -> StoredTask(1, pkg, 0, home, excl, avail) }

    @Test fun `G-ROW-removed a usage entry from before its removal stays out, one after it comes back`() {
        val l = Ledger(mapOf("a" to 100L))
        assertTrue(Stored.merge(emptyList(), listOf(Seen("a", -1, 100)), l, { false }).isEmpty())
        assertEquals(1, Stored.merge(emptyList(), listOf(Seen("a", -1, 101)), l, { false }).size)
    }

    @Test fun `G-ROW-epoch Kill all hides every usage entry from before it`() {
        assertTrue(Stored.merge(emptyList(), listOf(Seen("x", -1, 199)), Ledger(epoch = 200), { false }).isEmpty())
    }

    @Test fun `G-ROW-home a home task never becomes a row candidate`() {
        assertTrue(Stored.candidates(listOf(st("l", true, false, true)), 10).isEmpty())
    }

    @Test fun `G-ROW-excluded a task started excluded from recents never becomes a row candidate`() {
        assertTrue(Stored.candidates(listOf(st("d", false, true, true)), 10).isEmpty())
    }

    @Test fun `G-ROW-avail an unavailable task never becomes a row candidate`() {
        assertTrue(Stored.candidates(listOf(st("u", false, false, false)), 10).isEmpty())
    }

    @Test fun `G-ROW-resume an app with a stored task keeps the task id when usage also has it`() {
        val r = Stored.merge(listOf(Seen("a", 5, 10)), listOf(Seen("a", -1, 50)), Ledger(), { false })
        assertEquals(5, r.single().taskId)
    }

    @Test fun `G-ROW-flag only the exclude-from-recents bit marks a task excluded`() {
        val t = Stored.parse("* Recent #0: Task{a #3 A=1:p.q}\n intent={flg=0x10200000 cmp=p.q/.M}")
        assertEquals(false, t.single().excluded)
        assertTrue(Stored.parse("* Recent #0: Task{a #3 A=1:p.q}\n intent={flg=0x00800000 cmp=p.q/.M}").single().excluded)
    }

    @Test fun `G-ROW-room usage-only apps only fill the places tasks leave`() {
        val r = Stored.merge(listOf(Seen("t", 1, 1)), listOf(Seen("u1", -1, 9), Seen("u2", -1, 8)), Ledger(), { false }, max = 2)
        assertEquals(listOf("u1", "t"), r.map { it.pkg })
    }

    // ── G-TOGGLE: a second launch closes, except mid-close / mid-open ──
    @Test fun `G-TOGGLE-closing a second launch during the exit fade does nothing`() {
        val c = ready(a).copy(closing = true)
        assertTrue(Overlay.relaunched(c).second.isEmpty())
    }

    @Test fun `G-TOGGLE-selecting a second launch while an app is opening does nothing`() {
        val s = ready(a).copy(selecting = "app.a")
        assertTrue(Overlay.relaunched(s).second.isEmpty())
    }

    // ── G-UPMENU: UP opens the menu only on an app (1.8) ──
    @Test fun `G-UPMENU-card UP opens the menu on a task or pin, never on the in-row gear`() {
        assertEquals("app.a", press(ready(a), Btn.UP).first.ctxPkg)
        assertNull(press(Overlay.loaded(OvState(), listOf(a), emptyList(), false, false).copy(focus = 0), Btn.UP).first.ctxPkg)
    }

    @Test fun `G-UPMENU-pin Close on a pin keeps it pinned, on a task it flies off`() {
        var m = press(ready(a, pins = listOf("app.a")).copy(focus = 1), Btn.UP).first
        repeat(3) { m = press(m, Btn.UP).first }
        assertNull(press(m, Btn.OK).first.flying)
        var t = press(ready(a), Btn.UP).first
        repeat(3) { t = press(t, Btn.UP).first }
        assertEquals("app.a", press(t, Btn.OK).first.flying)
    }

    // ── 1.9: the balloon is a sub-menu of the card ──
    @Test fun `G-UPMENU-peek UP on a lowered card puts it back, on a normal card it opens the menu`() {
        val low = ready(a).copy(peek = PEEK_DOWN)
        assertNull(press(low, Btn.UP).first.ctxPkg); assertEquals(PEEK_NONE, press(low, Btn.UP).first.peek)
        assertEquals("app.a", press(ready(a), Btn.UP).first.ctxPkg)
    }

    @Test fun `G-UPMENU-bottom DOWN closes the menu only from the bottom row, higher rows step down`() {
        val m = press(ready(a), Btn.UP).first
        assertNull(press(m, Btn.DOWN).first.ctxPkg)
        assertEquals("app.a", press(m.copy(ctxSel = 1), Btn.DOWN).first.ctxPkg)
    }

    // ── G-CURRENT (1.10): the app under the overlay is not re-opened ──
    @Test fun `G-CURRENT-match only the app under the overlay closes instead of switching`() {
        val r = Overlay.loaded(OvState(), listOf(a), emptyList(), true, false, current = "app.a")
        assertEquals(Effect.Close, release(press(r, Btn.OK).first, Btn.OK).second.last())
        val o = Overlay.loaded(OvState(), listOf(a), emptyList(), true, false, current = "app.z")
        assertEquals(Effect.Switch("app.a", 11), release(press(o, Btn.OK).first, Btn.OK).second.last())
    }

    @Test fun `G-CURRENT-home a launcher on top means no current app`() {
        val home = StoredTask(5, "launcher", 0, true, false, true)
        assertNull(Stored.current(listOf(home, StoredTask(6, "app.a", 0, false, false, true)), "me"))
        assertEquals("launcher", Stored.current(listOf(home.copy(home = false)), "me"))
    }

    @Test fun `G-CURRENT-excluded an excluded overlay task on top is skipped`() {
        val dlg = StoredTask(5, "dlg", 0, false, true, true)
        assertEquals("app.a", Stored.current(listOf(dlg, StoredTask(6, "app.a", 0, false, false, true)), "me"))
    }
}
