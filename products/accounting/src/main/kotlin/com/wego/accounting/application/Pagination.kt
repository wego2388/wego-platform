package com.wego.accounting.application

/** Shared page-size bounds for `findAll` query methods on this module's repositories — a module-local copy of `com.wego.divers.application.Pagination`'s exact reasoning. */
internal object Pagination {
    const val DEFAULT_PAGE_SIZE = 50
    const val MAX_PAGE_SIZE = 200

    fun boundedSize(size: Int): Int = size.coerceIn(1, MAX_PAGE_SIZE)

    fun offsetFor(
        page: Int,
        size: Int,
    ): Int {
        val boundedPage = page.coerceAtLeast(0).toLong()
        val offset = boundedPage * boundedSize(size).toLong()
        return offset.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
