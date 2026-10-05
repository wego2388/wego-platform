package com.wego.toursoperator.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class CategoryMediaTest {
    private fun base(category: String = "SEA"): CategoryMedia =
        CategoryMedia(
            category = category,
            assetId = null,
            alt = emptyMap(),
            rightsStatus = MediaRightsStatus.DRAFT,
            approvedAt = null,
            approvedByUserId = null,
            updatedAt = Instant.now(),
            updatedByUserId = null,
        )

    @Test
    fun `approve sets status and timestamps`() {
        val media =
            base().copy(
                assetId = UUID.randomUUID(),
                alt = mapOf(ContentLocale.EN to "Sea view"),
            )
        val actor = UUID.randomUUID()
        val now = Instant.now()
        val approved = media.approve(now, actor)
        assertEquals(MediaRightsStatus.APPROVED, approved.rightsStatus)
        assertEquals(now, approved.approvedAt)
        assertEquals(actor, approved.approvedByUserId)
    }

    @Test
    fun `approve without English alt throws`() {
        val media =
            base().copy(
                assetId = UUID.randomUUID(),
                alt = mapOf(ContentLocale.AR to "منظر البحر"),
            )
        val ex = assertThrows<IllegalArgumentException> { media.approve(Instant.now(), null) }
        assert(ex.message!!.contains("English alt"))
    }

    @Test
    fun `APPROVED requires approvedAt - constructor invariant`() {
        assertThrows<IllegalArgumentException> {
            CategoryMedia(
                category = "SEA",
                assetId = UUID.randomUUID(),
                alt = mapOf(ContentLocale.EN to "Sea"),
                rightsStatus = MediaRightsStatus.APPROVED,
                approvedAt = null, // mismatch
                approvedByUserId = null,
                updatedAt = Instant.now(),
                updatedByUserId = null,
            )
        }
    }

    @Test
    fun `DRAFT must not have approvedAt - constructor invariant`() {
        assertThrows<IllegalArgumentException> {
            CategoryMedia(
                category = "DESERT",
                assetId = null,
                alt = emptyMap(),
                rightsStatus = MediaRightsStatus.DRAFT,
                approvedAt = Instant.now(), // mismatch
                approvedByUserId = null,
                updatedAt = Instant.now(),
                updatedByUserId = null,
            )
        }
    }

    @Test
    fun `revision is deterministic for same key and alt`() {
        val media = base().copy(alt = mapOf(ContentLocale.EN to "Sea view"))
        val key = "assets/category_media/SEA/uuid.jpg"
        assertEquals(media.revision(key), media.revision(key))
    }

    @Test
    fun `revision changes when alt changes`() {
        val key = "assets/category_media/SEA/uuid.jpg"
        val m1 = base().copy(alt = mapOf(ContentLocale.EN to "Sea view"))
        val m2 = base().copy(alt = mapOf(ContentLocale.EN to "Beach view"))
        assert(m1.revision(key) != m2.revision(key))
    }

    @Test
    fun `revision changes when storage key changes`() {
        val alt = mapOf(ContentLocale.EN to "Sea")
        val m = base().copy(alt = alt)
        assert(m.revision("assets/category_media/SEA/a.jpg") != m.revision("assets/category_media/SEA/b.jpg"))
    }

    @Test
    fun `invalid category pattern rejected`() {
        assertThrows<IllegalArgumentException> { base("lower-case") }
        assertThrows<IllegalArgumentException> { base("has spaces") }
        assertThrows<IllegalArgumentException> { base("") }
    }

    @Test
    fun `alt text too long rejected`() {
        val longAlt = "x".repeat(201)
        assertThrows<IllegalArgumentException> {
            base().copy(alt = mapOf(ContentLocale.EN to longAlt))
        }
    }

    @Test
    fun `blank alt text rejected`() {
        assertThrows<IllegalArgumentException> {
            base().copy(alt = mapOf(ContentLocale.EN to "  "))
        }
    }
}
