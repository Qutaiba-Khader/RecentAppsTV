package com.websnake.recentstv.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.websnake.recentstv.logic.Geometry
import com.websnake.recentstv.ui.theme.LocalAccent
import com.websnake.recentstv.ui.theme.Motion
import com.websnake.recentstv.ui.theme.Tokens
import com.websnake.recentstv.ui.theme.Type

/* Shared settings controls. These screens run RTL (design): Row children are
   placed from the right, so the text block sits right and the control left. */

private val rowShape = RoundedCornerShape(11.dp)

/** A settings row: surface fill; focused = surface-2 + accent border + soft accent halo. */
@Composable
fun SettingRow(
    title: String,
    desc: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
    control: @Composable RowScope.() -> Unit,
) {
    val accent = LocalAccent.current
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(160, easing = Motion.standard), label = "row")
    Row(
        modifier
            .fillMaxWidth()
            .focusHalo(f, 11.dp)
            .clip(rowShape)
            .drawBehind {
                drawRect(androidx.compose.ui.graphics.lerp(Tokens.surface, Tokens.surface2, f))
                if (f > 0.01f) drawRoundRect(
                    accent.base.copy(alpha = f), cornerRadius = CornerRadius(11.dp.toPx()),
                    style = Stroke(2.dp.toPx()),
                )
            }
            .padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = Type.row, color = Tokens.text, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
            Text(desc, style = Type.small, color = Tokens.textDim, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp), content = control)
    }
}

/** Outer accent-tint ring + glow, drawn outside the element (design: 0 0 0 4px tint, 0 0 40px glow). */
fun Modifier.focusHalo(amount: Float, radius: androidx.compose.ui.unit.Dp): Modifier = drawBehind {
    if (amount <= 0.01f) return@drawBehind
    val accentTint = Color(0x243DDC84)
    val r = radius.toPx()
    val grow = 2.dp.toPx()
    drawRoundRect(
        accentTint.copy(alpha = accentTint.alpha * amount),
        topLeft = Offset(-grow, -grow), size = Size(size.width + grow * 2, size.height + grow * 2),
        cornerRadius = CornerRadius(r + grow),
    )
}

/** Pill toggle; the knob slides (RTL: off = right, on = left). */
@Composable
fun Toggle(on: Boolean) {
    val accent = LocalAccent.current
    val density = LocalDensity.current
    val x by animateFloatAsState(if (on) 1f else 0f, tween(200, easing = Motion.standard), label = "knob")
    val track by animateColorAsState(if (on) accent.base else Tokens.track, tween(180), label = "track")
    val knob by animateColorAsState(if (on) Color.White else Tokens.knob, tween(180), label = "knobc")
    Box(
        Modifier
            .size(width = 54.dp, height = 28.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(track)
            .border(1.dp, if (on) accent.base else Tokens.line, RoundedCornerShape(percent = 50)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .size(21.dp)
                .graphicsLayer {
                    // CenterStart = right edge in RTL; travel toward the left when on
                    translationX = -x * with(density) { (54.dp - 21.dp - 6.dp).toPx() }
                }
                .clip(RoundedCornerShape(percent = 50))
                .background(knob),
        )
    }
}

/** Stepped segmented control. Selected = solid accent, dark text. */
@Composable
fun Segmented(labels: List<String>, selected: Int) {
    val accent = LocalAccent.current
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Tokens.segBg)
            .border(1.dp, Tokens.line, RoundedCornerShape(8.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        labels.forEachIndexed { i, lab ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) accent.base else Color.Transparent, tween(150), label = "seg")
            val fg by animateColorAsState(if (sel) accent.onDark else Tokens.textDim, tween(150), label = "segt")
            Box(
                Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 15.dp, vertical = 7.dp),
            ) { Text(lab, style = Type.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = fg) }
        }
    }
}

/**
 * Keep row [index] fully visible with a margin, scrolling only as far as needed
 * (design: applyExcFocus) instead of jumping the focused row to the top.
 */
@Composable
fun KeepInView(state: LazyListState, index: Int, marginDp: Int = 20) {
    val margin = with(LocalDensity.current) { marginDp.dp.toPx() }
    LaunchedEffect(index) {
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        if (item == null) {
            if (index >= 0) state.animateScrollToItem(index)
            return@LaunchedEffect
        }
        val d = Geometry.revealDelta(
            item.offset.toFloat(), (item.offset + item.size).toFloat(),
            info.viewportStartOffset.toFloat(), info.viewportEndOffset.toFloat() - info.afterContentPadding, margin,
        )
        if (d != 0f) state.animateScrollBy(d, tween(280, easing = Motion.standard))
    }
}

/** Soft fade at the top and bottom edge of a scrolling list (design: bottom fade affordance). */
fun Modifier.edgeFade(): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color.Transparent, 0.04f to Color.Black, 0.88f to Color.Black, 1f to Color.Transparent,
            ),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }
