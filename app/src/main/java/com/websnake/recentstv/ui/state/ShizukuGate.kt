package com.websnake.recentstv.ui.state

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.websnake.recentstv.ui.theme.LocalAccent
import com.websnake.recentstv.ui.theme.Tokens
import com.websnake.recentstv.ui.theme.Type

/**
 * Surface 3 — shown whenever Shizuku's binder isn't alive or permission is
 * missing. Two focusable buttons; [focus] 0 = primary "Open & grant", 1 = Retry.
 */
@Composable
fun ShizukuGate(focus: Int, running: Boolean = false) {
    val accent = LocalAccent.current
    Box(Modifier.fillMaxSize().background(Tokens.bg), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(17.dp),
            modifier = Modifier.widthIn(max = 590.dp),
        ) {
            // shield-ish icon
            Box(
                Modifier
                    .size(75.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Tokens.surface)
                    .border(1.dp, Tokens.line, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(com.websnake.recentstv.R.drawable.ic_shield_check),
                    contentDescription = null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(accent.base),
                    modifier = Modifier.size(40.dp),
                )
            }

            // permission-required pill
            Box(
                Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(accent.tint)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) { Text("PERMISSION REQUIRED", style = Type.badge, color = accent.base) }

            // 1.7: Shizuku can be running with the grant missing (after a reinstall / data clear)
            Text(if (running) "Allow Recent Apps in Shizuku" else "Shizuku isn’t running", style = Type.hero, color = Tokens.text, textAlign = TextAlign.Center)
            Text(
                "Recent Apps reads your task list through Shizuku. Start the Shizuku service and grant access, then return here to see your recent apps.",
                style = Type.body, color = Tokens.textDim, textAlign = TextAlign.Center,
            )

            Row(
                Modifier.padding(top = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // RTL (design): the primary button sits on the right, Retry to its left
                GateButton("Retry", focused = focus == 1, primary = false)
                GateButton("Open Shizuku & grant", focused = focus == 0, primary = true)
            }
        }
    }
}

@Composable
private fun GateButton(label: String, focused: Boolean, primary: Boolean) {
    val accent = LocalAccent.current
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(160, easing = com.websnake.recentstv.ui.theme.Motion.standard), label = "btn")
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused) Modifier.drawBehind {
                drawRoundRect(accent.tint, topLeft = androidx.compose.ui.geometry.Offset(-2.5.dp.toPx(), -2.5.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width + 5.dp.toPx(), size.height + 5.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(11.5.dp.toPx()))
            } else Modifier)
            .clip(RoundedCornerShape(9.dp))
            .background(if (focused) accent.base else Tokens.surface2)
            .border(1.dp, if (focused) accent.base else Tokens.line, RoundedCornerShape(9.dp))
            .padding(horizontal = 26.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Type.row, color = if (focused) accent.onDark else Tokens.text)
    }
}
