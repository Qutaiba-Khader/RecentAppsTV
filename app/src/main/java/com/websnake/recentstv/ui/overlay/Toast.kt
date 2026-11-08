package com.websnake.recentstv.ui.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.websnake.recentstv.ui.theme.Tokens
import com.websnake.recentstv.ui.theme.Type

/** White pill near the bottom that confirms an action (~1.8 s, timed by the caller). */
@Composable
fun Toast(text: String?) {
    var last by remember { mutableStateOf("") }
    if (text != null) last = text
    val a by animateFloatAsState(if (text != null) 1f else 0f, tween(200), label = "toast")
    if (a <= 0f) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Text(
            last, style = Type.body.copy(fontWeight = FontWeight.Bold), color = Tokens.bg, maxLines = 1,
            modifier = Modifier
                .padding(bottom = 75.dp)
                .graphicsLayer { alpha = a; translationY = (1f - a) * 10.dp.toPx() }
                .clip(RoundedCornerShape(percent = 50))
                .background(Tokens.text)
                .padding(horizontal = 19.dp, vertical = 11.dp),
        )
    }
}
