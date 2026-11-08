package com.websnake.recentstv.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Area TASK — reading and filtering the system task list (docs/flow-map/tasks.md). */
class TaskListTest {

    private val stackList = """
        Stack id=1 bounds=[0,0][1920,1080] displayId=0 userId=0
          taskId=5: com.google.android.tvlauncher/.MainActivity bounds=[0,0][1920,1080] userId=0 visible=true
        Stack id=40 bounds=[0,0][1920,1080] displayId=0 userId=0
          taskId=41: com.netflix.ninja/.MainActivity bounds=[0,0][1920,1080] userId=0 visible=false
          taskId=42: com.netflix.ninja/.PlayerActivity bounds=[0,0][1920,1080] userId=0 visible=false
        Stack id=30 bounds=[0,0][1920,1080] displayId=0 userId=0
          taskId=31: org.videolan.vlc/.StartActivity bounds=[0,0][1920,1080] userId=0 visible=false
    """.trimIndent()

    private val dumpsys = """
        ACTIVITY MANAGER RECENT TASKS (dumpsys activity recents)
          * Recent #0: Task{a1b2c3 #77 type=standard A=10123:com.plex.app U=0 visible=true}
            realActivity=com.plex.app/.Main
          * Recent #1: Task{d4 #78 type=standard U=0}
            realActivity=tv.twitch.android.app/.Main
          * Recent #2: something #79
            baseIntent=Intent { act=android.intent.action.MAIN cmp=com.spotify.tv.android/.Main }
          * Recent #3: Task{ff #80 type=standard U=0}
            nothing useful here
    """.trimIndent()

    @Test fun `P-TASK-01 am stack list parses every task in z-order`() {
        assertEquals(
            listOf(TaskRef(5, "com.google.android.tvlauncher"), TaskRef(41, "com.netflix.ninja"),
                TaskRef(42, "com.netflix.ninja"), TaskRef(31, "org.videolan.vlc")),
            TaskList.parseStackList(stackList),
        )
    }

    @Test fun `P-TASK-02 dumpsys recents - affinity, realActivity and cmp fallbacks, skips unreadable`() {
        assertEquals(
            listOf(TaskRef(77, "com.plex.app"), TaskRef(78, "tv.twitch.android.app"), TaskRef(79, "com.spotify.tv.android")),
            TaskList.parseDumpsysRecents(dumpsys),
        )
    }

    @Test fun `P-TASK-03 dumpsys block with a package but no id keeps -1 (filtered later)`() {
        val t = TaskList.parseDumpsysRecents("* Recent #0:\n realActivity=com.x.y/.Main")
        assertEquals(listOf(TaskRef(-1, "com.x.y")), t)
    }

    @Test fun `P-TASK-04 binder root tasks flatten in order - null and blank entries skipped`() {
        val roots = listOf(
            intArrayOf(5) to arrayOf<String?>("com.google.android.tvlauncher/.Main"),
            null to arrayOf<String?>("x/y"),
            intArrayOf(41, 42, 43) to arrayOf<String?>("com.netflix.ninja/.A", null, "/.NoPkg"),
            intArrayOf(31) to null,
        )
        assertEquals(listOf(TaskRef(5, "com.google.android.tvlauncher"), TaskRef(41, "com.netflix.ninja")), TaskList.fromRootTasks(roots))
    }

    @Test fun `P-TASK-05 read prefers the binder when it has two or more tasks`() {
        var stackCalled = false
        val (t, src) = TaskList.read({ listOf(TaskRef(1, "a"), TaskRef(2, "b")) }, { stackCalled = true; "" }, { "" })
        assertEquals(TaskSource.BINDER, src); assertEquals(2, t.size); assertFalse(stackCalled)
    }

    @Test fun `P-TASK-06 read falls to am stack list when the binder fails or is short`() {
        val (t, src) = TaskList.read({ null }, { stackList }, { error("dumpsys must not run") })
        assertEquals(TaskSource.STACK_LIST, src); assertEquals(4, t.size)
        assertEquals(TaskSource.STACK_LIST, TaskList.read({ listOf(TaskRef(1, "a")) }, { stackList }, { "" }).second)
    }

    @Test fun `P-TASK-07 read falls to dumpsys when both are short`() {
        val (t, src) = TaskList.read({ emptyList() }, { "" }, { dumpsys })
        assertEquals(TaskSource.DUMPSYS, src); assertEquals(3, t.size)
    }

