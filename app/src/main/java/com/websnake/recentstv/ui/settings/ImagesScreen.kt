package com.websnake.recentstv.ui.settings

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.websnake.recentstv.data.ImageSources
import com.websnake.recentstv.data.Picker
import com.websnake.recentstv.logic.Geometry
import com.websnake.recentstv.logic.SettingsNav
import com.websnake.recentstv.ui.theme.LocalAccent
import com.websnake.recentstv.ui.theme.Motion
import com.websnake.recentstv.ui.theme.Tokens
import com.websnake.recentstv.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Settings → Background → Image (as Projectivy Launcher does it on TV): the apps that can
 * pick a picture (Gallery, file managers…) in a bar on top, the device's own pictures in a
 * D-pad grid below. [focus] 0 = the bar ([act] = its button), 1.. = pictures.
 */
@Composable
fun ImagesScreen(
    pickers: List<Picker>,
    images: List<Uri>,
    focus: Int,
    act: Int,
    loading: Boolean,
    hasAccess: Boolean,
) {
    val f = focus.coerceIn(0, images.size)
    val grid = rememberLazyGridState()
    KeepInViewGrid(grid, (f - 1).coerceAtLeast(0), SettingsNav.IMAGE_COLS)
    val thumbs = remember { ConcurrentHashMap<Uri, ImageBitmap>() }

    Column(Modifier.fillMaxSize().background(Tokens.bg).padding(start = 60.dp, end = 60.dp, top = 40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("→", style = Type.title, color = Tokens.textDim)
            Text("Background image", style = Type.hero, color = Tokens.text)
        }
        Text(
            "Pick a picture below, or open one with an app.", style = Type.body, color = Tokens.textDim, textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
        )
        // the picker apps — reached with UP from the first row of pictures
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (pickers.isEmpty() && !loading) Text("No picker apps on this device", style = Type.small, color = Tokens.textFaint)
            pickers.forEachIndexed { i, p -> PickerButton(p, f == 0 && i == act) }
        }
        Text(
            "On this device", style = Type.badge, color = Tokens.textFaint, textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp, end = 4.dp),
        )
        when {
            loading -> Text("Loading pictures…", style = Type.body, color = Tokens.textFaint, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
            !hasAccess -> Text("No access to the pictures on this device — use an app above.", style = Type.body, color = Tokens.textDim, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
            images.isEmpty() -> Text("No pictures on this device — use an app above.", style = Type.body, color = Tokens.textDim, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(SettingsNav.IMAGE_COLS),
                state = grid,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 40.dp, start = 4.dp, end = 4.dp),
                modifier = Modifier.fillMaxSize().edgeFade(),
            ) {
                itemsIndexed(images, key = { _, u -> u.toString() }) { i, uri -> Thumb(uri, i + 1 == f, thumbs) }
            }
        }
    }
}

@Composable
private fun Thumb(uri: Uri, focused: Boolean, cache: ConcurrentHashMap<Uri, ImageBitmap>) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(Motion.FOCUS_MS, easing = Motion.standard), label = "thumb")
    // decoded at the drawn size, off the main thread, once per picture
    val img by produceState(cache[uri], uri) {
        if (value == null) value = withContext(Dispatchers.IO) {
            ImageSources.thumbnail(context, uri, 384, 216)?.also { cache[uri] = it }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .graphicsLayer { val s = 1f + 0.06f * f; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(10.dp))
            .background(Tokens.card)
            .drawWithContent {
                drawContent()
                if (f > 0.01f) drawRoundRect(accent.base.copy(alpha = f), cornerRadius = CornerRadius(10.dp.toPx()), style = Stroke(3.dp.toPx()))
            },
    ) {
        img?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
private fun PickerButton(p: Picker, focused: Boolean) {
    val accent = LocalAccent.current
    val bg by animateColorAsState(if (focused) accent.base else Tokens.surface2, tween(140), label = "pb")
    val fg by animateColorAsState(if (focused) accent.onDark else Tokens.text, tween(140), label = "pbf")
    Row(
        Modifier
            .focusHalo(if (focused) 1f else 0f, 9.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(1.dp, if (focused) accent.base else Tokens.line, RoundedCornerShape(9.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        p.icon?.let { Image(it, null, Modifier.size(22.dp)) }
        Text(p.label, style = Type.label, color = fg, maxLines = 1)
    }
}

/** Keep grid item [index] in view, scrolling only as far as needed. */
@Composable
private fun KeepInViewGrid(state: LazyGridState, index: Int, cols: Int) {
    val margin = with(LocalDensity.current) { 16.dp.toPx() }
    LaunchedEffect(index) {
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        if (item == null) {
            if (index >= 0 && info.totalItemsCount > 0) state.animateScrollToItem((index / cols) * cols)
            return@LaunchedEffect
        }
        val d = Geometry.revealDelta(
            item.offset.y.toFloat(), (item.offset.y + item.size.height).toFloat(),
            info.viewportStartOffset.toFloat(), info.viewportEndOffset.toFloat() - info.afterContentPadding, margin,
        )
        if (d != 0f) state.animateScrollBy(d, tween(280, easing = Motion.standard))
    }
}
