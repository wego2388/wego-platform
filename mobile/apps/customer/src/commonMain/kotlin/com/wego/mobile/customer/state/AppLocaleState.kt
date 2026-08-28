package com.wego.mobile.customer.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wego.mobile.shared.locale.AppLocale

/** The mobile equivalent of the website's `useSiteLocale` composable. */
@Stable
class AppLocaleState(
    initial: AppLocale = AppLocale.EN,
) {
    var locale: AppLocale by mutableStateOf(initial)
        private set

    fun toggle() {
        locale = if (locale == AppLocale.EN) AppLocale.AR else AppLocale.EN
    }
}
