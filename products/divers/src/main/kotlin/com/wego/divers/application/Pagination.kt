package com.wego.divers.application

/** Shared page-size bounds for `findAll` query methods on this module's repositories. */
internal object Pagination {
    const val DEFAULT_PAGE_SIZE = 50
    const val MAX_PAGE_SIZE = 200

    fun boundedSize(size: Int): Int = size.coerceIn(1, MAX_PAGE_SIZE)

    /**
     * Computed in `Long` before narrowing back to `Int`: `page * size` as
     * plain `Int` arithmetic can overflow silently for a large-enough
     * `page` (e.g. a malicious or malformed query parameter) and wrap to a
     * negative offset, which jOOQ would reject or mishandle downstream.
     * An absurdly large result clamps to `Int.MAX_VALUE`, which simply
     * yields zero rows — a safe outcome, not a crash.
     */
    fun offsetFor(
        page: Int,
        size: Int,
    ): Int {
        val boundedPage = page.coerceAtLeast(0).toLong()
        val offset = boundedPage * boundedSize(size).toLong()
        return offset.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
