package com.websnake.recentstv.data

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.websnake.recentstv.logic.Ledger
import com.websnake.recentstv.logic.RowMode
import com.websnake.recentstv.logic.Seen
import com.websnake.recentstv.logic.Stored
import com.websnake.recentstv.logic.TaskList
import com.websnake.recentstv.logic.TaskRef
import com.websnake.recentstv.logic.TaskSource
import com.websnake.recentstv.shizuku.ShizukuManager
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method

/**
 * Reads and changes the system task list through Shizuku, behind one interface so
 * the UI never touches Shizuku directly.
 *
 * The calls are the ones Android's own recents screen makes (SystemUI / Launcher3 through
 * ActivityManagerWrapper and the WM Shell RecentTasksController):
 *  - read    : IActivityTaskManager.getRecentTasks(max, RECENT_IGNORE_UNAVAILABLE, user) — the
 *              system's visible recents, most recent first (shell holds REAL_GET_TASKS) → root-task
 *              infos over the binder → `am stack list` → `dumpsys activity recents`
 *  - switch  : startActivityFromRecents(taskId) (START_TASKS_FROM_RECENTS; also brings back a task
 *              whose app was killed) → moveTaskToFront → monkey (caller adds the intent fallback)
 *  - 1.7     : the row reads the system's STORED recents (`dumpsys activity recents`) — the visible list hides
 *              tasks unused for 6 h on the TV — merged with usage access and a removal ledger (logic/Stored.kt)
 *  - dismiss : force-stop (optional) + removeTask for every task of the package
 *  - killAll : the same for every shown package (not removeAllVisibleRecentTasks: that would also
 *              clear the apps hidden with Exceptions)
 *
 * Found 2026-11-08: with Shizuku as shell (the TV), moveTaskToFront(null package) throws
 * "package=null does not belong to uid=2000", so 1.0–1.5 always fell back to a monkey launch there.
 */
class RecentTasksRepository(private val context: Context) {

    private val pkgRepo = PackageRepository(context)
    private val skipPackages = setOf(context.packageName, "com.android.systemui", ShizukuManager.SHIZUKU_PKG)

