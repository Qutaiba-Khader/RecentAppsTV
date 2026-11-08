package com.websnake.recentstv.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Area STORED — the 1.7 row: stored recents + usage access + removal ledger (docs/flow-map/stored.md). */
class StoredTest {

    // Shapes copied from the Google TV Streamer (Android 14) and the A50 (Android 11), 2026-11-09.
    private val tv = """
        ACTIVITY MANAGER RECENT TASKS (dumpsys activity recents)
        mRecentsUid=-1
          Recent tasks:
          * Recent #0: Task{6f9c078 #32950 type=standard A=10131:ar.tvplayer.tv}
            userId=0 effectiveUid=u0a131 mCallingUid=u0a130 mCallingPackage=com.spocky.projengmenu
            intent={act=android.intent.action.MAIN cat=[android.intent.category.LEANBACK_LAUNCHER] flg=0x10000000 pkg=ar.tvplayer.tv cmp=ar.tvplayer.tv/.ui.MainActivity}
            mActivityComponent=ar.tvplayer.tv/.ui.MainActivity
            askedCompatMode=false inRecents=true isAvailable=true
            lastActiveTime=4304489355 (inactive for 2045s)
          * Recent #1: Task{f22ddc #32949 type=standard I=com.websnake.recentstv/.RecentsActivity}
            intent={act=android.intent.action.MAIN cat=[android.intent.category.LEANBACK_LAUNCHER] flg=0x10800000 pkg=com.websnake.recentstv cmp=com.websnake.recentstv/.RecentsActivity}
            mActivityComponent=com.websnake.recentstv/.RecentsActivity
            isAvailable=true
            lastActiveTime=4294047536 (inactive for 12494s)
          * Recent #2: Task{bc2736 #32825 type=home I=com.spocky.projengmenu/.ui.home.MainActivity}
            mActivityComponent=com.spocky.projengmenu/.ui.home.MainActivity
            lastActiveTime=4292788315 (inactive for 13753s)
          * Recent #3: Task{422e204 #32675 type=standard A=10150:rikka.appops}
            intent={act=android.intent.action.MAIN flg=0x10200000 cmp=rikka.appops/.home.HomeActivity}
            isAvailable=false
            lastActiveTime=4283515983 (inactive for 23026s)
          * Recent #4: Task{3fef764 #32874 type=standard A=10169:.activities.ImageViewerActivity}
            intent={act=android.intent.action.VIEW flg=0x10000000 pkg=com.mixplorer.silver}
            lastActiveTime=4133609918 (inactive for 172932s)
          * Recent #5: Task{87ea156 #32856 type=standard A=10171:org.smarttube.stable:1996066746}
            lastActiveTime=4124264297 (inactive for 182277s)
          * Recent #6: Task{d4 type=standard A=10171:no.id.here}
          * Recent #7: Task{ab #32800 type=standard}
            nothing to name it by
    """.trimIndent()

    private val a50 = """
          * Recent #2: Task{8ead01b #1388 visible=false type=standard mode=fullscreen translucent=true I=com.websnake.recentstv/.RecentsActivity U=0 StackId=1388 sz=0}
            intent={flg=0x10800000 cmp=com.websnake.recentstv/.RecentsActivity}
            realActivity=com.websnake.recentstv/.RecentsActivity
            lastActiveTime=2412400841 (inactive for 1195s)
          * Recent #3: Task{977b266 #1385 visible=false type=standard I=com.sec.android.app.clockpackage/.ClockPackage U=0}
            lastActiveTime=2412000000
    """.trimIndent()

    @Test fun `P-STO-01 TV store - component, intent pkg and affinity fallbacks, flags, home, availability, idle time`() {
        val t = Stored.parse(tv)
        assertEquals(listOf(32950, 32949, 32825, 32675, 32874, 32856), t.map { it.taskId })
        assertEquals(listOf("ar.tvplayer.tv", "com.websnake.recentstv", "com.spocky.projengmenu", "rikka.appops",
            "com.mixplorer.silver", "org.smarttube.stable"), t.map { it.pkg })
        assertEquals(StoredTask(32950, "ar.tvplayer.tv", 2_045_000, home = false, excluded = false, available = true), t[0])
        assertTrue(t[1].excluded)                       // flg 0x10800000 has FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        assertTrue(t[2].home)
        assertEquals(false, t[3].available)
        assertEquals(false, t[5].excluded)              // no intent line: no flags
    }

