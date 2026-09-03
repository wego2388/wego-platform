package com.wego.mobile.sharmtogo.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Ported verbatim from `web/apps/sharm-to-go-site/app/assets/css/main.css`'s
 * `:root` custom properties — one design source, rendered by both the
 * website (Tailwind) and this app (Compose). Deliberately client-specific,
 * so it lives in `apps/sharm-to-go` (Sharm To Go branded) rather than
 * `mobile/shared` (product-neutral, shared with the unbranded Wego Ops
 * staff app) — same split `SdcTokens.kt` uses for Sharm Divers Club.
 */
object StgColor {
    val sea = Color(0xFF075F67)
    val seaBright = Color(0xFF0B8691)
    val lagoon = Color(0xFFD8F1EF)
    val sand = Color(0xFFF4DEC0)
    val sun = Color(0xFFF2A93B)

    val canvas = Color(0xFFF7FBFA)
    val surface = Color(0xFFFFFFFF)

    val textPrimary = Color(0xFF102F35)
    val textSecondary = Color(0xFF587078)

    val border = Color(0xFFD5E1DF)

    val statusInfo = Color(0xFF155E75)
    val statusInfoSoft = Color(0xFFCFFAFE)
    val statusSuccess = Color(0xFF0F7A3D)
    val statusSuccessSoft = Color(0xFFDCF5E4)
    val statusWarning = Color(0xFF8A5809)
    val statusWarningSoft = Color(0xFFFFF1D8)
    val statusDanger = Color(0xFFB3261E)
    val statusDangerSoft = Color(0xFFFBDCD9)
}

object StgSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

object StgRadius {
    val control = 10.dp
    val card = 18.dp
    val panel = 28.dp
    val pill = 999.dp
}

object StgType {
    val caption = 12.sp
    val bodySmall = 14.sp
    val body = 16.sp
    val title = 20.sp
    val heading = 32.sp
    val display = 54.sp
}

val StgTouchTargetMin = 44.dp
