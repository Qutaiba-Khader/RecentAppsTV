package com.websnake.recentstv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.lifecycleScope
import com.websnake.recentstv.data.BackdropImage
import com.websnake.recentstv.data.InstalledApp
import com.websnake.recentstv.data.PackageRepository
import com.websnake.recentstv.data.SettingsStore
import com.websnake.recentstv.logic.Btn
import com.websnake.recentstv.logic.BulkAction
import com.websnake.recentstv.logic.SetEffect
import com.websnake.recentstv.logic.SetScreen
import com.websnake.recentstv.logic.SetState
import com.websnake.recentstv.logic.Settings
import com.websnake.recentstv.logic.SettingsNav
import com.websnake.recentstv.ui.overlay.Toast
import com.websnake.recentstv.ui.settings.AppListScreen
import com.websnake.recentstv.ui.settings.SettingsScreen
import com.websnake.recentstv.ui.theme.Motion
import com.websnake.recentstv.ui.theme.RecentsTheme
import com.websnake.recentstv.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {

    private lateinit var store: SettingsStore
    private lateinit var pkgRepo: PackageRepository

    private var nav by mutableStateOf(SetState())
    private var settings by mutableStateOf(Settings())
    private var apps by mutableStateOf<List<InstalledApp>>(emptyList())
    private var appsLoading by mutableStateOf(false)
    private var toast by mutableStateOf<String?>(null)
    private var toastJob: Job? = null

    private val blurSupported by lazy {
        android.os.Build.VERSION.SDK_INT >= 31 && runCatching { windowManager.isCrossWindowBlurEnabled }.getOrDefault(false)
    }

    // Background image: the document picker first, then any app that offers images.
    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { onPicked(it) }
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { onPicked(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(applicationContext)
        pkgRepo = PackageRepository(applicationContext)
        lifecycleScope.launch { store.settings.collect { settings = it } }

        setContent {
            RecentsTheme(accent = settings.accent.color) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {   // design: full RTL
                    Box(Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = nav.screen,
                            transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(160)) },
                            label = "screen",
                        ) { screen ->
                            when (screen) {
                                SetScreen.SETTINGS -> SettingsScreen(settings, nav.focus, blurSupported)
                                SetScreen.EXCEPTIONS -> AppListScreen(
                                    screen, apps, { it in settings.exceptions }, settings.exceptions.count { e -> apps.any { it.packageName == e } },
                                    nav.listFocus, nav.listAct, appsLoading,
                                )
                                SetScreen.PINNED -> AppListScreen(
                                    screen, apps, { it in settings.pinned }, settings.pinned.size,
                                    nav.listFocus, nav.listAct, appsLoading,
                                )
                            }
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Toast(toast) }
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val btn = Keys.btn(event.keyCode)
        if (btn == Btn.OTHER) return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true
        val (n, effects) = SettingsNav.key(nav, settings, btn, apps.map { it.packageName })
        nav = n
        for (e in effects) when (e) {
            is SetEffect.Save -> { settings = e.settings; lifecycleScope.launch { store.save(e.settings) } }
            SetEffect.LoadApps -> loadApps()
            SetEffect.PickImage -> pickImage()
            is SetEffect.Bulk -> showToast(
                when (e.action) {
                    BulkAction.HIDE_ALL -> "All apps hidden from recents"
                    BulkAction.SHOW_ALL -> "All apps shown in recents"
                    BulkAction.UNPIN_ALL -> "All apps unpinned"
                }
            )
            SetEffect.Finish -> finish()
        }
        return true
    }

    private fun loadApps() {
        if (apps.isNotEmpty() || appsLoading) return
        appsLoading = true
        lifecycleScope.launch {
            apps = withContext(Dispatchers.IO) { pkgRepo.installedApps() }
            appsLoading = false
        }
    }

    private fun pickImage() {
        val ok = runCatching { openDoc.launch(arrayOf("image/*")) }.isSuccess ||
            runCatching { getContent.launch("image/*") }.isSuccess
        if (!ok) showToast("No image picker on this device")
    }

    private fun onPicked(uri: Uri?) {
        uri ?: return
        lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) { BackdropImage.import(applicationContext, uri) }
            if (name == null) { showToast("Couldn't read that image"); return@launch }
            val n = settings.copy(bgImage = name)
            settings = n
            store.save(n)
            showToast("Background image set")
        }
    }

    private fun showToast(text: String) {
        toast = text
        toastJob?.cancel()
        toastJob = lifecycleScope.launch { delay(Motion.TOAST_MS.toLong()); toast = null }
    }
}
