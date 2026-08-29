package com.wego.mobile.shared.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiveSiteTest {
    @Test
    fun `has exactly the 4 real named sites from the approved catalog`() {
        assertEquals(4, DiveSites.all.size)
    }

    @Test
    fun `every site slug is unique`() {
        val slugs = DiveSites.all.map { it.slug }
        assertEquals(slugs.size, slugs.distinct().size)
    }

    @Test
    fun `bySlug finds a real site`() {
        val site = assertNotNull(DiveSites.bySlug("ras-mohammed"))
        assertEquals("Ras Mohammed", site.name.en)
    }

    @Test
    fun `bySlug returns null for an unknown slug`() {
        assertNull(DiveSites.bySlug("does-not-exist"))
    }

    @Test
    fun `every site links to at least one real existing offering`() {
        for (site in DiveSites.all) {
            val offerings = site.offerings()
            assertTrue(offerings.isNotEmpty(), "Site ${site.slug} has no linked offerings")
            assertEquals(site.offeringCodes.size, offerings.size, "Site ${site.slug} references a code not in DiveCatalog")
        }
    }

    @Test
    fun `no blurb mentions a fabricated depth visibility or marine-life stat`() {
        for (site in DiveSites.all) {
            assertTrue(!site.blurb.en.contains(Regex("\\d+\\s*m(eters)?\\b", RegexOption.IGNORE_CASE)))
        }
    }
}