    @Test fun `P-STO-02 A50 store - realActivity, head I= fallback, no idle time gives -1`() {
        val t = Stored.parse(a50)
        assertEquals(listOf(TaskRef(1388, "com.websnake.recentstv"), TaskRef(1385, "com.sec.android.app.clockpackage")), t.map { TaskRef(it.taskId, it.pkg) })
        assertEquals(1_195_000L, t[0].inactiveMs)
        assertEquals(-1L, t[1].inactiveMs)
    }

    @Test fun `P-STO-03 unreadable numbers - an id too big is skipped, huge flags and idle time fall back`() {
        val t = Stored.parse(
            "* Recent #0: Task{a #99999999999 A=1:com.big}\n" +
                "* Recent #1: Task{b #5 A=1:com.ok}\n intent={flg=0x1ffffffffffffffff cmp=com.ok/.M}\n (inactive for 99999999999999999999s)"
        )
        assertEquals(listOf(StoredTask(5, "com.ok", -1, home = false, excluded = false, available = true)), t)
    }

    @Test fun `P-STO-04 candidates - store order kept, times from idle clamped, home excluded unavailable dropped`() {
        val now = 10_000_000L
        val stored = listOf(
            StoredTask(1, "a", 1_000, false, false, true),
            StoredTask(2, "b", 500, false, false, true),       // restored task reporting a newer time: clamped to a's
            StoredTask(3, "launcher", 9_000, true, false, true),
            StoredTask(4, "dialog", 9_000, false, true, true),
            StoredTask(5, "gone", 9_000, false, false, false),
            StoredTask(0, "zero", 9_000, false, false, true),
            StoredTask(6, "c", -1, false, false, true),        // no idle time: takes the one before it
        )
        assertEquals(listOf(Seen("a", 1, now - 1_000), Seen("b", 2, now - 1_000), Seen("c", 6, now - 9_000)), Stored.candidates(stored, now))
    }

    @Test fun `P-STO-05 candidates - a visible task the store missed is added, a duplicate is not`() {
        val now = 1_000L
        val c = Stored.candidates(listOf(StoredTask(1, "a", 100, false, false, true)), now,
            visible = listOf(Seen("a", 1, 999), Seen("v", 9, 950)))
        assertEquals(listOf(Seen("v", 9, 950), Seen("a", 1, 900)), c)
    }

    @Test fun `P-STO-06 merge - a task wins over usage for the same app and takes the newer time`() {
        val r = Stored.merge(
            tasks = listOf(Seen("a", 1, 100), Seen("a", 2, 50), Seen("a", 3, 120), Seen("b", 4, 90)),
            soft = listOf(Seen("a", -1, 200), Seen("b", -1, 10)),
            ledger = Ledger(), excluded = { false },
        )
        assertEquals(listOf(Seen("a", 3, 200), Seen("b", 4, 90)), r)
    }

    @Test fun `P-STO-07 merge - a usage-only app shows, a removed one only if used after its removal`() {
        val ledger = Ledger(tombstones = mapOf("swiped" to 100L, "reopened" to 100L), epoch = 50L)
        val r = Stored.merge(
            tasks = emptyList(),
            soft = listOf(Seen("fresh", -1, 300), Seen("swiped", -1, 90), Seen("reopened", -1, 150), Seen("old", -1, 40)),
            ledger = ledger, excluded = { false },
        )
        assertEquals(listOf("fresh", "reopened"), r.map { it.pkg })
    }