    private val homePackages: Set<String> by lazy {
        runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            // real launchers only: the system Settings app registers a FallbackHome at priority -1000
            // (com.android.settings, com.android.tv.settings) and must still show as a recent app
            context.packageManager.queryIntentActivities(intent, 0)
                .filter { it.priority >= 0 }
                .mapTo(HashSet()) { it.activityInfo.packageName }
        }.getOrDefault(emptySet())
    }

    fun ready(): Boolean = ShizukuManager.state().ready

    /** Raw ordered task list (front first) and where it came from. */
    fun readRaw(): Pair<List<TaskRef>, TaskSource> {
        if (!ready()) return emptyList<TaskRef>() to TaskSource.NONE
        homePackages        // resolve the launcher set here, in parallel with the settings read
        return TaskList.read(
            recents = { runCatching { readRecents() }.getOrNull() },
            binder = { runCatching { readViaBinder() }.getOrNull() },
            stackList = { execShell("am stack list") },
            dumpsys = { execShell("dumpsys activity recents") },
        )
    }

    /** The row's apps: running tasks (filtered) and the pinned apps, with labels and icons. */
    data class Resolved(val all: List<RecentTask>, val pins: List<RecentTask>)

    /** Filter, then attach labels, pre-rasterised icons and colours, one app per IO thread. */
    suspend fun resolve(raw: List<TaskRef>, exceptions: Set<String>, pinned: List<String>): Resolved = coroutineScope {
        val selected = TaskList.select(raw, excluded = { pkg ->
            pkg in skipPackages || TaskList.isHome(pkg, homePackages) || pkg in exceptions || !pkgRepo.isUsable(pkg)
        })
        val all = selected.map { t -> async(Dispatchers.IO) { describe(t.pkg, t.taskId) } }
        val pins = pinned.filter { it !in exceptions && it !in skipPackages }.map { p ->
            async(Dispatchers.IO) { if (pkgRepo.isUsable(p)) describe(p, selected.firstOrNull { it.pkg == p }?.taskId) else null }
        }
        Resolved(all.awaitAll(), pins.awaitAll().filterNotNull())
    }

    // ── 1.7: the row from every source ───────────────────────────────────
    /** Not a recent app: this app, SystemUI, Shizuku, launchers, the user's exceptions, disabled/uninstalled apps. */
    private fun hidden(pkg: String, exceptions: Set<String>): Boolean =
        pkg in skipPackages || TaskList.isHome(pkg, homePackages) || pkg in exceptions || !pkgRepo.isUsable(pkg)

    /**
     * Every task the system stores (`dumpsys activity recents`, the store's MRU order), plus the visible recents
     * (getRecentTasks) as a canary for a changed dumpsys format. Null when neither can be read.
     */
    /** 1.10: the app under the overlay at the last [readStoredRow] (null = the launcher / unknown). */
    @Volatile var current: String? = null
        private set

    fun readStoredRow(now: Long): Pair<List<Seen>, RowMode>? {
        current = null
        if (!ready()) return null
        homePackages
        // the two reads run at once: the dumpsys process spawn is the slow part
        val visibleJob = java.util.concurrent.FutureTask { runCatching { readRecentsSeen(now) }.getOrNull().orEmpty() }
        Thread(visibleJob, "rtv-visible").start()
        val stored = Stored.parse(execShell("dumpsys activity recents"))
        val visible = visibleJob.get()
        current = if (stored.isNotEmpty()) Stored.current(stored, context.packageName) else Stored.currentOf(visible, context.packageName)
        if (stored.isNotEmpty()) return Stored.candidates(stored, now, visible) to RowMode.STORED
        if (visible.isNotEmpty()) return visible to RowMode.VISIBLE
        // last resort: the 1.6 chain (root tasks → am stack list), without times (store order kept)
        val (raw, _) = readRaw()
        if (raw.isEmpty()) return null
        return raw.mapIndexed { i, t -> Seen(t.pkg, t.taskId, now - i) } to RowMode.VISIBLE
    }

    /**
     * The row: live [tasks] (trusted) + [soft] entries (usage data; with Shizuku off also the seed), merged with
     * the removal [ledger]. A soft-only app must have a launcher entry so it can be opened.
     */
    suspend fun resolveRow(
        tasks: List<Seen>, soft: List<Seen>, ledger: Ledger, exceptions: Set<String>, pinned: List<String>, max: Int,
    ): Resolved = coroutineScope {
        val taskPkgs = tasks.mapTo(HashSet()) { it.pkg }
        val openable = soft.filter { it.pkg in taskPkgs || pkgRepo.isLaunchable(it.pkg) }
        val rows = Stored.merge(tasks, openable, ledger, excluded = { hidden(it, exceptions) }, max = max)
        val all = rows.map { r -> async(Dispatchers.IO) { describe(r.pkg, r.taskId.takeIf { it > 0 }) } }
        val pins = pinned.filter { it !in exceptions && it !in skipPackages }.map { p ->
            async(Dispatchers.IO) { if (pkgRepo.isUsable(p)) describe(p, rows.firstOrNull { it.pkg == p }?.taskId?.takeIf { it > 0 }) else null }
        }
        Resolved(all.awaitAll(), pins.awaitAll().filterNotNull())
    }

    /** The full merged row (no exceptions, generous cap) to keep as the seed for Shizuku-off opens. */
    fun seedOf(tasks: List<Seen>, soft: List<Seen>, ledger: Ledger): List<Seen> =
        Stored.merge(tasks, soft, ledger, excluded = { it in skipPackages || TaskList.isHome(it, homePackages) }, max = SEED_MAX)

    /**
     * Removals made while Shizuku was off ([pending]: package → when): remove every stored task of that package
     * that was not used again after the removal, force-stopping it first when [forceStop]. Returns how many tasks were removed.
     */
    fun replayPending(pending: Map<String, Long>, now: Long, forceStop: Boolean): Int {
        if (!ready() || pending.isEmpty()) return 0
        val stored = Stored.candidates(Stored.parse(execShell("dumpsys activity recents")), now)
        // an app used again after its removal keeps running and keeps its task
        val reused = stored.filter { t -> pending[t.pkg]?.let { t.lastUsed > it } == true }.mapTo(HashSet()) { it.pkg }
        if (forceStop) for (p in pending.keys) if (p !in reused) forceStop(p)
        var n = 0
        for (t in stored) {
            val at = pending[t.pkg] ?: continue
            if (t.lastUsed <= at && removeTask(t.taskId)) n++
        }
        return n
    }

    /** Usage access survives reboots, Shizuku (shell) does not: while Shizuku is here, let the app keep it. */
    fun grantUsageAccess(): Boolean {
        if (!ready()) return false
        execShell("appops set --user $userId ${context.packageName} GET_USAGE_STATS allow")
        return true
    }

    /** Every task id of every package, stored and visible (for removals: hidden sibling tasks go too). */
    private fun allTaskRefs(): List<TaskRef> {
        // never a home task: the system Settings app also owns FallbackHome (found on the A50: Kill all removed it)
        val stored = Stored.parse(execShell("dumpsys activity recents")).filter { it.taskId > 0 && !it.home }.map { TaskRef(it.taskId, it.pkg) }
        return if (stored.isNotEmpty()) stored + readRaw().first.filter { r -> stored.none { it.taskId == r.taskId } } else readRaw().first
    }

    private fun describe(pkg: String, taskId: Int?): RecentTask {
        val (label, category) = pkgRepo.labelAndCategory(pkg)
        return RecentTask(taskId, pkg, label, pkgRepo.icon(pkg), category, pkgRepo.brand(pkg))
    }

    // ── binder read ──────────────────────────────────────────────────────
    private val userId get() = android.os.Process.myUid() / 100_000     // UserHandle.getUserId

    /** The system's recents list, most recent first: (taskId, package of the task's base intent). */
    private fun readRecents(): List<TaskRef>? {
        val atm = activityTaskManager() ?: return null
        val m = atm.javaClass.getMethod("getRecentTasks", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val slice = m.invoke(atm, RECENTS_MAX, RECENT_IGNORE_UNAVAILABLE, userId) ?: return null
        val list = slice.javaClass.getMethod("getList").invoke(slice) as? List<*> ?: return null
        return list.filterNotNull().mapNotNull { info ->
            // TaskInfo.taskId (Android 10+), RecentTaskInfo.persistentId before that
            val id = (readField(info, "taskId") ?: readField(info, "persistentId")) as? Int ?: return@mapNotNull null
            val cmp = (readField(info, "baseIntent") as? Intent)?.component
                ?: readField(info, "realActivity") as? android.content.ComponentName
                ?: readField(info, "baseActivity") as? android.content.ComponentName
            cmp?.packageName?.let { TaskRef(id, it) }
        }
    }

    /** getRecentTasks with wall-clock times (lastActiveTime counts from elapsedRealtime). */
    private fun readRecentsSeen(now: Long): List<Seen>? {
        val atm = activityTaskManager() ?: return null
        val m = atm.javaClass.getMethod("getRecentTasks", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val slice = m.invoke(atm, RECENTS_MAX, RECENT_IGNORE_UNAVAILABLE, userId) ?: return null
        val list = slice.javaClass.getMethod("getList").invoke(slice) as? List<*> ?: return null
        val up = android.os.SystemClock.elapsedRealtime()
        return list.filterNotNull().mapNotNull { info ->
            val id = (readField(info, "taskId") ?: readField(info, "persistentId")) as? Int ?: return@mapNotNull null
            val cmp = (readField(info, "baseIntent") as? Intent)?.component
                ?: readField(info, "realActivity") as? android.content.ComponentName ?: return@mapNotNull null
            val active = readField(info, "lastActiveTime") as? Long ?: up
            Seen(cmp.packageName, id, now - (up - active).coerceAtLeast(0))
        }
    }

    private var rootTasksMethod: Method? = null

    private fun readViaBinder(): List<TaskRef>? {
        val atm = activityTaskManager() ?: return null
        val m = rootTasksMethod ?: (
            runCatching { atm.javaClass.getMethod("getAllRootTaskInfos") }.getOrNull()      // Android 12+
                ?: runCatching { atm.javaClass.getMethod("getAllStackInfos") }.getOrNull()  // Android 10–11
            )?.also { rootTasksMethod = it } ?: return null
        val list = m.invoke(atm) as? List<*> ?: return null
        val roots = list.filterNotNull().map { info ->
            // Android 12+ RootTaskInfo: childTaskIds/childTaskNames; Android 10–11 StackInfo: taskIds/taskNames
            val ids = (readField(info, "childTaskIds") ?: readField(info, "taskIds")) as? IntArray
            @Suppress("UNCHECKED_CAST")
            val names = (readField(info, "childTaskNames") ?: readField(info, "taskNames")) as? Array<String?>
            ids to names
        }
        return TaskList.fromRootTasks(roots)
    }

    // ── actions ──────────────────────────────────────────────────────────
    /** Bring a recent task back. False → caller launches via intent. */
    fun switchTo(pkg: String, taskId: Int): Boolean {
        if (!ready()) return false
        // what the system recents screen does: also restarts a task whose app was killed
        val fromRecents = runCatching {
            val atm = activityTaskManager() ?: return@runCatching false
            val r = atm.javaClass.getMethod("startActivityFromRecents", Int::class.javaPrimitiveType, android.os.Bundle::class.java)
                .invoke(atm, taskId, null) as? Int
            Log.i(TAG, "startActivityFromRecents($taskId)=$r")
            r != null && r in 0..99          // ActivityManager.isStartResultSuccessful
        }.getOrElse { Log.w(TAG, "startActivityFromRecents($taskId) failed: ${it.cause ?: it}"); false }
        if (fromRecents) return true
        // moveTaskToFront(IApplicationThread, String callingPackage, int taskId, int flags, Bundle) —
        // signatures vary across ROMs: taskId in the first int slot, the caller's package (the shell
        // identity Shizuku runs as; any package passes as root) in the String slot, 0/null elsewhere.
        val moved = runCatching {
            val atm = activityTaskManager() ?: return@runCatching false
            val m = atm.javaClass.methods.firstOrNull {
                it.name == "moveTaskToFront" && it.parameterTypes.any { p -> p == Int::class.javaPrimitiveType }
            } ?: return@runCatching false
            var firstInt = true
            val args = m.parameterTypes.map { p ->
                if (p == Int::class.javaPrimitiveType) (if (firstInt) taskId else 0).also { firstInt = false }
                else if (p == String::class.java) SHELL_PKG
                else nullFor(p)
            }.toTypedArray()
            m.invoke(atm, *args)
            true
        }.getOrElse { Log.w(TAG, "moveTaskToFront($taskId) failed: ${it.cause ?: it}"); false }
        if (moved) return true
        return execShell("monkey -p $pkg -c android.intent.category.LEANBACK_LAUNCHER 1")
            .contains("Events injected")
    }

    /** Remove a recent entry: every task of the package (else it reappears from a
     *  sibling task), force-stopping first so the app can't recreate them. */
    fun dismiss(taskId: Int, pkg: String, forceStop: Boolean): Boolean {
        if (!ready()) return false
        if (forceStop) forceStop(pkg)
        var ok = false
        for (id in TaskList.idsFor(allTaskRefs(), pkg, taskId)) if (removeTask(id)) ok = true
        return ok
    }

    fun killAll(pkgs: Set<String>, forceStop: Boolean) {
        if (!ready()) return
        if (forceStop) for (p in pkgs) forceStop(p)
        for ((id, pkg) in allTaskRefs()) if (pkg in pkgs) removeTask(id)
    }

    private fun removeTask(taskId: Int): Boolean = runCatching {
        val atm = activityTaskManager() ?: return false
        val r = atm.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType).invoke(atm, taskId)
        Log.i(TAG, "removeTask($taskId)=$r")
        (r as? Boolean) ?: true
    }.getOrElse {
        Log.w(TAG, "removeTask($taskId) failed: ${it.message}")
        false
    }

    /** Privileged binder first, then ALWAYS the shell `am force-stop` (shell holds
     *  FORCE_STOP_PACKAGES) so the process is reliably killed. */
    private fun forceStop(pkg: String) {
        runCatching {
            activityManager()?.let { iam ->
                iam.javaClass.getMethod("forceStopPackage", String::class.java, Int::class.javaPrimitiveType)
                    .invoke(iam, pkg, userId)
            }
        }
        execShell("am force-stop --user $userId $pkg")
    }

    // ── privileged binder handles (cached per process) ───────────────────
    private fun activityTaskManager(): Any? = atm ?: runCatching {
        val stub = Class.forName("android.app.IActivityTaskManager\$Stub")
        val asInterface: Method = stub.getMethod("asInterface", IBinder::class.java)
        asInterface.invoke(null, ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity_task")))
    }.getOrNull()?.also { atm = it }

    private fun activityManager(): Any? = runCatching {
        val stub = Class.forName("android.app.IActivityManager\$Stub")
        val asInterface: Method = stub.getMethod("asInterface", IBinder::class.java)
        asInterface.invoke(null, ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity")))
    }.getOrNull()

    private fun readField(obj: Any, name: String): Any? = runCatching {
        var c: Class<*>? = obj.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(name); f.isAccessible = true; return f.get(obj)
            } catch (_: NoSuchFieldException) { c = c.superclass }
        }
        null
    }.getOrNull()

    private fun nullFor(p: Class<*>): Any? = when (p) {
        Int::class.javaPrimitiveType -> 0
        Boolean::class.javaPrimitiveType -> false
        Long::class.javaPrimitiveType -> 0L
        else -> null
    }

    private fun execShell(command: String): String = runCatching {
        val newProcess = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        newProcess.isAccessible = true
        // stderr dropped so a chatty command can't fill the pipe and deadlock waitFor()
        val process = newProcess.invoke(null, arrayOf("sh", "-c", "$command 2>/dev/null"), null, null) as Process
        BufferedReader(InputStreamReader(process.inputStream)).use { r ->
            val text = r.readText()
            process.waitFor()
            text
        }
    }.getOrElse { Log.w(TAG, "shell failed: ${it.message}"); "" }

    companion object {
        const val TAG = "RTV"
        private const val RECENTS_MAX = 30       // the row shows 12; room for skipped and hidden apps
        private const val RECENT_IGNORE_UNAVAILABLE = 0x0002      // ActivityManager.RECENT_IGNORE_UNAVAILABLE
        private const val SHELL_PKG = "com.android.shell"
        private const val SEED_MAX = 40
        // The binder proxy dies with Shizuku; dropped when its binder dies (see ShizukuManager).
        @Volatile private var atm: Any? = null
        fun dropBinders() { atm = null }
    }
}
