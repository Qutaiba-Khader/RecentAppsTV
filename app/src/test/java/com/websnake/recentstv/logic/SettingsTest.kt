package com.websnake.recentstv.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Area SET — stored settings and the Settings / Pinned apps / Exceptions keys (docs/flow-map/settings.md). */
class SettingsTest {

    private val apps = listOf("app.a", "app.b", "app.c")
    private fun key(st: SetState, s: Settings, b: Btn) = SettingsNav.key(st, s, b, apps)
    private fun saved(e: List<SetEffect>) = (e.first() as SetEffect.Save).settings
    private fun at(row: SetRow, s: Settings = Settings()) = SetState(focus = SettingsNav.rows(s).indexOf(row))

    @Test fun `P-SET-01 decode of an empty store gives the defaults (design values)`() {
        val s = SettingsCodec.decode { null }
        assertEquals(Settings(), s); assertEquals(66, s.bgDim); assertEquals(CardSize.MEDIUM, s.size)
        assertEquals(PinPosition.LEFT, s.pinPosition); assertEquals(PinSize.SMALL, s.pinSize); assertEquals(Backdrop.DIM, s.background)
        assertTrue(s.pinned.isEmpty()); assertNull(s.bgImage)
    }

    @Test fun `P-SET-02 encode then decode round-trips every field (v1 key names kept)`() {
        val s = Settings(false, CardSize.LARGE, CardShape.CIRCLE, setOf("x"), Accent.PINK, 40, false,
            listOf("p.b", "p.a"), PinPosition.RIGHT, PinSize.SAME, Backdrop.IMAGE, 3, "wall.jpg")
        val m = SettingsCodec.encode(s)
        assertEquals(s, SettingsCodec.decode { m[it] })
        assertTrue(m.keys.containsAll(listOf("show_kill_all", "bg_dim_pct", "force_stop_on_kill", "exceptions", "pinned")))
    }

    @Test fun `P-SET-03 unknown names, wrong types and out-of-range numbers fall back safely`() {
        val m = mapOf<String, Any>("size" to "HUGE", "shape" to 3, "bg_dim_pct" to 250, "show_kill_all" to "yes",
            "bg_color" to 99, "bg_image" to "  ", "pinned" to 5)
        val s = SettingsCodec.decode { m[it] }
        assertEquals(CardSize.MEDIUM, s.size); assertEquals(CardShape.ROUNDED, s.shape)
        assertEquals(100, s.bgDim); assertEquals(true, s.showKillAll); assertEquals(4, s.bgColor)
        assertNull(s.bgImage); assertTrue(s.pinned.isEmpty())
        assertEquals(0, SettingsCodec.decode { if (it == "bg_dim_pct") -5 else null }.bgDim)
    }

    @Test fun `P-SET-04 the pin list keeps its order, drops blanks and repeats`() {
        assertEquals(listOf("b", "a"), SettingsCodec.decode { if (it == "pinned") "b\n\n a \nb\na" else null }.pinned)
        assertEquals("", SettingsCodec.encode(Settings())["bg_image"])
    }

    @Test fun `P-SET-05 summary and count labels`() {
        assertEquals("No apps hidden", SettingsCodec.exceptionsSummary(0))
        assertEquals("1 app hidden", SettingsCodec.exceptionsSummary(1))
        assertEquals("3 apps hidden", SettingsCodec.exceptionsSummary(3))
        assertEquals("No apps pinned", SettingsCodec.pinnedSummary(0))
        assertEquals("1 app pinned", SettingsCodec.pinnedSummary(1))
        assertEquals("2 apps pinned", SettingsCodec.pinnedSummary(2))
        assertEquals("", SettingsCodec.countLabel(0)); assertEquals("1 app", SettingsCodec.countLabel(1))
        assertEquals("7 apps · 3 pinned", SettingsCodec.countLabel(7, 3)); assertEquals("2 pinned", SettingsCodec.countLabel(0, 2))
    }

    @Test fun `P-SET-06 rows follow the Background choice and carry their section titles`() {
        assertTrue(SetRow.BG_DIM in SettingsNav.rows(Settings()))
        val color = SettingsNav.rows(Settings(background = Backdrop.COLOR))
        assertTrue(SetRow.BG_COLOR in color && SetRow.BG_DIM !in color && SetRow.BG_IMAGE !in color)
        assertTrue(SetRow.BG_IMAGE in SettingsNav.rows(Settings(background = Backdrop.IMAGE)))
        assertEquals(10, SettingsNav.rows(Settings(background = Backdrop.BLUR)).size)
        assertEquals(listOf("Overlay", "Pinned apps", "Background", "Hidden apps"), SetRow.entries.mapNotNull { it.section() })
    }

    @Test fun `P-SET-07 UP DOWN move between rows and clamp`() {
        val s = Settings()
        assertEquals(0, key(SetState(focus = 0), s, Btn.UP).first.focus)
        assertEquals(1, key(SetState(focus = 0), s, Btn.DOWN).first.focus)
        val last = SettingsNav.rows(s).lastIndex
        assertEquals(last, key(SetState(focus = last), s, Btn.DOWN).first.focus)
    }

    @Test fun `P-SET-08 toggle rows flip with OK, LEFT or RIGHT, both ways`() {
        for (b in listOf(Btn.OK, Btn.LEFT, Btn.RIGHT)) {
            assertEquals(false, saved(key(at(SetRow.KILL_ALL), Settings(), b).second).showKillAll)
            assertEquals(false, saved(key(at(SetRow.FORCE_STOP), Settings(), b).second).forceStopOnKill)
        }
        val off = Settings(showKillAll = false, forceStopOnKill = false)
        assertEquals(true, SettingsNav.edit(SetRow.KILL_ALL, off, Btn.OK).showKillAll)
        assertEquals(true, SettingsNav.edit(SetRow.FORCE_STOP, off, Btn.OK).forceStopOnKill)
    }

    @Test fun `P-SET-09 segmented rows step RTL - RIGHT earlier, LEFT later, OK cycles, ends save nothing`() {
        assertEquals(CardSize.SMALL, saved(key(at(SetRow.SIZE), Settings(), Btn.RIGHT).second).size)
        assertEquals(CardSize.LARGE, saved(key(at(SetRow.SIZE), Settings(), Btn.LEFT).second).size)
        assertEquals(CardSize.SMALL, saved(key(at(SetRow.SIZE), Settings(size = CardSize.LARGE), Btn.OK).second).size)
        assertTrue(key(at(SetRow.SIZE), Settings(size = CardSize.LARGE), Btn.LEFT).second.isEmpty())
        assertEquals(CardShape.CIRCLE, saved(key(at(SetRow.SHAPE), Settings(), Btn.LEFT).second).shape)
        assertEquals(Accent.BLUE, saved(key(at(SetRow.ACCENT), Settings(), Btn.OK).second).accent)
        assertEquals(PinSize.SAME, saved(key(at(SetRow.PIN_SIZE), Settings(), Btn.LEFT).second).pinSize)
        assertEquals(Backdrop.BLUR, saved(key(at(SetRow.BACKGROUND), Settings(), Btn.LEFT).second).background)
    }

    @Test fun `P-SET-10 Pinned position and colour swatches read literally (LTR)`() {
        assertEquals(PinPosition.RIGHT, saved(key(at(SetRow.PIN_POSITION), Settings(), Btn.RIGHT).second).pinPosition)
        assertEquals(PinPosition.LEFT, saved(key(at(SetRow.PIN_POSITION), Settings(pinPosition = PinPosition.RIGHT), Btn.LEFT).second).pinPosition)
        val c = Settings(background = Backdrop.COLOR)
        assertEquals(1, saved(key(at(SetRow.BG_COLOR, c), c, Btn.RIGHT).second).bgColor)
        assertTrue(key(at(SetRow.BG_COLOR, c), c, Btn.LEFT).second.isEmpty())
    }

    @Test fun `P-SET-11 dim bar - LEFT and OK raise by 5, RIGHT lowers, clamped 0 to 100`() {
        val st = at(SetRow.BG_DIM)
        assertEquals(71, saved(key(st, Settings(), Btn.LEFT).second).bgDim)
        assertEquals(71, saved(key(st, Settings(), Btn.OK).second).bgDim)
        assertEquals(61, saved(key(st, Settings(), Btn.RIGHT).second).bgDim)
        assertTrue(key(st, Settings(bgDim = 100), Btn.LEFT).second.isEmpty())
        assertTrue(key(st, Settings(bgDim = 0), Btn.RIGHT).second.isEmpty())
    }

    @Test fun `P-SET-12 Exceptions and Pinned apps rows - OK or LEFT open a list and load apps, RIGHT nothing`() {
        for (b in listOf(Btn.OK, Btn.LEFT)) {
            val (n, e) = key(at(SetRow.EXCEPTIONS).copy(listFocus = 5, listAct = 1), Settings(), b)
            assertEquals(SetScreen.EXCEPTIONS, n.screen); assertEquals(1, n.listFocus); assertEquals(0, n.listAct)
            assertEquals(listOf(SetEffect.LoadApps), e)
            assertEquals(SetScreen.PINNED, key(at(SetRow.PINNED_LIST), Settings(), b).first.screen)
        }
        assertEquals(at(SetRow.EXCEPTIONS) to emptyList<SetEffect>(), key(at(SetRow.EXCEPTIONS), Settings(), Btn.RIGHT))
        assertEquals(Settings(), SettingsNav.edit(SetRow.EXCEPTIONS, Settings(), Btn.OK))
    }

    @Test fun `P-SET-13 Background image row - OK or LEFT open the picture screen and load it, RIGHT nothing`() {
        val s = Settings(background = Backdrop.IMAGE)
        val (n, e) = key(at(SetRow.BG_IMAGE, s).copy(listFocus = 7, listAct = 2), s, Btn.OK)
        assertEquals(SetScreen.IMAGES, n.screen); assertEquals(1, n.listFocus); assertEquals(0, n.listAct)
        assertEquals(listOf(SetEffect.LoadImages), e)
        assertTrue(key(at(SetRow.BG_IMAGE, s), s, Btn.RIGHT).second.isEmpty())
        assertTrue(SettingsNav.bulkActions(SetScreen.IMAGES).isEmpty())
    }

    @Test fun `P-SET-14 BACK on Settings finishes, other keys do nothing`() {
        assertEquals(listOf(SetEffect.Finish), key(SetState(), Settings(), Btn.BACK).second)
        assertEquals(SetState() to emptyList<SetEffect>(), key(SetState(), Settings(), Btn.MENU))
    }

    @Test fun `P-SET-15 list - UP reaches the bulk bar, DOWN clamps to the list, OK toggles the row`() {
        val st = SetState(screen = SetScreen.EXCEPTIONS, listFocus = 1)
        assertEquals(0, key(st, Settings(), Btn.UP).first.listFocus)
        assertEquals(0, key(st.copy(listFocus = 0), Settings(), Btn.UP).first.listFocus)
        assertEquals(3, key(st.copy(listFocus = 3), Settings(), Btn.DOWN).first.listFocus)
        assertEquals(setOf("app.b"), saved(key(st.copy(listFocus = 2), Settings(), Btn.OK).second).exceptions)
        assertEquals(emptySet<String>(), saved(key(st.copy(listFocus = 2), Settings(exceptions = setOf("app.b")), Btn.OK).second).exceptions)
    }

    @Test fun `P-SET-16 Pinned list - OK pins at the end, again unpins`() {
        val st = SetState(screen = SetScreen.PINNED, listFocus = 1)
        assertEquals(listOf("z", "app.a"), saved(key(st, Settings(pinned = listOf("z")), Btn.OK).second).pinned)
        assertEquals(listOf("z"), saved(key(st, Settings(pinned = listOf("app.a", "z")), Btn.OK).second).pinned)
    }

    @Test fun `P-SET-17 bulk bar - LEFT RIGHT move between buttons, OK runs Hide all, Show all, Unpin all`() {
        val bar = SetState(screen = SetScreen.EXCEPTIONS, listFocus = 0)
        assertEquals(1, key(bar, Settings(), Btn.LEFT).first.listAct)
        assertEquals(1, key(bar.copy(listAct = 1), Settings(), Btn.LEFT).first.listAct)
        assertEquals(0, key(bar.copy(listAct = 1), Settings(), Btn.RIGHT).first.listAct)
        assertEquals(0, key(bar, Settings(), Btn.RIGHT).first.listAct)
        val (_, hide) = key(bar, Settings(exceptions = setOf("x")), Btn.OK)
        assertEquals(setOf("x") + apps, saved(hide).exceptions); assertEquals(SetEffect.Bulk(BulkAction.HIDE_ALL), hide[1])
        assertTrue(saved(key(bar.copy(listAct = 1), Settings(exceptions = setOf("x")), Btn.OK).second).exceptions.isEmpty())
        val pins = SetState(screen = SetScreen.PINNED, listFocus = 0)
        assertTrue(saved(key(pins, Settings(pinned = listOf("a")), Btn.OK).second).pinned.isEmpty())
        assertEquals(pins.copy(listAct = 5) to emptyList<SetEffect>(), key(pins.copy(listAct = 5), Settings(), Btn.OK))
    }

    @Test fun `P-SET-18 list - BACK or RIGHT on a row return, OK past the list does nothing`() {
        val st = SetState(screen = SetScreen.PINNED, listFocus = 2)
        assertEquals(SetScreen.SETTINGS, key(st, Settings(), Btn.BACK).first.screen)
        assertEquals(SetScreen.SETTINGS, key(st, Settings(), Btn.RIGHT).first.screen)
        assertEquals(st, key(st, Settings(), Btn.LEFT).first)
        assertTrue(SettingsNav.key(st, Settings(), Btn.OK, emptyList()).second.isEmpty())
        assertEquals(0, SettingsNav.key(st.copy(listFocus = 0), Settings(), Btn.DOWN, emptyList()).first.listFocus)
        assertEquals(st to emptyList<SetEffect>(), key(st, Settings(), Btn.MENU))
        assertTrue(SettingsNav.bulkActions(SetScreen.SETTINGS).isEmpty())
    }

    @Test fun `P-SET-19 step with an unknown current value starts from the first`() {
        assertEquals("b", SettingsNav.step(listOf("a", "b"), "zz", Btn.OK))
        assertEquals("zz", SettingsNav.step(listOf("a", "b"), "zz", Btn.UP))
    }

    @Test fun `P-SET-20 setPin from the overlay adds last or removes`() {
        assertEquals(listOf("b", "a"), SettingsNav.setPin(Settings(pinned = listOf("a", "b")), "a", true).pinned)
        assertEquals(listOf("b"), SettingsNav.setPin(Settings(pinned = listOf("a", "b")), "a", false).pinned)
    }

    // ── picture screen: bar of picker apps (focus 0) over a 4-wide RTL grid (1..n) ──
    private val pics = List(10) { "p$it" }
    private fun img(f: Int, b: Btn, pickers: Int = 3, n: List<String> = pics, act: Int = 0) =
        SettingsNav.key(SetState(screen = SetScreen.IMAGES, listFocus = f, listAct = act), Settings(), b, n, pickers)

    @Test fun `P-SET-21 pictures - LEFT is the next picture (RTL), RIGHT the previous, both stop at the row ends`() {
        assertEquals(2, img(1, Btn.LEFT).first.listFocus)
        assertEquals(4, img(4, Btn.LEFT).first.listFocus)          // last column
        assertEquals(10, img(10, Btn.LEFT).first.listFocus)        // last picture
        assertEquals(1, img(2, Btn.RIGHT).first.listFocus)
        assertEquals(5, img(5, Btn.RIGHT).first.listFocus)         // first column
    }

    @Test fun `P-SET-22 pictures - UP DOWN by a row, the first row goes up to the bar, a short last row clamps`() {
        assertEquals(6, img(2, Btn.DOWN).first.listFocus)
        assertEquals(10, img(7, Btn.DOWN).first.listFocus)         // row 2 → the shorter last row
        assertEquals(10, img(10, Btn.DOWN).first.listFocus)
        assertEquals(9, img(9, Btn.DOWN).first.listFocus)          // already on the last row
        assertEquals(2, img(6, Btn.UP).first.listFocus)
        assertEquals(0, img(3, Btn.UP).first.listFocus)
        assertEquals(1, img(0, Btn.DOWN).first.listFocus)
        assertEquals(0, img(0, Btn.DOWN, n = emptyList()).first.listFocus)
    }

    @Test fun `P-SET-23 pictures - OK uses the picture and returns to Settings`() {
        val (n, e) = img(3, Btn.OK)
        assertEquals(SetScreen.SETTINGS, n.screen); assertEquals(listOf(SetEffect.UseImage(2)), e)
    }

    @Test fun `P-SET-24 picker bar - LEFT RIGHT move between apps, OK opens the focused one`() {
        assertEquals(1, img(0, Btn.LEFT).first.listAct)
        assertEquals(2, img(0, Btn.LEFT, act = 2).first.listAct)
        assertEquals(0, img(0, Btn.RIGHT).first.listAct)
        assertEquals(1, img(0, Btn.RIGHT, act = 2).first.listAct)
        assertEquals(listOf(SetEffect.OpenWith(1)), img(0, Btn.OK, act = 1).second)
        assertTrue(img(0, Btn.OK, pickers = 0).second.isEmpty())
        assertEquals(0, img(0, Btn.LEFT, pickers = 0).first.listAct)
    }

    @Test fun `P-SET-25 pictures - with none loaded the focus is the bar - BACK returns, other keys nothing`() {
        assertEquals(listOf(SetEffect.OpenWith(0)), img(1, Btn.OK, n = emptyList()).second)
        assertEquals(SetScreen.SETTINGS, img(4, Btn.BACK).first.screen)
        assertTrue(img(4, Btn.MENU).second.isEmpty())
    }
}
