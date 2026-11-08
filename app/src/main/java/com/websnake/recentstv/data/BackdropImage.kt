package com.websnake.recentstv.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Settings → Background → Image. The picked image is copied once, scaled to the
 * screen, into app storage: the overlay then reads a local file (no content-URI
 * permission to keep, nothing to resolve) and the picker app may be gone later.
 */
object BackdropImage {
    private const val FILE = "backdrop.jpg"

    private fun file(context: Context) = File(context.filesDir, FILE)

    fun exists(context: Context) = file(context).isFile

    /** Copy [uri] into app storage, scaled to fit the screen. Returns its display name, or null on failure. */
    fun import(context: Context, uri: Uri): String? = runCatching {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val (sw, sh) = screen(context)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, bounds.outHeight, sw, sh) }
        val decoded = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val scale = maxOf(sw.toFloat() / decoded.width, sh.toFloat() / decoded.height).coerceAtMost(1f)
        val fitted = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true) else decoded
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.outputStream().use { fitted.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (!tmp.renameTo(file(context))) return null
        displayName(context, uri) ?: "Image"
    }.getOrNull()

    /** Decode the stored copy for one overlay showing (dropped again when the overlay hides). */
    fun load(context: Context): ImageBitmap? = runCatching {
        val f = file(context).takeIf { it.isFile } ?: return null
        BitmapFactory.decodeFile(f.path)?.asImageBitmap()?.also { it.prepareToDraw() }
    }.getOrNull()

    private fun screen(context: Context): Pair<Int, Int> {
        val m = context.resources.displayMetrics
        return maxOf(m.widthPixels, m.heightPixels) to minOf(m.widthPixels, m.heightPixels)
    }

    private fun sample(w: Int, h: Int, sw: Int, sh: Int): Int {
        var s = 1
        while (w / (s * 2) >= sw && h / (s * 2) >= sh) s *= 2
        return s
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
}
