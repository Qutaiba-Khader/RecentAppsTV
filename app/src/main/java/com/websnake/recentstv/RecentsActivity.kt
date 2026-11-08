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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.websnake.recentstv.data.RecentTask
import com.websnake.recentstv.data.RecentTasksRepository
import com.websnake.recentstv.data.SettingsStore
import com.websnake.recentstv.logic.Btn
import com.websnake.recentstv.logic.Effect
import com.websnake.recentstv.logic.KeyIn
import com.websnake.recentstv.logic.OvState
import com.websnake.recentstv.logic.Overlay
import com.websnake.recentstv.logic.Scene
import com.websnake.recentstv.logic.Settings
import com.websnake.recentstv.logic.SettingsNav
import com.websnake.recentstv.logic.TaskRef
import com.websnake.recentstv.shizuku.ShizukuManager
import com.websnake.recentstv.ui.overlay.ContextMenu
import com.websnake.recentstv.ui.overlay.RecentsOverlay
import com.websnake.recentstv.ui.state.EmptyState
import com.websnake.recentstv.ui.state.ShizukuGate
import com.websnake.recentstv.ui.theme.Motion
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

    private var st by mutableStateOf(OvState())
    private var info by mutableStateOf<Map<Int, RecentTask>>(emptyMap())
    private var settings by mutableStateOf(Settings())
    private var loadId by mutableIntStateOf(0)
    private var scrimOn by mutableStateOf(false)
    private var contentOn by mutableStateOf(false)
    private var launchingId by mutableStateOf<Int?>(null)

    private var reloadJob: Job? = null
    private var openedAt = 0L
    @Volatile private var tRaw = 0L

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ -> reload() }
    // Shizuku's binder attaches asynchronously after a cold start: re-resolve the scene
    // when it arrives, but only while still waiting for it.
    private val binderReceived = Shizuku.OnBinderReceivedListener {
        if (st.scene == Scene.LOADING || st.scene == Scene.SHIZUKU) reload()
    }
    private val binderDead = Shizuku.OnBinderDeadListener {
        RecentTasksRepository.dropBinders()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) st = Overlay.needShizuku(st)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = RecentTasksRepository(applicationContext)
        store = SettingsStore(applicationContext)
        ShizukuManager.addPermissionListener(permListener)
        runCatching { Shizuku.addBinderReceivedListenerSticky(binderReceived) }
        runCatching { Shizuku.addBinderDeadListener(binderDead) }

        setContent {
            RecentsTheme(accent = settings.accent.color) {
                val dim = settings.bgDim / 100f
                val tint = settings.bgTint.color
                val scrim by animateFloatAsState(
                    if (scrimOn) 1f else 0f, tween(if (scrimOn) Motion.SCRIM_MS else Motion.EXIT_MS), label = "scrim",
                )
                val content by animateFloatAsState(
                    if (contentOn) 1f else 0f,
                    tween(if (contentOn) Motion.SCROLL_MS else Motion.EXIT_MS, easing = Motion.standard), label = "content",
                )
                val rise = with(LocalDensity.current) { 24.dp.toPx() }
                Box(Modifier.fillMaxSize().drawBehind { if (scrim > 0f) drawRect(tint, alpha = dim * scrim) }) {
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            alpha = content
                            translationY = (1f - content) * rise          // the strip eases up into place
                        },
                    ) {
                        when (st.scene) {
                            Scene.LOADING -> {}
                            Scene.SHIZUKU -> ShizukuGate(focus = st.shzFocus)
                            Scene.EMPTY -> EmptyState()
                            Scene.OVERLAY -> RecentsOverlay(
                                state = st, info = info, size = settings.size, shape = settings.shape,
                                loadId = loadId, launchingId = launchingId,
                                onFlyComplete = ::onFlyComplete,
                            )
                        }
                    }
                    val ctx = st.ctxTask?.let { info[it.taskId] }
                    AnimatedVisibility(
                        visible = ctx != null,
                        enter = fadeIn(tween(160)) + scaleIn(tween(200, easing = Motion.standard), initialScale = 0.94f),
                        exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.97f),
                    ) {
                        lastCtx = ctx ?: lastCtx
                        lastCtx?.let { ContextMenu(it, st.ctxSel) }
                    }
                }
            }
        }
    }

    private var lastCtx: RecentTask? = null   // keeps the menu drawn while it fades out

    override fun onStart() {
        super.onStart()
        openedAt = SystemClock.uptimeMillis()
        scrimOn = true                       // the scrim fades in at once, while the list loads
        reload()                             // start reading now, not at onResume
    }

    override fun onResume() {
        super.onResume()
        // back from the Shizuku app / permission dialog: re-check
        if (st.scene == Scene.SHIZUKU) reload()
    }

    override fun onStop() {
        super.onStop()
        // Hidden: hold nothing, so the next open starts clean (no stale frame) and the
        // process keeps no bitmaps or animations alive while asleep.
        reloadJob?.cancel()
        st = OvState(scene = Scene.LOADING, showKillAll = st.showKillAll)
        info = emptyMap()
        launchingId = null
        lastCtx = null
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
            val sDef = async(Dispatchers.IO) { store.current() }
            if (!ShizukuManager.state().ready) {
                settings = sDef.await()
                st = Overlay.needShizuku(st)
                contentOn = true
                return@launch
            }
            val rawDef = async(Dispatchers.IO) { repo.readRaw().also { tRaw = SystemClock.uptimeMillis() - t0 } }
            val s = sDef.await()
            val tSet = SystemClock.uptimeMillis() - t0
            val (raw, src) = rawDef.await()
            val tasks = withContext(Dispatchers.IO) { repo.resolve(raw, s.exceptions) }
            val tRes = SystemClock.uptimeMillis() - t0
            settings = s
            info = tasks.associateBy { it.taskId }
            loadId++
            launchingId = null
            st = Overlay.loaded(st, tasks.map { TaskRef(it.taskId, it.packageName) }, s.showKillAll)
            contentOn = true
            val now = SystemClock.uptimeMillis()
            Log.i(TAG, "content ${tasks.size} tasks src=$src load=${now - t0}ms (settings@$tSet raw@$tRaw resolved@$tRes) sinceStart=${now - openedAt}ms")
        }
    }

    // ── keys ───────────────────────────────────────────────────────────────
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val btn = Keys.btn(event.keyCode)
        if (btn == Btn.OTHER) return super.dispatchKeyEvent(event)     // volume etc. still work
        val down = event.action == KeyEvent.ACTION_DOWN
        if (!down && event.action != KeyEvent.ACTION_UP) return true
        val (n, effects) = Overlay.key(st, KeyIn(btn, down, event.repeatCount))
        st = n
        effects.forEach(::run)
        return true
    }

    private fun onFlyComplete(taskId: Int) {
        val (n, effects) = Overlay.flyComplete(st, taskId)
        st = n
        effects.forEach(::run)
    }

    private fun run(e: Effect) {
        when (e) {
            is Effect.Switch -> switchTo(e.task)
            is Effect.Dismiss -> background { repo.dismiss(e.task.taskId, e.task.pkg, settings.forceStopOnKill) }
            is Effect.KillApp -> background { repo.dismiss(e.task.taskId, e.task.pkg, settings.forceStopOnKill) }
            is Effect.KillAll -> killAll(e.tasks)
            is Effect.AddException -> {
                val n = SettingsNav.toggleException(settings, e.task.pkg)
                settings = n
                lifecycleScope.launch { store.save(n) }
            }
            is Effect.AppInfo -> runCatching {
                startActivity(
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${e.task.pkg}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            Effect.OpenSettings -> startActivity(Intent(this, SettingsActivity::class.java))
            Effect.Close -> close()
            Effect.GateAction -> {
                val s = ShizukuManager.state()
                if (!s.binderAlive) ShizukuManager.openShizuku(this)
                else if (!s.permissionGranted) ShizukuManager.requestPermission()
                reload()
            }
        }
    }

    private fun background(block: () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) { block() }
    }

    private fun switchTo(task: TaskRef) {
        val rt = info[task.taskId] ?: return
        launchingId = task.taskId                   // focused card zooms, the rest fade
        lifecycleScope.launch {
            val moved = withContext(Dispatchers.IO) { repo.switchTo(rt) }
            if (!moved) {
                val li = packageManager.getLeanbackLaunchIntentForPackage(task.pkg)
                    ?: packageManager.getLaunchIntentForPackage(task.pkg)
                li?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { li?.let { startActivity(it) } }
            }
            delay(Motion.LAUNCH_MS.toLong())
            // Normally the app is already in front and we are stopped; if not, step aside.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) hideNow()
        }
    }

    private fun killAll(tasks: List<TaskRef>) {
        val pkgs = tasks.mapTo(HashSet()) { it.pkg }
        val fs = settings.forceStopOnKill
        lifecycleScope.launch {
            val job = async(Dispatchers.IO) { repo.killAll(pkgs, fs) }
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

    companion object { private const val TAG = "RTV" }
}
