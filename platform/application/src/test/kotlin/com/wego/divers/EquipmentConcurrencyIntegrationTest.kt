package com.wego.divers

import com.wego.divers.application.CreateEquipmentCommand
import com.wego.divers.application.CreateEquipmentService
import com.wego.divers.application.RecordRentalCommand
import com.wego.divers.application.RecordRentalService
import com.wego.divers.application.RetireEquipmentService
import com.wego.divers.application.StartMaintenanceService
import com.wego.divers.domain.EquipmentType
import com.wego.generated.jooq.tables.DiversEquipment.DIVERS_EQUIPMENT
import com.wego.generated.jooq.tables.DiversEquipmentRentalRecord.DIVERS_EQUIPMENT_RENTAL_RECORD
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Proves the `SELECT ... FOR UPDATE` row lock added to
 * `EquipmentRepository.findByIdForUpdate` closes two real races independent
 * Tier 1 review found: a retire racing an open-rental start (finding 4),
 * and starting maintenance racing an open-rental start with no check at all
 * (finding 5, `StartMaintenanceService` now rejects `HasOpenRental`).
 * Real invariant checked across many independent trials: an item can never
 * end up RETIRED/IN_MAINTENANCE while a rental record for it is still open.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class EquipmentConcurrencyIntegrationTest {
    @Autowired
    private lateinit var createEquipmentService: CreateEquipmentService

    @Autowired
    private lateinit var retireEquipmentService: RetireEquipmentService

    @Autowired
    private lateinit var startMaintenanceService: StartMaintenanceService

    @Autowired
    private lateinit var recordRentalService: RecordRentalService

    @Autowired
    private lateinit var dsl: DSLContext

    @Test
    fun `retiring never wins against a racing rental start`() {
        val trials = 30
        val equipmentIds =
            (1..trials).map { index ->
                (
                    createEquipmentService.create(
                        CreateEquipmentCommand(
                            equipmentType = EquipmentType.TANK,
                            label = "Retire-Race Tank $index",
                            qrCode = "retire-race-$index",
                            itemSize = null,
                            serialNumber = null,
                            createdByUserId = null,
                            correlationId = null,
                        ),
                    ) as com.wego.divers.application.CreateEquipmentResult.Created
                ).equipment.id
            }

        runConcurrently(
            equipmentIds.flatMap { equipmentId ->
                listOf(
                    {
                        retireEquipmentService.retire(equipmentId, null, null)
                        Unit
                    },
                    {
                        recordRentalService.record(
                            RecordRentalCommand(
                                equipmentId = equipmentId,
                                customerName = "Race Customer",
                                rentedOn = LocalDate.parse("2026-09-01"),
                                notes = null,
                            ),
                        )
                        Unit
                    },
                )
            },
        )

        for (equipmentId in equipmentIds) {
            val status =
                dsl.select(DIVERS_EQUIPMENT.STATUS).from(DIVERS_EQUIPMENT).where(DIVERS_EQUIPMENT.ID.eq(equipmentId.value)).fetchOne(
                    0,
                    String::class.java,
                )
            val hasOpenRental =
                dsl.fetchExists(
                    dsl
                        .selectFrom(DIVERS_EQUIPMENT_RENTAL_RECORD)
                        .where(DIVERS_EQUIPMENT_RENTAL_RECORD.EQUIPMENT_ID.eq(equipmentId.value))
                        .and(DIVERS_EQUIPMENT_RENTAL_RECORD.RETURNED_ON.isNull),
                )
            assertThat(status == "RETIRED" && hasOpenRental)
                .withFailMessage("Equipment $equipmentId ended RETIRED with an open rental — the exact race this test guards against")
                .isFalse()
        }
    }

    @Test
    fun `starting maintenance never wins against a racing rental start`() {
        val trials = 30
        val equipmentIds =
            (1..trials).map { index ->
                (
                    createEquipmentService.create(
                        CreateEquipmentCommand(
                            equipmentType = EquipmentType.TANK,
                            label = "Maintenance-Race Tank $index",
                            qrCode = "maintenance-race-$index",
                            itemSize = null,
                            serialNumber = null,
                            createdByUserId = null,
                            correlationId = null,
                        ),
                    ) as com.wego.divers.application.CreateEquipmentResult.Created
                ).equipment.id
            }

        runConcurrently(
            equipmentIds.flatMap { equipmentId ->
                listOf(
                    {
                        startMaintenanceService.start(equipmentId, null, null)
                        Unit
                    },
                    {
                        recordRentalService.record(
                            RecordRentalCommand(
                                equipmentId = equipmentId,
                                customerName = "Race Customer",
                                rentedOn = LocalDate.parse("2026-09-01"),
                                notes = null,
                            ),
                        )
                        Unit
                    },
                )
            },
        )

        for (equipmentId in equipmentIds) {
            val status =
                dsl.select(DIVERS_EQUIPMENT.STATUS).from(DIVERS_EQUIPMENT).where(DIVERS_EQUIPMENT.ID.eq(equipmentId.value)).fetchOne(
                    0,
                    String::class.java,
                )
            val hasOpenRental =
                dsl.fetchExists(
                    dsl
                        .selectFrom(DIVERS_EQUIPMENT_RENTAL_RECORD)
                        .where(DIVERS_EQUIPMENT_RENTAL_RECORD.EQUIPMENT_ID.eq(equipmentId.value))
                        .and(DIVERS_EQUIPMENT_RENTAL_RECORD.RETURNED_ON.isNull),
                )
            assertThat(status == "IN_MAINTENANCE" && hasOpenRental)
                .withFailMessage(
                    "Equipment $equipmentId ended IN_MAINTENANCE with an open rental — the exact race this test guards against",
                ).isFalse()
        }
    }

    private fun runConcurrently(actions: List<() -> Any?>) {
        val pool = Executors.newFixedThreadPool(minOf(actions.size, 64))
        val ready = CountDownLatch(actions.size)
        val start = CountDownLatch(1)
        val done = CountDownLatch(actions.size)

        actions.forEach { action ->
            pool.submit {
                ready.countDown()
                start.await()
                try {
                    action()
                } catch (_: Exception) {
                    // A rejected outcome under real contention is expected.
                } finally {
                    done.countDown()
                }
            }
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue()
        start.countDown()
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue()
        pool.shutdown()
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_test")
                .withUsername("wego_test")
                .withPassword("wego_test")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.enabled") { true }
        }
    }
}
