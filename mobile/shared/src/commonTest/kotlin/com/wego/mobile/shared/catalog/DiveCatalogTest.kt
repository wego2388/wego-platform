package com.wego.mobile.shared.catalog

import com.wego.mobile.shared.locale.AppLocale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiveCatalogTest {
    @Test
    fun `has exactly 18 real GOV-003-approved offerings`() {
        assertEquals(18, DiveCatalog.offerings.size)
    }

    @Test
    fun `every offering code is unique`() {
        val codes = DiveCatalog.offerings.map { it.code }
        assertEquals(codes.size, codes.distinct().size)
    }

    @Test
    fun `byCode finds a real offering case-insensitively`() {
        val offering = assertNotNull(DiveCatalog.byCode("pc04"))
        assertEquals("PADI Open Water Diver", offering.name.en)
        assertEquals(350, offering.priceEur)
    }

    @Test
    fun `byCode returns null for an unknown code`() {
        assertNull(DiveCatalog.byCode("DOES-NOT-EXIST"))
    }

    @Test
    fun `byCategory filters correctly and every category has at least one offering`() {
        for (category in CategoryId.entries) {
            val offerings = DiveCatalog.byCategory(category)
            assertTrue(offerings.isNotEmpty(), "Category $category has no offerings")
            assertTrue(offerings.all { it.categoryId == category })
        }
    }

    @Test
    fun `every offering has a positive price`() {
        assertTrue(DiveCatalog.offerings.all { it.priceEur > 0 })
    }

    @Test
    fun `Categories has metadata for exactly the seven catalog categories`() {
        val catalogCategories = DiveCatalog.offerings.map { it.categoryId }.toSet()
        val metaCategories = Categories.all.map { it.id }.toSet()
        assertEquals(catalogCategories, metaCategories)
        assertEquals(7, metaCategories.size)
    }

    @Test
    fun `formatting helpers produce real non-blank bilingual output`() {
        val offering = assertNotNull(DiveCatalog.byCode("SD02"))
        assertEquals("€50", formatEur(offering.priceEur))
        assertEquals("30 minutes", durationLabel(AppLocale.EN, offering.durationMinutes))
        assertEquals("30 دقيقة", durationLabel(AppLocale.AR, offering.durationMinutes))
        assertEquals("1 dive", diveCountLabel(AppLocale.EN, offering.diveCount))
        assertTrue(offering.audience.label(AppLocale.AR).isNotBlank())
    }

    @Test
    fun `every category icon path is non-blank`() {
        for (category in CategoryId.entries) {
            assertTrue(category.iconPath().isNotBlank())
        }
    }
}
