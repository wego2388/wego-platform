package com.wego.mobile.customer.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wego.mobile.shared.catalog.CategoryId
import com.wego.mobile.shared.catalog.iconPath

/** The SVG path strings in `CategoryIcon.kt` are all a 24x24 viewBox, stroked (not filled) — matches `categoryIcon()`'s `<svg viewBox="0 0 24 24">` usage on the website exactly. */
private const val ICON_VIEWPORT = 24f
private const val STROKE_WIDTH = 1.6f

/**
 * Parses the real SVG path data Phase 1 ported into `CategoryIcon.kt` at
 * render time via Compose UI's own `PathParser` (stable, zero new
 * dependency) — nothing invented, just finally rendering data that was
 * already there and pixel-identical to the website's icon set.
 */
@Composable
@Suppress("FunctionName")
fun SdcCategoryIcon(
    categoryId: CategoryId,
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
) {
    val path = remember(categoryId) { PathParser().parsePathString(categoryId.iconPath()).toPath() }
    Canvas(modifier = modifier.size(iconSize)) {
        val scaleFactor = size.width / ICON_VIEWPORT
        scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
            drawPath(
                path = path,
                color = tint,
                style = Stroke(width = STROKE_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
