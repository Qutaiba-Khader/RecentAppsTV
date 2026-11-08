package com.websnake.recentstv.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.websnake.recentstv.R
import com.websnake.recentstv.data.InstalledApp
import com.websnake.recentstv.logic.BulkAction
import com.websnake.recentstv.logic.SetScreen
import com.websnake.recentstv.logic.SettingsNav
import com.websnake.recentstv.ui.theme.LocalAccent
import com.websnake.recentstv.ui.theme.Motion
import com.websnake.recentstv.ui.theme.Tokens
import com.websnake.recentstv.ui.theme.Type

/**
 * Exceptions (screens 16–18: toggle on = hidden, Hide all / Show all) and Pinned apps
 * (screen 15: toggle on = pinned, Unpin all). [focus] 0 = the bulk bar ([act] = its
 * button), 1.. = app rows.
 */
@Composable
fun AppListScreen(
    screen: SetScreen,
    apps: List<InstalledApp>,
    isOn: (String) -> Boolean,
    onCount: Int,
    focus: Int,
    act: Int,
    loading: Boolean,
) {
    val pins = screen == SetScreen.PINNED
    val title = if (pins) "Pinned apps" else "Exceptions"
    val subtitle = if (pins) "Pinned apps always appear in the row, running or not." else "Apps switched on here are hidden from the overlay."
    val onLabel = if (pins) "Pinned" else "Hidden from recents"
    val offLabel = if (pins) "Not pinned" else "Shown"
    val listState = rememberLazyListState()
    KeepInView(listState, (focus - 1).coerceAtLeast(0))

    Column(Modifier.fillMaxSize().background(Tokens.bg).padding(start = 60.dp, end = 60.dp, top = 40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("→", style = Type.title, color = Tokens.textDim)       // back affordance (RTL: rightmost)
            Text(title, style = Type.hero, color = Tokens.text)
        }
        Text(
            subtitle, style = Type.body, color = Tokens.textDim, textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
        )
        // bulk bar — reached with UP from the first row; LEFT/RIGHT move between buttons
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsNav.bulkActions(screen).forEachIndexed { i, a -> BulkButton(a, focus == 0 && i == act) }
            Spacer(Modifier.weight(1f))
            Text(
                buildAnnotatedString {
                    withStyle(androidx.compose.ui.text.SpanStyle(color = Tokens.text)) { append("$onCount") }
                    append(if (pins) " pinned" else " of ${apps.size} hidden")
                },
                style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = Tokens.textDim, maxLines = 1,
            )
        }
        Spacer(Modifier.size(14.dp))
        if (loading && apps.isEmpty()) {
            Text("Loading apps…", style = Type.body, color = Tokens.textFaint)
            return@Column
        }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 45.dp),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {           // bottom fade affordance
                    drawContent()
                    drawRect(Brush.verticalGradient(0.85f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                },
        ) {
            itemsIndexed(apps, key = { _, a -> a.packageName }) { i, app ->
                AppRow(app, isOn(app.packageName), i + 1 == focus, onLabel, offLabel)
            }
        }
    }
}

@Composable
private fun BulkButton(a: BulkAction, focused: Boolean) {
    val accent = LocalAccent.current
    val bg by animateColorAsState(if (focused) accent.base else Tokens.surface2, tween(140), label = "bb")
    val fg by animateColorAsState(if (focused) accent.onDark else Tokens.text, tween(140), label = "bbf")
    val (label, icon) = when (a) {
        BulkAction.HIDE_ALL -> "Hide all" to R.drawable.ic_hide
        BulkAction.SHOW_ALL -> "Show all" to R.drawable.ic_show
        BulkAction.UNPIN_ALL -> "Unpin all" to R.drawable.ic_unpin
    }
    Row(
        Modifier
            .focusHalo(if (focused) 1f else 0f, 9.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(1.dp, if (focused) accent.base else Tokens.line, RoundedCornerShape(9.dp))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Image(painterResource(icon), null, colorFilter = ColorFilter.tint(fg), modifier = Modifier.size(15.dp))
        Text(label, style = Type.label, color = fg, maxLines = 1)
    }
}

@Composable
private fun AppRow(app: InstalledApp, on: Boolean, focused: Boolean, onLabel: String, offLabel: String) {
    val accent = LocalAccent.current
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(150, easing = Motion.standard), label = "exc")
    val stateColor by animateColorAsState(if (on) accent.base else Tokens.textDim, tween(150), label = "excs")
    Row(
        Modifier
            .fillMaxWidth()
            .focusHalo(f, 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .drawBehind {
                drawRect(androidx.compose.ui.graphics.lerp(Tokens.surface, Tokens.surface2, f))
                if (f > 0.01f) drawRoundRect(accent.base.copy(alpha = f), cornerRadius = CornerRadius(10.dp.toPx()), style = Stroke(2.dp.toPx()))
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(17.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(Tokens.card), contentAlignment = Alignment.Center) {
            val icon = app.icon
            if (icon != null) Image(icon, app.label, Modifier.size(32.dp), contentScale = ContentScale.Fit)
            else Text(app.label.take(1).uppercase(), style = Type.row, color = Tokens.text)
        }
        Text(app.label, style = Type.row, color = Tokens.text, maxLines = 1, textAlign = TextAlign.Right, modifier = Modifier.weight(1f))
        Text(app.tag, style = Type.small, color = Tokens.textDim, maxLines = 1)
        Text(
            if (on) onLabel else offLabel,
            style = Type.small, color = stateColor, maxLines = 1, textAlign = TextAlign.Left,
            modifier = Modifier.widthIn(min = 120.dp),
        )
        Toggle(on = on)
    }
}
