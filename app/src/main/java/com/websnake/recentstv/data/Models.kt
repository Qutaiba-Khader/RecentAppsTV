package com.websnake.recentstv.data

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/** A recent task ready to draw: the icon is already rasterised (off the UI thread),
 *  so composing a card never decodes or scales a Drawable. */
@Immutable
data class RecentTask(
    val taskId: Int,
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val category: String = "",
)

/** A launchable installed app, for the Exceptions list. */
@Immutable
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val tag: String,            // "System" / "App" / category
)
