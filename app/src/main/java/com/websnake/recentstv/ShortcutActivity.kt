package com.websnake.recentstv

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * 1.8: "Show recent apps" in the shortcut lists of button mappers (tvQuickActions → Shortcuts, Button Mapper, …) —
 * the standard legacy shortcut picker (`ACTION_CREATE_SHORTCUT`). It hands back an intent for the overlay; pressing
 * the mapped button again while the overlay shows closes it (RecentsActivity.onNewIntent), so the button toggles.
 * The app itself remaps no keys.
 */
class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val open = Intent(ACTION_OPEN).setClassName(packageName, "$packageName.RecentsActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        @Suppress("DEPRECATION")
        val result = Intent()
            .putExtra(Intent.EXTRA_SHORTCUT_INTENT, open)
            .putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.shortcut_recents_long))
            .putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(this, R.drawable.ic_launcher))
        setResult(RESULT_OK, result)
        finish()
    }

    companion object {
        const val ACTION_OPEN = "com.websnake.recentstv.OPEN_RECENTS"
    }
}
