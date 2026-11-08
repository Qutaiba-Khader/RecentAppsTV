package com.websnake.recentstv.ui.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.websnake.recentstv.R
import com.websnake.recentstv.data.RecentTask
import com.websnake.recentstv.logic.Geometry
import com.websnake.recentstv.logic.Item
import com.websnake.recentstv.logic.OvState
import com.websnake.recentstv.logic.PEEK_NONE
import com.websnake.recentstv.logic.PEEK_UP
import com.websnake.recentstv.logic.Scene
import com.websnake.recentstv.logic.Settings
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
import kotlin.math.roundToInt

private val GAP = 20.dp
private val PIN_GAP = 16.dp
private val GROUP_PAD = 17.dp
private val EDGE = 38.dp
private val PEEK = 23.dp
private val FLY = 350.dp
private val LABEL_GAP = 15.dp         // design: 30px → 40px below the card as it fades in
private val MENU_SHIFT = 97.dp        // row slides down while the long-press menu is open
private val EMPTY_SLOT_W = 320.dp
private const val SETTINGS_TILE = 0.86f   // ⚙ tile width, × card size (in the row, when Kill All isn't shown)
private val GEAR_H = 38.dp                 // ⚙ Settings button above Kill All
private val GEAR_GAP = 16.dp
private val MENU_EST_H = 240.dp        // long-press menu height (header + 4 rows)
private const val DIMMED = 0.32f

/** One place in the row. [item] null = the "No recent apps" placeholder (not focusable). */
private class Slot(val key: String, val item: Item?, val logical: Int, val width: Dp, val grouped: Boolean)

/**
 * Surface 1 — the recent-apps row (RTL: Kill All far right, newest next to it, pins at
 * the chosen end). Positions are computed (Geometry), not measured, and every moving
 * value is read in the draw phase (graphicsLayer / drawBehind), so a focus move or a
 * dismiss animates without recomposing or re-laying-out the row.
 *
 * [loadId] changes on every fresh load: positions snap instead of sliding in.
 */
