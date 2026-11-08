package com.websnake.recentstv.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.websnake.recentstv.logic.Accent
import com.websnake.recentstv.logic.CardShape
import com.websnake.recentstv.logic.SetRow
import com.websnake.recentstv.logic.Settings
import com.websnake.recentstv.logic.SettingsCodec
import com.websnake.recentstv.logic.Tint
import com.websnake.recentstv.ui.theme.LocalAccent
import com.websnake.recentstv.ui.theme.Motion
import com.websnake.recentstv.ui.theme.Tokens
import com.websnake.recentstv.ui.theme.Type
import com.websnake.recentstv.ui.theme.color
import com.websnake.recentstv.ui.theme.shape

/** Surface 2 — settings list (RTL, design). [focus] = SetRow ordinal. */
@Composable
fun SettingsScreen(settings: Settings, focus: Int) {
    val listState = rememberLazyListState()
    KeepInView(listState, focus)

    Column(Modifier.fillMaxSize().background(Tokens.bg).padding(start = 60.dp, end = 60.dp, top = 40.dp)) {
        Text("Settings", style = Type.hero, color = Tokens.text)
        Text("Recent Apps overlay", style = Type.body, color = Tokens.textDim, modifier = Modifier.padding(top = 4.dp, bottom = 22.dp))

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(11.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize().edgeFade(),
        ) {
            item {
                SettingRow("Show Kill All", "A clear-all tile at the start of the row.", focus == SetRow.KILL_ALL.ordinal) {
                    Toggle(on = settings.showKillAll)
                }
            }
            item {
                SettingRow("Force stop on kill", "Also force-stop the app so it can't relaunch.", focus == SetRow.FORCE_STOP.ordinal) {
                    Toggle(on = settings.forceStopOnKill)
                }
            }
            item {
                SettingRow("Size of task", "Scales every card in the overlay.", focus == SetRow.SIZE.ordinal) {
                    Segmented(listOf("Small", "Medium", "Large"), settings.size.ordinal)
                }
            }
            item {
                SettingRow("Shape of task", "Corner style of every card.", focus == SetRow.SHAPE.ordinal) {
                    ShapePreview(settings.shape)        // RTL: first = rightmost, beside the title
                    Segmented(listOf("Square", "Rounded", "Circle"), settings.shape.ordinal)
                }
            }
            item {
                SettingRow("Accent color", "Focus rings and the Kill All tile.", focus == SetRow.ACCENT.ordinal) {
                    SwatchRow(Accent.entries.map { it.color }, settings.accent.ordinal)
                }
            }
            item {
                SettingRow("Background dim", "How much the running app is hidden.", focus == SetRow.BG_DIM.ordinal) {
                    DimBar(settings.bgDim)
                }
            }
            item {
                SettingRow("Background color", "Tint of the overlay backdrop.", focus == SetRow.BG_TINT.ordinal) {
                    SwatchRow(Tint.entries.map { it.color }, settings.bgTint.ordinal)
                }
            }
            item {
                SettingRow("Exceptions", SettingsCodec.exceptionsSummary(settings.exceptions.size), focus == SetRow.EXCEPTIONS.ordinal) {
                    Text("‹", style = Type.title, color = Tokens.textDim)
                }
            }
        }
    }
}

/** Colour swatches; the selected one grows and gets a white ring. */
@Composable
private fun SwatchRow(colors: List<Color>, selected: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEachIndexed { i, c ->
            val sel = i == selected
            val s by animateFloatAsState(if (sel) 38f / 30f else 1f, tween(180, easing = Motion.standard), label = "sw")
            Box(
                Modifier
                    .size(30.dp)
                    .graphicsLayer { scaleX = s; scaleY = s }
                    .clip(RoundedCornerShape(percent = 50))
                    .background(c)
                    .border(if (sel) 2.dp else 1.dp, if (sel) Color.White else Tokens.line, RoundedCornerShape(percent = 50)),
            )
        }
    }
}

/** 0–100 % dim level as a filled bar (fills from the right in RTL) with the number. */
@Composable
private fun DimBar(pct: Int) {
    val accent = LocalAccent.current
    val fill by animateFloatAsState(pct / 100f, tween(160, easing = Motion.standard), label = "dim")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(260.dp).height(14.dp).clip(RoundedCornerShape(percent = 50)).background(Tokens.track)) {
            Box(Modifier.fillMaxWidth(fill).fillMaxHeight().clip(RoundedCornerShape(percent = 50)).background(accent.base))
        }
        Text("$pct%", style = Type.small, color = Tokens.text, modifier = Modifier.width(48.dp))
    }
}

/** Live shape preview next to "Shape of task". */
@Composable
private fun ShapePreview(shape: CardShape) {
    Box(
        Modifier.size(38.dp).clip(shape.shape()).background(Tokens.card).border(1.dp, Tokens.line, shape.shape())
    )
}

