package com.websnake.recentstv.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.websnake.recentstv.logic.Geometry
import java.io.File

/**
 * Settings → Background → Image. The picked picture is prepared ONCE, when it is chosen:
 * decoded small (sub-sampled), cropped to the screen's shape around its centre and scaled
 * to the screen (at most 1920×1080), then saved as a JPEG in app storage. So whatever its
 * size — a 50-megapixel photo or a small icon — the overlay always reads the same small,
 * screen-sized file, and the picker app may be gone later.
 */
object BackdropImage {
    private const val FILE = "backdrop.jpg"
    private const val MAX_W = 1920
    private const val MAX_H = 1080

    private fun file(context: Context) = File(context.filesDir, FILE)

    fun exists(context: Context) = file(context).isFile

    /** Prepare [uri] as the background. Returns its display name, or null on failure. */
    fun import(context: Context, uri: Uri): String? = runCatching {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val (tw, th) = target(context)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = Geometry.sampleSize(bounds.outWidth, bounds.outHeight, tw, th)
        }
        val decoded = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val crop = Geometry.centerCrop(decoded.width, decoded.height, tw, th)
        val out = Bitmap.createBitmap(crop.outW, crop.outH, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            decoded, Rect(crop.left, crop.top, crop.left + crop.width, crop.top + crop.height),
            Rect(0, 0, crop.outW, crop.outH), Paint(Paint.FILTER_BITMAP_FLAG),
        )
        decoded.recycle()
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        out.recycle()
        if (!tmp.renameTo(file(context))) return null
        displayName(context, uri) ?: "Image"
    }.getOrNull()

    /**
     * Decode the prepared file for one overlay showing (dropped again when the overlay hides).
     * On Android 8+ it goes straight to graphics memory (a hardware bitmap): no copy on the
     * app heap and no upload on the first frame.
     */
    fun load(context: Context): ImageBitmap? = runCatching {
        val f = file(context).takeIf { it.isFile } ?: return null
        val opts = BitmapFactory.Options().apply { if (Build.VERSION.SDK_INT >= 26) inPreferredConfig = Bitmap.Config.HARDWARE }
        (BitmapFactory.decodeFile(f.path, opts) ?: BitmapFactory.decodeFile(f.path))?.asImageBitmap()?.also { it.prepareToDraw() }
    }.getOrNull()

    /** The screen in landscape, capped at 1920×1080. */
    private fun target(context: Context): Pair<Int, Int> {
        val m = context.resources.displayMetrics
        val w = maxOf(m.widthPixels, m.heightPixels)
        val h = minOf(m.widthPixels, m.heightPixels)
        val s = minOf(1f, MAX_W.toFloat() / w, MAX_H.toFloat() / h)
        return (w * s).toInt() to (h * s).toInt()
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
}
