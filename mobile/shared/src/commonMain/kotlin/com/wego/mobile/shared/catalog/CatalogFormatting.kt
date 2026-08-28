package com.wego.mobile.shared.catalog

import com.wego.mobile.shared.locale.AppLocale

private val audienceLabels: Map<Audience, LocalizedText> =
    mapOf(
        Audience.BEGINNER to LocalizedText("For first-timers", "للمبتدئين"),
        Audience.CERTIFIED_DIVER to LocalizedText("For certified divers", "للغواصين المعتمدين"),
        Audience.QUALIFIED_CERTIFIED_DIVER to LocalizedText("For qualified certified divers", "للغواصين المعتمدين المؤهلين"),
        Audience.BEGINNER_COURSE to LocalizedText("No experience required", "بدون خبرة سابقة"),
        Audience.PROFESSIONAL_TRACK to LocalizedText("Professional track", "مسار احترافي"),
        Audience.GENERAL to LocalizedText("For everyone", "للجميع"),
        Audience.PRIVATE_GROUP to LocalizedText("Private group", "مجموعة خاصة"),
    )

fun Audience.label(locale: AppLocale): String = audienceLabels.getValue(this).of(locale)

fun durationLabel(
    locale: AppLocale,
    minutes: Int?,
): String? {
    if (minutes == null) return null
    return if (locale == AppLocale.AR) "$minutes دقيقة" else "$minutes minutes"
}

fun diveCountLabel(
    locale: AppLocale,
    count: Int?,
): String? {
    if (count == null) return null
    if (count == 1) return if (locale == AppLocale.AR) "غطسة واحدة" else "1 dive"
    return if (locale == AppLocale.AR) "$count غطسات" else "$count dives"
}

fun formatEur(amount: Int): String = "€$amount"
