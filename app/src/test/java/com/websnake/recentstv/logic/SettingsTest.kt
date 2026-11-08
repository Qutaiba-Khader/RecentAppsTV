package com.websnake.recentstv.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Area SET — stored settings and the Settings / Exceptions keys (docs/flow-map/settings.md). */
class SettingsTest {

    private val apps = listOf("app.a", "app.b", "app.c")
    private fun key(st: SetState, s: Settings, b: Btn) = SettingsNav.key(st, s, b, apps)
    private fun saved(e: List<SetEffect>) = (e.single() as SetEffect.Save).settings

    @Test fun `P-SET-01 decode of an empty store gives the defaults (design values)`() {
        val s = SettingsCodec.decode { null }
        assertEquals(Settings(), s); assertEquals(66, s.bgDim); assertEquals(CardSize.MEDIUM, s.size)
    }

    @Test fun `P-SET-02 encode then decode round-trips every field (v1 key names)`() {
        val s = Settings(false, CardSize.LARGE, CardShape.CIRCLE, setOf("x"), Accent.PINK, 40, Tint.NAVY, false)
        val m = SettingsCodec.encode(s)
        assertEquals(s, SettingsCodec.decode { m[it] })
        assertTrue(m.keys.containsAll(listOf("show_kill_all", "bg_dim_pct", "force_stop_on_kill", "exceptions")))
    }

    @Test fun `P-SET-03 unknown names, wrong types and out-of-range dim fall back safely`() {
        val m = mapOf<String, Any>("size" to "HUGE", "shape" to 3, "bg_dim_pct" to 250, "show_kill_all" to "yes")
        val s = SettingsCodec.decode { m[it] }
        assertEquals(CardSize.MEDIUM, s.size); assertEquals(CardShape.ROUNDED, s.shape)
        assertEquals(100, s.bgDim); assertEquals(true, s.showKillAll)
        assertEquals(0, SettingsCodec.decode { if (it == "bg_dim_pct") -5 else null }.bgDim)
    }

    @Test fun `P-SET-04 summary and count labels`() {
        assertEquals("No apps hidden", SettingsCodec.exceptionsSummary(0))
        assertEquals("1 app hidden", SettingsCodec.exceptionsSummary(1))
        assertEquals("3 apps hidden", SettingsCodec.exceptionsSummary(3))
        assertEquals("", SettingsCodec.countLabel(0)); assertEquals("1 app", SettingsCodec.countLabel(1)); assertEquals("8 apps", SettingsCodec.countLabel(8))
    }

    @Test fun `P-SET-05 UP DOWN move between rows and clamp`() {
        val s = Settings()
        assertEquals(0, key(SetState(focus = 0), s, Btn.UP).first.focus)
        assertEquals(1, key(SetState(focus = 0), s, Btn.DOWN).first.focus)
        val last = SetRow.entries.lastIndex
        assertEquals(last, key(SetState(focus = last), s, Btn.DOWN).first.focus)
    }

    @Test fun `P-SET-06 toggle rows flip with OK, LEFT or RIGHT`() {
        val s = Settings()
        for (b in listOf(Btn.OK, Btn.LEFT, Btn.RIGHT)) {
            assertEquals(false, saved(key(SetState(focus = SetRow.KILL_ALL.ordinal), s, b).second).showKillAll)
            assertEquals(false, saved(key(SetState(focus = SetRow.FORCE_STOP.ordinal), s, b).second).forceStopOnKill)
        }
    }

    @Test fun `P-SET-07 segmented rows step RTL - RIGHT earlier, LEFT later, OK cycles`() {
        val st = SetState(focus = SetRow.SIZE.ordinal)
        assertEquals(CardSize.SMALL, saved(key(st, Settings(), Btn.RIGHT).second).size)
        assertEquals(CardSize.LARGE, saved(key(st, Settings(), Btn.LEFT).second).size)
        assertEquals(CardSize.SMALL, saved(key(st, Settings(size = CardSize.LARGE), Btn.OK).second).size)
        // at the end, a step outward changes nothing → nothing saved
        assertTrue(key(st, Settings(size = CardSize.LARGE), Btn.LEFT).second.isEmpty())
    }

    @Test fun `P-SET-08 shape accent and tint rows step the same way`() {
        assertEquals(CardShape.CIRCLE, saved(key(SetState(focus = SetRow.SHAPE.ordinal), Settings(), Btn.LEFT).second).shape)
        assertEquals(Accent.BLUE, saved(key(SetState(focus = SetRow.ACCENT.ordinal), Settings(), Btn.OK).second).accent)
        assertEquals(Tint.BLACK, saved(key(SetState(focus = SetRow.BG_TINT.ordinal), Settings(), Btn.RIGHT).second).bgTint)
    }

    @Test fun `P-SET-09 dim bar - LEFT and OK raise by 5, RIGHT lowers, clamped 0 to 100`() {
        val st = SetState(focus = SetRow.BG_DIM.ordinal)
        assertEquals(71, saved(key(st, Settings(), Btn.LEFT).second).bgDim)
        assertEquals(71, saved(key(st, Settings(), Btn.OK).second).bgDim)
        assertEquals(61, saved(key(st, Settings(), Btn.RIGHT).second).bgDim)
        assertTrue(key(st, Settings(bgDim = 100), Btn.LEFT).second.isEmpty())
        assertTrue(key(st, Settings(bgDim = 0), Btn.RIGHT).second.isEmpty())
    }

    @Test fun `P-SET-10 Exceptions row - OK or LEFT opens and loads apps, RIGHT does nothing`() {
        val st = SetState(focus = SetRow.EXCEPTIONS.ordinal, excFocus = 4)
        for (b in listOf(Btn.OK, Btn.LEFT)) {
            val (n, e) = key(st, Settings(), b)
            assertEquals(SetScreen.EXCEPTIONS, n.screen); assertEquals(0, n.excFocus); assertEquals(listOf(SetEffect.LoadApps), e)
        }
        assertEquals(st to emptyList<SetEffect>(), key(st, Settings(), Btn.RIGHT))
        assertEquals(Settings(), SettingsNav.edit(SetRow.EXCEPTIONS, Settings(), Btn.OK))
    }

    @Test fun `P-SET-11 BACK on Settings finishes, other keys do nothing`() {
        assertEquals(listOf(SetEffect.Finish), key(SetState(), Settings(), Btn.BACK).second)
        assertEquals(SetState() to emptyList<SetEffect>(), key(SetState(), Settings(), Btn.MENU))
    }

    @Test fun `P-SET-12 Exceptions - UP DOWN clamp to the list, OK toggles the focused app`() {
        val st = SetState(screen = SetScreen.EXCEPTIONS, excFocus = 0)
        assertEquals(0, key(st, Settings(), Btn.UP).first.excFocus)
        assertEquals(2, key(st.copy(excFocus = 2), Settings(), Btn.DOWN).first.excFocus)
        assertEquals(setOf("app.b"), saved(key(st.copy(excFocus = 1), Settings(), Btn.OK).second).exceptions)
        assertEquals(emptySet<String>(), saved(key(st.copy(excFocus = 1), Settings(exceptions = setOf("app.b")), Btn.OK).second).exceptions)
    }

    @Test fun `P-SET-13 Exceptions - BACK or RIGHT returns - OK on an empty list does nothing`() {
        val st = SetState(screen = SetScreen.EXCEPTIONS)
        assertEquals(SetScreen.SETTINGS, key(st, Settings(), Btn.BACK).first.screen)
        assertEquals(SetScreen.SETTINGS, key(st, Settings(), Btn.RIGHT).first.screen)
        val empty = SettingsNav.key(st, Settings(), Btn.OK, emptyList())
        assertTrue(empty.second.isEmpty())
        assertEquals(0, SettingsNav.key(st, Settings(), Btn.DOWN, emptyList()).first.excFocus)
        assertEquals(st, key(st, Settings(), Btn.LEFT).first)
    }

    @Test fun `P-SET-14 step with an unknown current value starts from the first`() {
        assertEquals("b", SettingsNav.step(listOf("a", "b"), "zz", Btn.OK))
        assertEquals("zz", SettingsNav.step(listOf("a", "b"), "zz", Btn.UP))
    }

    @Test fun `P-SET-15 toggles switch back on as well`() {
        val off = Settings(showKillAll = false, forceStopOnKill = false)
        assertEquals(true, SettingsNav.edit(SetRow.KILL_ALL, off, Btn.OK).showKillAll)
        assertEquals(true, SettingsNav.edit(SetRow.FORCE_STOP, off, Btn.OK).forceStopOnKill)
    }
}
