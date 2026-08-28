package com.wego.mobile.customer.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Ported verbatim from `clients/sharm-divers-club/design/tokens.json` — one
 * design source, rendered by both the website (Tailwind) and this app
 * (Compose). Deliberately client-specific, so it lives in `apps/customer`
 * (Sharm Divers Club branded) rather than `mobile/shared` (product-neutral,
 * shared with the unbranded Wego Ops staff app).
 */
object SdcColor {
    val deep = Color(0xFF0A3A4A)
    val deepBright = Color(0xFF12707F)
    val turquoise = Color(0xFF1FA9B8)
    val turquoiseSoft = Color(0xFFD9F1F1)
    val sand = Color(0xFFC9975A)
    val sandSoft = Color(0xFFF1E1C3)

    val canvas = Color(0xFFFAF6EE)
    val surface = Color(0xFFFFFFFF)

    val textPrimary = Color(0xFF0B2027)
    val textSecondary = Color(0xFF3C555C)

    val border = Color(0xFFDDE4E1)

    val statusInfo = Color(0xFF155E75)
    val statusInfoSoft = Color(0xFFCFFAFE)
    val statusSuccess = Color(0xFF0F7A3D)
    val statusSuccessSoft = Color(0xFFDCF5E4)
    val statusWarning = Color(0xFF8A5809)
    val statusWarningSoft = Color(0xFFFFF1D8)
    val statusDanger = Color(0xFFB3261E)
    val statusDangerSoft = Color(0xFFFBDCD9)
}

object SdcSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

object SdcRadius {
    val control = 10.dp
    val card = 18.dp
    val panel = 28.dp
    val pill = 999.dp
}

object SdcType {
    val caption = 12.sp
    val bodySmall = 14.sp
    val body = 16.sp
    val title = 20.sp
    val heading = 32.sp
    val display = 54.sp
}

/** touchTargetMinPx from tokens.json — every tappable control respects this. */
val SdcTouchTargetMin = 44.dp