    @Test fun `P-TASK-08 nothing anywhere - the best short list, or NONE`() {
        assertEquals(emptyList<TaskRef>() to TaskSource.NONE, TaskList.read({ null }, { "" }, { "" }))
        assertEquals(TaskSource.BINDER, TaskList.read({ listOf(TaskRef(1, "a")) }, { "" }, { "" }).second)
        val one = "taskId=9: com.only.one/.Main bounds"
        assertEquals(listOf(TaskRef(9, "com.only.one")) to TaskSource.STACK_LIST, TaskList.read({ null }, { one }, { "" }))
    }

    @Test fun `P-TASK-09 select keeps the front-most task per package, drops invalid and excluded, caps`() {
        val raw = listOf(
            TaskRef(-1, "bad.id"), TaskRef(3, ""), TaskRef(41, "com.netflix.ninja"), TaskRef(42, "com.netflix.ninja"),
            TaskRef(50, "excluded.app"), TaskRef(51, "excluded.app"), TaskRef(31, "org.videolan.vlc"),
        )
        val s = TaskList.select(raw, excluded = { it == "excluded.app" })
        assertEquals(listOf(TaskRef(41, "com.netflix.ninja"), TaskRef(31, "org.videolan.vlc")), s)
        assertEquals(1, TaskList.select(raw, excluded = { false }, max = 1).size)
    }

    @Test fun `P-TASK-10 select caps at twelve by default`() {
        val raw = (1..20).map { TaskRef(it, "p$it") }
        assertEquals(TaskList.MAX_TASKS, TaskList.select(raw, { false }).size)
    }

    @Test fun `P-TASK-11 idsFor returns every task of the package, else the fallback`() {
        val raw = listOf(TaskRef(41, "n"), TaskRef(42, "n"), TaskRef(0, "n"), TaskRef(31, "v"))
        assertEquals(listOf(41, 42), TaskList.idsFor(raw, "n", 99))
        assertEquals(listOf(99), TaskList.idsFor(raw, "gone", 99))
    }

    @Test fun `P-TASK-12 home detection by resolved launchers and known launcher prefixes`() {
        assertTrue(TaskList.isHome("com.custom.home", setOf("com.custom.home")))
        assertTrue(TaskList.isHome("com.google.android.apps.tv.launcherx", emptySet()))
        assertFalse(TaskList.isHome("com.netflix.ninja", setOf("com.custom.home")))
    }

    @Test fun `P-TASK-13 dumpsys - an id too large for Int becomes -1`() {
        assertEquals(listOf(TaskRef(-1, "com.big")), TaskList.parseDumpsysRecents("* Recent #0: Task{ab #99999999999 A=1:com.big U=0}"))
    }

    @Test fun `P-TASK-14 all short - the longer list wins, binder on a tie, empty binder loses to stack`() {
        val one = "taskId=9: com.only.one/.Main bounds"
        assertEquals(TaskSource.STACK_LIST, TaskList.read({ emptyList() }, { one }, { "" }).second)
        assertEquals(listOf(TaskRef(1, "a")) to TaskSource.BINDER, TaskList.read({ listOf(TaskRef(1, "a")) }, { one }, { "" }))
        assertEquals(emptyList<TaskRef>() to TaskSource.NONE, TaskList.read({ emptyList() }, { "" }, { "" }))
    }

    // 1.6: the system recents list (getRecentTasks) is read first, as SystemUI / Launcher3 read it
    @Test fun `P-TASK-15 the system recents list wins, in its order, and nothing else is read`() {
        val r = listOf(TaskRef(7, "app.newest"), TaskRef(3, "app.older"))
        val (t, src) = TaskList.read({ error("root tasks must not be read") }, { error("no stack list") }, { error("no dumpsys") }, recents = { r })
        assertEquals(TaskSource.RECENTS, src); assertEquals(r, t)
    }

    @Test fun `P-TASK-16 one app in the recents list is enough (the launcher is never in it)`() {
        assertEquals(listOf(TaskRef(4, "only")) to TaskSource.RECENTS,
            TaskList.read({ error("no root tasks") }, { "" }, { "" }, recents = { listOf(TaskRef(4, "only")) }))
    }

    @Test fun `P-TASK-17 a failed or empty recents read falls back to the root tasks`() {
        val roots = listOf(TaskRef(1, "a"), TaskRef(2, "b"))
        assertEquals(TaskSource.BINDER, TaskList.read({ roots }, { "" }, { "" }, recents = { null }).second)
        assertEquals(TaskSource.BINDER, TaskList.read({ roots }, { "" }, { "" }, recents = { emptyList() }).second)
    }
}
