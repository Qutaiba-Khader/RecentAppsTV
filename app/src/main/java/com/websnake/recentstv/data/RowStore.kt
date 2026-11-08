package com.websnake.recentstv.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.websnake.recentstv.logic.Ledger
import com.websnake.recentstv.logic.Seen
import com.websnake.recentstv.logic.Stored

/**
 * What the row remembers between opens, in one small SharedPreferences file — nothing runs while the overlay
 * is hidden; everything is read and written when it opens or when the user acts:
 *  - the removal ledger (tombstones + the Kill all epoch),
 *  - the seed: the last full row, shown when Shizuku is off (after a TV reboot),
 *  - removals made while Shizuku was off, replayed as removeTask when it is back,
 *  - usage access: the newest resume per app and a cursor, so each open reads only the events since the last.
 */
class RowStore(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ── ledger ──────────────────────────────────────────────────────────
    fun ledger(): Ledger = Ledger(decodeTimes(prefs.getString(K_TOMB, null)), prefs.getLong(K_EPOCH, 0L))

    fun saveLedger(l: Ledger) {
        prefs.edit().putString(K_TOMB, encodeTimes(l.tombstones)).putLong(K_EPOCH, l.epoch).apply()
    }

    // ── seed ────────────────────────────────────────────────────────────
    fun seed(): List<Seen> = (prefs.getString(K_SEED, null) ?: "").lineSequence().mapNotNull { line ->
        val f = line.split('\t')
        if (f.size != 3) null else Seen(f[0], f[1].toIntOrNull() ?: -1, f[2].toLongOrNull() ?: return@mapNotNull null)
    }.toList()

    fun saveSeed(rows: List<Seen>) {
        prefs.edit().putString(K_SEED, rows.joinToString("\n") { "${it.pkg}\t${it.taskId}\t${it.lastUsed}" }).apply()
    }

    // ── removals waiting for Shizuku ───────────────────────────────────
    fun pending(): Map<String, Long> = decodeTimes(prefs.getString(K_PENDING, null))

    fun addPending(pkgs: Collection<String>, at: Long) {
        val m = pending().toMutableMap()
        for (p in pkgs) m[p] = at
        prefs.edit().putString(K_PENDING, encodeTimes(m)).apply()
    }

    fun clearPending() { prefs.edit().remove(K_PENDING).apply() }

    // ── usage access ───────────────────────────────────────────────────
    fun hasUsageAccess(): Boolean = runCatching {
        val ops = app.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = if (android.os.Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName)
        else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /**
     * The newest resume per app over the last [USAGE_DAYS] days. Only the events since the previous read are
     * fetched (the system keeps them; no service of ours has to watch), folded into the saved map.
     */
    fun usage(now: Long): List<Seen> {
        if (!hasUsageAccess()) return emptyList()
        val since = now - USAGE_DAYS * DAY_MS
        val cursor = prefs.getLong(K_CURSOR, 0L).coerceAtLeast(since)
        val known = decodeTimes(prefs.getString(K_USAGE, null))
        val events = ArrayList<Pair<String, Long>>()
        runCatching {
            val usm = app.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val ev = usm.queryEvents(cursor - OVERLAP_MS, now) ?: return@runCatching
            val e = UsageEvents.Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                if (e.eventType == RESUMED && e.packageName != app.packageName) events.add(e.packageName to e.timeStamp)
            }
        }
        val folded = Stored.foldUsage(known, events, since)
        prefs.edit().putString(K_USAGE, encodeTimes(folded)).putLong(K_CURSOR, now).apply()
        return folded.map { (p, t) -> Seen(p, -1, t) }
    }

    private fun encodeTimes(m: Map<String, Long>) = m.entries.joinToString("\n") { "${it.key}\t${it.value}" }
    private fun decodeTimes(s: String?): Map<String, Long> = (s ?: "").lineSequence().mapNotNull { line ->
        val f = line.split('\t')
        if (f.size == 2) f[1].toLongOrNull()?.let { f[0] to it } else null
    }.toMap()

    companion object {
        private const val FILE = "row_state"
        private const val K_TOMB = "tombstones"
        private const val K_EPOCH = "kill_epoch"
        private const val K_SEED = "seed"
        private const val K_PENDING = "pending_removals"
        private const val K_USAGE = "usage_last"
        private const val K_CURSOR = "usage_cursor"
        const val USAGE_DAYS = 3L
        const val DAY_MS = 86_400_000L
        private const val OVERLAP_MS = 5_000L
        private const val RESUMED = 1          // UsageEvents.Event.ACTIVITY_RESUMED (MOVE_TO_FOREGROUND before API 29)
    }
}
