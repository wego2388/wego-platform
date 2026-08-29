package com.wego.mobile.customer.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The one card shape/elevation every screen should use, so the app actually
 * looks like it shares a design system instead of each screen improvising
 * its own `Card(...)` call — these tokens were ported in Phase 1
 * (`SdcRadius`/`SdcColor`) but Phase 2's screens never wired them in.
 */
@Composable
@Suppress("FunctionName")
fun SdcCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = SdcColor.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(SdcRadius.card)
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 3.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, elevation = elevation, content = content)
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, elevation = elevation, content = content)
    }
}

/** A small pill badge — the Compose equivalent of the website's trust-strip chips. */
@Composable
@Suppress("FunctionName")
fun SdcBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(SdcRadius.pill),
        color = SdcColor.turquoiseSoft,
    ) {
        Text(
            text,
            color = SdcColor.deepBright,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = SdcSpace.md, vertical = SdcSpace.sm),
        )
    }
}

/**
 * A repeated mockup placeholder — real photography isn't approved for the
 * app yet (same gap the website has), so every card gets the exact same
 * gradient block instead of a fabricated or borrowed photo, easy to swap
 * one-by-one for `AsyncImage`/`Image` once real per-offering photos land.
 */
@Composable
@Suppress("FunctionName")
fun SdcMockPhoto(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(SdcRadius.control))
                .background(Brush.linearGradient(listOf(SdcColor.deepBright, SdcColor.turquoise))),
    )
}
