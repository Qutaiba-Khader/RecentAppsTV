package com.websnake.recentstv

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.websnake.recentstv.data.BackdropImage
import com.websnake.recentstv.data.RecentTask
import com.websnake.recentstv.data.RecentTasksRepository
import com.websnake.recentstv.data.RowStore
import com.websnake.recentstv.data.SettingsStore
import com.websnake.recentstv.logic.Backdrop
import com.websnake.recentstv.logic.Btn
import com.websnake.recentstv.logic.Effect
import com.websnake.recentstv.logic.KeyIn
import com.websnake.recentstv.logic.OvState
import com.websnake.recentstv.logic.Overlay
import com.websnake.recentstv.logic.PinPosition
import com.websnake.recentstv.logic.Scene
import com.websnake.recentstv.logic.RowMode
import com.websnake.recentstv.logic.Seen
import com.websnake.recentstv.logic.Settings
import com.websnake.recentstv.logic.Stored
import com.websnake.recentstv.logic.SettingsNav
import com.websnake.recentstv.logic.TaskRef
import com.websnake.recentstv.shizuku.ShizukuManager
import com.websnake.recentstv.ui.overlay.RecentsOverlay
import com.websnake.recentstv.ui.overlay.Toast
import com.websnake.recentstv.ui.state.EmptyState
import com.websnake.recentstv.ui.state.ShizukuGate
import com.websnake.recentstv.ui.theme.BackdropSwatches
import com.websnake.recentstv.ui.theme.Motion
import com.websnake.recentstv.ui.theme.Tokens
import com.websnake.recentstv.ui.theme.RecentsTheme
import com.websnake.recentstv.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * The overlay: a translucent window over the running app.
 *
 * Speed: the activity is never finished on close — it moves its own task to the back,
 * so the next open is a hot start (no process, activity or composition to build).
 * While hidden it holds no state and runs nothing: no service, no timer, no listener
 * that wakes the process.
 */
class RecentsActivity : ComponentActivity() {

    private lateinit var repo: RecentTasksRepository
    private lateinit var store: SettingsStore
    private lateinit var rows: RowStore

    private var st by mutableStateOf(OvState())
    private var info by mutableStateOf<Map<String, RecentTask>>(emptyMap())
    private var settings by mutableStateOf(Settings())
    private var loadId by mutableIntStateOf(0)
    private var scrimOn by mutableStateOf(false)
    private var contentOn by mutableStateOf(false)
    private var toast by mutableStateOf<String?>(null)
    private var backdropImage by mutableStateOf<ImageBitmap?>(null)
    private var shizukuRunning by mutableStateOf(false)   // gate wording: running but not granted
    private var limited by mutableStateOf(false)      // Shizuku off: the row comes from the seed + usage access
    private var toastJob: Job? = null
    private var selectJob: Job? = null

