package com.websnake.recentstv.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.websnake.recentstv.logic.Accent
import com.websnake.recentstv.logic.Backdrop
import com.websnake.recentstv.logic.CardShape
import com.websnake.recentstv.logic.PinPosition
import com.websnake.recentstv.logic.PinSize
import com.websnake.recentstv.logic.SetRow
import com.websnake.recentstv.logic.Settings
import com.websnake.recentstv.logic.SettingsCodec
import com.websnake.recentstv.logic.SettingsNav
import com.websnake.recentstv.logic.section
import com.websnake.recentstv.ui.theme.BackdropSwatches
import com.websnake.recentstv.ui.theme.LocalAccent
import com.websnake.recentstv.ui.theme.Motion
import com.websnake.recentstv.ui.theme.Tokens
import com.websnake.recentstv.ui.theme.Type
import com.websnake.recentstv.ui.theme.color
import com.websnake.recentstv.ui.theme.shape

/** Surface 2 — settings in four sections (RTL, design). [focus] = index into SettingsNav.rows. */
@Composable
fun SettingsScreen(settings: Settings, focus: Int, blurSupported: Boolean) {
    val rows = SettingsNav.rows(settings)
    // lazy index of each row: a section title takes a slot before its first row
    val lazyIndex = buildList {
        var i = 0
        rows.forEach { r -> if (r.section() != null) i++; add(i); i++ }
    }
    val listState = rememberLazyListState()
    KeepInView(listState, lazyIndex.getOrElse(focus) { 0 })

    Column(Modifier.fillMaxSize().background(Tokens.bg).padding(start = 60.dp, end = 60.dp, top = 40.dp)) {
        Text("Settings", style = Type.hero, color = Tokens.text)
        Text("Recent Apps overlay", style = Type.body, color = Tokens.textDim, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(11.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp),
            modifier = Modifier.fillMaxSize().edgeFade(),
        ) {
            rows.forEachIndexed { i, row ->
                row.section()?.let { t -> item(key = "s-$t") { Section(t) } }
                item(key = row.name) { RowFor(row, settings, i == focus, blurSupported) }
            }
        }
    }
}

@Composable
private fun Section(t: String) = Text(
    t.uppercase(), style = Type.badge, color = Tokens.textFaint, textAlign = TextAlign.Right,
    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp, end = 4.dp),
)

@Composable
private fun RowFor(row: SetRow, s: Settings, focused: Boolean, blurSupported: Boolean) = when (row) {
    SetRow.KILL_ALL -> SettingRow("Show Kill All", "A clear-all tile at the start of the row.", focused) { Toggle(s.showKillAll) }
    SetRow.FORCE_STOP -> SettingRow("Force stop on kill", "Also force-stop the app so it can't relaunch.", focused) { Toggle(s.forceStopOnKill) }
    SetRow.SIZE -> SettingRow("Size of task", "Scales every card in the overlay.", focused) {
        Segmented(listOf("Small", "Medium", "Large"), s.size.ordinal)
    }
    SetRow.SHAPE -> SettingRow("Shape of task", "Corner style of every card.", focused) {
        ShapePreview(s.shape)        // RTL: first = rightmost, beside the title
        Segmented(listOf("Square", "Rounded", "Circle"), s.shape.ordinal)
    }
    SetRow.ACCENT -> SettingRow("Accent color", "Focus rings and the Kill All tile.", focused) {
        SwatchRow(Accent.entries.map { it.color }, s.accent.ordinal)
    }
    SetRow.PINNED_LIST -> SettingRow("Pinned apps", SettingsCodec.pinnedSummary(s.pinned.size), focused) { Chevron() }
    SetRow.PIN_POSITION -> SettingRow("Pinned position", "Which end of the row pinned apps stay on.", focused) {
        PositionPreview(s.pinPosition)
        // read literally: DPAD_RIGHT selects "Right"
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Segmented(listOf("Left", "Right"), s.pinPosition.ordinal)
        }
    }
    SetRow.PIN_SIZE -> SettingRow("Pinned icon size", "Pinned apps can be smaller than tasks.", focused) {
        SizePreview(s.pinSize)
        Segmented(listOf("Small", "Same as tasks"), s.pinSize.ordinal)
    }
    SetRow.BACKGROUND -> SettingRow(
        "Background",
        if (s.background == Backdrop.BLUR && !blurSupported) "Blur isn't available on this device — Dim is used." else "What sits behind the row.",
        focused,
    ) { Segmented(listOf("Dim", "Blur", "Color", "Image"), s.background.ordinal) }
    SetRow.BG_DIM -> SettingRow("Dim level", "How much the running app is hidden.", focused) { DimBar(s.bgDim) }
    SetRow.BG_COLOR -> SettingRow("Background color", BackdropSwatches[s.bgColor].first, focused) { Swatches(s.bgColor) }
    SetRow.BG_IMAGE -> SettingRow("Background image", s.bgImage?.let { "$it · from device storage" } ?: "Choose an image from device storage", focused) { Chevron() }
    SetRow.EXCEPTIONS -> SettingRow("Exceptions", SettingsCodec.exceptionsSummary(s.exceptions.size), focused) { Chevron() }
}

