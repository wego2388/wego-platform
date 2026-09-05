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

// internal, not private: WEGO-015's SdcThemeContrastTest verifies every
// text-bearing role pairing in both schemes against the real objects,
// not a reimplemented copy that could silently drift from them.
internal val SdcLightColors =
    lightColorScheme(
        primary = SdcColor.deepBright,
        onPrimary = SdcColor.surface,
        secondary = SdcColor.turquoise,
        // textPrimary, not deep: deep-on-turquoise measures 4.32:1, just
        // under the 4.5:1 WCAG AA text floor. textPrimary is the same ink
        // family, slightly darker, and clears it at 5.94:1.
        onSecondary = SdcColor.textPrimary,
        tertiary = SdcColor.sand,
        onTertiary = SdcColor.deep,
        background = SdcColor.canvas,
        onBackground = SdcColor.textPrimary,
        surface = SdcColor.surface,
        onSurface = SdcColor.textPrimary,
        surfaceVariant = SdcColor.turquoiseSoft,
        onSurfaceVariant = SdcColor.textSecondary,
        surfaceTint = SdcColor.deepBright,
        outline = SdcColor.border,
        error = SdcColor.statusDanger,
        onError = SdcColor.surface,
        errorContainer = SdcColor.statusDangerSoft,
        onErrorContainer = SdcColor.statusDanger,
        tertiaryContainer = SdcColor.sandSoft,
        onTertiaryContainer = SdcColor.deep,
    )

internal val SdcDarkColors =
    darkColorScheme(
        primary = SdcColor.turquoise,
        // Same fix as light's onSecondary above — turquoise/deep is the
        // identical 4.32:1 pairing, reused here under a different M3 role.
        onPrimary = SdcColor.textPrimary,
        secondary = SdcColor.sand,
        onSecondary = SdcColor.deep,
        tertiary = SdcColor.sandSoft,
        onTertiary = SdcColor.deep,
        background = SdcColor.deep,
        onBackground = SdcColor.canvas,
        surface = SdcColor.deep,
        onSurface = SdcColor.canvas,
        surfaceVariant = SdcColor.deepBright,
        onSurfaceVariant = SdcColor.turquoiseSoft,
        surfaceTint = SdcColor.turquoise,
        outline = SdcColor.deepBright,
        error = SdcColor.statusDangerSoft,
        onError = SdcColor.statusDanger,
        errorContainer = SdcColor.statusDanger,
        onErrorContainer = SdcColor.statusDangerSoft,
        tertiaryContainer = SdcColor.deepBright,
        // canvas, not sandSoft: sandSoft-on-deepBright measures 4.47:1,
        // just under the 4.5:1 floor. canvas is the same light-cream
        // family, slightly lighter, and clears it at 5.34:1.
        onTertiaryContainer = SdcColor.canvas,
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
