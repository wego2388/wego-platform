package com.wego.mobile.shared.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TravelCatalogSnapshotTest {
    @Test
    fun `is honestly empty - matching the real live-verified backend state as of Packet 1D`() {
        assertTrue(TravelCatalogSnapshot.categories.isEmpty())
        assertTrue(TravelCatalogSnapshot.services.isEmpty())
    }

    @Test
    fun `lookups on an empty catalog return null or empty and never crash`() {
        assertNull(TravelCatalogSnapshot.categoryById("does-not-exist"))
        assertNull(TravelCatalogSnapshot.serviceById("does-not-exist"))
        assertEquals(emptyList(), TravelCatalogSnapshot.servicesByCategory("does-not-exist"))
    }

    @Test
    fun `a real service shape carries the public API's exact fields and options and media`() {
        val service =
            TravelService(
                id = "s1",
                categoryId = "c1",
                name = LocalizedText("Desert Safari", "سفاري صحراوي"),
                description = LocalizedText("An evening safari.", "رحلة مسائية."),
                confirmationType = TravelConfirmationType.INSTANT,
                cancellationPolicy = LocalizedText("Free cancellation.", "إلغاء مجاني."),
                pickupInfo = null,
                inclusions = null,
                exclusions = null,
                operatedBy = "Red Sea Adventures",
                options =
                    listOf(
                        TravelServiceOption(
                            label = LocalizedText("Evening trip", "رحلة مسائية"),
                            durationMinutes = 180,
                            maxParticipants = 10,
                            priceAmount = "500.00",
                            priceCurrency = "EGP",
                            priceBasis = TravelPriceBasis.PER_PERSON,
                        ),
                    ),
                media = listOf(TravelServiceMedia(assetReference = "asset-1", locale = "en")),
            )

        assertEquals("Desert Safari", service.name.en)
        assertEquals("500.00", service.options.single().priceAmount)
        assertEquals("Red Sea Adventures", service.operatedBy)
    }
}
