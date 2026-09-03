package com.wego.mobile.sharmtogo.design

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

/** The one card shape/elevation every screen should use — mirrors `SdcCard`'s exact pattern. */
@Composable
@Suppress("FunctionName")
fun StgCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(StgRadius.card)
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
fun StgBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(StgRadius.pill),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = StgSpace.md, vertical = StgSpace.sm),
        )
    }
}

/**
 * A repeated mockup placeholder — no real, rights-cleared photos exist yet
 * for any published service (none is published at all as of this packet),
 * so every card gets the same gradient block instead of a fabricated photo,
 * matching the website's own `SdcMockPhoto`-equivalent discipline. Swap for
 * `AsyncImage`/`Image` per-service once `TravelServiceMedia.assetReference`
 * resolves to a real, rights-cleared asset URL.
 */
@Composable
@Suppress("FunctionName")
fun StgMockPhoto(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(StgRadius.control))
                .background(Brush.linearGradient(listOf(StgColor.seaBright, StgColor.sun))),
    )
}
