package com.websnake.recentstv.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.websnake.recentstv.data.RecentTask
import com.websnake.recentstv.ui.theme.LocalAccent
import com.websnake.recentstv.ui.theme.Motion
import com.websnake.recentstv.ui.theme.Type
import kotlin.math.roundToInt

/**
 * Select animation (screens 20–22). After OK on a card the rest of the overlay fades
 * (RecentsOverlay) while a copy of the tile grows from [from] to the full screen in the
 * app's colour: corners go to 0, the accent ring fades, the icon grows 1.2× and the
 * app's name fades in under it. The Activity hands over to the app when it is done.
 * Every frame is drawn in the draw phase: no recomposition while it plays.
 */
@Composable
fun SelectZoom(task: RecentTask, from: Rect, startRadius: Float, iconDp: Dp) {
    val accent = LocalAccent.current
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(Motion.SELECT_MS, easing = Motion.selectEasing)) }
    var nameH by remember { mutableFloatStateOf(0f) }
    val brand = Color(task.brand)
    val icon = task.icon

    // geometry for one progress value, shared by the drawing and the name
    fun rectAt(v: Float, w: Float, h: Float) = Rect(
        from.left + (0f - from.left) * v, from.top + (0f - from.top) * v,
        from.right + (w - from.right) * v, from.bottom + (h - from.bottom) * v,
    )

    Box(
        Modifier.fillMaxSize().drawBehind {
            val v = p.value
            val r = rectAt(v, size.width, size.height)
            val radius = startRadius * (1f - v)
            drawRoundRect(brand, Offset(r.left, r.top), Size(r.width, r.height), CornerRadius(radius))
            if (v < 1f) drawRoundRect(
                accent.base.copy(alpha = 1f - v), Offset(r.left, r.top), Size(r.width, r.height),
                CornerRadius(radius), style = Stroke(5.dp.toPx()),
            )
            if (icon != null) {
                val reveal = ((v - 0.6f) / 0.4f).coerceIn(0f, 1f)
                val px = iconDp.toPx() * (1f + 0.2f * v)
                val cy = r.center.y - (17.dp.toPx() + nameH) / 2f * reveal
                drawImage(
                    icon,
                    dstOffset = IntOffset((r.center.x - px / 2f).roundToInt(), (cy - px / 2f).roundToInt()),
                    dstSize = IntSize(px.roundToInt(), px.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
            }
        },
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            task.label,
            style = Type.hero.copy(fontWeight = FontWeight.ExtraBold), color = Color.White, maxLines = 1,
            modifier = Modifier
                .onSizeChanged { nameH = it.height.toFloat() }
                .graphicsLayer {
                    val v = p.value
                    val reveal = ((v - 0.6f) / 0.4f).coerceIn(0f, 1f)
                    alpha = reveal
                    val px = iconDp.toPx() * (1f + 0.2f * v)
                    // under the icon; the pair is centred on the screen once the name is in
                    val iconCy = -(17.dp.toPx() + nameH) / 2f * reveal
                    translationY = (iconCy + px / 2f + 17.dp.toPx() + nameH / 2f + 8.dp.toPx() * (1f - reveal)).roundToInt().toFloat()
                },
        )
    }
}
