package com.wego.security

import com.wego.travelmarketplace.application.EmailMessage
import com.wego.travelmarketplace.application.EmailSender
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * Compose passes an empty mail host when no SMTP relay is configured, and
 * Spring Boot then builds a JavaMailSender whose health indicator reports
 * DOWN. The compose healthcheck greps for UP, so that used to keep the app
 * unhealthy (and the edge from starting) with notifications switched off.
 * Email delivery must never decide the API's health.
 */
@SpringBootTest(
    properties = [
        "spring.flyway.enabled=false",
        "management.health.db.enabled=false",
        "spring.mail.host=",
        "travel-marketplace.notifications.enabled=false",
        "travel-marketplace.notifications.smtp.host=",
    ],
)
@AutoConfigureMockMvc
class MailHealthIndicatorTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val emailSender: EmailSender,
) {
    @Test
    fun `an empty mail host with the dispatcher disabled still reports UP`() {
        mockMvc
            .get("/actuator/health")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UP") }
            }
    }

    @Test
    fun `an empty SMTP host yields a sender that refuses to send instead of a half-configured one`() {
        assertThatThrownBy { emailSender.send(EmailMessage("a@b.example", "s", "b")) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("email_not_configured")
        assertThat(emailSender.javaClass.simpleName).doesNotContain("Smtp")
    }
}
