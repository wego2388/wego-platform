package com.wego.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest(
    properties = [
        "spring.flyway.enabled=false",
        "management.health.db.enabled=false",
    ],
)
@AutoConfigureMockMvc
class SecurityConfigurationTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `foreign product API prefixes are not exposed by the Safari release`() {
        listOf("/api/v1/divers/offerings", "/api/v1/travel-marketplace/public/services", "/api/v1/hr/employees")
            .forEach { path -> mockMvc.get(path).andExpect { status { isUnauthorized() } } }
    }
}
