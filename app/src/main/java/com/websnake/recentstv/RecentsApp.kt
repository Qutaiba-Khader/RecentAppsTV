package com.websnake.recentstv

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

class RecentsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        com.websnake.recentstv.data.SettingsStore.warm(this)      // start the async prefs loads now
        com.websnake.recentstv.data.PackageRepository.warm(this)
        // Lift the hidden-API blocklist so privileged framework reflection
        // (IActivityTaskManager / IActivityManager) is permitted.
        if (Build.VERSION.SDK_INT >= 28) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
        }
        publishDynamicShortcut()
    }

    /** Publish a dynamic shortcut mirroring the static one so shortcut-surfacing
     *  apps / remappers can find and launch the overlay. */
    private fun publishDynamicShortcut() {
        if (Build.VERSION.SDK_INT < 25) return
        runCatching {
            val sm = getSystemService(ShortcutManager::class.java) ?: return
            val intent = Intent("com.websnake.recentstv.OPEN_RECENTS").apply {
                setClassName(packageName, "com.websnake.recentstv.RecentsActivity")
            }
            val shortcut = ShortcutInfo.Builder(this, "open_recents")
                .setShortLabel(getString(R.string.shortcut_recents_short))
                .setLongLabel(getString(R.string.shortcut_recents_long))
                .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_recents))
                .setIntent(intent)
                .build()
            sm.dynamicShortcuts = listOf(shortcut)
        }
    }
}
