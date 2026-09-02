package com.wego.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * The executable half of WEGO-010-A Packet 0R's proof: this application
 * never has `products/divers` on its compile classpath, so it never
 * registers a `com.wego.identity.AuthenticatedApiPrefix` bean for the
 * Divers product's own API route tree (only `DiversBeanConfiguration`,
 * which does not exist here, contributes that). Kernel
 * `SecurityConfiguration` therefore falls through to its default
 * `denyAll()` for that path space — a real 401, the same failure an
 * unrecognized route gets, not a 403 or a 404 that could be mistaken for
 * "the route exists but is guarded differently."
 */
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
        mockMvc
            .get("/api/v1/not-configured")
            .andExpect {
                status { isUnauthorized() }
            }
    }

    @Test
    fun `the Divers product's own API prefix is not authorized here — it is denied like any unknown route`() {
        mockMvc
            .get("/api/v1/divers/divers")
            .andExpect {
                status { isUnauthorized() }
            }
    }
}
