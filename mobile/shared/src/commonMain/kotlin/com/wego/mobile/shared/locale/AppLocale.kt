package com.wego.mobile.shared.locale

/**
 * Mirrors `SdcLocale` from
 * `web/apps/sharm-divers-club-site/app/content/locales.ts` — Arabic and
 * English only. No other language ships reviewed UI copy yet.
 */
enum class AppLocale {
    EN,
    AR,
}

val AppLocale.isRtl: Boolean
    get() = this == AppLocale.AR
