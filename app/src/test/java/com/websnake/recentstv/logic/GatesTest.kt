package com.websnake.recentstv.logic

import org.junit.Assert.assertEquals
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
    private fun ready(vararg t: TaskRef) = Overlay.loaded(OvState(), t.toList(), true)
    private fun press(s: OvState, btn: Btn, repeat: Int = 0) = Overlay.key(s, KeyIn(btn, true, repeat))
    private fun release(s: OvState, btn: Btn) = Overlay.key(s, KeyIn(btn, false))

    // ── G-OK: who handles OK (tap on release / hold for the menu) ──
    @Test fun `G-OK-scene only the overlay scene takes the tap-hold path`() {
        assertEquals(listOf(Effect.OpenSettings), press(ready(), Btn.OK).second)        // Empty: acts on press
    }

    @Test fun `G-OK-btn other keys never enter the OK path`() {
        assertEquals(2, press(ready(a, b), Btn.LEFT).first.focus)
    }

    @Test fun `G-OK-gear with the gear focused OK acts on press`() {
        val g = press(ready(a), Btn.UP).first
        assertEquals(listOf(Effect.OpenSettings), press(g, Btn.OK).second)
    }

    @Test fun `G-OK-busy a hold while switching opens no menu`() {
        assertNull(press(ready(a).copy(busy = true), Btn.OK, 1).first.ctxTaskId)
        assertEquals(11, press(ready(a), Btn.OK, 1).first.ctxTaskId)
    }

    @Test fun `G-OK-killing a hold during Kill All opens no menu`() {
        assertNull(press(ready(a).copy(killing = true), Btn.OK, 1).first.ctxTaskId)
    }

    @Test fun `G-OK-flying a hold during a fly-off opens no menu`() {
        assertNull(press(ready(a, b).copy(flying = 12), Btn.OK, 1).first.ctxTaskId)
    }

    // ── G-HOLD: when a held OK opens the context menu ──
    @Test fun `G-HOLD-repeat the first press of OK opens nothing`() {
        assertNull(press(ready(a), Btn.OK, 0).first.ctxTaskId)
    }

    @Test fun `G-HOLD-task a hold with no task under focus opens nothing`() {
        assertNull(press(ready(a).copy(focus = 7), Btn.OK, 1).first.ctxTaskId)
    }

    // ── G-INPUT: the overlay ignores input while it is changing ──
    @Test fun `G-INPUT-busy no focus move while switching`() {
        assertEquals(1, press(ready(a, b).copy(busy = true), Btn.LEFT).first.focus)
    }

    @Test fun `G-INPUT-killing no peek during Kill All`() {
        assertEquals(PEEK_NONE, press(ready(a, b).copy(killing = true), Btn.DOWN).first.peek)
    }

    @Test fun `G-INPUT-flying no gear jump during a fly-off`() {
        assertTrue(!press(ready(a, b).copy(flying = 11), Btn.UP).first.gear)
        assertTrue(press(ready(a, b), Btn.UP).first.gear)
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
}