@Composable
fun RecentsOverlay(
    state: OvState,
    info: Map<String, RecentTask>,
    settings: Settings,
    loadId: Int,
    onFlyComplete: (String) -> Unit,
    limited: Boolean = false,
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val viewportW = constraints.maxWidth.toFloat()
    val cardDp = settings.size.dp
    val pinDp = cardDp * settings.pinSize.factor
    val cardShape = settings.shape.shape()
    val menuOpen = state.ctxPkg != null
    val selecting = state.selecting

    // everything but the chosen card fades out when an app is chosen
    val chrome = animateFloatAsState(if (selecting != null) 0f else 1f, tween(Motion.CHROME_FADE_MS), label = "chrome")
    // the row slides down 97dp (design); on a short screen far enough for the whole menu to fit above it
    val shiftOpen = with(density) {
        val half = constraints.maxHeight / 2f
        val cardHalf = (cardDp * Motion.FOCUS_SCALE / 2f).toPx()
        val need = (MENU_EST_H + 31.dp + 8.dp).toPx() - (half - cardHalf)
        maxOf(MENU_SHIFT.toPx(), need).coerceAtMost(half - cardHalf - 8.dp.toPx())
    }
    val shift = animateFloatAsState(
        if (menuOpen) shiftOpen else 0f,
        tween(Motion.MENU_SHIFT_MS, easing = Motion.standard), label = "shift",
    )

    // ── slots, visual order left → right (logical order is right → left) ──
    val slots: List<Slot> = remember(state.items, state.emptySlot, state.pinRight, cardDp, pinDp) {
        val logical = state.items.mapIndexed { i, it ->
            val w = when (it) {
                Item.Settings -> cardDp * SETTINGS_TILE; Item.KillAll -> cardDp * 1.34f; is Item.Pin -> pinDp; is Item.Task -> cardDp
            }
            Slot(it.key, it, i, w, it is Item.Pin)
        }.toMutableList()
        if (state.emptySlot) {
            val e = Slot("#empty", null, -1, EMPTY_SLOT_W, false)
            if (state.pinRight) logical.add(e) else logical.add(1, e)     // after the ⚙ tile
        }
        logical.asReversed().toList()
    }
    val strip = remember(slots, density) {
        with(density) {
            Geometry.layout(
                slots.map { it.width.toPx() },
                Geometry.groupGaps(slots.map { it.grouped }, GAP.toPx(), PIN_GAP.toPx(), GROUP_PAD.toPx()),
                (EDGE + GROUP_PAD).toPx(),
            )
        }
    }
    val focusVisual = slots.indexOfFirst { it.logical == state.focus && it.item != null }
    val targetTx = Geometry.stripOffset(viewportW, strip, focusVisual)
    val tx = remember(loadId) { Animatable(targetTx) }
    LaunchedEffect(targetTx, loadId) {
        if (tx.targetValue != targetTx) tx.animateTo(targetTx, tween(Motion.SCROLL_MS, easing = Motion.standard))
    }

    // ── header: centred title with the count line under it ──
    Column(
        Modifier.fillMaxWidth().padding(top = 36.dp).graphicsLayer { alpha = chrome.value },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Recent apps", style = Type.title, color = Tokens.text)
        val count = SettingsCodec.countLabel(state.tasks.size, state.pins.size)
        if (count.isNotEmpty()) Text(count, style = Type.small, color = Tokens.textDim, modifier = Modifier.padding(top = 6.dp))
    }

    // ── pinned group frame + its "Pinned" label ──
    val pinVis = slots.indices.filter { slots[it].grouped }
    if (pinVis.isNotEmpty()) {
        val l = strip.lefts[pinVis.first()] - with(density) { GROUP_PAD.toPx() }
        val r = strip.lefts[pinVis.last()] + strip.widths[pinVis.last()] + with(density) { GROUP_PAD.toPx() }
        PinnedFrame(
            left = l, right = r, loadId = loadId, tx = { tx.value }, shift = { shift.value },
            pinDp = pinDp, viewportW = viewportW,
            alpha = {
                val chosenInside = selecting != null && slots.any { it.grouped && it.key == selecting }
                when {
                    selecting != null -> if (chosenInside) 0f else chrome.value
                    menuOpen && state.ctxItem !is Item.Pin -> DIMMED
                    else -> 1f
                }
            },
        )
    }

    // ── the row ──
    slots.forEachIndexed { vi, slot ->
        key(slot.key) {
            val left = strip.lefts[vi]
            val leftAnim = remember(loadId) { Animatable(left) }
            LaunchedEffect(left) {   // a neighbour was removed or moved: slide over
                if (leftAnim.targetValue != left) leftAnim.animateTo(left, tween(Motion.FOCUS_MS, easing = Motion.standard))
            }
            val item = slot.item
            val focused = item != null && state.scene == Scene.OVERLAY && slot.logical == state.focus && !state.gearFocused
            val chosen = selecting != null && slot.key == selecting
            val dim = animateFloatAsState(
                if (menuOpen && slot.key != state.ctxPkg) DIMMED else 1f, tween(200), label = "dim",
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        // whole pixels: text and icon move together, no sub-pixel shimmer
                        translationX = (leftAnim.value + tx.value).roundToInt().toFloat()
                        translationY = shift.value.roundToInt().toFloat()
                        alpha = dim.value * (if (chosen) 1f else chrome.value)
                    },
            ) {
                when (item) {
                    null -> EmptySlotBox(cardDp)
                    Item.Settings -> SettingsTile(focused, cardDp, cardShape)
                    Item.KillAll -> KillAllTile(focused, cardDp, cardShape, state.killing)
                    is Item.Task, is Item.Pin -> {
                        val task = info[slot.key]
                        if (task != null) AppCard(
                            task = task,
                            pinned = item is Item.Pin,
                            running = item !is Item.Pin || (item as Item.Pin).running,
                            focused = focused,
                            pressing = focused && state.pressing,
                            peek = if (focused) state.peek else PEEK_NONE,
                            flying = state.flying == slot.key,
                            flyDir = state.flyDir,
                            killing = state.killing && item is Item.Task,
                            staggerIndex = slot.logical,
                            labelOn = focused && state.peek == PEEK_NONE && state.flying == null && !state.killing &&
                                selecting == null && !menuOpen,
                            cardDp = slot.width, cardShape = cardShape,
                            onFlyComplete = { onFlyComplete(slot.key) },
                        )
                    }
                }
            }
        }
    }

    // ── the ⚙ Settings button above Kill All (↑ from Kill All) ──
    val killVisual = slots.indexOfFirst { it.item == Item.KillAll }
    if (killVisual >= 0) {
        val killLeft = strip.lefts[killVisual]
        val gearLeft = remember(loadId) { Animatable(killLeft) }
        LaunchedEffect(killLeft) {
            if (gearLeft.targetValue != killLeft) gearLeft.animateTo(killLeft, tween(Motion.FOCUS_MS, easing = Motion.standard))
        }
        // also out of the way while the "Press again to remove" pill is up: next to Kill all they overlapped (A50)
        val gone = animateFloatAsState(if (state.killing || state.peek != PEEK_NONE) 0f else 1f, tween(200), label = "gearGone")
        val dim = animateFloatAsState(if (menuOpen) DIMMED else 1f, tween(200), label = "gearDim")
        val lift = with(density) { (cardDp * 1.18f * Motion.KILL_FOCUS_SCALE / 2f + GEAR_GAP + GEAR_H / 2f).toPx() }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    translationX = (gearLeft.value + tx.value).roundToInt().toFloat()
                    translationY = (shift.value - lift).roundToInt().toFloat()
                    alpha = gone.value * dim.value * chrome.value
                },
        ) { SettingsButton(state.gearFocused, cardDp * 1.34f) }
    }

    // ── long-press menu, above the target card ──
    val ctxTask = state.ctxPkg?.let { info[it] }
    val ctxVisual = slots.indexOfFirst { it.key == state.ctxPkg }
    ContextMenu(
        task = ctxTask,
        pinned = state.ctxItem is Item.Pin,
        running = when (val it = state.ctxItem) { is Item.Pin -> it.running; is Item.Task -> true; else -> false },
        selected = state.ctxSel,
        anchorX = { if (ctxVisual >= 0) strip.center(ctxVisual) + tx.value else viewportW / 2f },
        anchorTop = {
            val w = if (ctxVisual >= 0) slots[ctxVisual].width else cardDp
            constraints.maxHeight / 2f + shift.value - with(density) { (w * Motion.FOCUS_SCALE / 2f).toPx() }
        },
        viewportW = viewportW,
    )

    // ── footer key hints (hidden while the menu is open) ──
    val footer by animateFloatAsState(if (menuOpen) 0f else 1f, tween(200), label = "foot")
    Row(
        Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp).graphicsLayer { alpha = footer * chrome.value },
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyHint("OK", "Open"); KeyHint("Hold OK", "Options"); KeyHint("↑ ↓", "Twice to remove"); KeyHint("Back", "Close")
    }
    // 1.7: Shizuku is off (after a TV reboot): the row comes from the last list + usage access. Above the key
    // hints, clear of the ⚙ button (on a short screen it overlapped the header)
    if (limited) Text(
        "Shizuku is off · apps open from their start screen · removals wait for Shizuku",
        style = Type.small, color = LIMITED_TINT, maxLines = 1,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 74.dp).graphicsLayer { alpha = footer * chrome.value },
    )

    // ── select: the chosen tile grows to full screen in the app's colour ──
    if (selecting != null) {
        val t = info[selecting]
        val vi = slots.indexOfFirst { it.key == selecting }
        if (t != null && vi >= 0) {
            val w = with(density) { (slots[vi].width * Motion.PRESS_SCALE).toPx() }
            val cx = strip.center(vi) + tx.value
            val cy = constraints.maxHeight / 2f + shift.value
            SelectZoom(
                task = t,
                from = androidx.compose.ui.geometry.Rect(cx - w / 2f, cy - w / 2f, cx + w / 2f, cy + w / 2f),
                startRadius = settings.shape.let { s ->
                    when (s) {
                        com.websnake.recentstv.logic.CardShape.CIRCLE -> w / 2f
                        com.websnake.recentstv.logic.CardShape.ROUNDED -> with(density) { 16.dp.toPx() } * Motion.PRESS_SCALE
                        com.websnake.recentstv.logic.CardShape.SQUARE -> with(density) { 8.dp.toPx() } * Motion.PRESS_SCALE
                    }
                },
                iconDp = slots[vi].width * 0.66f,
            )
        }
    }
}

private val LIMITED_TINT = androidx.compose.ui.graphics.Color(0xFFE8B04B)

@Composable
private fun KeyHint(key: String, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            key, style = Type.small.copy(fontWeight = FontWeight.Bold), color = Tokens.text, maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Tokens.text.copy(alpha = 0.08f))
                .border(1.dp, Tokens.line, RoundedCornerShape(4.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
        Text(label, style = Type.small, color = Tokens.textDim, maxLines = 1)
    }
}

/** The faint rounded frame around the pinned apps, with "Pinned" above it. Moves with the strip. */
@Composable
private fun PinnedFrame(
    left: Float, right: Float, loadId: Int, tx: () -> Float, shift: () -> Float,
    pinDp: Dp, viewportW: Float, alpha: () -> Float,
) {
    val density = LocalDensity.current
    val l = remember(loadId) { Animatable(left) }
    val r = remember(loadId) { Animatable(right) }
    LaunchedEffect(left) { if (l.targetValue != left) l.animateTo(left, tween(Motion.FOCUS_MS, easing = Motion.standard)) }
    LaunchedEffect(right) { if (r.targetValue != right) r.animateTo(right, tween(Motion.FOCUS_MS, easing = Motion.standard)) }
    val h = with(density) { (pinDp + GROUP_PAD * 2).toPx() }
    Box(
        Modifier.fillMaxSize().drawBehind {
            val a = alpha()
            if (a <= 0.01f) return@drawBehind
            val x = l.value + tx()
            val top = size.height / 2f - h / 2f + shift()
            val cr = CornerRadius(22.dp.toPx())
            drawRoundRect(Color.White.copy(alpha = 0.035f * a), Offset(x, top), Size(r.value - l.value, h), cr)
            drawRoundRect(Color.White.copy(alpha = 0.07f * a), Offset(x, top), Size(r.value - l.value, h), cr, style = Stroke(1.dp.toPx()))
        },
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.graphicsLayer {
                this.alpha = alpha()
                translationX = ((l.value + r.value) / 2f + tx() - viewportW / 2f).roundToInt().toFloat()
                translationY = (-h / 2f - 8.dp.toPx() - size.height / 2f + shift()).roundToInt().toFloat()
            },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.ic_pin), null, colorFilter = ColorFilter.tint(Tokens.textDim), modifier = Modifier.size(13.dp))
            Text("Pinned", style = Type.small.copy(fontWeight = FontWeight.Bold), color = Tokens.textDim, maxLines = 1)
        }
    }
}

