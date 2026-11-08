package com.websnake.recentstv.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.websnake.recentstv.data.InstalledApp
import com.websnake.recentstv.ui.theme.LocalAccent
import com.websnake.recentstv.ui.theme.Motion
import com.websnake.recentstv.ui.theme.Tokens
import com.websnake.recentstv.ui.theme.Type

/** Surface 2b — installed apps with per-app "hide from recents" toggles (RTL, design). */
@Composable
fun ExceptionsScreen(apps: List<InstalledApp>, excluded: Set<String>, focus: Int, loading: Boolean) {
    val listState = rememberLazyListState()
    KeepInView(listState, focus)

    Column(Modifier.fillMaxSize().background(Tokens.bg).padding(start = 60.dp, end = 60.dp, top = 40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("→", style = Type.title, color = Tokens.textDim)       // back affordance (RTL: rightmost)
            Text("Exceptions", style = Type.hero, color = Tokens.text)
        }
        Text(
            "Apps switched on here are hidden from the overlay.",
            style = Type.body, color = Tokens.textDim, textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 22.dp),
        )
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
                ExcRow(app, app.packageName in excluded, i == focus)
            }
        }
    }
}

@Composable
private fun ExcRow(app: InstalledApp, excluded: Boolean, focused: Boolean) {
    val accent = LocalAccent.current
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(150, easing = Motion.standard), label = "exc")
    val stateColor by animateColorAsState(if (excluded) accent.base else Tokens.textDim, tween(150), label = "excs")
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
            if (excluded) "Hidden from recents" else "Shown",
            style = Type.small, color = stateColor, maxLines = 1, textAlign = TextAlign.Left,
            modifier = Modifier.widthIn(min = 120.dp),
        )
        Toggle(on = excluded)
    }
}
