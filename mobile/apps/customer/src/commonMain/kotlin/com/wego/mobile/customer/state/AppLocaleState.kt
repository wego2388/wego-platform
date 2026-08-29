package com.wego.mobile.customer.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wego.mobile.shared.locale.AppLocale
import com.wego.mobile.shared.locale.LocaleStore
import com.wego.mobile.shared.locale.NoOpLocaleStore

/** The mobile equivalent of the website's `useSiteLocale` composable. */
@Stable
class AppLocaleState(
    initial: AppLocale = AppLocale.EN,
    private val store: LocaleStore = NoOpLocaleStore,
) {
    var locale: AppLocale by mutableStateOf(store.read() ?: initial)
        private set

    fun toggle() {
        locale = if (locale == AppLocale.EN) AppLocale.AR else AppLocale.EN
        store.write(locale)
    }
}
