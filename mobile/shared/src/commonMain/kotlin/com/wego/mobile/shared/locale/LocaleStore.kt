package com.wego.mobile.shared.locale

/**
 * The mobile equivalent of the website's `localStorage`-backed locale
 * persistence — no `Context`-free way exists to reach `SharedPreferences`
 * from `commonMain`, so each platform's real entry point (`MainActivity` on
 * Android) constructs the real implementation and passes it down, rather
 * than an `expect`/`actual` pair.
 */
interface LocaleStore {
    fun read(): AppLocale?

    fun write(locale: AppLocale)
}

/** The default for platforms/entry points that haven't wired a real store yet. */
object NoOpLocaleStore : LocaleStore {
    override fun read(): AppLocale? = null

    override fun write(locale: AppLocale) = Unit
}
