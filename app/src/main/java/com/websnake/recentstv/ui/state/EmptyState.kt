package com.websnake.recentstv.ui.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.websnake.recentstv.ui.theme.Tokens
import com.websnake.recentstv.ui.theme.Type

/** Surface 4 — no recent tasks. Kill-All is hidden in this state. */
@Composable
fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // header as on the overlay (design 10): centred title
        Text(
            "Recent apps", style = Type.title, color = Tokens.text,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 36.dp),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // faint dashed-square-in-circle glyph
            Box(
                Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .border(1.5.dp, Tokens.text.copy(alpha = 0.14f), RoundedCornerShape(percent = 50)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(23.dp)
                        .border(1.5.dp, Tokens.text.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                )
            }
            Text("No recent apps", style = Type.title, color = Tokens.text)
            Text(
                "Apps you open will appear here.",
                style = Type.body, color = Tokens.textDim, textAlign = TextAlign.Center,
            )
            Text(
                "Press OK for settings",
                style = Type.small, color = Tokens.textFaint, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
