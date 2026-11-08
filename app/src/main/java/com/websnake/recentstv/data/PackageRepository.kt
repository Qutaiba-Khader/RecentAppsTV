package com.websnake.recentstv.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.websnake.recentstv.logic.Geometry
import java.util.concurrent.ConcurrentHashMap

/** Installed-app metadata: labels, real launcher icons (pre-rasterised), categories. */
class PackageRepository(private val context: Context) {

    // Process-wide caches: resolved once, then instant on every later overlay open.
    companion object {
        private val labelCache = ConcurrentHashMap<String, String>()
        private val iconCache = ConcurrentHashMap<String, ImageBitmap>()      // card icons
        private val smallIconCache = ConcurrentHashMap<String, ImageBitmap>() // list rows
        private val categoryCache = ConcurrentHashMap<String, String>()
        private val brandCache = ConcurrentHashMap<String, Int>()
        const val META_FILE = "app_meta"
        /** Begin loading the metadata file off the main thread (call at process start). */
        fun warm(context: Context) { context.getSharedPreferences(META_FILE, Context.MODE_PRIVATE) }
    }

    private val DEFAULT_BRAND = 0xFF2B2F36.toInt()
    private val pm: PackageManager = context.packageManager
    private val density = context.resources.displayMetrics.density

    /** Largest on-screen icon: Large card (115dp) × icon share 0.66 × focus scale 1.3 (launch zoom). */
    val cardIconPx = Geometry.iconRasterPx(115f, 0.66f, 1.3f, density)
    private val rowIconPx = Geometry.iconRasterPx(42f, 0.75f, 1f, density)

    /** True only if the app is installed AND enabled — a disabled/uninstalled app
     *  whose task still lingers in recents must not be surfaced. Not cached. */
    fun isUsable(pkg: String): Boolean = runCatching {
        pm.getApplicationInfo(pkg, 0).enabled
    }.getOrDefault(false)

    /** Has a launcher entry (leanback or phone): an app known only from usage data must be openable. */
    fun isLaunchable(pkg: String): Boolean = runCatching {
        pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null
    }.getOrDefault(false)

    /**
     * Label + category for a recent card. Reading another app's label loads that
     * app's resources (the slow part of a cold open), so the pair is also kept on
     * disk, keyed by the app's update time.
     */
    fun labelAndCategory(pkg: String): Pair<String, String> {
        val l = labelCache[pkg]; val c = categoryCache[pkg]
        if (l != null && c != null) return l to c
        val stamp = runCatching { pm.getPackageInfo(pkg, 0).lastUpdateTime }.getOrNull()
        val saved = stamp?.let { meta.getString(pkg, null) }?.split('\t')
        if (saved != null && saved.size == 3 && saved[0] == stamp.toString()) {
            labelCache[pkg] = saved[1]; categoryCache[pkg] = saved[2]
            return saved[1] to saved[2]
        }
        val pair = label(pkg) to category(pkg)
        if (stamp != null) meta.edit().putString(pkg, "$stamp\t${pair.first.replace('\t', ' ')}\t${pair.second}").apply()
        return pair
    }

    private val meta by lazy { context.getSharedPreferences(META_FILE, Context.MODE_PRIVATE) }

