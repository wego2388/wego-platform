package com.wego.mobile.shared.experience

import kotlin.test.Test
import kotlin.test.assertEquals

class ExperienceProfileTest {
    @Test
    fun `foundation exposes only generic experience profiles`() {
        assertEquals(
            listOf("STANDARD", "VOICE_FIRST", "SIMPLIFIED"),
            ExperienceProfile.entries.map(ExperienceProfile::name),
        )
    }
}
