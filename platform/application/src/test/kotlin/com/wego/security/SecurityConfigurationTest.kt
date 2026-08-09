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
    fun `health endpoint is anonymously available`() {
        mockMvc
            .get("/actuator/health")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UP") }
            }
    }

    @Test
    fun `all other routes are denied by default, as a 401 challenge for an unauthenticated caller`() {
        // 401, not 403: an anonymous caller is "not authenticated", which
        // is a different failure than "authenticated but forbidden" (403).
        // Spring Security's ExceptionTranslationFilter itself makes this
        // distinction and routes it to the registered
        // AuthenticationEntryPoint — see identity's SecurityConfiguration.
        mockMvc
            .get("/api/v1/not-configured")
            .andExpect {
                status { isUnauthorized() }
            }
    }
}