    private var reloadJob: Job? = null
    private var openedAt = 0L
    @Volatile private var tRaw = 0L

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ -> reload() }
    // Shizuku's binder attaches asynchronously after a cold start: re-resolve the scene
    // when it arrives, but only while still waiting for it.
    private val binderReceived = Shizuku.OnBinderReceivedListener {
        if (st.scene == Scene.LOADING || st.scene == Scene.SHIZUKU || limited) reload()
    }
    private val binderDead = Shizuku.OnBinderDeadListener {
        RecentTasksRepository.dropBinders()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) st = Overlay.needShizuku(st)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = RecentTasksRepository(applicationContext)
        store = SettingsStore(applicationContext)
        rows = RowStore(applicationContext)
        ShizukuManager.addPermissionListener(permListener)
        runCatching { Shizuku.addBinderReceivedListenerSticky(binderReceived) }
        runCatching { Shizuku.addBinderDeadListener(binderDead) }

        setContent {
            RecentsTheme(accent = settings.accent.color) {
                val scrim by animateFloatAsState(
                    if (scrimOn) 1f else 0f, tween(if (scrimOn) Motion.SCRIM_MS else Motion.EXIT_MS), label = "scrim",
                )
                val content by animateFloatAsState(
                    if (contentOn) 1f else 0f,
                    tween(if (contentOn) Motion.SCROLL_MS else Motion.EXIT_MS, easing = Motion.standard), label = "content",
                )
                val rise = with(LocalDensity.current) { 24.dp.toPx() }
                val bg = settings.background
                val blurOn = bg == Backdrop.BLUR && blurSupported
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        if (scrim <= 0f) return@drawBehind
                        when {
                            bg == Backdrop.COLOR -> drawRect(BackdropSwatches[settings.bgColor].second, alpha = 0.94f * scrim)
                            bg == Backdrop.IMAGE -> if (backdropImage == null) drawRect(Tokens.bg, alpha = 0.94f * scrim)
                            blurOn -> drawRect(Color.Black, alpha = 0.42f * scrim)
                            else -> drawRect(Color.Black, alpha = settings.bgDim / 100f * scrim)
                        }
                    },
                ) {
                    val img = backdropImage
                    if (bg == Backdrop.IMAGE && img != null) {
                        Image(
                            img, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = scrim }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.62f))))
                                },
                        )
                    }
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            alpha = content
                            translationY = (1f - content) * rise          // the row eases up into place
                        },
                    ) {
                        when (st.scene) {
                            Scene.LOADING -> {}
                            Scene.SHIZUKU -> ShizukuGate(focus = st.shzFocus, running = shizukuRunning)
                            Scene.EMPTY -> EmptyState()
                            Scene.OVERLAY -> RecentsOverlay(
                                state = st, info = info, settings = settings, loadId = loadId,
                                onFlyComplete = ::onFlyComplete, limited = limited,
                            )
                        }
                    }
                    Toast(toast)
                }
            }
        }
    }

    /** Background → Blur needs Android 12 and a device that allows cross-window blur; else it falls back to Dim. */
    private val blurSupported: Boolean by lazy {
        android.os.Build.VERSION.SDK_INT >= 31 && runCatching { windowManager.isCrossWindowBlurEnabled }.getOrDefault(false)
    }

    private fun applyBlur(on: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < 31 || !blurSupported) return
        runCatching { window.setBackgroundBlurRadius(if (on) (28 * resources.displayMetrics.density).toInt() else 0) }
    }

    override fun onStart() {
        super.onStart()
        openedAt = SystemClock.uptimeMillis()
        scrimOn = true                       // the scrim fades in at once, while the list loads
        reload()                             // start reading now, not at onResume
    }

    /**
     * True from onResume until onStop: the overlay is on screen. A launch that arrives now is the user pressing the
     * same button again (onPause → onNewIntent → onResume), so it closes the overlay. A launch that brings a hidden
     * overlay back arrives after onStop cleared this (onRestart → onStart → onNewIntent → onResume) and opens it.
     */
    private var shown = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!shown) return
        val (n, effects) = Overlay.relaunched(st)
        st = n
        effects.forEach(::run)
    }

    override fun onResume() {
        super.onResume()
        shown = true
        // back from the Shizuku app / permission dialog: re-check
        if (st.scene == Scene.SHIZUKU) reload()
    }

    override fun onStop() {
        super.onStop()
        shown = false
        // Hidden: hold nothing, so the next open starts clean (no stale frame) and the
        // process keeps no bitmaps or animations alive while asleep.
        reloadJob?.cancel()
        st = OvState(scene = Scene.LOADING, showKillAll = st.showKillAll)
        info = emptyMap()
        selectJob?.cancel()
        holdJob?.cancel()
        toastJob?.cancel()
        toast = null
        backdropImage = null          // decoded again on the next open: nothing held while hidden
        applyBlur(false)
        scrimOn = false
        contentOn = false
    }

    override fun onDestroy() {
        super.onDestroy()
        ShizukuManager.removePermissionListener(permListener)
        runCatching { Shizuku.removeBinderReceivedListener(binderReceived) }
        runCatching { Shizuku.removeBinderDeadListener(binderDead) }
    }

    // ── loading ────────────────────────────────────────────────────────────
    private fun reload() {
        reloadJob?.cancel()
        reloadJob = lifecycleScope.launch {
            val t0 = SystemClock.uptimeMillis()
            val now = System.currentTimeMillis()
            val sDef = async(Dispatchers.IO) { store.current() }
            val shz = ShizukuManager.state()
            val ready = shz.ready
            shizukuRunning = shz.binderAlive
            // Shizuku off (a TV reboot): the last row + usage access, if there is anything to show; else the gate
            var limitedCurrent: String? = null
            val limitedSoft = if (ready) null else withContext(Dispatchers.IO) {
                val usage = rows.usage(now)
                limitedCurrent = Stored.currentOf(usage, packageName)      // the last app resumed before the overlay
                rows.seed() + usage
            }
            if (!ready && limitedSoft.isNullOrEmpty()) {
                settings = sDef.await()
                limited = false
                st = Overlay.needShizuku(st)
                contentOn = true
                return@launch
            }
            // with Shizuku: first replay removals made while it was off, then read the store; usage meanwhile
            val tasksDef = if (ready) async(Dispatchers.IO) {
                val pending = rows.pending()
                if (pending.isNotEmpty()) { repo.replayPending(pending, now, sDef.await().forceStopOnKill); rows.clearPending() }
                repo.readStoredRow(now).also { tRaw = SystemClock.uptimeMillis() - t0 }
            } else null
            val usageDef = if (ready) async(Dispatchers.IO) {
                if (!rows.hasUsageAccess()) repo.grantUsageAccess()
                rows.usage(now)
            } else null
            val s = sDef.await()
            val tSet = SystemClock.uptimeMillis() - t0
            val imgDef = if (s.background == Backdrop.IMAGE) async(Dispatchers.IO) { BackdropImage.load(applicationContext) } else null
            val stored = tasksDef?.await()
            val tasks: List<Seen> = stored?.first.orEmpty()
            val soft: List<Seen> = usageDef?.await() ?: limitedSoft.orEmpty()
            val mode = if (!ready) RowMode.LIMITED else stored?.second ?: RowMode.NONE
            val ledger = Stored.prune(rows.ledger(), now - RowStore.USAGE_DAYS * RowStore.DAY_MS)
            val r = repo.resolveRow(tasks, soft, ledger, s.exceptions, s.pinned, Stored.ROW_MAX)
            if (ready) launch(Dispatchers.IO) { rows.saveLedger(ledger); rows.saveSeed(repo.seedOf(tasks, soft, ledger)) }
            val tRes = SystemClock.uptimeMillis() - t0
            settings = s
            limited = !ready
            applyBlur(s.background == Backdrop.BLUR)
            backdropImage = imgDef?.await()
            info = (r.all + r.pins).associateBy { it.packageName }
            loadId++
            st = Overlay.loaded(
                st, r.all.map { TaskRef(it.taskId ?: -1, it.packageName) }, r.pins.map { it.packageName },
                s.showKillAll, s.pinPosition == PinPosition.RIGHT,
                current = if (ready) repo.current else limitedCurrent,
            )
            contentOn = true
            val nowUp = SystemClock.uptimeMillis()
            Log.i(TAG, "content ${r.all.size} apps (${r.all.count { (it.taskId ?: -1) > 0 }} resumable) ${r.pins.size} pins mode=$mode " +
                "stored=${tasks.size} usage=${soft.size} current=${if (ready) repo.current else limitedCurrent} load=${nowUp - t0}ms (settings@$tSet store@$tRaw resolved@$tRes) sinceStart=${nowUp - openedAt}ms")
        }
    }

    // ── keys ───────────────────────────────────────────────────────────────
    private var holdJob: Job? = null      // OK went down on a card: opens the menu at HOLD_MS

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val btn = Keys.btn(event.keyCode)
        if (btn == Btn.OTHER) return super.dispatchKeyEvent(event)     // volume etc. still work
        val down = event.action == KeyEvent.ACTION_DOWN
        if (!down && event.action != KeyEvent.ACTION_UP) return true
        if (!down && btn == Btn.OK) holdJob?.cancel()
        val (n, effects) = Overlay.key(st, KeyIn(btn, down, event.repeatCount))
        st = n
        effects.forEach(::run)
        return true
    }

    private fun onFlyComplete(pkg: String) {
        val (n, effects) = Overlay.flyComplete(st, pkg)
        st = n
        effects.forEach(::run)
    }

    private fun run(e: Effect) {
        when (e) {
            is Effect.Switch -> switchTo(e.pkg, e.taskId)
            is Effect.Dismiss -> background {
                val now = System.currentTimeMillis()
                rows.saveLedger(Stored.remove(rows.ledger(), e.task.pkg, now))     // usage data can't bring it back
                if (!repo.dismiss(e.task.taskId, e.task.pkg, settings.forceStopOnKill) && !repo.ready())
                    rows.addPending(listOf(e.task.pkg), now)                       // removed for real once Shizuku is back
            }
            is Effect.KillAll -> killAll(e.tasks)
            is Effect.AddException -> {
                saveSettings(settings.copy(exceptions = settings.exceptions + e.pkg))
                showToast("${label(e.pkg)} added to exceptions")
            }
            is Effect.SetPin -> {
                saveSettings(SettingsNav.setPin(settings, e.pkg, e.pinned))
                showToast("${label(e.pkg)} ${if (e.pinned) "pinned" else "unpinned"}")
            }
            is Effect.AppInfo -> runCatching {
                startActivity(
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${e.pkg}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            Effect.OpenSettings -> startActivity(Intent(this, SettingsActivity::class.java))
            Effect.ArmHold -> {
                holdJob?.cancel()
                holdJob = lifecycleScope.launch { delay(Overlay.HOLD_MS); st = Overlay.holdElapsed(st) }
            }
            Effect.Close -> close()
            Effect.GateAction -> {
                val s = ShizukuManager.state()
                if (!s.binderAlive) ShizukuManager.openShizuku(this)
                else if (!s.permissionGranted) ShizukuManager.requestPermission()
                reload()
            }
        }
    }

    private fun label(pkg: String) = info[pkg]?.label ?: pkg

    private fun saveSettings(n: Settings) {
        settings = n
        lifecycleScope.launch { store.save(n) }
    }

    private fun showToast(text: String) {
        toast = text
        toastJob?.cancel()
        toastJob = lifecycleScope.launch { delay(Motion.TOAST_MS.toLong()); toast = null }
    }

    private fun background(block: () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { block() }
    }

    /**
     * The select zoom plays first (the tile grows to full screen in the app's colour,
     * the name fades in); then the app is brought forward over it. Opening the app
     * any earlier would cover the animation — the "freeze" of 1.1.
     */
    private fun switchTo(pkg: String, taskId: Int?) {
        selectJob?.cancel()
        selectJob = lifecycleScope.launch {
            delay((Motion.SELECT_MS + Motion.SELECT_HOLD_MS - SWITCH_LEAD_MS).toLong())
            val moved = taskId?.takeIf { it > 0 }?.let { id -> withContext(Dispatchers.IO) { repo.switchTo(pkg, id) } } ?: false
            if (!moved) {
                val li = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
                li?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (li == null || runCatching { startActivity(li) }.isFailure) {
                    // nothing to open: give the row back
                    st = st.copy(selecting = null, busy = false)
                    showToast("Can't open ${label(pkg)}")
                    return@launch
                }
            }
            delay(1500)
            // Normally the app is in front and we are stopped by now; if not, step aside.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) hideNow()
        }
    }

    private fun killAll(tasks: List<TaskRef>) {
        val pkgs = tasks.mapTo(HashSet()) { it.pkg }
        val fs = settings.forceStopOnKill
        lifecycleScope.launch {
            val job = async(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                rows.saveLedger(Stored.killAll(rows.ledger(), now))
                if (repo.ready()) repo.killAll(pkgs, fs) else rows.addPending(pkgs, now)
            }
            delay(Motion.KILL_TO_EMPTY_MS.toLong())        // the staggered fly-up plays meanwhile
            job.await()
            st = Overlay.killAllDone(st)
        }
    }

    /** BACK: fade scrim + content out, then step aside. */
    private fun close() {
        scrimOn = false
        contentOn = false
        lifecycleScope.launch {
            delay(Motion.EXIT_MS.toLong())
            hideNow()
        }
    }

    /** Move this overlay's own task behind everything (kept alive for a hot reopen). */
    @Suppress("DEPRECATION")
    private fun hideNow() {
        if (!moveTaskToBack(true)) finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "RTV"
        /** The switch binder call starts this much before the zoom ends, so the app lands as it finishes. */
        private const val SWITCH_LEAD_MS = 60
    }
}
