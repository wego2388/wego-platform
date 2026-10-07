package com.wego.toursoperator

import com.jayway.jsonpath.JsonPath
import com.wego.generated.jooq.tables.IdentityRole.IDENTITY_ROLE
import com.wego.generated.jooq.tables.IdentityRolePermission.IDENTITY_ROLE_PERMISSION
import com.wego.generated.jooq.tables.ToursOperatorDocumentPrint.TOURS_OPERATOR_DOCUMENT_PRINT
import com.wego.generated.jooq.tables.ToursOperatorTour.TOURS_OPERATOR_TOUR
import com.wego.generated.jooq.tables.ToursOperatorTourSlot.TOURS_OPERATOR_TOUR_SLOT
import com.wego.identity.application.PasswordHasher
import com.wego.identity.application.UserRepository
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import com.wego.identity.domain.UserStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * WEGO-016-OPS2-E over real PostgreSQL: supplier / driver / vehicle registries,
 * daily assignment with conflict prevention, revisions and audit, and the
 * driver sheet, supplier order and manifest PII rules.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ToursOperatorOpsRegistryHttpTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var passwordHasher: PasswordHasher

    @Autowired private lateinit var dsl: DSLContext

    private val password = "a-very-long-ops-registry-password-123"
    private lateinit var adminId: UUID

    @BeforeEach
    fun seed() {
        adminId = seedUser("ops-admin@example.com", setOf("platform-admin"))
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private class Reply(
        val status: Int,
        val body: String,
        val cacheControl: String?,
    ) {
        fun <T> read(path: String): T = JsonPath.read(body, path)
    }

    private fun seedUser(
        email: String,
        roles: Set<String>,
    ): UUID {
        userRepository.findByEmail(EmailAddress.of(email))?.let { return it.id.value }
        val user =
            User(
                id = UserId.generate(),
                email = EmailAddress.of(email),
                passwordHash = passwordHasher.hash(password),
                status = UserStatus.ACTIVE,
                roles = roles.map(RoleCode::of).toSet(),
                createdAt = Instant.now(),
                failedLoginCount = 0,
                lockedUntil = null,
            )
        userRepository.save(user)
        return user.id.value
    }

    private fun login(email: String): String {
        val body =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/v1/identity/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"$email","password":"$password"}"""),
                ).andReturn()
                .response.contentAsString
        return JsonPath.read(body, "$.token")
    }

    private fun tokenWith(vararg permissions: String): String {
        val roleCode =
            "ops-test-" + permissions.joinToString("-") { it.substringAfter("tours-operator.").replace(':', '-').replace('.', '-') }
        if (dsl.fetchOne(IDENTITY_ROLE, IDENTITY_ROLE.CODE.eq(roleCode)) == null) {
            dsl
                .insertInto(IDENTITY_ROLE)
                .set(IDENTITY_ROLE.CODE, roleCode)
                .set(IDENTITY_ROLE.DESCRIPTION, "Test role")
                .execute()
            for (permission in permissions) {
                dsl
                    .insertInto(IDENTITY_ROLE_PERMISSION)
                    .set(IDENTITY_ROLE_PERMISSION.ROLE_CODE, roleCode)
                    .set(IDENTITY_ROLE_PERMISSION.PERMISSION_CODE, permission)
                    .execute()
            }
        }
        val email = "$roleCode@example.com"
        seedUser(email, setOf(roleCode))
        return login(email)
    }

    private fun admin() = login("ops-admin@example.com")

    private fun call(
        method: String,
        path: String,
        token: String? = admin(),
        body: String? = null,
    ): Reply {
        var builder = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path)
        if (token != null) builder = builder.header("Authorization", "Bearer $token")
        if (body != null) builder = builder.contentType(MediaType.APPLICATION_JSON).content(body)
        val response = mockMvc.perform(builder).andReturn().response
        return Reply(response.status, response.contentAsString, response.getHeader("Cache-Control"))
    }

    private fun unique() = UUID.randomUUID().toString().take(8)

    private val staff = "/api/v1/tours-operator/staff"

    private fun supplierBody(
        code: String = "S" + unique().uppercase(),
        name: String = "Supplier ${unique()}",
        tourIds: List<UUID> = emptyList(),
        extra: String = "",
        revision: Int? = null,
        active: Boolean = true,
    ): String {
        val rev = revision?.let { ""","expectedRevision":$it""" } ?: ""
        return """{"code":"$code","name":"$name","serviceType":"QUAD_BUGGY_SAFARI","contactPerson":"Hamed","businessPhone":"+201015048400",
            "confirmationChannel":"WHATSAPP","noticeHours":24,"pricingBasis":"PER_PERSON","currency":"EGP","settlementCadence":"AFTER_EACH_TRIP",
            "paymentMethod":"INSTAPAY","cancellationTerms":"Deduction when the trip is cancelled or the guest does not show",
            "active":$active,"tourIds":[${tourIds.joinToString(",") { "\"$it\"" }}]$extra$rev}"""
    }

    private fun newSupplier(
        tourIds: List<UUID> = emptyList(),
        name: String = "Supplier ${unique()}",
    ): String =
        call("POST", "$staff/suppliers", body = supplierBody(tourIds = tourIds, name = name))
            .also {
                assertThat(it.status).isEqualTo(201)
            }.read("$.id")

    private fun driverBody(
        name: String = "Driver ${unique()}",
        licence: LocalDate = LocalDate.of(2035, 12, 31),
        active: Boolean = true,
        revision: Int? = null,
        phone: String = "+201028215951",
    ): String {
        val rev = revision?.let { ""","expectedRevision":$it""" } ?: ""
        return """{"name":"$name","workPhone":"$phone","engagementType":"PER_TRIP","licenceValidUntil":"$licence","active":$active$rev}"""
    }

    private fun newDriver(
        licence: LocalDate = LocalDate.of(2035, 12, 31),
        name: String = "Driver ${unique()}",
    ): String =
        call("POST", "$staff/drivers", body = driverBody(name = name, licence = licence))
            .also {
                assertThat(it.status).isEqualTo(201)
            }.read("$.id")

    private fun vehicleBody(
        label: String? = "Van ${unique()}",
        plate: String? = null,
        seats: Int = 14,
        ownership: String = "OWNED",
        lender: String? = null,
        active: Boolean = true,
        revision: Int? = null,
    ): String {
        val parts =
            listOfNotNull(
                label?.let { "\"label\":\"$it\"" },
                plate?.let { "\"plate\":\"$it\"" },
                "\"vehicleType\":\"VAN\"",
                "\"seats\":$seats",
                "\"ownership\":\"$ownership\"",
                lender?.let { "\"hiredFromSupplierId\":\"$it\"" },
                "\"active\":$active",
                revision?.let { "\"expectedRevision\":$it" },
            )
        return "{" + parts.joinToString(",") + "}"
    }

    private fun newVehicle(
        seats: Int = 14,
        label: String? = "Van ${unique()}",
    ): String =
        call("POST", "$staff/vehicles", body = vehicleBody(label = label, seats = seats))
            .also {
                assertThat(it.status).isEqualTo(201)
            }.read("$.id")

    private fun seedTour(): UUID {
        val tourId = UUID.randomUUID()
        dsl
            .insertInto(TOURS_OPERATOR_TOUR)
            .set(TOURS_OPERATOR_TOUR.ID, tourId)
            .set(TOURS_OPERATOR_TOUR.SLUG, "ops-test-${unique()}")
            .set(TOURS_OPERATOR_TOUR.CATEGORY, "DESERT")
            .set(TOURS_OPERATOR_TOUR.DURATION_TEXT, "4 hours")
            .set(TOURS_OPERATOR_TOUR.PRICE_ADULT_CENTS, 3500L)
            .set(TOURS_OPERATOR_TOUR.PRICE_CHILD_CENTS, 1750L)
            .set(TOURS_OPERATOR_TOUR.CAPACITY, 40)
            .set(TOURS_OPERATOR_TOUR.AVAILABLE_TIME_SLOTS, "MORNING")
            .set(TOURS_OPERATOR_TOUR.SORT_ORDER, 1)
            .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, true)
            .set(TOURS_OPERATOR_TOUR.CREATED_BY_USER_ID, adminId)
            .set(TOURS_OPERATOR_TOUR.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute()
        return tourId
    }

    private fun seedSlot(
        date: LocalDate,
        timeSlot: String = "MORNING",
        tourId: UUID = seedTour(),
        booked: Int = 0,
    ): Pair<UUID, UUID> {
        val slotId = UUID.randomUUID()
        dsl
            .insertInto(TOURS_OPERATOR_TOUR_SLOT)
            .set(TOURS_OPERATOR_TOUR_SLOT.ID, slotId)
            .set(TOURS_OPERATOR_TOUR_SLOT.TOUR_ID, tourId)
            .set(TOURS_OPERATOR_TOUR_SLOT.DATE, date)
            .set(TOURS_OPERATOR_TOUR_SLOT.TIME_SLOT, timeSlot)
            .set(TOURS_OPERATOR_TOUR_SLOT.CAPACITY, 40)
            .set(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT, booked)
            .set(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED, false)
            .set(TOURS_OPERATOR_TOUR_SLOT.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute()
        return tourId to slotId
    }

    /** Dates are far in the future so the "slot in the past" rule never interferes; each test owns a distinct day. */
    private var dayCounter = 0

    private fun day(): LocalDate = LocalDate.of(2031, 1, 1).plusDays((dayCounter++).toLong() % 300)

    private fun assign(
        slotId: UUID,
        driver: String? = null,
        vehicle: String? = null,
        suppliers: List<String> = emptyList(),
        revision: Int = 0,
        token: String? = admin(),
        note: String? = null,
    ): Reply {
        val parts =
            listOfNotNull(
                driver?.let { "\"driverId\":\"$it\"" },
                vehicle?.let { "\"vehicleId\":\"$it\"" },
                note?.let { "\"supplierNote\":$it" },
                "\"supplierIds\":[${suppliers.joinToString(",") { "\"$it\"" }}]",
                "\"expectedRevision\":$revision",
            )
        return call("PUT", "$staff/slots/$slotId/assignment", token, "{" + parts.joinToString(",") + "}")
    }

    private fun issueCodes(reply: Reply): List<String> = reply.read("$.issues[*].code")

    private fun bookOffice(
        slotId: UUID,
        name: String = "Ahmed Hassan",
        phone: String = "+201234567890",
        hotel: String = "Hilton Sharm Dreams",
        room: String = "312",
        adults: Int = 2,
        children: Int = 1,
        requests: String? = null,
    ): String {
        val special = requests?.let { ""","specialRequests":"$it"""" } ?: ""
        val reply =
            call(
                "POST",
                "$staff/bookings",
                body =
                    """{"clientRequestId":"${UUID.randomUUID()}","slotId":"$slotId","adultsCount":$adults,"childrenCount":$children,
                    "customer":{"fullName":"$name","phone":"$phone","nationality":"EG","email":"guest-secret@example.com"},
                    "hotelName":"$hotel","hotelRoom":"$room","locale":"en"$special}""",
            )
        assertThat(reply.status).isEqualTo(201)
        return reply.read("$.id")
    }

    private fun print(
        path: String,
        token: String? = admin(),
        language: String = "en",
    ) = call("POST", "/api/v1/tours-operator/documents/$path", token, """{"language":"$language"}""")

    // ── permissions and PII ─────────────────────────────────────────────────

    @Test
    fun `each registry needs its own permission and nothing is served without a token`() {
        val supplierOnly = tokenWith("tours-operator.supplier:manage")
        val fleetOnly = tokenWith("tours-operator.fleet:manage")
        val assignmentOnly = tokenWith("tours-operator.assignment:manage")
        val viewOnly = tokenWith("tours-operator.booking:view")
        val (_, slotId) = seedSlot(day())

        for (path in listOf(
            "suppliers",
            "drivers",
            "vehicles",
            "assignments?date=2031-01-01",
            "assignment-options?date=2031-01-01",
            "slots/$slotId/assignment",
        )) {
            assertThat(call("GET", "$staff/$path", token = null).status).isEqualTo(401)
            assertThat(call("GET", "$staff/$path", token = viewOnly).status).isEqualTo(403)
        }
        assertThat(call("GET", "$staff/suppliers", supplierOnly).status).isEqualTo(200)
        assertThat(call("GET", "$staff/drivers", supplierOnly).status).isEqualTo(403)
        assertThat(call("GET", "$staff/vehicles", supplierOnly).status).isEqualTo(403)
        assertThat(call("GET", "$staff/drivers", fleetOnly).status).isEqualTo(200)
        assertThat(call("GET", "$staff/vehicles", fleetOnly).status).isEqualTo(200)
        assertThat(call("GET", "$staff/suppliers", fleetOnly).status).isEqualTo(403)
        assertThat(call("GET", "$staff/suppliers", assignmentOnly).status).isEqualTo(403)
        assertThat(call("GET", "$staff/drivers", assignmentOnly).status).isEqualTo(403)
        assertThat(call("POST", "$staff/suppliers", assignmentOnly, supplierBody()).status).isEqualTo(403)
        assertThat(call("POST", "$staff/drivers", supplierOnly, driverBody()).status).isEqualTo(403)
        assertThat(call("POST", "$staff/vehicles", supplierOnly, vehicleBody()).status).isEqualTo(403)
        assertThat(assign(slotId, suppliers = emptyList(), driver = newDriver(), token = fleetOnly).status).isEqualTo(403)
        assertThat(call("GET", "$staff/slots/$slotId/assignment", assignmentOnly).status).isEqualTo(200)
        assertThat(call("GET", "$staff/assignments?date=2031-01-01", assignmentOnly).status).isEqualTo(200)
    }

    @Test
    fun `phones reach only the registry permissions and responses are never cached`() {
        val phone = "+201028215951"
        val supplierId = newSupplier()
        val driverId = newDriver()
        val assignmentOnly = tokenWith("tours-operator.assignment:manage")
        val (_, slotId) = seedSlot(day())
        assertThat(assign(slotId, driver = driverId, suppliers = listOf(supplierId)).status).isEqualTo(200)

        val driversReply = call("GET", "$staff/drivers", tokenWith("tours-operator.fleet:manage"))
        assertThat(driversReply.body).contains(phone)
        assertThat(driversReply.cacheControl).contains("no-store")
        val suppliersReply = call("GET", "$staff/suppliers", tokenWith("tours-operator.supplier:manage"))
        assertThat(suppliersReply.body).contains("+201015048400")
        assertThat(suppliersReply.cacheControl).contains("no-store")

        for (path in listOf(
            "slots/$slotId/assignment",
            "assignment-options?date=2031-01-01",
            "assignments?date=${dayOf(slotId)}",
            "slots/$slotId/assignment/history",
        )) {
            val reply = call("GET", "$staff/$path", assignmentOnly)
            assertThat(reply.status).isEqualTo(200)
            assertThat(
                reply.body,
            ).doesNotContain(
                "1028215951",
            ).doesNotContain("1015048400")
                .doesNotContain("Phone")
                .doesNotContain("workPhone")
                .doesNotContain("businessPhone")
            assertThat(reply.cacheControl).contains("no-store")
        }
    }

    private fun dayOf(slotId: UUID): LocalDate =
        dsl.fetchOne("SELECT date FROM wego.tours_operator_tour_slot WHERE id = ?", slotId)!!.get(0, LocalDate::class.java)

    // ── supplier registry ────────────────────────────────────────────────────

    @Test
    fun `supplier create validates, normalises the code and refuses duplicates`() {
        val code = "SUP-" + unique().uppercase().take(6)
        val created = call("POST", "$staff/suppliers", body = supplierBody(code = code.lowercase()))
        assertThat(created.status).describedAs(created.body).isEqualTo(201)
        assertThat(created.read<String>("$.code")).isEqualTo(code)
        assertThat(created.read<Int>("$.revision")).isEqualTo(1)
        assertThat(created.read<Boolean>("$.active")).isTrue()
        assertThat(created.cacheControl).contains("no-store")

        val duplicate = call("POST", "$staff/suppliers", body = supplierBody(code = code))
        assertThat(duplicate.status).isEqualTo(409)
        assertThat(duplicate.read<String>("$.error")).isEqualTo("supplier_code_taken")

        val minimal =
            call(
                "POST",
                "$staff/suppliers",
                body = """{"code":"MIN${unique().uppercase().take(4)}","name":"Only the basics","serviceType":"BOAT"}""",
            )
        assertThat(minimal.status).describedAs(minimal.body).isEqualTo(201)
        assertThat(minimal.read<Any?>("$.businessPhone")).isNull()
        assertThat(minimal.read<Any?>("$.noticeHours")).isNull()

        for (
        bad in
        listOf(
            supplierBody(code = "1BAD"),
            supplierBody(code = "A"),
            supplierBody(name = " "),
            supplierBody(extra = ""","noticeHoursX":1"""),
            supplierBody().replace("\"WHATSAPP\"", "\"SMOKE_SIGNAL\""),
            supplierBody().replace("+201015048400", "call me"),
            supplierBody().replace("\"noticeHours\":24", "\"noticeHours\":9999"),
            supplierBody().replace("\"EGP\"", "\"USD\""),
        )
        ) {
            assertThat(call("POST", "$staff/suppliers", body = bad).status).describedAs(bad).isEqualTo(400)
        }
        assertThat(
            call("POST", "$staff/suppliers", body = supplierBody(tourIds = listOf(UUID.randomUUID()))).read<String>("$.error"),
        ).isEqualTo("tour_not_found")
    }

    @Test
    fun `supplier update needs the current revision, links tours and deactivates instead of deleting`() {
        val tourA = seedTour()
        val tourB = seedTour()
        val created = call("POST", "$staff/suppliers", body = supplierBody(tourIds = listOf(tourA)))
        val id = created.read<String>("$.id")
        val code = created.read<String>("$.code")
        assertThat(created.read<List<String>>("$.tourIds")).containsExactly(tourA.toString())

        assertThat(call("PUT", "$staff/suppliers/$id", body = supplierBody(code = code)).status).isEqualTo(400)
        val moved = call("PUT", "$staff/suppliers/$id", body = supplierBody(code = code, tourIds = listOf(tourB), revision = 1))
        assertThat(moved.status).isEqualTo(200)
        assertThat(moved.read<Int>("$.revision")).isEqualTo(2)
        assertThat(moved.read<List<String>>("$.tourIds")).containsExactly(tourB.toString())

        val stale = call("PUT", "$staff/suppliers/$id", body = supplierBody(code = code, revision = 1))
        assertThat(stale.status).isEqualTo(409)
        assertThat(stale.read<String>("$.error")).isEqualTo("revision_conflict")
        assertThat(stale.read<Int>("$.currentRevision")).isEqualTo(2)

        val off =
            call("PUT", "$staff/suppliers/$id", body = supplierBody(code = code, tourIds = listOf(tourB), revision = 2, active = false))
        assertThat(off.read<Boolean>("$.active")).isFalse()
        assertThat(call("GET", "$staff/suppliers?active=true").read<List<String>>("$[*].id")).doesNotContain(id)
        assertThat(call("GET", "$staff/suppliers?active=false").read<List<String>>("$[*].id")).contains(id)
        assertThat(call("DELETE", "$staff/suppliers/$id").status).isNotEqualTo(200)
        assertThat(call("GET", "$staff/suppliers/$id").status).isEqualTo(200)
        assertThat(call("GET", "$staff/suppliers/${UUID.randomUUID()}").status).isEqualTo(404)
        assertThat(call("PUT", "$staff/suppliers/${UUID.randomUUID()}", body = supplierBody(revision = 1)).status).isEqualTo(404)
        // changing the code to another supplier's code is refused
        val other = call("POST", "$staff/suppliers", body = supplierBody()).read<String>("$.code")
        assertThat(
            call("PUT", "$staff/suppliers/$id", body = supplierBody(code = other, revision = 3, active = false)).read<String>("$.error"),
        ).isEqualTo("supplier_code_taken")
    }

    // ── drivers and vehicles ─────────────────────────────────────────────────

    @Test
    fun `driver registry validates, versions and deactivates`() {
        val created = call("POST", "$staff/drivers", body = driverBody(name = "Amr", licence = LocalDate.of(2027, 12, 31)))
        assertThat(created.status).describedAs(created.body).isEqualTo(201)
        val id = created.read<String>("$.id")
        assertThat(created.read<String>("$.licenceValidUntil")).isEqualTo("2027-12-31")

        for (
        bad in
        listOf(
            driverBody(name = ""),
            driverBody(phone = "abc"),
            driverBody().replace("PER_TRIP", "WHENEVER"),
            """{"name":"No licence","engagementType":"PER_TRIP"}""",
            driverBody().replace("2035-12-31", "31/12/2035"),
        )
        ) {
            assertThat(call("POST", "$staff/drivers", body = bad).status).describedAs(bad).isEqualTo(400)
        }
        val noPhone =
            call("POST", "$staff/drivers", body = """{"name":"No phone","engagementType":"OTHER","licenceValidUntil":"2030-01-01"}""")
        assertThat(noPhone.status).describedAs(noPhone.body).isEqualTo(201)

        assertThat(
            call("PUT", "$staff/drivers/$id", body = driverBody(revision = 1, active = false, name = "Amr")).read<Int>("$.revision"),
        ).isEqualTo(2)
        assertThat(call("PUT", "$staff/drivers/$id", body = driverBody(revision = 1, name = "Amr")).status).isEqualTo(409)
        assertThat(call("GET", "$staff/drivers?active=false").read<List<String>>("$[*].id")).contains(id)
        assertThat(call("GET", "$staff/drivers?active=true").read<List<String>>("$[*].id")).doesNotContain(id)
    }

    @Test
    fun `vehicles work while the registry is empty and enforce identity, seats and the lender rule`() {
        // The owner has not supplied vehicles: an empty registry must list, offer and assign without one.
        dsl.execute("DELETE FROM wego.tours_operator_slot_assignment")
        dsl.execute("DELETE FROM wego.tours_operator_vehicle")
        assertThat(call("GET", "$staff/vehicles").status).isEqualTo(200)
        assertThat(call("GET", "$staff/vehicles").read<List<Any>>("$")).isEmpty()
        assertThat(call("GET", "$staff/assignment-options?date=2031-02-01").read<List<Any>>("$.vehicles")).isEmpty()
        val (_, slotId) = seedSlot(day())
        assertThat(assign(slotId, driver = newDriver()).status).isEqualTo(200)

        val plate = "ABC ${1000 + Math.floorMod(unique().hashCode(), 8999)}"
        val created = call("POST", "$staff/vehicles", body = vehicleBody(label = null, plate = plate, seats = 7))
        assertThat(created.status).isEqualTo(201)
        assertThat(created.read<String>("$.display")).isEqualTo(plate)
        assertThat(
            call("POST", "$staff/vehicles", body = vehicleBody(label = "other", plate = plate.lowercase())).read<String>("$.error"),
        ).isEqualTo("vehicle_plate_taken")

        for (
        bad in
        listOf(
            vehicleBody(label = null, plate = null),
            vehicleBody(seats = 0),
            vehicleBody(seats = 81),
            vehicleBody(ownership = "STOLEN"),
            vehicleBody(label = " "),
        )
        ) {
            assertThat(call("POST", "$staff/vehicles", body = bad).status).describedAs(bad).isEqualTo(400)
        }
        // only a hired vehicle has a lender; the lender must exist
        assertThat(call("POST", "$staff/vehicles", body = vehicleBody(ownership = "OWNED", lender = newSupplier())).status).isEqualTo(400)
        assertThat(
            call(
                "POST",
                "$staff/vehicles",
                body = vehicleBody(ownership = "HIRED", lender = UUID.randomUUID().toString()),
            ).read<String>("$.error"),
        ).isEqualTo("supplier_not_found")
        val lender = newSupplier()
        val hired = call("POST", "$staff/vehicles", body = vehicleBody(ownership = "HIRED", lender = lender))
        assertThat(hired.status).isEqualTo(201)
        assertThat(hired.read<String>("$.hiredFromSupplierId")).isEqualTo(lender)
        val id = created.read<String>("$.id")
        assertThat(
            call(
                "PUT",
                "$staff/vehicles/$id",
                body = vehicleBody(label = null, plate = plate, seats = 9, revision = 1),
            ).read<Int>("$.seats"),
        ).isEqualTo(9)
        assertThat(call("PUT", "$staff/vehicles/$id", body = vehicleBody(label = null, plate = plate, revision = 1)).status).isEqualTo(409)
    }

    // ── assignment ───────────────────────────────────────────────────────────

    @Test
    fun `assign, change and clear a departure with revisions and a complete audit trail`() {
        val tour = seedTour()
        val (_, slotId) = seedSlot(day(), tourId = tour)
        val supplier = newSupplier(tourIds = listOf(tour))
        val driver = newDriver()
        val vehicle = newVehicle()

        val view = call("GET", "$staff/slots/$slotId/assignment")
        assertThat(view.read<Any?>("$.assignment")).isNull()
        assertThat(view.read<List<String>>("$.suggestedSuppliers[*].id")).containsExactly(supplier)

        assertThat(assign(slotId, revision = 0).status).isEqualTo(400)
        assertThat(assign(UUID.randomUUID(), driver = driver).status).isEqualTo(404)

        val first = assign(slotId, driver = driver, suppliers = listOf(supplier))
        assertThat(first.status).isEqualTo(200)
        assertThat(first.read<Int>("$.assignment.revision")).isEqualTo(1)
        assertThat(first.read<String>("$.assignment.driver.id")).isEqualTo(driver)
        assertThat(first.read<Any?>("$.assignment.vehicle")).isNull()
        assertThat(first.read<String>("$.assignment.assignedByEmail")).isEqualTo("ops-admin@example.com")
        assertThat(first.cacheControl).contains("no-store")

        // a second writer holding revision 0 is refused; nothing changes
        val stale = assign(slotId, driver = driver, vehicle = vehicle, revision = 0)
        assertThat(stale.status).isEqualTo(409)
        assertThat(stale.read<String>("$.error")).isEqualTo("revision_conflict")
        assertThat(stale.read<Int>("$.currentRevision")).isEqualTo(1)

        val second = assign(slotId, driver = driver, vehicle = vehicle, suppliers = listOf(supplier), revision = 1)
        assertThat(second.read<Int>("$.assignment.revision")).isEqualTo(2)
        assertThat(second.read<String>("$.assignment.vehicle.id")).isEqualTo(vehicle)

        assertThat(call("DELETE", "$staff/slots/$slotId/assignment?expectedRevision=1").status).isEqualTo(409)
        assertThat(call("DELETE", "$staff/slots/$slotId/assignment?expectedRevision=0").status).isEqualTo(400)
        val cleared = call("DELETE", "$staff/slots/$slotId/assignment?expectedRevision=2")
        assertThat(cleared.status).isEqualTo(200)
        assertThat(cleared.read<Any?>("$.assignment")).isNull()
        assertThat(call("DELETE", "$staff/slots/$slotId/assignment?expectedRevision=2").status).isEqualTo(404)
        // after a clear the departure is free again; a stale revision cannot resurrect the old row
        assertThat(assign(slotId, driver = driver, revision = 2).status).isEqualTo(409)
        assertThat(assign(slotId, driver = driver, revision = 0).status).isEqualTo(200)

        val history = call("GET", "$staff/slots/$slotId/assignment/history")
        assertThat(history.read<List<String>>("$[*].action")).containsExactly("ASSIGNED", "CHANGED", "CLEARED", "ASSIGNED")
        assertThat(history.read<List<String>>("$[*].actorEmail").distinct()).containsExactly("ops-admin@example.com")
        assertThat(history.read<List<Int>>("$[*].revision")).containsExactly(1, 2, 3, 1)
        assertThat(call("GET", "$staff/slots/${UUID.randomUUID()}/assignment/history").status).isEqualTo(404)

        // the audit trail is append-only
        assertThatThrownBy {
            dsl.execute("UPDATE wego.tours_operator_assignment_audit SET revision = 99 WHERE slot_id = ?", slotId)
        }.hasMessageContaining("append-only")
        assertThatThrownBy {
            dsl.execute("DELETE FROM wego.tours_operator_assignment_audit WHERE slot_id = ?", slotId)
        }.hasMessageContaining("append-only")
        assertThatThrownBy { dsl.execute("TRUNCATE wego.tours_operator_assignment_audit") }.hasMessageContaining("append-only")
    }

    @Test
    fun `the same driver or vehicle cannot serve two departures in the same window of a day`() {
        val date = day()
        val (tourA, slotA) = seedSlot(date, "MORNING")
        val (tourB, slotB) = seedSlot(date, "MORNING")
        val driver = newDriver()
        val vehicle = newVehicle()
        assertThat(assign(slotA, driver = driver, vehicle = vehicle).status).isEqualTo(200)

        val driverClash = assign(slotB, driver = driver)
        assertThat(driverClash.status).isEqualTo(409)
        assertThat(driverClash.read<String>("$.error")).isEqualTo("assignment_conflict")
        assertThat(driverClash.read<String>("$.conflicts[0].kind")).isEqualTo("DRIVER")
        assertThat(driverClash.read<String>("$.conflicts[0].resourceId")).isEqualTo(driver)
        assertThat(driverClash.read<String>("$.conflicts[0].otherSlotId")).isEqualTo(slotA.toString())
        assertThat(driverClash.read<String>("$.conflicts[0].otherTourId")).isEqualTo(tourA.toString())
        assertThat(driverClash.read<String>("$.conflicts[0].otherTimeSlot")).isEqualTo("MORNING")

        val both = assign(slotB, driver = driver, vehicle = vehicle)
        assertThat(both.read<List<String>>("$.conflicts[*].kind")).containsExactlyInAnyOrder("DRIVER", "VEHICLE")
        val vehicleClash = assign(slotB, driver = newDriver(), vehicle = vehicle)
        assertThat(vehicleClash.status).isEqualTo(409)
        assertThat(vehicleClash.read<List<String>>("$.conflicts[*].kind")).containsExactly("VEHICLE")
        // nothing was saved for the refused attempts
        assertThat(call("GET", "$staff/slots/$slotB/assignment").read<Any?>("$.assignment")).isNull()

        // re-saving the same departure never conflicts with itself
        assertThat(assign(slotA, driver = driver, vehicle = vehicle, revision = 1).status).isEqualTo(200)
        // a different day is free
        val (_, otherDay) = seedSlot(date.plusDays(1), "MORNING", tourB)
        assertThat(assign(otherDay, driver = driver, vehicle = vehicle).status).isEqualTo(200)
        // freeing the driver on slot A lets slot B take it
        assertThat(assign(slotA, vehicle = vehicle, revision = 2).status).isEqualTo(200)
        assertThat(assign(slotB, driver = driver).status).isEqualTo(200)
    }

    @Test
    fun `adjacent windows only warn, distant windows are silent`() {
        val date = day()
        val driver = newDriver()
        val vehicle = newVehicle()
        val (_, morning) = seedSlot(date, "MORNING")
        val (_, afternoon) = seedSlot(date, "AFTERNOON")
        val (_, sunset) = seedSlot(date, "SUNSET")
        assertThat(assign(morning, driver = driver, vehicle = vehicle).status).isEqualTo(200)
        val next = assign(afternoon, driver = driver, vehicle = vehicle)
        assertThat(next.status).isEqualTo(200)
        assertThat(issueCodes(next)).containsExactlyInAnyOrder("driver_back_to_back", "vehicle_back_to_back")
        assertThat(next.read<List<Boolean>>("$.issues[*].blocking")).containsOnly(false)
        // SUNSET is two windows after MORNING (and adjacent only to AFTERNOON)
        val sun = assign(sunset, driver = driver)
        assertThat(sun.status).isEqualTo(200)
        assertThat(issueCodes(sun)).containsExactly("driver_back_to_back")
        val (_, sunrise) = seedSlot(date, "SUNRISE")
        val early = assign(sunrise, vehicle = vehicle)
        assertThat(issueCodes(early)).containsExactly("vehicle_back_to_back")
        val farVehicle = newVehicle()
        val (_, other) = seedSlot(date.plusDays(2), "SUNRISE")
        assertThat(issueCodes(assign(other, vehicle = farVehicle))).isEmpty()
    }

    @Test
    fun `concurrent assignments of one driver to overlapping departures let exactly one win`() {
        val date = day()
        val driver = newDriver()
        val vehicle = newVehicle()
        val slots = (1..6).map { seedSlot(date, "MORNING").second }
        val tokens = (1..6).map { admin() }
        val pool = Executors.newFixedThreadPool(6)
        val start = CountDownLatch(1)
        val results =
            slots.indices.map { i ->
                pool.submit(
                    Callable {
                        start.await()
                        assign(slots[i], driver = driver, vehicle = if (i % 2 == 0) vehicle else null, token = tokens[i]).status
                    },
                )
            }
        start.countDown()
        val statuses = results.map { it.get() }
        pool.shutdown()
        // all six want the same driver in the same window: one 200, five 409; the vehicle never doubles up either
        assertThat(statuses.count { it == 200 }).isEqualTo(1)
        assertThat(statuses.count { it == 409 }).isEqualTo(5)
        assertThat(
            dsl.fetchValue("SELECT count(*) FROM wego.tours_operator_slot_assignment WHERE driver_id = ?::uuid", driver),
        ).isEqualTo(1L)
    }

    @Test
    fun `concurrent writers of one departure holding the same revision let exactly one win`() {
        val (_, slotId) = seedSlot(day())
        val drivers = (1..5).map { newDriver() }
        val tokens = (1..5).map { admin() }
        val pool = Executors.newFixedThreadPool(5)
        val start = CountDownLatch(1)
        val results =
            drivers.indices.map { i ->
                pool.submit(
                    Callable {
                        start.await()
                        assign(slotId, driver = drivers[i], token = tokens[i]).status
                    },
                )
            }
        start.countDown()
        val statuses = results.map { it.get() }
        pool.shutdown()
        assertThat(statuses.sorted()).containsExactly(200, 409, 409, 409, 409)
        assertThat(call("GET", "$staff/slots/$slotId/assignment/history").read<List<String>>("$[*].action")).containsExactly("ASSIGNED")
    }

    @Test
    fun `driver rules refuse inactive and expired licences and flag one that ends soon`() {
        val date = day()
        val (_, slot) = seedSlot(date)
        assertThat(assign(slot, driver = UUID.randomUUID().toString()).read<String>("$.error")).isEqualTo("driver_not_found")
        assertThat(assign(slot, vehicle = UUID.randomUUID().toString()).read<String>("$.error")).isEqualTo("vehicle_not_found")
        assertThat(assign(slot, suppliers = listOf(UUID.randomUUID().toString())).read<String>("$.error")).isEqualTo("supplier_not_found")

        val expired = newDriver(licence = date.minusDays(1))
        val refused = assign(slot, driver = expired)
        assertThat(refused.status).isEqualTo(422)
        assertThat(refused.read<String>("$.error")).isEqualTo("driver_licence_expired")
        assertThat(call("GET", "$staff/slots/$slot/assignment").read<Any?>("$.assignment")).isNull()

        // the licence's last valid day still counts; it is flagged because it ends within 30 days
        val lastDay = newDriver(licence = date)
        val ok = assign(slot, driver = lastDay)
        assertThat(ok.status).isEqualTo(200)
        assertThat(issueCodes(ok)).containsExactly("driver_licence_expires_soon")

        val inactive = newDriver()
        val current = call("GET", "$staff/drivers/$inactive").read<Int>("$.revision")
        call("PUT", "$staff/drivers/$inactive", body = driverBody(revision = current, active = false))
        val (_, other) = seedSlot(date, "SUNSET")
        val inactiveReply = assign(other, driver = inactive)
        assertThat(inactiveReply.status).isEqualTo(422)
        assertThat(inactiveReply.read<String>("$.error")).isEqualTo("driver_inactive")

        val vehicle = newVehicle()
        call("PUT", "$staff/vehicles/$vehicle", body = vehicleBody(label = "Retired", revision = 1, active = false))
        assertThat(assign(other, vehicle = vehicle).read<String>("$.error")).isEqualTo("vehicle_inactive")
        val supplier = newSupplier()
        call(
            "PUT",
            "$staff/suppliers/$supplier",
            body = supplierBody(code = call("GET", "$staff/suppliers/$supplier").read("$.code"), revision = 1, active = false),
        )
        assertThat(assign(other, suppliers = listOf(supplier)).read<String>("$.error")).isEqualTo("supplier_inactive")
    }

    @Test
    fun `a driver who is deactivated or loses the licence after assignment is flagged on the day view`() {
        val date = day()
        val (_, slot) = seedSlot(date)
        val driver = newDriver()
        assertThat(assign(slot, driver = driver).status).isEqualTo(200)
        call("PUT", "$staff/drivers/$driver", body = driverBody(revision = 1, active = false, licence = date.minusDays(3)))
        val flagged = call("GET", "$staff/assignments?date=$date")
        val row = JsonPath.read<List<Map<String, Any>>>(flagged.body, "$[?(@.slotId=='$slot')]").single()

        @Suppress("UNCHECKED_CAST")
        val codes = (row["issues"] as List<Map<String, Any>>).map { it["code"] }
        assertThat(codes).contains("driver_inactive", "driver_licence_expired")
    }

    @Test
    fun `seat shortfall is a warning that does not block, and suppliers off the tour are flagged`() {
        val date = day()
        val tour = seedTour()
        val (_, slot) = seedSlot(date, tourId = tour, booked = 9)
        val small = newVehicle(seats = 4)
        val offTour = newSupplier()
        val onTour = newSupplier(tourIds = listOf(tour))
        val reply = assign(slot, vehicle = small, suppliers = listOf(offTour, onTour))
        assertThat(reply.status).isEqualTo(200)
        assertThat(issueCodes(reply)).containsExactlyInAnyOrder("vehicle_seats_below_guests", "supplier_not_linked_to_tour")
        assertThat(reply.read<List<Int>>("$.issues[?(@.code=='vehicle_seats_below_guests')].value")).containsExactly(9)
        assertThat(reply.read<List<Int>>("$.issues[?(@.code=='vehicle_seats_below_guests')].limit")).containsExactly(4)
        assertThat(reply.read<Int>("$.guests")).isEqualTo(9)
        val fits = newVehicle(seats = 9)
        assertThat(issueCodes(assign(slot, vehicle = fits, suppliers = listOf(onTour), revision = 1))).isEmpty()
    }

    @Test
    fun `a departure in the past cannot be assigned or cleared`() {
        val (_, slot) = seedSlot(LocalDate.now().minusDays(2))
        val reply = assign(slot, driver = newDriver())
        assertThat(reply.status).isEqualTo(422)
        assertThat(reply.read<String>("$.error")).isEqualTo("slot_in_past")
        assertThat(call("DELETE", "$staff/slots/$slot/assignment?expectedRevision=1").status).isIn(404, 422)
    }

    @Test
    fun `the day view lists departures with guests or an assignment and options carry names only`() {
        val date = day()
        val (_, busy) = seedSlot(date, "MORNING", booked = 3)
        val (_, empty) = seedSlot(date, "SUNSET")
        val (_, assignedEmpty) = seedSlot(date, "AFTERNOON")
        assertThat(assign(assignedEmpty, driver = newDriver()).status).isEqualTo(200)
        val day = call("GET", "$staff/assignments?date=$date")
        val ids = day.read<List<String>>("$[*].slotId")
        assertThat(ids).contains(busy.toString(), assignedEmpty.toString()).doesNotContain(empty.toString())
        assertThat(call("GET", "$staff/assignments").status).isEqualTo(400)
        assertThat(call("GET", "$staff/assignments?date=nope").status).isEqualTo(400)

        val options = call("GET", "$staff/assignment-options?date=$date")
        assertThat(options.read<List<Boolean>>("$.drivers[*].licenceCoversDate")).isNotEmpty()
    }

    // ── documents ────────────────────────────────────────────────────────────

    @Test
    fun `the pickup manifest shows the assigned driver and vehicle and a reprint after an assignment change is REVISED`() {
        val (_, slot) = seedSlot(day())
        bookOffice(slot, hotel = "Hilton Sharm Dreams")
        val first = print("slots/$slot/pickup-manifest")
        assertThat(first.read<Any?>("$.data.driver")).isNull()
        assertThat(first.read<Any?>("$.data.vehicle")).isNull()

        val driver = newDriver(name = "Captain Amr")
        val vehicle = newVehicle(label = "Hiace 14")
        assertThat(assign(slot, driver = driver, vehicle = vehicle).status).isEqualTo(200)
        val second = print("slots/$slot/pickup-manifest")
        assertThat(second.read<String>("$.data.driver")).isEqualTo("Captain Amr")
        assertThat(second.read<String>("$.data.vehicle")).isEqualTo("Hiace 14")
        assertThat(second.read<Boolean>("$.document.revised")).isTrue()
        assertThat(second.read<Boolean>("$.document.copy")).isFalse()
        assertThat(second.body).doesNotContain("workPhone").doesNotContain("028215951")
        val third = print("slots/$slot/pickup-manifest")
        assertThat(third.read<Boolean>("$.document.copy")).isTrue()
        assertThat(third.read<Boolean>("$.document.revised")).isFalse()
        // a driver change alone revises it again
        assertThat(assign(slot, driver = newDriver(name = "Captain Yasser"), vehicle = vehicle, revision = 1).status).isEqualTo(200)
        assertThat(print("slots/$slot/pickup-manifest").read<Boolean>("$.document.revised")).isTrue()
    }

    @Test
    fun `the driver sheet needs a driver, lists parties by hotel and carries no phone, e-mail or money`() {
        val (_, slot) = seedSlot(day())
        bookOffice(
            slot,
            name = "Ahmed Hassan",
            hotel = "Hilton Sharm Dreams",
            room = "312",
            phone = "+201234567890",
            requests = "Child seat please",
        )
        bookOffice(
            slot,
            name = "Maria Rossi",
            hotel = "Hilton Sharm Dreams",
            room = "101",
            phone = "+393331234567",
            adults = 1,
            children = 0,
        )
        bookOffice(slot, name = "John Smith", hotel = "Domina Coral Bay", room = "7", phone = "+447700900123", adults = 3, children = 0)

        val noDriver = print("slots/$slot/driver-sheet")
        assertThat(noDriver.status).isEqualTo(409)
        assertThat(noDriver.read<String>("$.error")).isEqualTo("no_driver_assigned")
        assertThat(
            dsl.fetchValue(
                "SELECT count(*) FROM wego.tours_operator_document_print WHERE document_type = 'DRIVER_SHEET' AND subject_key = ?",
                slot.toString(),
            ),
        ).isEqualTo(0L)

        assertThat(assign(slot, driver = newDriver(name = "Captain Amr")).status).isEqualTo(200)
        val sheet = print("slots/$slot/driver-sheet")
        assertThat(sheet.status).isEqualTo(200)
        assertThat(sheet.cacheControl).contains("no-store")
        assertThat(sheet.read<String>("$.document.number")).startsWith("DRV-")
        assertThat(sheet.read<String>("$.document.type")).isEqualTo("DRIVER_SHEET")
        assertThat(sheet.read<String>("$.data.driverName")).isEqualTo("Captain Amr")
        assertThat(sheet.read<Any?>("$.data.vehicle")).isNull()
        assertThat(sheet.read<Int>("$.data.totalGuests")).isEqualTo(7)
        assertThat(sheet.read<List<String>>("$.data.stops[*].hotelName")).containsExactly("Domina Coral Bay", "Hilton Sharm Dreams")
        assertThat(sheet.read<List<Int>>("$.data.stops[*].order")).containsExactly(1, 2)
        assertThat(sheet.read<List<String>>("$.data.stops[1].parties[*].leadName")).containsExactly("Maria Rossi", "Ahmed Hassan")
        assertThat(sheet.read<List<String>>("$.data.stops[1].parties[*].hotelRoom")).containsExactly("101", "312")
        assertThat(sheet.body)
            .doesNotContain("234567890")
            .doesNotContain("3331234567")
            .doesNotContain("7700900123")
            .doesNotContain("guest-secret")
            .doesNotContain("phone")
            .doesNotContain("Phone")
            .doesNotContain("price")
            .doesNotContain("Price")
            .doesNotContain("amount")
            .doesNotContain("payment")
            .doesNotContain("totalPrice")
            .doesNotContain("Child seat")

        // reprints: unchanged -> COPY, changed -> REVISED; number is kept
        val copy = print("slots/$slot/driver-sheet", language = "ar")
        assertThat(copy.read<Boolean>("$.document.copy")).isTrue()
        assertThat(copy.read<String>("$.document.number")).isEqualTo(sheet.read<String>("$.document.number"))
        bookOffice(slot, name = "Late Booker", hotel = "Domina Coral Bay", room = "9", adults = 1, children = 0)
        val revised = print("slots/$slot/driver-sheet")
        assertThat(revised.read<Boolean>("$.document.revised")).isTrue()
        assertThat(revised.read<Int>("$.data.totalGuests")).isEqualTo(8)
        assertThat(
            dsl.fetchValue(
                "SELECT count(*) FROM wego.tours_operator_document_print WHERE document_type = 'DRIVER_SHEET' AND subject_key = ?",
                slot.toString(),
            ),
        ).isEqualTo(3L)
    }

    @Test
    fun `the driver sheet shows the vehicle and a vehicle change revises it`() {
        val (_, slot) = seedSlot(day())
        bookOffice(slot)
        val driver = newDriver()
        assertThat(assign(slot, driver = driver).status).isEqualTo(200)
        val first = print("slots/$slot/driver-sheet")
        assertThat(first.read<Any?>("$.data.vehicle")).isNull()
        assertThat(assign(slot, driver = driver, vehicle = newVehicle(seats = 12, label = "Hiace 12"), revision = 1).status).isEqualTo(200)
        val second = print("slots/$slot/driver-sheet")
        assertThat(second.read<String>("$.data.vehicle.display")).isEqualTo("Hiace 12")
        assertThat(second.read<Int>("$.data.vehicle.seats")).isEqualTo(12)
        assertThat(second.read<Boolean>("$.document.revised")).isTrue()
    }

    @Test
    fun `the supplier order needs an assigned supplier and carries no customer data and no price`() {
        val tour = seedTour()
        val (_, slot) = seedSlot(day(), tourId = tour)
        bookOffice(
            slot,
            name = "Ahmed Hassan",
            phone = "+201234567890",
            adults = 2,
            children = 1,
            requests = "Diabetic, call my wife on 01099887766",
        )
        bookOffice(slot, name = "Maria Rossi", phone = "+393331234567", adults = 1, children = 0)
        val supplierCall = call("POST", "$staff/suppliers", body = supplierBody(name = "Panorama Quad", tourIds = listOf(tour)))
        val supplier = supplierCall.read<String>("$.id")
        val other = newSupplier()

        val notAssigned = print("slots/$slot/suppliers/$supplier/supplier-order")
        assertThat(notAssigned.status).isEqualTo(409)
        assertThat(notAssigned.read<String>("$.error")).isEqualTo("supplier_not_assigned")
        assertThat(assign(slot, suppliers = listOf(supplier)).status).isEqualTo(200)
        assertThat(print("slots/$slot/suppliers/$other/supplier-order").status).isEqualTo(409)
        assertThat(print("slots/${UUID.randomUUID()}/suppliers/$supplier/supplier-order").status).isEqualTo(404)

        val order = print("slots/$slot/suppliers/$supplier/supplier-order")
        assertThat(order.status).isEqualTo(200)
        assertThat(order.read<String>("$.document.number")).startsWith("SUP-")
        assertThat(order.read<String>("$.data.supplier.name")).isEqualTo("Panorama Quad")
        assertThat(order.read<String>("$.data.supplier.contactPerson")).isEqualTo("Hamed")
        assertThat(order.read<String>("$.data.supplier.confirmationChannel")).isEqualTo("WHATSAPP")
        assertThat(order.read<Int>("$.data.supplier.noticeHours")).isEqualTo(24)
        assertThat(order.read<Int>("$.data.totalGuests")).isEqualTo(4)
        assertThat(order.read<Int>("$.data.adults")).isEqualTo(3)
        assertThat(order.read<Int>("$.data.children")).isEqualTo(1)
        assertThat(order.read<Any?>("$.data.supplierNote")).isNull()
        assertThat(order.body)
            .doesNotContain("specialRequests")
            .doesNotContain("Diabetic")
            .doesNotContain("01099887766")
            .doesNotContain(
                "Ahmed",
            ).doesNotContain("Hassan")
            .doesNotContain("Maria")
            .doesNotContain("234567890")
            .doesNotContain("3331234567")
            .doesNotContain("guest-secret")
            .doesNotContain("businessPhone")
            .doesNotContain("1015048400")
            .doesNotContain(
                "price",
            ).doesNotContain("Price")
            .doesNotContain("amount")
            .doesNotContain("currency")
            .doesNotContain("settlement")
            .doesNotContain("cancellationTerms")
            .doesNotContain("hotel")

        val copy = print("slots/$slot/suppliers/$supplier/supplier-order")
        assertThat(copy.read<Boolean>("$.document.copy")).isTrue()
        bookOffice(slot, adults = 1, children = 0, name = "Another Guest")
        val revised = print("slots/$slot/suppliers/$supplier/supplier-order", language = "ar")
        assertThat(revised.read<Boolean>("$.document.revised")).isTrue()
        assertThat(revised.read<Int>("$.data.totalGuests")).isEqualTo(5)

        // The staff-written note is what reaches the supplier; changing it revises the order.
        assertThat(assign(slot, suppliers = listOf(supplier), revision = 1, note = "\"  One vegetarian lunch  \"").status).isEqualTo(200)
        val noted = print("slots/$slot/suppliers/$supplier/supplier-order")
        assertThat(noted.read<String>("$.data.supplierNote")).isEqualTo("One vegetarian lunch")
        assertThat(noted.read<Boolean>("$.document.revised")).isTrue()
        assertThat(noted.body).doesNotContain("Diabetic").doesNotContain("01099887766")
        val rows =
            dsl
                .selectFrom(
                    TOURS_OPERATOR_DOCUMENT_PRINT,
                ).where(
                    TOURS_OPERATOR_DOCUMENT_PRINT.DOCUMENT_TYPE.eq("SUPPLIER_ORDER"),
                ).and(TOURS_OPERATOR_DOCUMENT_PRINT.SUBJECT_KEY.eq("$slot:$supplier"))
                .fetch()
        assertThat(rows).hasSize(4)
        assertThat(rows.map { it.get(TOURS_OPERATOR_DOCUMENT_PRINT.CONTENT_FINGERPRINT) }.first()).matches("[0-9a-f]{64}")
    }

    @Test
    fun `the supplier note is validated, shown on the assignment and audited`() {
        val tour = seedTour()
        val (_, slot) = seedSlot(day(), tourId = tour)
        val supplier = newSupplier(tourIds = listOf(tour))
        val control = assign(slot, suppliers = listOf(supplier), note = "\"Bring\\u0007 water\"")
        assertThat(control.status).isEqualTo(400)
        assertThat(control.read<String>("$.error")).isEqualTo("validation_failed")
        assertThat(assign(slot, suppliers = listOf(supplier), note = "\"${"x".repeat(501)}\"").status).isEqualTo(400)

        val saved = assign(slot, suppliers = listOf(supplier), note = "\"Two child life jackets\"")
        assertThat(saved.status).isEqualTo(200)
        assertThat(saved.read<String>("$.assignment.supplierNote")).isEqualTo("Two child life jackets")
        val blank = assign(slot, suppliers = listOf(supplier), revision = 1, note = "\"   \"")
        assertThat(blank.read<Any?>("$.assignment.supplierNote")).isNull()

        val history = call("GET", "$staff/slots/$slot/assignment/history")
        assertThat(history.read<List<String>>("$[*].action")).containsExactly("ASSIGNED", "CHANGED")
        assertThat(history.read<String>("$[0].supplierNote")).isEqualTo("Two child life jackets")
        assertThat(history.read<Any?>("$[1].supplierNote")).isNull()
    }

    @Test
    fun `the driver sheet groups one hotel whatever its case or spacing, alphabetical by hotel`() {
        val (_, slot) = seedSlot(day())
        bookOffice(slot, name = "Guest One", hotel = "Hilton Sharm Dreams", room = "1", adults = 1, children = 0)
        bookOffice(slot, name = "Guest Two", hotel = "hilton  sharm dreams ", room = "2", adults = 2, children = 0)
        bookOffice(slot, name = "Guest Three", hotel = "Baron Palms", room = "3", adults = 1, children = 0)
        assertThat(assign(slot, driver = newDriver()).status).isEqualTo(200)
        val sheet = print("slots/$slot/driver-sheet")
        assertThat(sheet.read<List<String>>("$.data.stops[*].hotelName")).containsExactly("Baron Palms", "Hilton Sharm Dreams")
        assertThat(sheet.read<List<Int>>("$.data.stops[*].guests")).containsExactly(1, 3)
        assertThat(sheet.read<List<String>>("$.data.stops[1].parties[*].leadName")).containsExactly("Guest One", "Guest Two")
    }

    @Test
    fun `driver sheet and supplier order are operations documents`() {
        val tour = seedTour()
        val (_, slot) = seedSlot(day(), tourId = tour)
        val supplier = newSupplier(tourIds = listOf(tour))
        assertThat(assign(slot, driver = newDriver(), suppliers = listOf(supplier)).status).isEqualTo(200)
        val opsOnly = tokenWith("tours-operator.document:print-ops")
        val printOnly = tokenWith("tours-operator.document:print")
        val assignmentOnly = tokenWith("tours-operator.assignment:manage")
        for (token in listOf(
            printOnly,
            assignmentOnly,
            tokenWith("tours-operator.supplier:manage"),
            tokenWith("tours-operator.fleet:manage"),
        )) {
            assertThat(print("slots/$slot/driver-sheet", token).status).isEqualTo(403)
            assertThat(print("slots/$slot/suppliers/$supplier/supplier-order", token).status).isEqualTo(403)
        }
        assertThat(print("slots/$slot/driver-sheet", null).status).isEqualTo(401)
        assertThat(print("slots/$slot/driver-sheet", opsOnly).status).isEqualTo(200)
        assertThat(print("slots/$slot/suppliers/$supplier/supplier-order", opsOnly).status).isEqualTo(200)
        assertThat(
            call("POST", "/api/v1/tours-operator/documents/slots/$slot/driver-sheet", admin(), """{"language":"fr"}""").status,
        ).isEqualTo(400)
        assertThat(print("slots/${UUID.randomUUID()}/driver-sheet").status).isEqualTo(404)
    }

    // ── container ────────────────────────────────────────────────────────────

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_ops_registry_test")
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
