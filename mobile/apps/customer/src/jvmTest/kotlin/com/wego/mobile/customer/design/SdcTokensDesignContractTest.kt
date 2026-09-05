package com.wego.mobile.customer.design

import androidx.compose.ui.graphics.Color
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The mobile-side half of the design-token drift check WEGO-015 added on
 * the website (see `web/apps/sharm-divers-club-site/test/DesignTokens.spec.ts`)
 * — before this test, nothing anywhere verified `SdcTokens.kt` actually
 * matches `clients/sharm-divers-club/design/tokens.json`, the file its own
 * doc comment claims it is "ported verbatim" from. No JSON library exists
 * in this module yet, so this reads the handful of needed "key": "#hex"
 * pairs with a small regex rather than pulling in kotlinx.serialization
 * for one test file.
 */
class SdcTokensDesignContractTest {
    private val tokensJson =
        File("../../../clients/sharm-divers-club/design/tokens.json").readText()

    private fun hexFor(path: String): String {
        // path like "brand.deepBright" — finds the object for "brand", then
        // the string value for "deepBright" within it. Good enough for this
        // file's shallow, known shape; not a general JSON reader.
        val (category, key) = path.split(".")
        val categoryMatch =
            Regex(""""$category"\s*:\s*\{([^}]*)\}""").find(tokensJson)
                ?: error("category \"$category\" not found in tokens.json")
        val valueMatch =
            Regex(""""$key"\s*:\s*"(#[0-9a-fA-F]{6})"""").find(categoryMatch.groupValues[1])
                ?: error("key \"$path\" not found in tokens.json")
        return valueMatch.groupValues[1].lowercase()
    }

    private fun Color.toHex(): String =
        "#%02x%02x%02x".format((red * 255).roundToInt(), (green * 255).roundToInt(), (blue * 255).roundToInt())

    @Test
    fun `SdcColor matches every color tokens_json defines for it`() {
        val expectations =
            mapOf(
                SdcColor.deep to "brand.deep",
                SdcColor.deepBright to "brand.deepBright",
                SdcColor.turquoise to "brand.turquoise",
                SdcColor.turquoiseSoft to "brand.turquoiseSoft",
                SdcColor.sand to "brand.sand",
                SdcColor.sandSoft to "brand.sandSoft",
                SdcColor.canvas to "surface.canvas",
                SdcColor.surface to "surface.default",
                SdcColor.textPrimary to "text.primary",
                SdcColor.textSecondary to "text.secondary",
                SdcColor.border to "border.default",
                SdcColor.statusInfo to "status.info",
                SdcColor.statusInfoSoft to "status.infoSoft",
                SdcColor.statusSuccess to "status.success",
                SdcColor.statusSuccessSoft to "status.successSoft",
                SdcColor.statusWarning to "status.warning",
                SdcColor.statusWarningSoft to "status.warningSoft",
                SdcColor.statusDanger to "status.danger",
                SdcColor.statusDangerSoft to "status.dangerSoft",
            )

        for ((color, path) in expectations) {
            assertEquals(hexFor(path), color.toHex(), "SdcColor value for $path drifted from tokens.json")
        }
    }
}
