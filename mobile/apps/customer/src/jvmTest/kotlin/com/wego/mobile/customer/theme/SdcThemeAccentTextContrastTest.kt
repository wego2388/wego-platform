package com.wego.mobile.customer.theme

import androidx.compose.ui.graphics.Color
import com.wego.mobile.customer.design.SdcColor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies the accent-text fix described in [SdcTheme] (`LocalSdcAccentText`)
 * against every background it's actually used on across the app's screens
 * (`background`, `surface`, `surfaceVariant`) in both themes — the exact
 * check that would have caught the original bug: `MaterialTheme.colorScheme.primary`
 * used as ad hoc accent text measured as low as 2.03:1 in dark mode despite
 * passing fine in light mode.
 */
class SdcThemeAccentTextContrastTest {
    private fun channel(component: Float): Double =
        if (component <= 0.03928) component / 12.92 else ((component + 0.055) / 1.055).pow(2.4)

    private fun relativeLuminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrastRatio(a: Color, b: Color): Double {
        val (lighter, darker) = relativeLuminance(a).let { la ->
            relativeLuminance(b).let { lb -> max(la, lb) to min(la, lb) }
        }
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun `light accent text clears WCAG AA against every background it is used on`() {
        val accent = SdcColor.deepBright
        for ((label, background) in mapOf("background" to SdcColor.canvas, "surface" to SdcColor.surface, "surfaceVariant" to SdcColor.turquoiseSoft)) {
            val ratio = contrastRatio(accent, background)
            assertTrue(ratio >= 4.5, "light accentText on $label measured $ratio, needs >= 4.5")
        }
    }

    @Test
    fun `dark accent text clears WCAG AA against every background it is used on`() {
        val accent = SdcColor.turquoiseSoft
        for ((label, background) in mapOf("background" to SdcColor.deep, "surface" to SdcColor.deep, "surfaceVariant" to SdcColor.deepBright)) {
            val ratio = contrastRatio(accent, background)
            assertTrue(ratio >= 4.5, "dark accentText on $label measured $ratio, needs >= 4.5")
        }
    }
}
