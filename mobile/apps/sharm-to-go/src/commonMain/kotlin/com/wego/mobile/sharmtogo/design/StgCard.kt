package com.wego.mobile.sharmtogo.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
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

private const val ICON_VIEWPORT = 24f
private const val ICON_STROKE_WIDTH = 1.6f

/**
 * A gradient-and-icon illustration standing in for real photography — no
 * real, rights-cleared photos exist yet for any published service (none is
 * published at all as of this packet), so every card gets one of these
 * instead of a fabricated photo, matching the website's own `MockPhoto.vue`
 * exactly (same per-category tone, same gradient direction, same icon set,
 * parsed via Compose UI's own `PathParser` the same way `SdcCategoryIcon`
 * already established for Sharm Divers Club — stable, zero new dependency).
 * Swap for `AsyncImage`/`Image` per-service once `TravelServiceMedia
 * .assetReference` resolves to a real, rights-cleared asset URL — this
 * composable's shape/corners stay the same either way, only the fill changes.
 */
@Composable
@Suppress("FunctionName")
fun StgMockPhoto(
    tone: StgCategoryTone,
    modifier: Modifier = Modifier,
) {
    val path = remember(tone) { PathParser().parsePathString(tone.iconPath).toPath() }
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(StgRadius.control))
                .background(Brush.linearGradient(listOf(tone.accent, tone.gradientEnd))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(40.dp)) {
            val scaleFactor = size.width / ICON_VIEWPORT
            scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.9f),
                    style = Stroke(width = ICON_STROKE_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}
