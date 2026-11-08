package com.websnake.recentstv.data

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** An app that can hand back a picture (Gallery, a file manager…), and the intent that asks it. */
@Immutable
data class Picker(val label: String, val icon: ImageBitmap?, val intent: Intent)

/**
 * Where a background picture can come from on a TV — the way Projectivy Launcher does it:
 * the device's own pictures in an in-app D-pad grid (MediaStore), plus the apps that can pick
 * one. On Android TV the system "open document" request usually lands on a stub
 * (`com.android.tv.frameworkpackagestubs`) that only says "no app" and returns nothing, so the
 * apps are listed by name and asked directly instead of through the system default.
 */
object ImageSources {

    /** Activities that answer but cannot really pick (TV stubs, intent interceptors), and us. */
    private fun unusable(pkg: String, cls: String, self: String) =
        pkg == self || pkg == "com.android.tv.frameworkpackagestubs" || cls.contains("Stub") ||
            cls.contains("intercept", ignoreCase = true)

    fun readPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    fun canRead(context: Context) =
        context.checkSelfPermission(readPermission()) == PackageManager.PERMISSION_GRANTED

    /**
     * Every app that can pick a picture, one entry per app. Each is asked with the request it
     * answers best: "get content" (galleries, file managers, the photo picker), then "open
     * document" (the system Files app), then "pick".
     */
    fun pickers(context: Context): List<Picker> {
        val pm = context.packageManager
        val requests = listOf(
            Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),
            Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE),
            Intent(Intent.ACTION_PICK).setType("image/*"),
        )
        val out = LinkedHashMap<String, Picker>()
        val iconPx = (40 * context.resources.displayMetrics.density).toInt()
        for (req in requests) {
            val found = runCatching { pm.queryIntentActivities(req, PackageManager.MATCH_DEFAULT_ONLY) }.getOrDefault(emptyList())
            for (ri in found) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName in out || unusable(ai.packageName, ai.name, context.packageName)) continue
                val label = runCatching { ai.applicationInfo.loadLabel(pm).toString() }.getOrDefault(ai.packageName)
                val icon = runCatching { ri.loadIcon(pm) }.getOrNull()?.let { d ->
                    Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888).also { b ->
                        d.setBounds(0, 0, iconPx, iconPx); d.draw(Canvas(b))
                    }.asImageBitmap()
                }
                out[ai.packageName] = Picker(label, icon, Intent(req).setComponent(ComponentName(ai.packageName, ai.name)))
            }
        }
        return out.values.sortedBy { it.label.lowercase() }
    }

    /** The device's pictures, newest first (needs the read permission; empty without it). */
    fun deviceImages(context: Context, max: Int = 400): List<Uri> = runCatching {
        val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val out = ArrayList<Uri>()
        context.contentResolver.query(
            base, arrayOf(MediaStore.Images.Media._ID), null, null, "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext() && out.size < max) out.add(ContentUris.withAppendedId(base, c.getLong(id)))
        }
        out
    }.getOrDefault(emptyList())

    /** A small 16:9 preview for the grid, decoded at its drawn size. */
    fun thumbnail(context: Context, uri: Uri, w: Int, h: Int): ImageBitmap? = runCatching {
        val b = if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(uri, Size(w, h), null)
        else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var s = 1
            while (bounds.outWidth / (s * 2) >= w && bounds.outHeight / (s * 2) >= h) s *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
            }
        }
        b?.asImageBitmap()?.also { it.prepareToDraw() }
    }.getOrNull()
}
