package com.websnake.recentstv.logic

/* 1.7: the row is built from every source the device has, merged by one pure function.

   - The system's STORED recents (`dumpsys activity recents`, through Shizuku): every task the system keeps,
     not only the "visible" few — on the TV the visible list (getRecentTasks) hides any task unused for more
     than 6 h (config_activeTaskDurationHours), so it showed 2 apps while 47 were stored. Each stored task
     reopens where it was with startActivityFromRecents.
   - USAGE ACCESS (UsageStatsManager events, read on demand at open — no background service: the system
     already keeps the events): apps used recently that own no stored task.
   - The last row saved on disk (SEED), so with Shizuku off (after a TV reboot) the row is still there.
   - A REMOVAL LEDGER: an app removed in the row (↑↑, Kill all) stays out of the soft sources until it is
     used again after the removal, so usage data can't bring it back. */

/** One task from the system's stored recents, in the store's own order (most recent first). */
data class StoredTask(
    val taskId: Int,
    val pkg: String,
    val inactiveMs: Long,           // "inactive for Ns"; -1 = not printed
    val home: Boolean,              // type=home: the launcher, never a recent app
    val excluded: Boolean,          // started with FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS (dialogs, choosers, overlays)
    val available: Boolean,
)

/** A row candidate: [taskId] > 0 resumes a stored task, otherwise the app is launched. [lastUsed] is wall-clock ms. */
data class Seen(val pkg: String, val taskId: Int, val lastUsed: Long)

/** Removals made in the row. [tombstones]: package → when it was removed. [epoch]: the last Kill all. */
data class Ledger(val tombstones: Map<String, Long> = emptyMap(), val epoch: Long = 0L) {
    fun removedAt(pkg: String): Long = maxOf(tombstones[pkg] ?: 0L, epoch)
}

/** Where this open's row came from (logged). */
enum class RowMode { STORED, VISIBLE, LIMITED, NONE }

object Stored {
    /** Apps in the row (1.6 showed up to 12 of the visible few; the store holds many more). */
    const val ROW_MAX = 20
    private const val FLAG_EXCLUDE_FROM_RECENTS = 0x00800000

    private val block = Regex("\\* Recent #\\d+:")
    private val headId = Regex("#(\\d+)")
    private val component = Regex("(?:mActivityComponent|realActivity)=([\\w.]+)/")
    private val intentCmp = Regex("intent=\\{[^}]*cmp=([\\w.]+)/")
    private val intentPkg = Regex("intent=\\{[^}]*pkg=([\\w.]+)")
    private val headIntent = Regex(" I=([\\w.]+)/")
    private val affinity = Regex(" A=\\d+:([a-zA-Z][\\w.]*)")
    private val flags = Regex("intent=\\{[^}]*flg=0x([0-9a-fA-F]+)")
    private val inactive = Regex("inactive for (\\d+)s")
    private val type = Regex("type=(\\w+)")
    private val avail = Regex("isAvailable=(\\w+)")

    /** `dumpsys activity recents` → every stored task, in the store's order. Blocks without an id or a package are skipped. */
    fun parse(text: String): List<StoredTask> {
        val out = ArrayList<StoredTask>()
        for (b in text.split(block).drop(1)) {
            val head = b.substringBefore('\n')
            val id = headId.grab(head)?.toIntOrNull() ?: continue
            val pkg = component.grab(b) ?: intentCmp.grab(b) ?: intentPkg.grab(b) ?: headIntent.grab(head) ?: affinity.grab(head) ?: continue
            val flg = flags.grab(b)?.toLongOrNull(16) ?: 0L
            out.add(
                StoredTask(
                    taskId = id,
                    pkg = pkg,
                    inactiveMs = inactive.grab(b)?.toLongOrNull()?.times(1000) ?: -1L,
                    home = type.grab(head) == "home",
                    excluded = flg and FLAG_EXCLUDE_FROM_RECENTS.toLong() != 0L,
                    available = avail.grab(b) != "false",
                )
            )
        }
        return out
    }

    private fun Regex.grab(s: CharSequence): String? {
        val m = find(s) ?: return null
        return m.groupValues[1]
    }

    /**
     * 1.10: the app under the overlay — the first stored task (the store is in MRU order) that is not this app ([self])
     * or a task started excluded from recents (another overlay/dialog). Null when that is the launcher (home).
     */
    fun current(stored: List<StoredTask>, self: String): String? {
        val top = stored.firstOrNull { it.pkg != self && !it.excluded } ?: return null
        return if (top.home) null else top.pkg
    }

