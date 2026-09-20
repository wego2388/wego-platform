package com.wego.mobile.sharmtogo.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.wego.mobile.shared.locale.AppLocale
import com.wego.mobile.shared.locale.isRtl
import com.wego.mobile.sharmtogo.design.StgColor

private val StgLightColors =
    lightColorScheme(
        primary = StgColor.sea,
        onPrimary = StgColor.surface,
        secondary = StgColor.seaBright,
        onSecondary = StgColor.surface,
        tertiary = StgColor.sun,
        onTertiary = StgColor.textPrimary,
        background = StgColor.canvas,
        onBackground = StgColor.textPrimary,
        surface = StgColor.surface,
        onSurface = StgColor.textPrimary,
        surfaceVariant = StgColor.lagoon,
        onSurfaceVariant = StgColor.textSecondary,
        surfaceTint = StgColor.sea,
        outline = StgColor.border,
        error = StgColor.statusDanger,
        onError = StgColor.surface,
        errorContainer = StgColor.statusDangerSoft,
        onErrorContainer = StgColor.statusDanger,
        tertiaryContainer = StgColor.sand,
        onTertiaryContainer = StgColor.textPrimary,
    )

private val StgDarkColors =
    darkColorScheme(
        primary = StgColor.seaBright,
        onPrimary = StgColor.textPrimary,
        secondary = StgColor.sun,
        onSecondary = StgColor.textPrimary,
        tertiary = StgColor.sand,
        onTertiary = StgColor.textPrimary,
        background = StgColor.sea,
        onBackground = StgColor.canvas,
        surface = StgColor.sea,
        onSurface = StgColor.canvas,
        surfaceVariant = StgColor.seaBright,
        onSurfaceVariant = StgColor.lagoon,
        surfaceTint = StgColor.seaBright,
        outline = StgColor.seaBright,
        error = StgColor.statusDangerSoft,
        onError = StgColor.statusDanger,
        errorContainer = StgColor.statusDanger,
        onErrorContainer = StgColor.statusDangerSoft,
        tertiaryContainer = StgColor.seaBright,
        onTertiaryContainer = StgColor.sand,
    )

/** Wraps Material3's theme with the Sharm To Go token palette and flips `LocalLayoutDirection` for Arabic. */
@Composable
@Suppress("FunctionName")
fun StgTheme(
    locale: AppLocale,
    useDarkColors: Boolean = false,
    content: @Composable () -> Unit,
) {
    val direction = if (locale.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        MaterialTheme(
            colorScheme = if (useDarkColors) StgDarkColors else StgLightColors,
            content = content,
        )
    }
}
