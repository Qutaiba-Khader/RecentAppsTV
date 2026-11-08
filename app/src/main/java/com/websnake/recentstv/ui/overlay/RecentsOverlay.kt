package com.websnake.recentstv.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.websnake.recentstv.R
import com.websnake.recentstv.data.RecentTask
import com.websnake.recentstv.logic.CardShape
import com.websnake.recentstv.logic.CardSize
import com.websnake.recentstv.logic.Geometry
import com.websnake.recentstv.logic.OvState
import com.websnake.recentstv.logic.PEEK_NONE
import com.websnake.recentstv.logic.SettingsCodec
import com.websnake.recentstv.ui.theme.LocalAccent
import com.websnake.recentstv.ui.theme.Motion
import com.websnake.recentstv.ui.theme.Tokens
import com.websnake.recentstv.ui.theme.Type
import com.websnake.recentstv.ui.theme.dp
import com.websnake.recentstv.ui.theme.shape
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val KILL_KEY = "killall"
private val GAP = 20.dp
private val PAD = 30.dp
private val PEEK = 23.dp
private val FLY = 350.dp
private val LABEL_GAP = 15.dp   // design: 30px → 40px below the card as it fades in

/**
 * Surface 1 — the recent-apps carousel (RTL: Kill All far right, newest next to it).
 * Positions are computed (Geometry), not measured, and every moving value is read in
 * the draw phase (graphicsLayer / drawBehind), so a focus move or a dismiss animates
 * without recomposing or re-laying-out the row.
 *
 * [loadId] changes on every fresh load: positions snap instead of sliding in.
 */
@Composable
fun RecentsOverlay(
    state: OvState,
    info: Map<Int, RecentTask>,
    size: CardSize,
    shape: CardShape,
    loadId: Int,
    launchingId: Int?,
    onFlyComplete: (Int) -> Unit,
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val viewportW = constraints.maxWidth.toFloat()
    val cardDp = size.dp
    val cardPx = with(density) { cardDp.toPx() }
    val cardShape = shape.shape()

    // visual order, left → right: oldest … newest, Kill All
    val keys: List<Any> = remember(state.tasks, state.hasKillAll) {
        buildList {
            state.tasks.asReversed().forEach { add(it.taskId) }
            if (state.hasKillAll) add(KILL_KEY)
        }
    }
    val strip = remember(keys, cardPx) {
        with(density) {
            Geometry.layout(keys.map { if (it == KILL_KEY) cardPx * 1.34f else cardPx }, GAP.toPx(), PAD.toPx())
        }
    }
    val focusVisual = keys.size - 1 - state.focus
    val targetTx = Geometry.stripOffset(viewportW, strip, focusVisual)
    val tx = remember(loadId) { Animatable(targetTx) }
    LaunchedEffect(targetTx, loadId) {
        if (tx.targetValue != targetTx) tx.animateTo(targetTx, tween(Motion.SCROLL_MS, easing = Motion.standard))
    }

    // ── header: count on the left, title + gear on the right (RTL start) ──
    Box(Modifier.fillMaxWidth().padding(start = 60.dp, end = 44.dp, top = 40.dp)) {
        Text(
            SettingsCodec.countLabel(state.tasks.size), style = Type.small, color = Tokens.textDim,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Text("Recent apps", style = Type.title, color = Tokens.text)
            SettingsGear(focused = state.gear)
        }
    }

    // ── the row ──
    keys.forEachIndexed { vi, k ->
        key(k) {
            val left = strip.lefts[vi]
            val leftAnim = remember(loadId) { Animatable(left) }
            LaunchedEffect(left) {   // a neighbour was removed: slide over to close the gap
                if (leftAnim.targetValue != left) leftAnim.animateTo(left, tween(Motion.FOCUS_MS, easing = Motion.standard))
            }
            val logical = keys.size - 1 - vi
            val focused = !state.gear && logical == state.focus
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer { translationX = leftAnim.value + tx.value },
            ) {
                if (k == KILL_KEY) {
                    KillAllTile(focused, cardDp, cardShape, state.killing)
                } else {
                    val task = info[k as Int]
                    if (task != null) AppCard(
                        task = task,
                        focused = focused,
                        peek = focused && state.peek != PEEK_NONE,
                        flying = state.flying == task.taskId,
                        killing = state.killing,
                        staggerIndex = logical,
                        launching = launchingId == task.taskId,
                        dimmed = launchingId != null && launchingId != task.taskId,
                        cardDp = cardDp, cardShape = cardShape,
                        onFlyComplete = { onFlyComplete(task.taskId) },
                    )
                }
            }
        }
    }

    // ── one label under the focused card (design: name + category, fades in 40 ms late) ──
    val labelTask = state.focusedTask?.takeIf {
        state.peek == PEEK_NONE && state.flying == null && !state.killing && launchingId == null && state.ctxTaskId == null
    }?.let { info[it.taskId] }
    FocusLabel(
        task = labelTask,
        centerX = { strip.center(focusVisual) + tx.value - viewportW / 2f },
        topOffset = cardDp * Motion.FOCUS_SCALE / 2f + LABEL_GAP,
    )
}