    @Test fun `P-STO-08 merge - seed task beats a usage entry, newer usage beats older usage, older is ignored`() {
        val r = Stored.merge(
            tasks = emptyList(),
            soft = listOf(Seen("a", -1, 100), Seen("a", 7, 80), Seen("b", -1, 50), Seen("b", -1, 70), Seen("c", -1, 60), Seen("c", -1, 20)),
            ledger = Ledger(), excluded = { false },
        )
        assertEquals(listOf(Seen("a", 7, 100), Seen("b", -1, 70), Seen("c", -1, 60)), r)
    }

    @Test fun `P-STO-09 merge - excluded apps dropped, newest first, capped`() {
        val soft = (1..30).map { Seen("p$it", -1, it.toLong()) }
        val r = Stored.merge(emptyList(), soft, Ledger(), excluded = { it == "p30" }, max = 3)
        assertEquals(listOf("p29", "p28", "p27"), r.map { it.pkg })
        assertEquals(Stored.ROW_MAX, Stored.merge(emptyList(), soft, Ledger(), excluded = { false }).size)
        assertEquals(listOf("STORED", "VISIBLE", "LIMITED", "NONE"), RowMode.entries.map { it.name })   // the logged sources
    }

    @Test fun `P-STO-10 foldUsage - newest resume per app, pruned to the window`() {
        val m = Stored.foldUsage(
            known = mapOf("old" to 5L, "kept" to 20L, "bumped" to 20L),
            events = listOf("bumped" to 30L, "kept" to 15L, "new" to 25L, "ancient" to 1L),
            since = 10L,
        )
        assertEquals(mapOf("kept" to 20L, "bumped" to 30L, "new" to 25L), m)
    }

    @Test fun `P-STO-11 ledger - remove stamps one app, Kill all sets the epoch and clears tombstones, prune drops stale`() {
        val l = Stored.remove(Ledger(), "a", 100)
        assertEquals(100L, l.removedAt("a")); assertEquals(0L, l.removedAt("b"))
        val k = Stored.killAll(Stored.remove(l, "b", 120), 200)
        assertEquals(Ledger(emptyMap(), 200), k)
        assertEquals(200L, k.removedAt("a"))
        val p = Stored.prune(Ledger(mapOf("stale" to 5L, "covered" to 40L, "live" to 90L), epoch = 50L), since = 10L)
        assertEquals(mapOf("live" to 90L), p.tombstones)
    }

    @Test fun `P-STO-12 merge - usage-only apps never push an app with a task out of the row`() {
        val tasks = (1..3).map { Seen("t$it", it, it.toLong()) }                 // old tasks
        val soft = (1..5).map { Seen("u$it", -1, 100L + it) }                     // newer usage-only apps
        val r = Stored.merge(tasks, soft, Ledger(), excluded = { false }, max = 4)
        assertEquals(listOf("u5", "t3", "t2", "t1"), r.map { it.pkg })
        assertEquals(3, Stored.merge(tasks + Seen("t9", 9, 0), emptyList(), Ledger(), { false }, max = 3).size)
    }

    @Test fun `P-STO-13 current - the first stored task that is not this app nor excluded, null when it is home`() {
        val me = "com.websnake.recentstv"
        val t = { id: Int, pkg: String, home: Boolean, excl: Boolean -> StoredTask(id, pkg, 0, home, excl, true) }
        assertEquals("ar.tvplayer.tv", Stored.current(listOf(t(1, me, false, true), t(2, "dlg", false, true), t(3, "ar.tvplayer.tv", false, false), t(4, "x", false, false)), me))
        assertEquals(null, Stored.current(listOf(t(1, me, false, true), t(2, "launcher", true, false), t(3, "x", false, false)), me))
        assertEquals(null, Stored.current(listOf(t(1, me, false, false)), me))
        assertEquals("b", Stored.currentOf(listOf(Seen(me, 1, 99), Seen("a", 2, 10), Seen("b", -1, 20)), me))   // newest that isn't me
        assertEquals(null, Stored.currentOf(listOf(Seen(me, 1, 99)), me))
    }
}
