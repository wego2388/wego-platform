package com.wego.isolation

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Executable proof that Safari Tours Sharm is composed from the platform
 * kernels and tours-operator product only. The absence assertions are as
 * important as the positive ones: another client's code and tables must not
 * be merely hidden behind configuration flags.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ProductIsolationIntegrationTest(
    @Autowired private val flyway: Flyway,
) {
    @Test
    fun `boots and migrates only Safari Tours Sharm product capabilities`() {
        assertThat(flyway.info().applied().map { it.version.toString() })
            .containsExactly(
                "1",
                "2",
                "3",
                "14",
                "16",
                "17",
                "18",
                "19",
                "20",
                "21",
                "22",
                "23",
                "24",
                "25",
                "26",
                "27",
                "28",
                "29",
                "30",
                "31",
                "32",
            )

        postgres.createConnection("").use { connection ->
            val tableNames =
                connection.metaData.getTables(null, "wego", "%", arrayOf("TABLE")).use { rs ->
                    generateSequence {
                        if (rs.next()) rs.getString("TABLE_NAME") else null
                    }.toList()
                }

            assertThat(tableNames)
                .contains(
                    "identity_user",
                    "identity_role",
                    "identity_permission",
                    "integration_outbox",
                    "tours_operator_tour",
                    "tours_operator_tour_slot",
                    "tours_operator_booking",
                    "tours_operator_payment",
                    "tours_operator_payment_refund_event",
                    "tours_operator_office_collection",
                    "tours_operator_fx_rate",
                    "tours_operator_document_print",
                    "tours_operator_document_sequence",
                    "tours_operator_supplier",
                    "tours_operator_driver",
                    "tours_operator_vehicle",
                    "tours_operator_slot_assignment",
                    "tours_operator_assignment_audit",
                    "tours_operator_asset",
                    "tours_operator_asset_variant",
                    "tours_operator_category_media",
                ).noneMatch { it.startsWith("divers_") }
                .noneMatch { it.startsWith("travel_") }
                .noneMatch { it.startsWith("hr_") }
                .noneMatch { it.startsWith("accounting_") }
                .noneMatch { it.startsWith("payroll_") }

            val permissionCodes =
                connection.createStatement().executeQuery("SELECT code FROM wego.identity_permission ORDER BY code").use { rs ->
                    generateSequence { if (rs.next()) rs.getString("code") else null }.toList()
                }
            assertThat(permissionCodes).containsExactly(
                "identity:administer",
                "identity:role-manage",
                "identity:role-view",
                "identity:user-manage",
                "identity:user-view",
                "tours-operator.assignment:manage",
                "tours-operator.booking:cancel",
                "tours-operator.booking:collect-cash",
                "tours-operator.booking:complete",
                "tours-operator.booking:create-office",
                "tours-operator.booking:payment-update",
                "tours-operator.booking:reverse-collection",
                "tours-operator.booking:view",
                "tours-operator.content:publish",
                "tours-operator.document:print",
                "tours-operator.document:print-ops",
                "tours-operator.fleet:manage",
                "tours-operator.fx-rate:manage",
                "tours-operator.media:upload",
                "tours-operator.notification:manage",
                "tours-operator.payment:refund",
                "tours-operator.payment:view",
                "tours-operator.slot:manage",
                "tours-operator.supplier:manage",
                "tours-operator.tour:manage",
                "tours-operator.tour:view",
            )
        }

        assertThatThrownBy { Class.forName("com.wego.divers.api.BookingController") }
            .isInstanceOf(ClassNotFoundException::class.java)
        assertThatThrownBy { Class.forName("com.wego.travelmarketplace.api.PublicCatalogController") }
            .isInstanceOf(ClassNotFoundException::class.java)
        assertThatThrownBy { Class.forName("com.wego.hr.api.EmployeeController") }
            .isInstanceOf(ClassNotFoundException::class.java)
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_safari_tours_sharm_test")
                .withUsername("wego_test")
                .withPassword("wego_test")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.enabled") { true }
            registry.add("tours-operator.paymob.mock-enabled") { true }
        }
    }
}