    /** The same from a list without a home flag (the visible recents, or usage resumes): the newest that isn't [self]. */
    fun currentOf(seen: List<Seen>, self: String): String? =
        seen.filter { it.pkg != self }.maxByOrNull { it.lastUsed }?.pkg

    /**
     * Stored tasks → row candidates, newest first, with wall-clock times. The store's order is the truth (it is
     * the system's MRU order, kept across reboots); "inactive for" only gives each task a time to merge usage
     * against, so it is clamped never to run ahead of the task before it (restored tasks may all report the boot).
     * Home, excluded and unavailable tasks are dropped; [visible] tasks the store didn't print are added (a
     * canary for a dumpsys format change).
     */
    fun candidates(stored: List<StoredTask>, now: Long, visible: List<Seen> = emptyList()): List<Seen> {
        val out = ArrayList<Seen>()
        var prev = now
        for (t in stored) {
            val at = if (t.inactiveMs >= 0) minOf(prev, now - t.inactiveMs) else prev
            prev = at
            if (t.home || t.excluded || !t.available || t.taskId <= 0) continue
            out.add(Seen(t.pkg, t.taskId, at))
        }
        val ids = out.mapTo(HashSet()) { it.taskId }
        for (v in visible) if (v.taskId !in ids) out.add(v)
        return out.sortedByDescending { it.lastUsed }
    }

    /**
     * The row: [tasks] (live stored tasks — trusted, the system already dropped what was removed) plus [soft]
     * entries (usage events and, with Shizuku off, the saved seed). A soft entry shows only if it was used after
     * its removal in the [ledger]; an app with a task keeps the task (to resume) and the newer of the two times.
     * One entry per package, newest first, at most [max] (apps with a task first), filtered by [excluded].
     */
    fun merge(
        tasks: List<Seen>,
        soft: List<Seen>,
        ledger: Ledger,
        excluded: (String) -> Boolean,
        max: Int = ROW_MAX,
    ): List<Seen> {
        val byPkg = LinkedHashMap<String, Seen>()
        for (t in tasks) {
            val cur = byPkg[t.pkg]
            if (cur == null || t.lastUsed > cur.lastUsed) byPkg[t.pkg] = t
        }
        for (s in soft) {
            if (s.lastUsed <= ledger.removedAt(s.pkg)) continue
            val cur = byPkg[s.pkg]
            byPkg[s.pkg] = when {
                cur == null -> s
                cur.taskId > 0 -> cur.copy(lastUsed = maxOf(cur.lastUsed, s.lastUsed))
                s.taskId > 0 || s.lastUsed > cur.lastUsed -> s.copy(lastUsed = maxOf(cur.lastUsed, s.lastUsed))
                else -> cur
            }
        }
        // apps with a task always get a place first: usage-only apps only fill what is left (found on the A50:
        // 13 usage-only apps pushed older stored tasks out of the row, so Kill all left them behind)
        val sorted = byPkg.values.filter { !excluded(it.pkg) }.sortedByDescending { it.lastUsed }
        val withTask = sorted.filter { it.taskId > 0 }.take(max)
        val usageOnly = sorted.filter { it.taskId <= 0 }.take(max - withTask.size)
        return (withTask + usageOnly).sortedByDescending { it.lastUsed }
    }

    /** Usage events (package, resume time) → the newest resume per package, merged into [known] and pruned to [since]. */
    fun foldUsage(known: Map<String, Long>, events: List<Pair<String, Long>>, since: Long): Map<String, Long> {
        val m = HashMap<String, Long>()
        for ((p, t) in known) if (t >= since) m[p] = t
        for ((p, t) in events) if (t >= since && t > (m[p] ?: Long.MIN_VALUE)) m[p] = t
        return m
    }

    /** A removal in the row: a tombstone for one app, or (Kill all) a new epoch that makes every older tombstone moot. */
    fun remove(l: Ledger, pkg: String, now: Long): Ledger = l.copy(tombstones = l.tombstones + (pkg to now))
    fun killAll(l: Ledger, now: Long): Ledger = Ledger(emptyMap(), now)

    /** Drop tombstones older than [since] (their apps fell out of the usage window anyway) or already covered by the epoch. */
    fun prune(l: Ledger, since: Long): Ledger = l.copy(tombstones = l.tombstones.filterValues { it >= since && it > l.epoch })
}