@Composable
private fun FocusLabel(task: RecentTask?, centerX: () -> Float, topOffset: Dp) {
    val density = LocalDensity.current
    val alpha = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    LaunchedEffect(task?.taskId) {
        alpha.snapTo(0f); rise.snapTo(0f)
        if (task != null) coroutineScope {
            delay(40)
            launch { alpha.animateTo(1f, tween(200)) }
            launch { rise.animateTo(1f, tween(200, easing = Motion.standard)) }
        }
    }
    if (task == null) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .requiredWidth(560.dp)
                .graphicsLayer {
                    translationX = centerX()
                    translationY = with(density) { (topOffset + 5.dp * rise.value).toPx() } + size.height / 2f
                    this.alpha = alpha.value
                },
        ) {
            Text(task.label, style = Type.label, color = Tokens.text, maxLines = 1, textAlign = TextAlign.Center)
            if (task.category.isNotBlank()) {
                Text(
                    task.category, style = Type.small, color = Tokens.textDim, maxLines = 1,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsGear(focused: Boolean) {
    val accent = LocalAccent.current
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(Motion.FOCUS_MS, easing = Motion.standard), label = "gear")
    val bg by animateColorAsState(if (focused) accent.base else Tokens.surface2, tween(160), label = "gearBg")
    val fg by animateColorAsState(if (focused) accent.onDark else Tokens.textDim, tween(160), label = "gearFg")
    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.5.dp, if (focused) accent.base else Tokens.line, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = "Settings",
            colorFilter = ColorFilter.tint(fg),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun AppCard(
    task: RecentTask,
    focused: Boolean,
    peek: Boolean,
    flying: Boolean,
    killing: Boolean,
    staggerIndex: Int,
    launching: Boolean,
    dimmed: Boolean,
    cardDp: Dp,
    cardShape: Shape,
    onFlyComplete: () -> Unit,
) {
    val density = LocalDensity.current
    val accent = LocalAccent.current
    val std = tween<Float>(Motion.FOCUS_MS, easing = Motion.standard)
    val scale = animateFloatAsState(
        when { launching -> 1.32f; focused -> Motion.FOCUS_SCALE; else -> Motion.IDLE_SCALE },
        if (launching) tween(Motion.LAUNCH_MS, easing = Motion.standard) else std, label = "scale",
    )
    val focusAmt = animateFloatAsState(if (focused) 1f else 0f, std, label = "focus")
    val baseAlpha = animateFloatAsState(
        when { dimmed -> 0f; focused -> 1f; else -> Motion.IDLE_ALPHA },
        if (dimmed) tween(Motion.LAUNCH_MS) else std, label = "alpha",
    )
    val peekY = animateFloatAsState(if (peek) with(density) { PEEK.toPx() } else 0f, std, label = "peek")
    val fly = remember { Animatable(0f) }
    val flyAlpha = remember { Animatable(1f) }
    val flyPx = with(density) { FLY.toPx() }

    LaunchedEffect(flying) {
        if (flying) {
            coroutineScope {
                launch { fly.animateTo(flyPx, tween(Motion.FLY_MS, easing = Motion.flyOut)) }
                launch { flyAlpha.animateTo(0f, tween(Motion.FLY_MS, easing = Motion.flyOut)) }
            }
            onFlyComplete()
        }
    }
    LaunchedEffect(killing) {
        if (killing) {
            delay(staggerIndex * Motion.KILL_STAGGER_MS.toLong())
            coroutineScope {
                launch { fly.animateTo(-flyPx, tween(Motion.FLY_MS, easing = Motion.flyOut)) }
                launch { flyAlpha.animateTo(0f, tween(Motion.FLY_MS, easing = Motion.flyOut)) }
            }
        }
    }

    // Fixed footprint = exactly one card; glow, streak and hint overflow it.
    Box(Modifier.size(cardDp), contentAlignment = Alignment.Center) {
        // accent glow + motion streak (draw phase only)
        Box(
            Modifier
                .requiredSize(cardDp * 1.7f)
                .drawBehind {
                    val f = focusAmt.value * flyAlpha.value * baseAlpha.value
                    val y = peekY.value + fly.value
                    if (f > 0.01f) {
                        val s = scale.value
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(accent.glow.copy(alpha = accent.glow.alpha * f), Color.Transparent),
                                center = center + Offset(0f, y), radius = size.minDimension / 2f * s,
                            ),
                            radius = size.minDimension / 2f * s, center = center + Offset(0f, y),
                        )
                    }
                    if (fly.value > 1f) {        // streak trailing a card flying down
                        val w = 3.dp.toPx(); val len = 110.dp.toPx()
                        val tail = center.y + y - cardDp.toPx() * 0.5f
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(Color.Transparent, accent.glow), tail - len, tail),
                            topLeft = Offset(center.x - w / 2f, tail - len), size = Size(w, len),
                            cornerRadius = CornerRadius(w), alpha = 0.5f * flyAlpha.value,
                        )
                    }
                },
        )
        // the card: one layer for transform + clip; the ring is drawn on top, inside the clip
        Box(
            Modifier
                .size(cardDp)
                .graphicsLayer {
                    scaleX = scale.value; scaleY = scale.value
                    translationY = peekY.value + fly.value
                    alpha = baseAlpha.value * flyAlpha.value
                    this.shape = cardShape; clip = true
                }
                .background(Tokens.card)
                .drawWithContent {
                    drawContent()
                    val f = focusAmt.value
                    if (f > 0.01f) drawOutline(
                        cardShape.createOutline(size, layoutDirection, this),
                        accent.base.copy(alpha = f), style = Stroke(width = 5.dp.toPx() / scale.value),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val icon = task.icon
            if (icon != null) {
                Image(
                    bitmap = icon, contentDescription = task.label, contentScale = ContentScale.Fit,
                    modifier = Modifier.size(cardDp * 0.66f).clip(cardShape),
                )
            } else {
                Text(task.label.take(1).uppercase(), style = Type.hero, color = Tokens.text)
            }
        }
        // stage-1 hint, in the peek direction
        AnimatedVisibility(
            visible = peek && !flying,
            enter = fadeIn(tween(160)), exit = fadeOut(tween(120)),
            modifier = Modifier
                .requiredWidth(cardDp * 4.5f)
                .graphicsLayer { translationY = with(density) { (cardDp * 0.5f * Motion.FOCUS_SCALE + 40.dp).toPx() } },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(accent.tint)
                        .border(1.dp, accent.border, RoundedCornerShape(percent = 50))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("▼", style = Type.small, color = accent.base)
                    Text("Press again to remove", style = Type.small, color = accent.base, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun KillAllTile(focused: Boolean, cardDp: Dp, cardShape: Shape, killing: Boolean) {
    val accent = LocalAccent.current
    val std = tween<Float>(Motion.FOCUS_MS, easing = Motion.standard)
    val scale = animateFloatAsState(if (focused) Motion.KILL_FOCUS_SCALE else 1f, std, label = "kscale")
    val f = animateFloatAsState(if (focused) 1f else 0f, std, label = "kfocus")
    val gone = animateFloatAsState(if (killing) 0f else 1f, tween(200), label = "kgone")
    val glyph by animateColorAsState(if (focused) accent.onDark else accent.base, tween(Motion.FOCUS_MS), label = "kglyph")
    val w = cardDp * 1.34f
    val h = cardDp * 1.18f

    Box(Modifier.size(w, h), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .requiredSize(w * 1.5f, h * 1.5f)
                .drawBehind {
                    val a = f.value * gone.value
                    if (a > 0.01f) drawCircle(
                        Brush.radialGradient(listOf(accent.glow.copy(alpha = accent.glow.alpha * a), Color.Transparent)),
                        radius = size.minDimension / 2f * scale.value,
                    )
                },
        )
        // unfocused: accent tint + 45 % accent border; focused: solid accent (design)
        Column(
            Modifier
                .size(w, h)
                .graphicsLayer {
                    scaleX = scale.value; scaleY = scale.value; alpha = gone.value
                    this.shape = cardShape; clip = true
                }
                .drawBehind {
                    val outline = cardShape.createOutline(size, layoutDirection, this)
                    drawOutline(outline, lerp(accent.tint, accent.base, f.value))
                    drawOutline(outline, lerp(accent.border, accent.base, f.value), style = Stroke(2.dp.toPx()))
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_clear_all),
                contentDescription = "Kill all",
                colorFilter = ColorFilter.tint(glyph),
                modifier = Modifier.size(cardDp * 0.42f),
            )
            Text("Kill all", style = Type.killLabel, color = glyph, maxLines = 1)
        }
    }
}
