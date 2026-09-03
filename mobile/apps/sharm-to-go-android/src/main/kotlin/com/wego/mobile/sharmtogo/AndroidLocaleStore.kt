package com.wego.mobile.sharmtogo

import android.content.Context
import com.wego.mobile.shared.locale.AppLocale
import com.wego.mobile.shared.locale.LocaleStore

private const val PREFS_NAME = "wego_sharm_to_go_prefs"
private const val KEY_LOCALE = "locale"

class AndroidLocaleStore(
    context: Context,
) : LocaleStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): AppLocale? =
        when (prefs.getString(KEY_LOCALE, null)) {
            AppLocale.EN.name -> AppLocale.EN
            AppLocale.AR.name -> AppLocale.AR
            else -> null
        }

    override fun write(locale: AppLocale) {
        prefs.edit().putString(KEY_LOCALE, locale.name).apply()
    }
}
