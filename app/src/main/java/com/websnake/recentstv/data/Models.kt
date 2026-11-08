package com.websnake.recentstv.data

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/** An app in the row (a running task or a pin), ready to draw: the icon is already
 *  rasterised (off the UI thread), so composing a card never decodes a Drawable. */
@Immutable
data class RecentTask(
    val taskId: Int?,              // null: a pinned app that isn't running
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val category: String = "",
    val brand: Int = 0xFF2B2F36.toInt(),   // the icon's main colour (select animation)
)

/** A launchable installed app, for the Exceptions / Pinned apps lists. */
@Immutable
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val tag: String,            // "System" / "App" / category
)