@Composable
private fun Chevron() = Text("‹", style = Type.title.copy(fontWeight = FontWeight.Bold), color = Tokens.textDim)

/** Accent swatches; the selected one grows and gets a white ring. */
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

/** Background colour swatches, read left → right. Selected = white ring with a dark gap. */
@Composable
private fun Swatches(selected: Int) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            BackdropSwatches.forEachIndexed { i, (_, col) ->
                Box(
                    Modifier
                        .size(28.dp)
                        .then(if (i == selected) Modifier.border(2.dp, Tokens.text, RoundedCornerShape(percent = 50)).padding(4.dp) else Modifier)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(col)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(percent = 50)),
                )
            }
        }
    }
}

/** Mini row diagram: pins on the chosen side, tasks, Kill All (accent) at the right. Always LTR. */
@Composable
private fun PositionPreview(pos: PinPosition) {
    val accent = LocalAccent.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            Modifier.clip(RoundedCornerShape(6.dp)).background(Tokens.segBg).border(1.dp, Tokens.line, RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            val pins = @Composable { repeat(3) { Box(Modifier.size(7.5.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFC9CED6))) } }
            val tasks = @Composable { repeat(3) { Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF3A3F48))) } }
            if (pos == PinPosition.LEFT) { pins(); Spacer(Modifier.width(4.dp)); tasks() } else { tasks(); Spacer(Modifier.width(4.dp)); pins() }
            Box(Modifier.size(13.dp).clip(RoundedCornerShape(3.dp)).background(accent.tint).border(1.dp, accent.base, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun SizePreview(size: PinSize) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            repeat(2) { Box(Modifier.size(15.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF3A3F48))) }
            repeat(2) { Box(Modifier.size(15.dp * size.factor).clip(RoundedCornerShape(4.dp)).background(Color(0xFFC9CED6))) }
        }
    }
}

/** 0–100 % dim level as a filled bar (fills from the right in RTL) with the number. */
@Composable
private fun DimBar(pct: Int) {
    val accent = LocalAccent.current
    val fill by animateFloatAsState(pct / 100f, tween(160, easing = Motion.standard), label = "dim")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(220.dp).height(14.dp).clip(RoundedCornerShape(percent = 50)).background(Tokens.track)) {
            Box(Modifier.fillMaxWidth(fill).fillMaxHeight().clip(RoundedCornerShape(percent = 50)).background(accent.base))
        }
        Text("$pct%", style = Type.small, color = Tokens.text, modifier = Modifier.width(48.dp))
    }
}

/** Live shape preview next to "Shape of task". */
@Composable
private fun ShapePreview(shape: CardShape) {
    Box(Modifier.size(38.dp).clip(shape.shape()).background(Tokens.card).border(1.dp, Tokens.line, shape.shape()))
}
