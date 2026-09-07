package com.wego.mobile.sharmtogo.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Same coverage shape as the website's `CategoryAccents.spec.ts` for `accentForIndex`. */
class StgCategoryToneTest {
    @Test
    fun `assigns each of the four categories its own accent`() {
        val accents = StgCategoryTone.entries.map { it.accent }.toSet()
        assertEquals(StgCategoryTone.entries.size, accents.size)
    }

    @Test
    fun `cycles back to the first tone once the category count exceeds four`() {
        assertEquals(StgCategoryTone.forIndex(0), StgCategoryTone.forIndex(4))
        assertEquals(StgCategoryTone.forIndex(1), StgCategoryTone.forIndex(5))
    }

    @Test
    fun `never throws for an unmatched category (index -1)`() {
        assertNotNull(StgCategoryTone.forIndex(-1))
        assertEquals(StgCategoryTone.entries.last(), StgCategoryTone.forIndex(-1))
    }
}