/** Empty-with-pins (screen 11): a dashed placeholder where the tasks would be. */
@Composable
private fun EmptySlotBox(cardDp: Dp) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(Motion.FOCUS_MS)) }
    Column(
        Modifier
            .size(EMPTY_SLOT_W, cardDp * 1.18f)
            .graphicsLayer { alpha = a.value }
            .drawBehind {
                drawRoundRect(
                    Color.White.copy(alpha = 0.14f), cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically),
    ) {
        Text("No recent apps", style = Type.label, color = Tokens.text, maxLines = 1)
        Text("Pinned apps are still one press away.", style = Type.small, color = Tokens.textDim, maxLines = 1)
    }
}

@Composable
private fun AppCard(
    task: RecentTask,
    pinned: Boolean,
    running: Boolean,
    focused: Boolean,
    pressing: Boolean,
    peek: Int,
    flying: Boolean,
    flyDir: Int,
    killing: Boolean,
    staggerIndex: Int,
    labelOn: Boolean,
    cardDp: Dp,
    cardShape: Shape,
    onFlyComplete: () -> Unit,
) {
    val density = LocalDensity.current
    val accent = LocalAccent.current
    val std = tween<Float>(Motion.FOCUS_MS, easing = Motion.standard)
    val scale = animateFloatAsState(
        when { pressing -> Motion.PRESS_SCALE; focused -> Motion.FOCUS_SCALE; else -> Motion.IDLE_SCALE },
        if (pressing) tween(Motion.PRESS_MS, easing = Motion.standard) else std, label = "scale",
    )
    val focusAmt = animateFloatAsState(if (focused) 1f else 0f, std, label = "focus")
    val baseAlpha = animateFloatAsState(if (focused) 1f else Motion.IDLE_ALPHA, std, label = "alpha")
    val peekY = animateFloatAsState(if (peek != PEEK_NONE) peek * with(density) { PEEK.toPx() } else 0f, std, label = "peek")
    val fly = remember { Animatable(0f) }
    val flyAlpha = remember { Animatable(1f) }
    val flyPx = with(density) { FLY.toPx() }
    // label: fades in 40 ms after the focus lands and out as it leaves — a cross-fade, never a blink
    val labelA = animateFloatAsState(
        if (labelOn) 1f else 0f, if (labelOn) tween(200, delayMillis = 40) else tween(160), label = "label",
    )
    var labelUsed by remember { mutableStateOf(false) }
    if (labelOn) labelUsed = true

    LaunchedEffect(flying) {
        if (flying) {
            coroutineScope {
                launch { fly.animateTo(flyDir * flyPx, tween(Motion.FLY_MS, easing = Motion.flyOut)) }
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

    // Fixed footprint = exactly one card; glow, streak, label and hint overflow it.
    Box(Modifier.size(cardDp), contentAlignment = Alignment.Center) {
        // accent glow + motion streak (draw phase only)
        Box(
            Modifier
                .requiredSize(cardDp * 1.7f)
                .drawBehind {
                    val f = focusAmt.value * flyAlpha.value
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
                    if (kotlin.math.abs(fly.value) > 1f) {        // streak trailing the flying card
                        val w = 3.dp.toPx(); val len = 110.dp.toPx()
                        val up = fly.value < 0f
                        val edge = center.y + y + (if (up) 1f else -1f) * cardDp.toPx() * 0.5f
                        val top = if (up) edge else edge - len
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                if (up) listOf(accent.glow, Color.Transparent) else listOf(Color.Transparent, accent.glow),
                                top, top + len,
                            ),
                            topLeft = Offset(center.x - w / 2f, top), size = Size(w, len),
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
        // pin badge on the top corner (RTL inline-end = left), moving with the card
        if (pinned) {
            Box(
                Modifier
                    .size(cardDp)
                    .graphicsLayer {
                        scaleX = scale.value; scaleY = scale.value
                        translationY = peekY.value + fly.value
                        alpha = flyAlpha.value
                    },
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .graphicsLayer { translationX = -5.dp.toPx(); translationY = -5.dp.toPx() }
                        .size(22.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Tokens.bg)
                        .border(1.dp, Tokens.text.copy(alpha = 0.22f), RoundedCornerShape(percent = 50)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.ic_pin), null, colorFilter = ColorFilter.tint(Tokens.text), modifier = Modifier.size(11.dp))
                }
            }
            // running dot under an unfocused pinned app that is also running
            if (running) Box(
                Modifier
                    .graphicsLayer {
                        translationY = (cardDp * Motion.IDLE_SCALE / 2f + 11.dp).toPx()
                        alpha = 0.7f * (1f - focusAmt.value) * flyAlpha.value
                    }
                    .size(5.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Tokens.text),
            )
        }
        // name + category under the focused card
        if (labelUsed) Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .requiredWidth(300.dp)
                .graphicsLayer {
                    alpha = labelA.value
                    val y = (cardDp * Motion.FOCUS_SCALE / 2f + LABEL_GAP + 5.dp * labelA.value).toPx() + size.height / 2f
                    translationY = y.roundToInt().toFloat()
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
        // stage-1 hint, on the side the card is peeking to
        PeekHint(peek = if (flying) PEEK_NONE else peek, cardDp = cardDp)
    }
}

@Composable
private fun PeekHint(peek: Int, cardDp: Dp) {
    val accent = LocalAccent.current
    var lastDir by remember { mutableStateOf(PEEK_UP) }
    if (peek != PEEK_NONE) lastDir = peek
    val a by animateFloatAsState(if (peek != PEEK_NONE) 1f else 0f, tween(if (peek != PEEK_NONE) 160 else 120), label = "hint")
    if (a <= 0f) return
    Box(
        Modifier
            .requiredWidth(cardDp * 4.5f)
            .graphicsLayer {
                alpha = a
                translationY = lastDir * (cardDp * 0.5f * Motion.FOCUS_SCALE + 40.dp).toPx()
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(accent.tint)
                .border(1.dp, accent.border, RoundedCornerShape(percent = 50))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (lastDir == PEEK_UP) "▲" else "▼", style = Type.small, color = accent.base)
            Text("Press again to remove", style = Type.small, color = accent.base, maxLines = 1)
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

/** The ⚙ tile at the right end of the row: the way into Settings. Neutral until focused. */
@Composable
private fun SettingsTile(focused: Boolean, cardDp: Dp, cardShape: Shape) {
    val accent = LocalAccent.current
    val std = tween<Float>(Motion.FOCUS_MS, easing = Motion.standard)
    val scale = animateFloatAsState(if (focused) Motion.KILL_FOCUS_SCALE else 1f, std, label = "gscale")
    val f = animateFloatAsState(if (focused) 1f else 0f, std, label = "gfocus")
    val glyph by animateColorAsState(if (focused) accent.onDark else Tokens.textDim, tween(Motion.FOCUS_MS), label = "gglyph")
    val w = cardDp * SETTINGS_TILE

    Box(Modifier.size(w), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .requiredSize(w * 1.5f)
                .drawBehind {
                    if (f.value > 0.01f) drawCircle(
                        Brush.radialGradient(listOf(accent.glow.copy(alpha = accent.glow.alpha * f.value), Color.Transparent)),
                        radius = size.minDimension / 2f * scale.value,
                    )
                },
        )
        Column(
            Modifier
                .size(w)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.shape = cardShape; clip = true }
                .drawBehind {
                    val outline = cardShape.createOutline(size, layoutDirection, this)
                    drawOutline(outline, lerp(Tokens.surface2, accent.base, f.value))
                    drawOutline(outline, lerp(Tokens.line, accent.base, f.value), style = Stroke(2.dp.toPx()))
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = "Settings",
                colorFilter = ColorFilter.tint(glyph),
                modifier = Modifier.size(w * 0.38f),
            )
            Text("Settings", style = Type.small.copy(fontWeight = FontWeight.Bold), color = glyph, maxLines = 1, softWrap = false)
        }
    }
}

/** The ⚙ Settings button above Kill All: a pill as wide as Kill All. Neutral until focused (↑ from Kill All). */
@Composable
private fun SettingsButton(focused: Boolean, width: Dp) {
    val accent = LocalAccent.current
    val std = tween<Float>(Motion.FOCUS_MS, easing = Motion.standard)
    val scale = animateFloatAsState(if (focused) Motion.KILL_FOCUS_SCALE else 1f, std, label = "pscale")
    val f = animateFloatAsState(if (focused) 1f else 0f, std, label = "pfocus")
    val glyph by animateColorAsState(if (focused) accent.onDark else Tokens.textDim, tween(Motion.FOCUS_MS), label = "pglyph")
    val shape = RoundedCornerShape(GEAR_H / 2)
    Box(Modifier.size(width, GEAR_H), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .requiredSize(width * 1.4f, GEAR_H * 2.4f)
                .drawBehind {
                    if (f.value > 0.01f) drawOval(
                        Brush.radialGradient(listOf(accent.glow.copy(alpha = accent.glow.alpha * f.value), Color.Transparent)),
                    )
                },
        )
        Row(
            Modifier
                .size(width, GEAR_H)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.shape = shape; clip = true }
                .drawBehind {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    drawOutline(outline, lerp(Tokens.surface2, accent.base, f.value))
                    drawOutline(outline, lerp(Tokens.line, accent.base, f.value), style = Stroke(2.dp.toPx()))
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = "Settings",
                colorFilter = ColorFilter.tint(glyph),
                modifier = Modifier.size(GEAR_H * 0.5f),
            )
            Text("Settings", style = Type.small.copy(fontWeight = FontWeight.Bold), color = glyph, maxLines = 1, softWrap = false)
        }
    }
}
