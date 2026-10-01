package com.wego.travelmarketplace

import com.wego.identity.application.PasswordHasher
import com.wego.identity.application.UserRepository
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import com.wego.identity.domain.UserStatus
import com.wego.travelmarketplace.application.CancelTravelRequestCommand
import com.wego.travelmarketplace.application.CancelTravelRequestResult
import com.wego.travelmarketplace.application.CancelTravelRequestService
import com.wego.travelmarketplace.application.CategoryRepository
import com.wego.travelmarketplace.application.CompleteTravelRequestResult
import com.wego.travelmarketplace.application.CompleteTravelRequestService
import com.wego.travelmarketplace.application.ConfirmTravelRequestResult
import com.wego.travelmarketplace.application.ConfirmTravelRequestService
import com.wego.travelmarketplace.application.CreateTravelRequestCommand
import com.wego.travelmarketplace.application.CreateTravelRequestResult
import com.wego.travelmarketplace.application.CreateTravelRequestService
import com.wego.travelmarketplace.application.ExpireTravelRequestsService
import com.wego.travelmarketplace.application.ServiceRepository
import com.wego.travelmarketplace.application.StartTravelRequestReviewResult
import com.wego.travelmarketplace.application.StartTravelRequestReviewService
import com.wego.travelmarketplace.application.TravelRequestRepository
import com.wego.travelmarketplace.domain.Category
import com.wego.travelmarketplace.domain.CategoryId
import com.wego.travelmarketplace.domain.ConfirmationType
import com.wego.travelmarketplace.domain.FulfilmentModel
import com.wego.travelmarketplace.domain.LocalizedText
import com.wego.travelmarketplace.domain.Money
import com.wego.travelmarketplace.domain.PriceBasis
import com.wego.travelmarketplace.domain.Service
import com.wego.travelmarketplace.domain.ServiceId
import com.wego.travelmarketplace.domain.ServiceMedia
import com.wego.travelmarketplace.domain.ServiceOption
import com.wego.travelmarketplace.domain.ServiceStatus
import com.wego.travelmarketplace.domain.TravelRequestActorType
import com.wego.travelmarketplace.domain.TravelRequestCancelReason
import com.wego.travelmarketplace.domain.TravelRequestCustomer
import com.wego.travelmarketplace.domain.TravelRequestSourceChannel
import com.wego.travelmarketplace.domain.TravelRequestStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Proves Phase 1A's real invariants end to end against real PostgreSQL:
 * the full `NEW -> ... -> COMPLETED`/`CANCELLED`/`EXPIRED` lifecycle actually
 * persists and reloads correctly, an `INSTANT` service auto-confirms on
 * creation while a `STAFF_REVIEW` one does not, party size is checked
 * against the snapshotted option, and — the sub-packet's central proof per
 * `delivery/01_REQUEST_AND_BOOKING.md` — concurrent duplicate submissions of
 * the same idempotency key never create two rows.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class TravelRequestServiceTest {
    @Autowired
    private lateinit var categoryRepository: CategoryRepository

    @Autowired
    private lateinit var serviceRepository: ServiceRepository

    @Autowired
    private lateinit var requestRepository: TravelRequestRepository

    @Autowired
    private lateinit var createTravelRequestService: CreateTravelRequestService

    @Autowired
    private lateinit var startTravelRequestReviewService: StartTravelRequestReviewService

    @Autowired
    private lateinit var confirmTravelRequestService: ConfirmTravelRequestService

    @Autowired
    private lateinit var cancelTravelRequestService: CancelTravelRequestService

    @Autowired
    private lateinit var completeTravelRequestService: CompleteTravelRequestService

    @Autowired
    private lateinit var expireTravelRequestsService: ExpireTravelRequestsService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    private var staffUserId: UUID = UUID.randomUUID()
    private var instantServiceId: ServiceId = ServiceId(UUID.randomUUID())
    private lateinit var instantOptionId: UUID
    private var staffReviewServiceId: ServiceId = ServiceId(UUID.randomUUID())
    private lateinit var staffReviewOptionId: UUID

    @BeforeEach
    fun seedCatalog() {
        staffUserId = seedStaffUser()
        val now = Instant.now()
        val category =
            Category.create(
                id = CategoryId.generate(),
                code = "seed-category-${UUID.randomUUID().toString().take(8)}",
                name = LocalizedText("Transfers", "الانتقالات"),
                description = null,
                displayOrder = 0,
                now = now,
            )
        categoryRepository.save(category)

        val instantOption =
            ServiceOption(
                id = UUID.randomUUID(),
                label = LocalizedText("Airport arrival", "استلام من المطار"),
                durationMinutes = 60,
                maxParticipants = 3,
                price = Money(BigDecimal("700.00"), "EGP"),
                priceBasis = PriceBasis.PER_VEHICLE,
            )
        instantOptionId = instantOption.id
        val instantService =
            publishedService(category.id, ConfirmationType.INSTANT, listOf(instantOption))
        instantServiceId = instantService.id
        serviceRepository.save(instantService)

        val staffOption =
            ServiceOption(
                id = UUID.randomUUID(),
                label = LocalizedText("Private car and driver", "سيارة خاصة بسائق"),
                durationMinutes = 180,
                maxParticipants = 3,
                price = Money(BigDecimal("900.00"), "EGP"),
                priceBasis = PriceBasis.PER_VEHICLE,
            )
        staffReviewOptionId = staffOption.id
        val staffReviewService =
            publishedService(category.id, ConfirmationType.STAFF_REVIEW, listOf(staffOption))
        staffReviewServiceId = staffReviewService.id
        serviceRepository.save(staffReviewService)
    }

    private fun seedStaffUser(): UUID {
        val email = "travel-request-staff-${UUID.randomUUID().toString().take(8)}@example.com"
        val user =
            User(
                id = UserId.generate(),
                email = EmailAddress.of(email),
                passwordHash = passwordHasher.hash("a-very-long-staff-password-123"),
                status = UserStatus.ACTIVE,
                roles = setOf(RoleCode.of("platform-admin")),
                createdAt = Instant.now(),
                failedLoginCount = 0,
                lockedUntil = null,
            )
        userRepository.save(user)
        return user.id.value
    }

    private fun publishedService(
        categoryId: CategoryId,
        confirmationType: ConfirmationType,
        options: List<ServiceOption>,
    ): Service {
        val now = Instant.now()
        return Service(
            id = ServiceId.generate(),
            categoryId = categoryId,
            name = LocalizedText("Test service", "خدمة اختبار"),
            description = LocalizedText("A test service.", "خدمة اختبار."),
            fulfilmentModel = FulfilmentModel.DIRECT,
            providerId = null,
            confirmationType = confirmationType,
            cancellationPolicy = LocalizedText("Free up to 24h before.", "إلغاء مجاني حتى 24 ساعة قبل الموعد."),
            pickupInfo = null,
            inclusions = null,
            exclusions = null,
            options = options,
            media = listOf(ServiceMedia(UUID.randomUUID(), "seed-asset.jpg", "test fixture rights", "en")),
            status = ServiceStatus.PUBLISHED,
            createdAt = now,
            publishedAt = now,
            archivedAt = null,
        )
    }

    /** The real price each seeded test option actually carries — see seedCatalog(). */
    private fun defaultExpectedPrice(optionId: UUID): Money =
        when (optionId) {
            instantOptionId -> Money(BigDecimal("700.00"), "EGP")
            staffReviewOptionId -> Money(BigDecimal("900.00"), "EGP")
            else -> error("Unknown option id in test fixture: $optionId")
        }

    private fun createCommand(
        serviceId: ServiceId,
        optionId: UUID,
        adults: Int = 2,
        children: Int = 0,
        idempotencyKey: String = UUID.randomUUID().toString(),
        expectedPrice: Money = defaultExpectedPrice(optionId),
    ) = CreateTravelRequestCommand(
        serviceId = serviceId,
        serviceOptionId = optionId,
        requestedDate = LocalDate.now().plusDays(10),
        requestedTime = null,
        adults = adults,
        children = children,
        hotelOrPickup = "Four Seasons Sharm",
        locale = "en",
        notes = null,
        sourceChannel = TravelRequestSourceChannel.WEBSITE,
        customer = TravelRequestCustomer(name = "Nour", phone = "+201001413469", email = null),
        idempotencyKey = idempotencyKey,
        correlationId = UUID.randomUUID(),
        expectedPrice = expectedPrice,
    )

    @Test
    fun `creating a request for an INSTANT service auto-confirms it, snapshotting price and policy`() {
        val result = createTravelRequestService.create(createCommand(instantServiceId, instantOptionId))
        check(result is CreateTravelRequestResult.Created)

        assertThat(result.request.status).isEqualTo(TravelRequestStatus.CONFIRMED)
        assertThat(result.request.confirmedAt).isNotNull()
        assertThat(result.request.price).isEqualTo(Money(BigDecimal("700.00"), "EGP"))

        val reloaded = requestRepository.findById(result.request.id)
        assertThat(reloaded?.status).isEqualTo(TravelRequestStatus.CONFIRMED)
    }

    @Test
    fun `creating a request for a STAFF_REVIEW service stays NEW until staff acts`() {
        val result = createTravelRequestService.create(createCommand(staffReviewServiceId, staffReviewOptionId))
        check(result is CreateTravelRequestResult.Created)
        assertThat(result.request.status).isEqualTo(TravelRequestStatus.NEW)
        assertThat(result.request.confirmedAt).isNull()
    }

    @Test
    fun `a stale expected price is rejected rather than silently confirmed at the new price`() {
        val staleExpectedPrice = Money(BigDecimal("650.00"), "EGP") // the real option is 700.00
        val key = UUID.randomUUID().toString()
        val result =
            createTravelRequestService.create(
                createCommand(instantServiceId, instantOptionId, idempotencyKey = key, expectedPrice = staleExpectedPrice),
            )

        check(result is CreateTravelRequestResult.PriceChanged)
        assertThat(result.currentPrice).isEqualTo(Money(BigDecimal("700.00"), "EGP"))
        assertThat(requestRepository.findByIdempotencyKey(key)).isNull() // confirms the rejected attempt created no row at all
    }

    @Test
    fun `an expected price matching the real current price is accepted`() {
        val result = createTravelRequestService.create(createCommand(instantServiceId, instantOptionId))
        check(result is CreateTravelRequestResult.Created)
        assertThat(result.request.price).isEqualTo(Money(BigDecimal("700.00"), "EGP"))
    }

    @Test
    fun `party size over the snapshotted option's maxParticipants is rejected`() {
        val result = createTravelRequestService.create(createCommand(instantServiceId, instantOptionId, adults = 4, children = 0))
        assertThat(result).isEqualTo(CreateTravelRequestResult.PartySizeExceedsCapacity(3))
    }

    @Test
    fun `resubmitting the same idempotency key returns the original request, not a duplicate`() {
        val key = UUID.randomUUID().toString()
        val first = createTravelRequestService.create(createCommand(instantServiceId, instantOptionId, idempotencyKey = key))
        val second = createTravelRequestService.create(createCommand(instantServiceId, instantOptionId, idempotencyKey = key))

        check(first is CreateTravelRequestResult.Created)
        check(second is CreateTravelRequestResult.AlreadyExists)
        assertThat(second.request.id).isEqualTo(first.request.id)
    }

    @Test
    fun `concurrent creates with the same idempotency key never produce two rows`() {
        val key = UUID.randomUUID().toString()
        val threadCount = 8
        val executor = Executors.newFixedThreadPool(threadCount)
        val readyLatch = CountDownLatch(threadCount)
        val goLatch = CountDownLatch(1)

        // Futures are kept and .get() below — a worker that throws (a real
        // concurrency bug, not just the expected AlreadyExists outcome)
        // must fail this test loudly on the test thread, not vanish
        // silently inside a discarded Future.
        val futures =
            (1..threadCount).map {
                executor.submit(
                    java.util.concurrent.Callable {
                        readyLatch.countDown()
                        goLatch.await()
                        createTravelRequestService.create(createCommand(instantServiceId, instantOptionId, idempotencyKey = key))
                    },
                )
            }
        readyLatch.await(10, TimeUnit.SECONDS)
        goLatch.countDown()
        val results = futures.map { it.get(30, TimeUnit.SECONDS) }
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)

        // All 8 workers must have actually completed and returned a result —
        // not just "however many happened to add to a queue before one
        // silently failed."
        assertThat(results).hasSize(threadCount)

        fun CreateTravelRequestResult.requestId() =
            when (this) {
                is CreateTravelRequestResult.Created -> request.id
                is CreateTravelRequestResult.AlreadyExists -> request.id
                else -> error("Unexpected result: $this")
            }
        val distinctIds = results.map { it.requestId() }.toSet()
        assertThat(distinctIds).hasSize(1)
        assertThat(results.count { it is CreateTravelRequestResult.Created }).isEqualTo(1)
        assertThat(results.count { it is CreateTravelRequestResult.AlreadyExists }).isEqualTo(threadCount - 1)

        // Authoritative check, independent of the in-memory results above:
        // exactly one row for this idempotency key actually exists in the
        // real database, and it is the same row every worker agreed on.
        val persisted = requestRepository.findByIdempotencyKey(key)
        assertThat(persisted).isNotNull()
        assertThat(persisted!!.id).isEqualTo(distinctIds.single())
    }

    @Test
    fun `full staff lifecycle, review through completion, persists at every step`() {
        val created = createTravelRequestService.create(createCommand(staffReviewServiceId, staffReviewOptionId))
        check(created is CreateTravelRequestResult.Created)
        val id = created.request.id

        val reviewed = startTravelRequestReviewService.start(id, staffUserId, null)
        check(reviewed is StartTravelRequestReviewResult.Started)
        assertThat(reviewed.request.status).isEqualTo(TravelRequestStatus.IN_REVIEW)

        val confirmed = confirmTravelRequestService.confirm(id, staffUserId, null)
        check(confirmed is ConfirmTravelRequestResult.Confirmed)
        assertThat(confirmed.request.status).isEqualTo(TravelRequestStatus.CONFIRMED)

        val completed = completeTravelRequestService.complete(id, staffUserId, null)
        check(completed is CompleteTravelRequestResult.Completed)
        assertThat(completed.request.status).isEqualTo(TravelRequestStatus.COMPLETED)

        val reloaded = requestRepository.findById(id)
        assertThat(reloaded?.status).isEqualTo(TravelRequestStatus.COMPLETED)
        assertThat(reloaded?.confirmedAt).isNotNull()
        assertThat(reloaded?.completedAt).isNotNull()
    }

    @Test
    fun `cancelling a confirmed request keeps confirmedAt as a historical marker`() {
        val created = createTravelRequestService.create(createCommand(instantServiceId, instantOptionId))
        check(created is CreateTravelRequestResult.Created)

        val cancelled =
            cancelTravelRequestService.cancel(
                CancelTravelRequestCommand(
                    requestId = created.request.id,
                    reason = TravelRequestCancelReason.CUSTOMER_REQUESTED,
                    detail = "Change of plans",
                    actorType = TravelRequestActorType.CUSTOMER,
                    actorUserId = null,
                    correlationId = null,
                ),
            )
        check(cancelled is CancelTravelRequestResult.Cancelled)
        assertThat(cancelled.request.status).isEqualTo(TravelRequestStatus.CANCELLED)
        assertThat(cancelled.request.confirmedAt).isNotNull()
        assertThat(cancelled.request.cancelReason).isEqualTo(TravelRequestCancelReason.CUSTOMER_REQUESTED)

        val secondAttempt =
            cancelTravelRequestService.cancel(
                CancelTravelRequestCommand(
                    requestId = created.request.id,
                    reason = TravelRequestCancelReason.OTHER,
                    detail = null,
                    actorType = TravelRequestActorType.CUSTOMER,
                    actorUserId = null,
                    correlationId = null,
                ),
            )
        assertThat(secondAttempt).isEqualTo(CancelTravelRequestResult.AlreadyTerminal)
    }

    @Test
    fun `the staff actor invariant is enforced at the command boundary`() {
        val created = createTravelRequestService.create(createCommand(instantServiceId, instantOptionId))
        check(created is CreateTravelRequestResult.Created)

        org.assertj.core.api.Assertions
            .assertThatIllegalArgumentException()
            .isThrownBy {
                cancelTravelRequestService.cancel(
                    CancelTravelRequestCommand(
                        requestId = created.request.id,
                        reason = TravelRequestCancelReason.STAFF_REJECTED,
                        detail = null,
                        actorType = TravelRequestActorType.STAFF,
                        actorUserId = null,
                        correlationId = null,
                    ),
                )
            }
    }

    @Test
    fun `an unconfirmed request whose date has passed expires, a confirmed one does not`() {
        val overdueKey = UUID.randomUUID().toString()
        val staffCreated =
            createTravelRequestService.create(
                createCommand(staffReviewServiceId, staffReviewOptionId, idempotencyKey = overdueKey)
                    .copy(requestedDate = LocalDate.now().minusDays(2)),
            )
        check(staffCreated is CreateTravelRequestResult.Created)

        val confirmedOverdue =
            createTravelRequestService.create(
                createCommand(instantServiceId, instantOptionId).copy(requestedDate = LocalDate.now().minusDays(2)),
            )
        check(confirmedOverdue is CreateTravelRequestResult.Created)
        assertThat(confirmedOverdue.request.status).isEqualTo(TravelRequestStatus.CONFIRMED)

        val expiredCount = expireTravelRequestsService.expireOverdue()
        assertThat(expiredCount).isGreaterThanOrEqualTo(1)

        val reloadedNew = requestRepository.findById(staffCreated.request.id)
        assertThat(reloadedNew?.status).isEqualTo(TravelRequestStatus.EXPIRED)

        val reloadedConfirmed = requestRepository.findById(confirmedOverdue.request.id)
        assertThat(reloadedConfirmed?.status).isEqualTo(TravelRequestStatus.CONFIRMED)
    }

    @Test
    fun `an unpublished service cannot receive a request`() {
        val now = Instant.now()
        val category =
            Category.create(
                CategoryId.generate(),
                "draft-only-${UUID.randomUUID().toString().take(6)}",
                LocalizedText("X", "س"),
                null,
                0,
                now,
            )
        categoryRepository.save(category)
        val draftService =
            Service.create(
                id = ServiceId.generate(),
                categoryId = category.id,
                name = LocalizedText("Draft service", "خدمة مسودة"),
                description = LocalizedText("Not published.", "غير منشورة."),
                fulfilmentModel = FulfilmentModel.DIRECT,
                providerId = null,
                confirmationType = ConfirmationType.INSTANT,
                cancellationPolicy = LocalizedText("N/A", "غير متاح"),
                pickupInfo = null,
                inclusions = null,
                exclusions = null,
                options =
                    listOf(
                        ServiceOption(
                            UUID.randomUUID(),
                            LocalizedText("Only option", "الخيار الوحيد"),
                            null,
                            4,
                            Money(BigDecimal("100.00"), "EGP"),
                            PriceBasis.PER_PERSON,
                        ),
                    ),
                media = emptyList(),
                now = now,
            )
        serviceRepository.save(draftService)

        val result =
            createTravelRequestService.create(
                createCommand(draftService.id, draftService.options.first().id, expectedPrice = draftService.options.first().price),
            )
        assertThat(result).isEqualTo(CreateTravelRequestResult.ServiceNotFound)
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_sharm_to_go_test")
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
