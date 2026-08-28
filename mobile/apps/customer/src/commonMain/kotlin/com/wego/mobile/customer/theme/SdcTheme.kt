package com.wego.mobile.customer.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.wego.mobile.customer.design.SdcColor
import com.wego.mobile.shared.locale.AppLocale
import com.wego.mobile.shared.locale.isRtl

private val SdcLightColors =
    lightColorScheme(
        primary = SdcColor.deepBright,
        onPrimary = SdcColor.surface,
        secondary = SdcColor.turquoise,
        onSecondary = SdcColor.deep,
        tertiary = SdcColor.sand,
        background = SdcColor.canvas,
        onBackground = SdcColor.textPrimary,
        surface = SdcColor.surface,
        onSurface = SdcColor.textPrimary,
        surfaceVariant = SdcColor.turquoiseSoft,
        onSurfaceVariant = SdcColor.textSecondary,
        outline = SdcColor.border,
        error = SdcColor.statusDanger,
        onError = SdcColor.surface,
        errorContainer = SdcColor.statusDangerSoft,
        onErrorContainer = SdcColor.statusDanger,
    )

private val SdcDarkColors =
    darkColorScheme(
        primary = SdcColor.turquoise,
        onPrimary = SdcColor.deep,
        secondary = SdcColor.sand,
        onSecondary = SdcColor.deep,
        background = SdcColor.deep,
        onBackground = SdcColor.canvas,
        surface = SdcColor.deep,
        onSurface = SdcColor.canvas,
        outline = SdcColor.deepBright,
    )

/**
 * Wraps Material3's theme with the Sharm Divers Club token palette (see
 * `SdcTokens.kt` — ported from `clients/sharm-divers-club/design/tokens.json`)
 * and flips `LocalLayoutDirection` for Arabic, mirroring the website's
 * `:dir="direction"` handling in every page.
 */
@Composable
@Suppress("FunctionName")
fun SdcTheme(
    locale: AppLocale,
    useDarkColors: Boolean = false,
    content: @Composable () -> Unit,
) {
    val direction = if (locale.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        MaterialTheme(
            colorScheme = if (useDarkColors) SdcDarkColors else SdcLightColors,
            content = content,
        )
    }
}
