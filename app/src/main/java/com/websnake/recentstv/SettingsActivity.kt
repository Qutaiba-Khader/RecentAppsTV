package com.websnake.recentstv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.lifecycleScope
import com.websnake.recentstv.data.InstalledApp
import com.websnake.recentstv.data.PackageRepository
import com.websnake.recentstv.data.SettingsStore
import com.websnake.recentstv.logic.SetEffect
import com.websnake.recentstv.logic.SetScreen
import com.websnake.recentstv.logic.SetState
import com.websnake.recentstv.logic.Settings
import com.websnake.recentstv.logic.SettingsNav
import com.websnake.recentstv.ui.settings.ExceptionsScreen
import com.websnake.recentstv.ui.settings.SettingsScreen
import com.websnake.recentstv.ui.theme.RecentsTheme
import com.websnake.recentstv.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {

    private lateinit var store: SettingsStore
    private lateinit var pkgRepo: PackageRepository

    private var nav by mutableStateOf(SetState())
    private var settings by mutableStateOf(Settings())
    private var apps by mutableStateOf<List<InstalledApp>>(emptyList())
    private var appsLoading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(applicationContext)
        pkgRepo = PackageRepository(applicationContext)
        lifecycleScope.launch { store.settings.collect { settings = it } }

        setContent {
            RecentsTheme(accent = settings.accent.color) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {   // design: full RTL
                    AnimatedContent(
                        targetState = nav.screen,
                        transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(160)) },
                        label = "screen",
                    ) { screen ->
                        when (screen) {
                            SetScreen.SETTINGS -> SettingsScreen(settings, nav.focus)
                            SetScreen.EXCEPTIONS -> ExceptionsScreen(apps, settings.exceptions, nav.excFocus, appsLoading)
                        }
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val btn = Keys.btn(event.keyCode)
        if (btn == com.websnake.recentstv.logic.Btn.OTHER) return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true
        val (n, effects) = SettingsNav.key(nav, settings, btn, apps.map { it.packageName })
        nav = n
        for (e in effects) when (e) {
            is SetEffect.Save -> { settings = e.settings; lifecycleScope.launch { store.save(e.settings) } }
            SetEffect.LoadApps -> loadApps()
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
}
