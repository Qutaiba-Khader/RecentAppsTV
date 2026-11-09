package com.websnake.recentstv.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.websnake.recentstv.R
import com.websnake.recentstv.data.RecentTask
import com.websnake.recentstv.ui.theme.LocalAccent
import com.websnake.recentstv.ui.theme.Motion
import com.websnake.recentstv.ui.theme.Tokens
import com.websnake.recentstv.ui.theme.Type
import kotlin.math.roundToInt

private val MenuBg = Color(0xFF1B1E23)
private val MENU_W = 280.dp
private val MENU_GAP = 31.dp     // balloon tip → focused card
private val TIP = 10.dp

/**
 * Long-press OK balloon (screens 05, 06): above the target card with a downward tip.
 * Rows, top to bottom (1.9): Close · App info · Add to exceptions · Pin to row / Unpin (nearest the card, focused first). [selected] is
 * driven by the logic (UP/DOWN); OK runs it, BACK closes. Fades and rises in.
 */
@Composable
fun ContextMenu(
    task: RecentTask?,
    pinned: Boolean,
    running: Boolean,
    selected: Int,
    anchorX: () -> Float,
    anchorTop: () -> Float,
    viewportW: Float,
) {
    var last by remember { mutableStateOf<Triple<RecentTask, Boolean, Boolean>?>(null) }
    if (task != null) last = Triple(task, pinned, running)
    val shown by animateFloatAsState(if (task != null) 1f else 0f, tween(if (task != null) 220 else 140, easing = Motion.standard), label = "menu")
    val (t, isPinned, _) = last ?: return
    if (shown <= 0f && task == null) return

    val rows = listOf(
        (if (isPinned) "Unpin" else "Pin to row") to (if (isPinned) R.drawable.ic_unpin else R.drawable.ic_pin),
        "Add to exceptions" to R.drawable.ic_hide,
        "App info" to R.drawable.ic_info,
        "Close" to R.drawable.ic_close,
    )
    val fade = Modifier.graphicsLayer {
        alpha = shown
        translationY = (1f - shown) * 8.dp.toPx()
    }
    // the body is clamped to the screen; the tip always points at the card
    Column(
        Modifier
            .layout { m, c ->
                val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
                layout(c.maxWidth, c.maxHeight) {
                    val margin = 16.dp.toPx()
                    val x = (anchorX() - p.width / 2f).coerceIn(margin, viewportW - p.width - margin)
                    p.place(x.roundToInt(), (anchorTop() - MENU_GAP.toPx() - p.height).roundToInt())
                }
            }
            .then(fade)
            .width(MENU_W)
            .shadow(40.dp, RoundedCornerShape(15.dp))
            .clip(RoundedCornerShape(15.dp))
            .background(MenuBg)
            .border(1.dp, Tokens.text.copy(alpha = 0.12f), RoundedCornerShape(15.dp))
            .padding(7.dp),
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(Tokens.card), contentAlignment = Alignment.Center) {
                t.icon?.let { Image(it, null, Modifier.size(24.dp), contentScale = ContentScale.Fit) }
            }
            Column {
                Text(t.label, style = Type.label.copy(fontWeight = FontWeight.ExtraBold), color = Tokens.text, maxLines = 1)
                // 1.10.1: no running status under the name (owner); only "Pinned" for a pinned app
                if (isPinned) Text("Pinned", style = Type.small, color = Tokens.textDim, maxLines = 1)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.line))
        Spacer(Modifier.height(5.dp))
        // 1.9: bottom-up — Pin to row nearest the card, Close at the top; [selected] counts from the bottom
        rows.asReversed().forEachIndexed { v, (label, icon) -> MenuItem(label, icon, rows.lastIndex - v == selected) }
    }
    Box(
        Modifier
            .layout { m, c ->
                val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
                layout(c.maxWidth, c.maxHeight) {
                    p.place((anchorX() - p.width / 2f).roundToInt(), (anchorTop() - MENU_GAP.toPx() - 1f).roundToInt())
                }
            }
            .then(fade)
            .size(20.dp, TIP)
            .background(MenuBg, TipShape),
    )
}

@Composable
private fun MenuItem(label: String, icon: Int, focused: Boolean) {
    val accent = LocalAccent.current
    val bg by animateColorAsState(if (focused) accent.base else Color.Transparent, tween(140), label = "mi")
    val fg by animateColorAsState(if (focused) accent.onDark else Tokens.text, tween(140), label = "mif")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Image(painterResource(icon), null, colorFilter = ColorFilter.tint(fg), modifier = Modifier.size(17.dp))
        Text(label, style = Type.body.copy(fontWeight = FontWeight.Bold), color = fg, maxLines = 1)
    }
}

private val TipShape = GenericShape { size, _ -> moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }
