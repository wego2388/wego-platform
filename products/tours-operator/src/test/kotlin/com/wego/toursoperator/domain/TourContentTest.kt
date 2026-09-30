package com.wego.toursoperator.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class TourContentTest {
    private fun doc(
        name: String = "Coral Reef Cruise",
        includes: List<String> = emptyList(),
        stops: List<StopText> = emptyList(),
    ) = TourContentDocument(name, "Short", "Long description", includes = includes, stops = stops)

    @Test
    fun `content documents are bounded`() {
        assertThrows<IllegalArgumentException> { doc(name = " ") }
        assertThrows<IllegalArgumentException> { doc(name = "x".repeat(121)) }
        assertThrows<IllegalArgumentException> { doc(includes = List(21) { "item" }) }
        assertThrows<IllegalArgumentException> { doc(includes = listOf("x".repeat(201))) }
        assertThrows<IllegalArgumentException> {
            doc(stops = listOf(StopText("reef", "Reef", null), StopText("reef", "Reef again", null)))
        }
        assertEquals("Coral Reef Cruise", doc().name)
    }

    @Test
    fun `facts reject impossible values and allow unstated ones`() {
        val unstated = TourFactsDocument()
        assertNull(unstated.childrenAllowed)
        assertThrows<IllegalArgumentException> { TourFactsDocument(minimumAge = 120) }
        assertThrows<IllegalArgumentException> { TourFactsDocument(guideLanguages = listOf("English")) }
        assertThrows<IllegalArgumentException> { TourStop("reef", StopKind.STOP, 91.0, 34.0) }
        assertThrows<IllegalArgumentException> {
            TourFactsDocument(
                stops =
                    listOf(
                        TourStop("a", StopKind.MEETING_POINT, 27.9, 34.3),
                        TourStop("b", StopKind.MEETING_POINT, 27.8, 34.2),
                    ),
            )
        }
    }

    @Test
    fun `locales are the four customer languages`() {
        assertEquals(ContentLocale.AR, ContentLocale.fromCode("AR"))
        assertNull(ContentLocale.fromCode("de"))
    }

    private fun media(alt: Map<ContentLocale, String>) =
        TourMedia(
            id = UUID.randomUUID(),
            tourId = TourId(UUID.randomUUID()),
            position = 0,
            path = "/media/tours/reef/cover.avif",
            width = 1600,
            height = 900,
            isCover = true,
            alt = alt,
            rightsStatus = MediaRightsStatus.DRAFT,
            approvedAt = null,
            approvedByUserId = null,
            createdAt = Instant.EPOCH,
        )

    @Test
    fun `media paths are local and approval needs English alt`() {
        assertThrows<IllegalArgumentException> {
            media(emptyMap()).let {
                TourMedia(
                    it.id,
                    it.tourId,
                    0,
                    "https://cdn.example/x.jpg",
                    1,
                    1,
                    false,
                    emptyMap(),
                    MediaRightsStatus.DRAFT,
                    null,
                    null,
                    Instant.EPOCH,
                )
            }
        }
        assertThrows<IllegalArgumentException> { media(mapOf(ContentLocale.IT to "Barca")).approve(Instant.EPOCH, null) }
        val approved = media(mapOf(ContentLocale.EN to "Boat"))
        val actor = UUID.randomUUID()
        approved.approve(Instant.EPOCH, actor)
        assertEquals(MediaRightsStatus.APPROVED, approved.rightsStatus)
        assertEquals(actor, approved.approvedByUserId)
    }

    @Test
    fun `media revision covers file and alt texts but not layout`() {
        val base = media(mapOf(ContentLocale.EN to "Boat"))
        val resized =
            TourMedia(base.id, base.tourId, 3, base.path, 800, 450, false, base.alt, MediaRightsStatus.DRAFT, null, null, Instant.EPOCH)
        val recaptioned = media(mapOf(ContentLocale.EN to "Different caption"))
        assertEquals(base.revision, resized.revision)
        assert(base.revision != recaptioned.revision)
        // Splitting one approved text into two locales must not keep the same revision.
        val joined = media(mapOf(ContentLocale.EN to "x|ar=y"))
        val split = media(mapOf(ContentLocale.EN to "x", ContentLocale.AR to "y"))
        assert(joined.revision != split.revision)
    }

    @Test
    fun `guide languages are bounded and distinct`() {
        assertThrows<IllegalArgumentException> { TourFactsDocument(guideLanguages = listOf("en", "en")) }
        assertThrows<IllegalArgumentException> { TourFactsDocument(guideLanguages = List(11) { "e" + ('a' + it) }) }
    }
}
