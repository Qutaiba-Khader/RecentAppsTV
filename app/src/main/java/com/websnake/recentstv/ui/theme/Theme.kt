package com.websnake.recentstv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.websnake.recentstv.R
import com.websnake.recentstv.logic.Accent
import com.websnake.recentstv.logic.CardShape
import com.websnake.recentstv.logic.CardSize
import com.websnake.recentstv.logic.Tint

/**
 * Design tokens lifted verbatim from the prototype's tv/styles.css.
 * The prototype is authored at 1920x1080 CSS px; this device is 960x540 dp
 * (320dpi / density 2.0), so every design px is mapped to dp at a 0.5 factor
 * to reproduce the prototype proportions pixel-for-pixel.
 */
object Tokens {
    // base
    val bg = Color(0xFF0A0B0D)
    val running = Color(0xFF101317)
    val surface = Color(0xFF15171B)
    val surface2 = Color(0xFF1D2026)
    val card = Color(0xFF23262D)
    val line = Color(0x14FFFFFF)        // rgba(255,255,255,0.08)

    // text
    val text = Color(0xFFF3F5F7)
    val textDim = Color(0xFF9AA0A8)
    val textFaint = Color(0xFF5E646C)

    // accent — Android green
    val accent = Color(0xFF3DDC84)
    val accentStrong = Color(0xFF2FC973)
    val accentTint = Color(0x243DDC84)   // ~0.14 alpha
    val accentGlow = Color(0x8C3DDC84)   // ~0.55 alpha
    val accentOnDark = Color(0xFF08210F) // dark text on solid-accent fills
    val track = Color(0xFF2B2F36)
    val knob = Color(0xFF6B7280)
    val segBg = Color(0xFF16181C)
    val killGlyphOnFocus = Color(0xFF0A2417)

    // scrim — the system dim provides the real 0.66 scrim; this is a light top-up
    val scrim = Color(0xA6000000)

    // type scale (design px → sp at 0.5)
    val tHero = 31.sp
    val tTitle = 23.sp
    val tRow = 20.sp
    val tLabel = 17.sp
    val tBody = 15.sp
    val tSmall = 13.sp
    val tMono = 11.sp
}

// Manrope ships as ONE variable TTF whose default instance is weight 200 (ExtraLight).
// Each weight must set the wght axis explicitly, or every style renders ExtraLight.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun manropeAt(w: Int) = Font(
    R.font.manrope, FontWeight(w),
    variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(
        androidx.compose.ui.text.font.FontVariation.weight(w)
    ),
)

private val manrope = FontFamily(manropeAt(500), manropeAt(600), manropeAt(700), manropeAt(800))

/** English runs stay LTR inside the RTL screens, so punctuation and digits keep their place. */
object Type {
    val hero = TextStyle(textDirection = TextDirection.Ltr, fontFamily = manrope, fontWeight = FontWeight.ExtraBold, fontSize = Tokens.tHero, letterSpacing = (-0.02).em)
    val title = TextStyle(textDirection = TextDirection.Ltr, fontFamily = manrope, fontWeight = FontWeight.Bold, fontSize = Tokens.tTitle, letterSpacing = (-0.01).em)
    val row = TextStyle(textDirection = TextDirection.Ltr, fontFamily = manrope, fontWeight = FontWeight.Bold, fontSize = Tokens.tRow)
    val label = TextStyle(textDirection = TextDirection.Ltr, fontFamily = manrope, fontWeight = FontWeight.Bold, fontSize = Tokens.tLabel)
    val body = TextStyle(textDirection = TextDirection.Ltr, fontFamily = manrope, fontWeight = FontWeight.Medium, fontSize = Tokens.tBody)
    val small = TextStyle(textDirection = TextDirection.Ltr, fontFamily = manrope, fontWeight = FontWeight.Medium, fontSize = Tokens.tSmall)
    val mono = TextStyle(textDirection = TextDirection.Ltr, fontFamily = manrope, fontWeight = FontWeight.Bold, fontSize = Tokens.tMono)
    val badge = TextStyle(textDirection = TextDirection.Ltr, fontFamily = manrope, fontWeight = FontWeight.Bold, fontSize = Tokens.tMono, letterSpacing = 0.14.em)
    val killLabel = TextStyle(textDirection = TextDirection.Ltr, fontFamily = manrope, fontWeight = FontWeight.ExtraBold, fontSize = Tokens.tLabel, letterSpacing = 0.01.em)
}

/** Settings · Size → card edge (design px → dp at 0.5). */
val CardSize.dp: androidx.compose.ui.unit.Dp get() = when (this) {
    CardSize.SMALL -> 71.dp; CardSize.MEDIUM -> 90.dp; CardSize.LARGE -> 115.dp
}

/** Settings · Shape → corner style (radius 16 / 32 / 50 % at 0.5). */
fun CardShape.shape(): androidx.compose.ui.graphics.Shape = when (this) {
    CardShape.SQUARE -> RoundedCornerShape(8.dp)
    CardShape.ROUNDED -> RoundedCornerShape(16.dp)
    CardShape.CIRCLE -> RoundedCornerShape(percent = 50)
}

/** A full accent palette derived from one base colour, so the accent is themeable. */
@androidx.compose.runtime.Immutable
data class AccentPalette(val base: Color, val tint: Color, val border: Color, val glow: Color, val onDark: Color)

fun accentPaletteFor(base: Color) = AccentPalette(
    base = base,
    tint = base.copy(alpha = 0.14f),
    border = base.copy(alpha = 0.45f),
    glow = base.copy(alpha = 0.55f),
    onDark = Tokens.accentOnDark,
)

val LocalAccent = androidx.compose.runtime.staticCompositionLocalOf { accentPaletteFor(Tokens.accent) }

/** Settings · Accent. */
val Accent.color: Color get() = when (this) {
    Accent.GREEN -> Color(0xFF3DDC84); Accent.BLUE -> Color(0xFF40A9FF); Accent.PURPLE -> Color(0xFF9B6CFF)
    Accent.AMBER -> Color(0xFFF2A93B); Accent.PINK -> Color(0xFFEC4899)
}

/** Settings · Background colour (scrim tint). */
val Tint.color: Color get() = when (this) {
    Tint.BLACK -> Color(0xFF000000); Tint.CHARCOAL -> Color(0xFF0A0B0D)
    Tint.NAVY -> Color(0xFF0A1428); Tint.GREY -> Color(0xFF181A1E)
}

/** Motion spec from the design (README §6). */
object Motion {
    val standard = androidx.compose.animation.core.CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
    val flyOut = androidx.compose.animation.core.FastOutLinearInEasing
    const val SCRIM_MS = 320
    const val SCROLL_MS = 340
    const val FOCUS_MS = 260
    const val FLY_MS = 330
    const val KILL_STAGGER_MS = 28
    const val KILL_TO_EMPTY_MS = 520
    const val EXIT_MS = 170
    const val LAUNCH_MS = 190
    const val FOCUS_SCALE = 1.15f
    const val KILL_FOCUS_SCALE = 1.10f
    const val IDLE_SCALE = 0.90f
    const val IDLE_ALPHA = 0.80f
}

@Composable
fun RecentsTheme(accent: Color = Tokens.accent, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent,
        background = Tokens.bg,
        surface = Tokens.surface,
        onSurface = Tokens.text,
    )
    CompositionLocalProvider(
        LocalContentColor provides Tokens.text,
        LocalAccent provides accentPaletteFor(accent),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
