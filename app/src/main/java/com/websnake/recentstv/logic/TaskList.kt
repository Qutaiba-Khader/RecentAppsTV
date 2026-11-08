package com.websnake.recentstv.logic

/** One task as read from the system, before labels and icons are attached. */
data class TaskRef(val taskId: Int, val pkg: String)

/** Where a task list came from (logged, so a slow device can be diagnosed). */
enum class TaskSource { RECENTS, BINDER, STACK_LIST, DUMPSYS, NONE }

object TaskList {
    const val MAX_TASKS = 12

    private val stackLine = Regex("taskId=(\\d+): ([\\w.]+)/")
    private val recentBlock = Regex("\\* Recent #\\d+:")
    private val idRe = Regex("Task\\{[0-9a-fA-F]+ #(\\d+)")
    private val bareIdRe = Regex("#(\\d+)")
    private val affRe = Regex("A=\\d+:([\\w.]+)")
    private val realRe = Regex("realActivity=([\\w.]+)/")
    private val cmpRe = Regex("baseIntent=.*?cmp=([\\w.]+)/")

    /** `am stack list` output → tasks in z-order (front first). */
    fun parseStackList(text: String): List<TaskRef> =
        stackLine.findAll(text).map { TaskRef(it.groupValues[1].toInt(), it.groupValues[2]) }.toList()

    /** `dumpsys activity recents` output → tasks in MRU order. */
    fun parseDumpsysRecents(text: String): List<TaskRef> {
        val out = ArrayList<TaskRef>()
        for (b in text.split(recentBlock).drop(1)) {
            val pkgMatch = affRe.find(b) ?: realRe.find(b) ?: cmpRe.find(b) ?: continue
            val idMatch = idRe.find(b) ?: bareIdRe.find(b)
            val taskId = idMatch?.let { it.groupValues[1].toIntOrNull() } ?: -1   // -1 is dropped by select()
            out.add(TaskRef(taskId, pkgMatch.groupValues[1]))
        }
        return out
    }

    /**
     * Root-task infos read over the binder (the same data `am stack list` prints):
     * per root task, its child task ids and flattened component names ("pkg/cls").
     */
    fun fromRootTasks(roots: List<Pair<IntArray?, Array<String?>?>>): List<TaskRef> {
        val out = ArrayList<TaskRef>()
        for ((ids, names) in roots) {
            if (ids == null || names == null) continue
            for (i in ids.indices) {
                val pkg = (names.getOrNull(i) ?: continue).substringBefore('/')
                if (pkg.isNotBlank()) out.add(TaskRef(ids[i], pkg))
            }
        }
        return out
    }

    /**
     * Pick the source. First the system's own recents list ([recents]: getRecentTasks, most
     * recent first, as SystemUI / Launcher3 read it — the launcher is never in it, so one app is
     * enough). Then the root tasks over the binder (no process spawn), then `am stack list`,
     * then the heavier dumpsys; for those a list of fewer than two tasks is only the launcher,
     * so the next source is tried. Each source is only read when the one before falls short.
     */
    fun read(
        binder: () -> List<TaskRef>?,
        stackList: () -> String,
        dumpsys: () -> String,
        recents: () -> List<TaskRef>? = { null },
    ): Pair<List<TaskRef>, TaskSource> {
        val r = recents()
        if (!r.isNullOrEmpty()) return r to TaskSource.RECENTS
        val b = binder()
        if (b != null && b.size >= 2) return b to TaskSource.BINDER
        val s = parseStackList(stackList())
        if (s.size >= 2) return s to TaskSource.STACK_LIST
        val d = parseDumpsysRecents(dumpsys())
        if (d.isNotEmpty()) return d to TaskSource.DUMPSYS
        // all short: keep the longer of the two (the binder on a tie)
        if (b != null && b.size >= s.size) return b to (if (b.isEmpty()) TaskSource.NONE else TaskSource.BINDER)
        return s to (if (s.isEmpty()) TaskSource.NONE else TaskSource.STACK_LIST)
    }

    /** Tasks to show: valid, not excluded, one per package (the front-most), capped. */
    fun select(raw: List<TaskRef>, excluded: (String) -> Boolean, max: Int = MAX_TASKS): List<TaskRef> {
        val seen = HashSet<String>()
        val out = ArrayList<TaskRef>()
        for (t in raw) {
            if (out.size >= max) break
            if (t.taskId <= 0 || t.pkg.isBlank() || t.pkg in seen) continue
            seen.add(t.pkg)                  // a package's first task decides, kept or not
            if (excluded(t.pkg)) continue
            out.add(t)
        }
        return out
    }

    /** Every task id a package owns (an app can own several; all must go on dismiss). */
    fun idsFor(raw: List<TaskRef>, pkg: String, fallback: Int): List<Int> =
        raw.filter { it.pkg == pkg && it.taskId > 0 }.map { it.taskId }.ifEmpty { listOf(fallback) }

    /** Launcher/home packages that never count as a recent app. */
    private val launcherHints = listOf(
        "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx",
        "com.google.android.leanbacklauncher",
        "com.android.launcher",
    )

    fun isHome(pkg: String, homePackages: Set<String>): Boolean =
        pkg in homePackages || launcherHints.any { pkg.startsWith(it) }
}
