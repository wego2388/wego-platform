package com.wego.mobile.customer.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WEGO-015 Phase 4: nothing verified these Material3 color-role pairings
 * against real WCAG contrast math before this test — the gap that let
 * turquoise/deep (4.32:1) and deepBright/sandSoft (4.47:1), both just
 * under the 4.5:1 text floor, ship unnoticed in [SdcTheme]'s color
 * schemes. Mirrors the same relative-luminance formula this platform's
 * web packages already use (`web/packages/design-tokens/test/design-tokens.spec.ts`),
 * reimplemented here since this module has no shared color-math utility.
 */
class SdcThemeContrastTest {
    private fun channel(component: Float): Double = if (component <= 0.03928) component / 12.92 else ((component + 0.055) / 1.055).pow(2.4)

    private fun relativeLuminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrastRatio(
        a: Color,
        b: Color,
    ): Double {
        val (lighter, darker) =
            relativeLuminance(a).let { la ->
                relativeLuminance(b).let { lb -> max(la, lb) to min(la, lb) }
            }
        return (lighter + 0.05) / (darker + 0.05)
    }

    // Every text/icon-bearing "on<Role>" pairing Material3 actually renders
    // as foreground-on-background — the same 9 roles checked in both schemes.
    private fun textBearingPairs(scheme: ColorScheme): List<Triple<String, Color, Color>> =
        listOf(
            Triple("primary/onPrimary", scheme.primary, scheme.onPrimary),
            Triple("secondary/onSecondary", scheme.secondary, scheme.onSecondary),
            Triple("tertiary/onTertiary", scheme.tertiary, scheme.onTertiary),
            Triple("background/onBackground", scheme.background, scheme.onBackground),
            Triple("surface/onSurface", scheme.surface, scheme.onSurface),
            Triple("surfaceVariant/onSurfaceVariant", scheme.surfaceVariant, scheme.onSurfaceVariant),
            Triple("error/onError", scheme.error, scheme.onError),
            Triple("errorContainer/onErrorContainer", scheme.errorContainer, scheme.onErrorContainer),
            Triple("tertiaryContainer/onTertiaryContainer", scheme.tertiaryContainer, scheme.onTertiaryContainer),
        )

    @Test
    fun `every light-scheme text-bearing role pairing clears WCAG AA 4_5 to 1`() {
        for ((label, background, foreground) in textBearingPairs(SdcLightColors)) {
            val ratio = contrastRatio(background, foreground)
            assertTrue(ratio >= 4.5, "light $label measured $ratio, needs >= 4.5")
        }
    }

    @Test
    fun `every dark-scheme text-bearing role pairing clears WCAG AA 4_5 to 1`() {
        for ((label, background, foreground) in textBearingPairs(SdcDarkColors)) {
            val ratio = contrastRatio(background, foreground)
            assertTrue(ratio >= 4.5, "dark $label measured $ratio, needs >= 4.5")
        }
    }
}