    fun label(pkg: String): String = labelCache.getOrPut(pkg) {
        runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })
    }

    fun icon(pkg: String): ImageBitmap? = cached(iconCache, pkg, cardIconPx)
    fun smallIcon(pkg: String): ImageBitmap? = cached(smallIconCache, pkg, rowIconPx)

    /**
     * Memory → disk → package. The disk copy (a small PNG named after the app's update
     * time) makes a cold start after the process was reclaimed cost a decode instead of
     * loading another app's resources; an updated app gets a new name, so a new icon.
     */
    private fun cached(cache: ConcurrentHashMap<String, ImageBitmap>, pkg: String, px: Int): ImageBitmap? {
        cache[pkg]?.let { return it }
        val stamp = runCatching { pm.getPackageInfo(pkg, 0).lastUpdateTime }.getOrNull()
        val file = stamp?.let { java.io.File(iconDir, "${pkg}_${it}_$px.png") }
        val fromDisk = file?.takeIf { it.isFile }?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it.path) }.getOrNull() }
        val bitmap = fromDisk ?: run {
            val d = resolveIcon(pkg) ?: return null
            rasterise(d, px).also { b -> if (file != null) writeQuietly(file, pkg, b) }
        }
        val img = bitmap.asImageBitmap()
        img.prepareToDraw()          // upload to the GPU now, not on the first animated frame
        cache[pkg] = img
        return img
    }

    private val iconDir by lazy { java.io.File(context.cacheDir, "icons").apply { mkdirs() } }

    private fun writeQuietly(file: java.io.File, pkg: String, b: Bitmap) = runCatching {
        iconDir.listFiles { f -> f.name.startsWith("${pkg}_") }?.forEach { it.delete() }   // older versions
        val tmp = java.io.File(iconDir, file.name + ".tmp")
        tmp.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        tmp.renameTo(file)
    }

    private fun rasterise(d: Drawable, cap: Int): Bitmap {
        val iw = d.intrinsicWidth.takeIf { it > 0 } ?: cap
        val ih = d.intrinsicHeight.takeIf { it > 0 } ?: cap
        val s = minOf(1f, cap.toFloat() / maxOf(iw, ih))
        val w = (iw * s).toInt().coerceAtLeast(1)
        val h = (ih * s).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val old = d.copyBounds()
        d.setBounds(0, 0, w, h)
        d.draw(Canvas(bmp))
        d.bounds = old
        return bmp
    }

    /** Real launcher icon through several fallbacks, so every app gets one
     *  (e.g. phone apps on TV that lack a leanback launcher). */
    private fun resolveIcon(pkg: String): Drawable? {
        runCatching { pm.getApplicationIcon(pkg) }.getOrNull()?.let { return it }
        runCatching {
            val cn = (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))?.component
            cn?.let { pm.getActivityIcon(it) }
        }.getOrNull()?.let { return it }
        runCatching { pm.getApplicationBanner(pkg) }.getOrNull()?.let { return it }
        return runCatching { pm.defaultActivityIcon }.getOrNull()
    }

    /**
     * The icon's main colour, for the select animation's full-screen fill: the
     * saturation-weighted average of a 24×24 copy, darkened enough for white text.
     */
    fun brand(pkg: String): Int = brandCache[pkg] ?: run {
        val img = icon(pkg) ?: return DEFAULT_BRAND
        brandOf(img.asAndroidBitmap()).also { brandCache[pkg] = it }
    }

    private fun brandOf(src: Bitmap): Int = runCatching {
        val b = Bitmap.createScaledBitmap(src, 24, 24, true)
        val px = IntArray(24 * 24); b.getPixels(px, 0, 24, 0, 0, 24, 24)
        var r = 0.0; var g = 0.0; var bl = 0.0; var w = 0.0
        var ar = 0.0; var ag = 0.0; var ab = 0.0; var an = 0.0
        val hsv = FloatArray(3)
        for (c in px) {
            val a = (c ushr 24) and 0xFF
            if (a < 160) continue
            val cr = (c shr 16) and 0xFF; val cg = (c shr 8) and 0xFF; val cb = c and 0xFF
            android.graphics.Color.RGBToHSV(cr, cg, cb, hsv)
            ar += cr; ag += cg; ab += cb; an++
            val weight = hsv[1] * hsv[1] * (0.25 + hsv[2])     // vivid pixels dominate
            if (weight < 0.02) continue
            r += cr * weight; g += cg * weight; bl += cb * weight; w += weight
        }
        val base = when {
            w > 0.5 -> android.graphics.Color.rgb((r / w).toInt(), (g / w).toInt(), (bl / w).toInt())
            an > 0 -> android.graphics.Color.rgb((ar / an).toInt(), (ag / an).toInt(), (ab / an).toInt())
            else -> return DEFAULT_BRAND
        }
        android.graphics.Color.colorToHSV(base, hsv)
        hsv[2] = hsv[2].coerceIn(0.22f, 0.72f)                // never white-on-white, never black
        if (w <= 0.5) hsv[1] = minOf(hsv[1], 0.2f)            // a grey icon gets a calm grey
        android.graphics.Color.HSVToColor(hsv)
    }.getOrDefault(DEFAULT_BRAND)

    fun category(pkg: String): String = categoryCache.getOrPut(pkg) { resolveCategory(pkg) }

    private fun resolveCategory(pkg: String): String = runCatching {
        val ai = pm.getApplicationInfo(pkg, 0)
        if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 && Build.VERSION.SDK_INT >= 26 &&
            ai.category == ApplicationInfo.CATEGORY_UNDEFINED
        ) "System" else categoryName(ai)
    }.getOrDefault("")

    private fun categoryName(ai: ApplicationInfo): String {
        if (Build.VERSION.SDK_INT < 26) return ""
        return when (ai.category) {
            ApplicationInfo.CATEGORY_GAME -> "Games"
            ApplicationInfo.CATEGORY_AUDIO -> "Music"
            ApplicationInfo.CATEGORY_VIDEO -> "Video"
            ApplicationInfo.CATEGORY_IMAGE -> "Photos"
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_NEWS -> "News"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            else -> if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) "System" else "App"
        }
    }

    /** All launchable apps (leanback first, then standard), sorted by label. */
    fun installedApps(): List<InstalledApp> {
        val seen = LinkedHashMap<String, InstalledApp>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(cat)
            val acts = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
            for (ri in acts) {
                val pkg = ri.activityInfo.packageName
                if (pkg in seen) continue
                seen[pkg] = InstalledApp(pkg, label(pkg), smallIcon(pkg), category(pkg).ifBlank { "App" })
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }
}
