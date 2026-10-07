package com.wego.toursoperator

import com.jayway.jsonpath.JsonPath
import com.wego.generated.jooq.tables.IdentityRole.IDENTITY_ROLE
import com.wego.generated.jooq.tables.IdentityRolePermission.IDENTITY_ROLE_PERMISSION
import com.wego.generated.jooq.tables.ToursOperatorPayment.TOURS_OPERATOR_PAYMENT
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
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * WEGO-016-OPS2-F over real PostgreSQL: cost components, profitability,
 * supplier/driver payables with the 5000 EGP approval rule, the daily cash
 * box, office refunds, the settlement statement, and the append-only guards.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ToursOperatorFinanceOpsHttpTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var passwordHasher: PasswordHasher

    @Autowired private lateinit var dsl: DSLContext

    private val password = "a-very-long-finance-ops-password-123"
    private lateinit var adminId: UUID
    private val cairo: ZoneId = ZoneId.of("Africa/Cairo")
    private val staff = "/api/v1/tours-operator/staff"

    @BeforeEach
    fun seed() {
        adminId = seedUser("fin-admin@example.com", setOf("platform-admin"))
        seedUser("fin-admin2@example.com", setOf("platform-admin"))
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private class Reply(
        val status: Int,
        val body: String,
    ) {
        fun <T> read(path: String): T = JsonPath.read(body, path)

        fun str(path: String): String = JsonPath.read<Any?>(body, path).toString()

        override fun toString() = "$status $body"
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

    /** A user holding exactly [permissions]; [who] makes a second, distinct user with the same permissions. */
    private fun tokenWith(
        vararg permissions: String,
        who: String = "",
    ): String {
        val roleCode =
            "fin-test-" + permissions.joinToString("-") { it.substringAfter("tours-operator.").replace(':', '-').replace('.', '-') }
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
        val email = "$roleCode$who@example.com"
        seedUser(email, setOf(roleCode))
        return login(email)
    }

    private fun admin() = login("fin-admin@example.com")

    private fun admin2() = login("fin-admin2@example.com")

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
        return Reply(response.status, response.contentAsString)
    }

    private fun today(): LocalDate = LocalDate.now(cairo)

    private fun unique() = UUID.randomUUID().toString().take(8)

    private fun seedTour(timeSlots: String = "MORNING"): UUID {
        val tourId = UUID.randomUUID()
        dsl
            .insertInto(TOURS_OPERATOR_TOUR)
            .set(TOURS_OPERATOR_TOUR.ID, tourId)
            .set(TOURS_OPERATOR_TOUR.SLUG, "fin-test-${unique()}")
            .set(TOURS_OPERATOR_TOUR.CATEGORY, "DESERT")
            .set(TOURS_OPERATOR_TOUR.DURATION_TEXT, "4 hours")
            .set(TOURS_OPERATOR_TOUR.PRICE_ADULT_CENTS, 3500L)
            .set(TOURS_OPERATOR_TOUR.PRICE_CHILD_CENTS, 1750L)
            .set(TOURS_OPERATOR_TOUR.CAPACITY, 60)
            .set(TOURS_OPERATOR_TOUR.AVAILABLE_TIME_SLOTS, timeSlots)
            .set(TOURS_OPERATOR_TOUR.SORT_ORDER, 1)
            .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, true)
            .set(TOURS_OPERATOR_TOUR.CREATED_BY_USER_ID, adminId)
            .set(TOURS_OPERATOR_TOUR.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute()
        return tourId
    }

    private fun seedSlot(
        tourId: UUID,
        date: LocalDate = today(),
        timeSlot: String = "MORNING",
    ): UUID {
        val slotId = UUID.randomUUID()
        dsl
            .insertInto(TOURS_OPERATOR_TOUR_SLOT)
            .set(TOURS_OPERATOR_TOUR_SLOT.ID, slotId)
            .set(TOURS_OPERATOR_TOUR_SLOT.TOUR_ID, tourId)
            .set(TOURS_OPERATOR_TOUR_SLOT.DATE, date)
            .set(TOURS_OPERATOR_TOUR_SLOT.TIME_SLOT, timeSlot)
            .set(TOURS_OPERATOR_TOUR_SLOT.CAPACITY, 60)
            .set(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT, 0)
            .set(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED, false)
            .set(TOURS_OPERATOR_TOUR_SLOT.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute()
        return slotId
    }

    private fun office(
        slotId: UUID,
        adults: Int = 2,
        children: Int = 1,
    ): String {
        val reply =
            call(
                "POST",
                "$staff/bookings",
                body =
                    """{"clientRequestId":"${UUID.randomUUID()}","slotId":"$slotId","adultsCount":$adults,"childrenCount":$children,
                    "customer":{"fullName":"Fin Test","phone":"+201234567890","nationality":"EG"},"hotelName":"Hilton","locale":"en"}""",
            )
        assertThat(reply.status).withFailMessage(reply.toString()).isEqualTo(201)
        return reply.read("$.id")
    }

    private fun collect(
        bookingId: String,
        amount: String,
        method: String = "CASH_AT_OFFICE",
        currency: String = "EUR",
        reference: String? = null,
        token: String = admin(),
    ): Reply {
        val ref = reference?.let { ""","reference":"$it"""" } ?: ""
        val rate = if (currency == "EGP") ""","fxRateId":"${rateId()}"""" else ""
        return call(
            "POST",
            "$staff/bookings/$bookingId/collections",
            token,
            """{"clientRequestId":"${UUID.randomUUID()}","method":"$method","currency":"$currency","amount":$amount$ref$rate}""",
        )
    }

    private fun cancel(bookingId: String) {
        val reply = call("POST", "/api/v1/tours-operator/bookings/$bookingId/cancel", body = """{"reason":"Customer cancelled"}""")
        assertThat(reply.status).withFailMessage(reply.toString()).isEqualTo(200)
    }

    private fun setRate(rate: String) {
        assertThat(call("POST", "$staff/fx-rate", body = """{"egpPerEur":$rate}""").status).isEqualTo(201)
    }

    private fun rateId(): String =
        dsl
            .fetchOne(
                "SELECT id FROM wego.tours_operator_fx_rate WHERE rate_date = (now() AT TIME ZONE 'Africa/Cairo')::date ORDER BY set_at DESC, id DESC LIMIT 1",
            )!!
            .get(0)
            .toString()

    private fun cost(
        owner: String,
        category: String,
        basis: String,
        currency: String,
        amount: String,
        extra: String = "",
        validFrom: LocalDate = today().minusDays(30),
        token: String = admin(),
    ): Reply =
        call(
            "POST",
            "$staff/costs",
            token,
            """{$owner,"category":"$category","label":"$category ${unique()}","basis":"$basis","currency":"$currency",
            "amount":$amount,"validFrom":"$validFrom"$extra}""",
        )

    private fun newSupplier(): String {
        val reply =
            call(
                "POST",
                "$staff/suppliers",
                body =
                    """{"code":"S${unique().uppercase()}","name":"Supplier ${unique()}","serviceType":"QUAD_BUGGY_SAFARI","active":true,"tourIds":[]}""",
            )
        assertThat(reply.status).withFailMessage(reply.toString()).isEqualTo(201)
        return reply.read("$.id")
    }

    private fun newDriver(): String {
        val reply =
            call(
                "POST",
                "$staff/drivers",
                body = """{"name":"Driver ${unique()}","engagementType":"PER_TRIP","licenceValidUntil":"2035-12-31","active":true}""",
            )
        assertThat(reply.status).withFailMessage(reply.toString()).isEqualTo(201)
        return reply.read("$.id")
    }

    private fun assign(
        slotId: UUID,
        driver: String? = null,
        suppliers: List<String> = emptyList(),
    ) {
        val driverPart = driver?.let { """"driverId":"$it",""" } ?: ""
        val reply =
            call(
                "PUT",
                "$staff/slots/$slotId/assignment",
                body = """{$driverPart"supplierIds":[${suppliers.joinToString(",") { "\"$it\"" }}],"expectedRevision":0}""",
            )
        assertThat(reply.status).withFailMessage(reply.toString()).isEqualTo(200)
    }

    private fun pay(
        type: String,
        id: String,
        amount: String,
        currency: String = "EGP",
        method: String = "BANK_TRANSFER",
        approvalId: String? = null,
        token: String = admin(),
        key: UUID = UUID.randomUUID(),
    ): Reply {
        val ref = if (method == "CASH") "" else ""","reference":"TRX-${unique()}""""
        val approval = approvalId?.let { ""","approvalId":"$it"""" } ?: ""
        return call(
            "POST",
            "$staff/payables/$type/$id/payments",
            token,
            """{"clientRequestId":"$key","method":"$method","currency":"$currency","amount":$amount$ref$approval}""",
        )
    }

    private fun charge(
        type: String,
        id: String,
        amount: String,
        currency: String = "EGP",
        slotId: UUID? = null,
        kind: String = "CHARGE",
        token: String = admin(),
    ): Reply {
        val where = slotId?.let { """"slotId":"$it"""" } ?: """"serviceDate":"${today()}""""
        return call(
            "POST",
            "$staff/payables/$type/$id/adjustments",
            token,
            """{"clientRequestId":"${UUID.randomUUID()}","kind":"$kind","currency":"$currency","amount":$amount,$where,"reason":"Agreed extra"}""",
        )
    }

    private fun statement(
        type: String,
        id: String,
        from: LocalDate = today().minusDays(1),
        to: LocalDate = today(),
    ): Reply = call("GET", "$staff/payables/$type/$id/statement?from=$from&to=$to")

    private fun balance(
        statement: Reply,
        currency: String,
    ): String = statement.str("$.balances[?(@.currency == '$currency')].closing.amount").removePrefix("[\"").removeSuffix("\"]")

    private fun concurrently(
        n: Int,
        block: (Int) -> Reply,
    ): List<Reply> {
        val pool = Executors.newFixedThreadPool(n)
        try {
            val start = CountDownLatch(1)
            val futures =
                (0 until n).map { i ->
                    pool.submit(
                        Callable {
                            start.await()
                            block(i)
                        },
                    )
                }
            start.countDown()
            return futures.map { it.get() }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun departure(
        report: Reply,
        slotId: UUID,
    ): Map<String, Any?> = report.read<List<Map<String, Any?>>>("$.groups[?(@.key == '$slotId')].totals").single()

    @Suppress("UNCHECKED_CAST")
    private fun amountOf(
        totals: Map<String, Any?>,
        field: String,
    ): String? = (totals[field] as Map<String, Any?>?)?.get("amount") as String?

    // ── permissions ──────────────────────────────────────────────────────────

    @Test
    fun `every OPS2-F endpoint enforces its permission`() {
        val tour = seedTour()
        val supplier = newSupplier()
        val booking = office(seedSlot(tour))
        val unrelated = tokenWith("tours-operator.tour:view")
        val d = today()
        val cases =
            listOf(
                Triple("GET", "$staff/costs", null) to listOf("tours-operator.cost:manage", "tours-operator.payment:view"),
                Triple(
                    "POST",
                    "$staff/costs",
                    """{"tourId":"$tour","category":"FIXED","label":"Guide","basis":"PER_DEPARTURE","currency":"EGP","amount":1,"validFrom":"$d"}""",
                ) to
                    listOf("tours-operator.cost:manage"),
                Triple("GET", "$staff/finance/profitability?from=$d&to=$d", null) to listOf("tours-operator.payment:view"),
                Triple("GET", "$staff/finance/office-summary?from=$d&to=$d", null) to listOf("tours-operator.payment:view"),
                Triple("GET", "$staff/payables", null) to
                    listOf("tours-operator.settlement:pay", "tours-operator.settlement:approve", "tours-operator.payment:view"),
                Triple("GET", "$staff/payables/suppliers/$supplier/statement?from=$d&to=$d", null) to
                    listOf("tours-operator.settlement:pay"),
                Triple(
                    "POST",
                    "$staff/payables/suppliers/$supplier/payments",
                    """{"clientRequestId":"${UUID.randomUUID()}","method":"CASH","currency":"EGP","amount":1}""",
                ) to listOf("tours-operator.settlement:pay"),
                Triple(
                    "POST",
                    "$staff/payables/suppliers/$supplier/approvals",
                    """{"clientRequestId":"${UUID.randomUUID()}","currency":"EGP","amount":1}""",
                ) to listOf("tours-operator.settlement:approve"),
                Triple(
                    "POST",
                    "$staff/payables/suppliers/$supplier/adjustments",
                    """{"clientRequestId":"${UUID.randomUUID()}","kind":"CHARGE","currency":"EGP","amount":1,"serviceDate":"$d","reason":"x"}""",
                ) to listOf("tours-operator.settlement:pay"),
                Triple(
                    "POST",
                    "$staff/payables/suppliers/$supplier/payments/${UUID.randomUUID()}/reverse",
                    """{"clientRequestId":"${UUID.randomUUID()}","reason":"x"}""",
                ) to listOf("tours-operator.settlement:approve"),
                Triple("GET", "$staff/cash-box?date=$d&currency=EUR", null) to
                    listOf("tours-operator.cash-box:close", "tours-operator.cash-box:confirm", "tours-operator.payment:view"),
                Triple("POST", "$staff/cash-box/${d.minusDays(400)}/EGP/count", """{"counted":0}""") to
                    listOf("tours-operator.cash-box:close"),
                Triple("POST", "$staff/cash-box/${d.minusDays(400)}/EGP/confirm", null) to listOf("tours-operator.cash-box:confirm"),
                Triple("POST", "$staff/cash-box/${d.minusDays(400)}/EGP/reopen", """{"reason":"x"}""") to
                    listOf("tours-operator.cash-box:confirm"),
                Triple("GET", "$staff/bookings/$booking/refunds", null) to listOf("tours-operator.booking:view"),
                Triple(
                    "POST",
                    "$staff/bookings/$booking/refunds",
                    """{"clientRequestId":"${UUID.randomUUID()}","method":"CASH","amount":1,"reason":"x"}""",
                ) to listOf("tours-operator.booking:refund-office"),
                Triple(
                    "POST",
                    "/api/v1/tours-operator/documents/payables/suppliers/$supplier/statement",
                    """{"from":"$d","to":"$d","language":"en"}""",
                ) to listOf("tours-operator.settlement:pay", "tours-operator.settlement:approve"),
            )
        for ((request, allowed) in cases) {
            val (method, path, body) = request
            assertThat(call(method, path, null, body).status).withFailMessage("$method $path anonymous").isEqualTo(401)
            val denied = call(method, path, unrelated, body)
            assertThat(denied.status).withFailMessage("$method $path unrelated -> $denied").isEqualTo(403)
            for (permission in allowed) {
                val status = call(method, path, tokenWith(permission), body).status
                assertThat(status).withFailMessage("$method $path with $permission -> $status").isNotIn(401, 403)
            }
        }
        // Paying needs settlement:pay; approving does not grant it, and vice versa.
        val approver = tokenWith("tours-operator.settlement:approve")
        assertThat(pay("suppliers", supplier, "1", token = approver).status).isEqualTo(403)
        assertThat(
            call(
                "POST",
                "$staff/payables/suppliers/$supplier/approvals",
                tokenWith("tours-operator.settlement:pay"),
                """{"clientRequestId":"${UUID.randomUUID()}","currency":"EGP","amount":1}""",
            ).status,
        ).isEqualTo(403)
        assertThat(call("GET", "$staff/payables/clients/$supplier/statement?from=$d&to=$d").status).isEqualTo(404)
    }

    // ── cost model ───────────────────────────────────────────────────────────

    @Test
    fun `cost components are effective-dated, replaced not edited, and validated`() {
        val tour = seedTour()
        val created = cost(""""tourId":"$tour"""", "SUPPLIER", "PER_PERSON", "EGP", "1100.00", validFrom = today().minusDays(10))
        assertThat(created.status).withFailMessage(created.toString()).isEqualTo(201)
        val id: String = created.read("$.id")
        assertThat(created.str("$.amount.currencyCode")).isEqualTo("EGP")

        val replaced =
            call(
                "POST",
                "$staff/costs/$id/replace",
                body =
                    """{"tourId":"$tour","category":"SUPPLIER","label":"Supplier price","basis":"PER_PERSON","currency":"EGP",
                    "amount":1200,"validFrom":"${today()}"}""",
            )
        assertThat(replaced.status).withFailMessage(replaced.toString()).isEqualTo(201)
        assertThat(replaced.str("$.replacesComponentId")).isEqualTo(id)
        val all = call("GET", "$staff/costs?tourId=$tour&includeEnded=true")
        assertThat(all.read<List<Any>>("$")).hasSize(2)
        assertThat(all.str("$[?(@.id == '$id')].validUntil")).contains(today().minusDays(1).toString())
        assertThat(call("GET", "$staff/costs?tourId=$tour").read<List<Any>>("$")).hasSize(1)

        // Ended once only; a second replace or end of the old row is refused.
        assertThat(call("POST", "$staff/costs/$id/end", body = """{"lastDay":"${today()}"}""").status).isEqualTo(409)
        assertThat(
            call(
                "POST",
                "$staff/costs/$id/replace",
                body =
                    """{"tourId":"$tour","category":"SUPPLIER","label":"x","basis":"PER_PERSON","currency":"EGP",
                    "amount":1,"validFrom":"${today()}"}""",
            ).str("$.error"),
        ).isEqualTo("cost_already_ended")

        // Validation.
        assertThat(cost(""""tourId":"$tour"""", "FIXED", "PER_PERSON", "EUR", "10").str("$.error")).isEqualTo("basis_must_be_per_departure")
        assertThat(cost(""""tourId":"$tour"""", "FIXED", "PER_DEPARTURE", "EUR", "10", ""","childAmount":1""").str("$.error"))
            .isEqualTo("child_amount_per_person_only")
        assertThat(
            cost(""""tourId":"$tour"""", "DRIVER", "PER_DEPARTURE", "EGP", "300").str("$.error"),
        ).isEqualTo("driver_cost_needs_driver")
        assertThat(
            cost(""""tourId":"${UUID.randomUUID()}"""", "FIXED", "PER_DEPARTURE", "EUR", "10").str("$.error"),
        ).isEqualTo("tour_not_found")
        assertThat(cost(""""tourId":"$tour"""", "FIXED", "PER_DEPARTURE", "EUR", "10.001").status).isEqualTo(400)
        assertThat(cost(""""tourId":"$tour"""", "FIXED", "PER_DEPARTURE", "EUR", "-1").status).isEqualTo(400)

        // The database refuses edits other than ending, and refuses deletes.
        val newId: String = replaced.read("$.id")
        assertThatThrownBy { dsl.execute("UPDATE wego.tours_operator_cost_component SET amount = 1 WHERE id = ?::uuid", newId) }
            .hasMessageContaining("never edited")
        assertThatThrownBy { dsl.execute("DELETE FROM wego.tours_operator_cost_component WHERE id = ?::uuid", newId) }
            .hasMessageContaining("never edited")
        assertThatThrownBy {
            dsl.execute("UPDATE wego.tours_operator_cost_component SET valid_until = valid_from WHERE id = ?::uuid", id)
        }.hasMessageContaining("never edited")
    }

    // ── profitability ────────────────────────────────────────────────────────

    @Test
    fun `profitability converts EGP costs at the departure-day rate and splits per-departure costs by guests`() {
        setRate("50.0000")
        val tour = seedTour()
        val owner = """"tourId":"$tour""""
        assertThat(cost(owner, "SUPPLIER", "PER_PERSON", "EGP", "1100.00").status).isEqualTo(201)
        assertThat(cost(owner, "OWN_EXTRA", "PER_PERSON", "EGP", "50.00", ""","childAmount":25""").status).isEqualTo(201)
        assertThat(cost(owner, "FIXED", "PER_DEPARTURE", "EUR", "10.00").status).isEqualTo(201)
        // A component that starts tomorrow does not apply today.
        assertThat(cost(owner, "FIXED", "PER_DEPARTURE", "EUR", "999.00", validFrom = today().plusDays(1)).status).isEqualTo(201)
        val slot = seedSlot(tour)
        val family = office(slot, 2, 1) // 87.50 EUR
        val solo = office(slot, 1, 0) // 35.00 EUR
        assertThat(collect(family, "87.50").status).isEqualTo(201)
        assertThat(collect(solo, "20.00").status).isEqualTo(201) // deposit only: revenue is what was collected

        val report =
            call(
                "GET",
                "$staff/finance/profitability?from=${today()}&to=${today()}&groupBy=DEPARTURE",
                tokenWith("tours-operator.payment:view"),
            )
        assertThat(report.status).withFailMessage(report.toString()).isEqualTo(200)
        val totals = departure(report, slot)
        // family: (1100×3 + 50×2 + 25) EGP = 3425 / 50 = 68.50; solo: 1150 / 50 = 23.00; fixed 10.00
        assertThat(amountOf(totals, "revenue")).isEqualTo("107.50")
        assertThat(amountOf(totals, "officeRevenue")).isEqualTo("107.50")
        assertThat(amountOf(totals, "onlineRevenue")).isEqualTo("0.00")
        assertThat(amountOf(totals, "cost")).isEqualTo("101.50")
        assertThat(amountOf(totals, "profit")).isEqualTo("6.00")
        assertThat(totals["marginPercent"]).isEqualTo("5.6")
        assertThat(report.str("$.groups[?(@.key == '$slot')].rateSource")).contains("DEPARTURE_DATE")
        assertThat(report.str("$.groups[?(@.key == '$slot')].egpPerEur")).contains("50")

        val byBooking = call("GET", "$staff/finance/profitability?from=${today()}&to=${today()}&groupBy=BOOKING")
        val familyCost = byBooking.read<List<Map<String, Any?>>>("$.groups[?(@.key == '$family')].totals").single()
        val soloCost = byBooking.read<List<Map<String, Any?>>>("$.groups[?(@.key == '$solo')].totals").single()
        // The 10.00 per departure is split 3:1 by guests; the bookings add up to the departure exactly.
        assertThat(BigDecimal(amountOf(familyCost, "cost")!!).add(BigDecimal(amountOf(soloCost, "cost")!!))).isEqualByComparingTo("101.50")
        assertThat(amountOf(familyCost, "cost")).isIn("76.00", "76.01", "75.99")
        val byTour = call("GET", "$staff/finance/profitability?from=${today()}&to=${today()}&groupBy=TOUR")
        assertThat(
            amountOf(byTour.read<List<Map<String, Any?>>>("$.groups[?(@.key == '$tour')].totals").single(), "profit"),
        ).isEqualTo("6.00")
        assertThat(
            call("GET", "$staff/finance/profitability?from=${today()}&to=${today()}&groupBy=MONTH").read<List<Any>>("$.groups"),
        ).isNotEmpty
        assertThat(call("GET", "$staff/finance/profitability?from=${today()}&to=${today().minusDays(1)}").status).isEqualTo(400)
        assertThat(call("GET", "$staff/finance/profitability?from=${today()}&to=${today().plusDays(400)}").status).isEqualTo(400)
    }

    @Test
    fun `a departure day without a rate uses the latest rate and says so`() {
        setRate("52.0000")
        val tour = seedTour()
        assertThat(cost(""""tourId":"$tour"""", "SUPPLIER", "PER_PERSON", "EGP", "520.00").status).isEqualTo(201)
        val later = today().plusDays(20)
        val slot = seedSlot(tour, later)
        val booking = office(slot, 1, 0)
        assertThat(collect(booking, "35.00").status).isEqualTo(201)
        val report = call("GET", "$staff/finance/profitability?from=$later&to=$later&groupBy=DEPARTURE")
        assertThat(report.str("$.groups[?(@.key == '$slot')].rateSource")).contains("LATEST")
        assertThat(amountOf(departure(report, slot), "cost")).isEqualTo("10.00")
        assertThat(amountOf(departure(report, slot), "profit")).isEqualTo("25.00")
    }

    @Test
    fun `an unknown cost hides the profit instead of overstating it`() {
        setRate("50.0000")
        val bare = seedTour()
        val bareSlot = seedSlot(bare)
        assertThat(collect(office(bareSlot, 1, 0), "35.00").status).isEqualTo(201)
        var report = call("GET", "$staff/finance/profitability?from=${today()}&to=${today()}")
        var totals = departure(report, bareSlot)
        assertThat(totals["cost"]).isNull()
        assertThat(totals["profit"]).isNull()
        assertThat(totals["gaps"].toString()).contains("NO_COST_COMPONENTS")
        assertThat(report.read<Int>("$.totals.incompleteBookings")).isGreaterThanOrEqualTo(1)
        assertThat(report.read<Any?>("$.totals.profit")).isNull()

        // A driver without a trip rate: missing until a manual charge is entered for that departure.
        val tour = seedTour()
        assertThat(cost(""""tourId":"$tour"""", "FIXED", "PER_DEPARTURE", "EUR", "5.00").status).isEqualTo(201)
        val slot = seedSlot(tour)
        assertThat(collect(office(slot, 1, 0), "35.00").status).isEqualTo(201)
        val driver = newDriver()
        assign(slot, driver)
        totals = departure(call("GET", "$staff/finance/profitability?from=${today()}&to=${today()}"), slot)
        assertThat(totals["gaps"].toString()).contains("DRIVER_COST_MISSING")
        assertThat(charge("drivers", driver, "250.00", slotId = slot).status).isEqualTo(201)
        totals = departure(call("GET", "$staff/finance/profitability?from=${today()}&to=${today()}"), slot)
        assertThat(totals["gaps"].toString()).isEqualTo("[]")
        // 5.00 EUR + 250 EGP / 50 = 10.00
        assertThat(amountOf(totals, "cost")).isEqualTo("10.00")
    }

    @Test
    fun `online recognised revenue and office money are reported apart`() {
        setRate("50.0000")
        val tour = seedTour()
        assertThat(cost(""""tourId":"$tour"""", "FIXED", "PER_DEPARTURE", "EUR", "0.00").status).isEqualTo(201)
        val slot = seedSlot(tour)
        val online = office(slot, 1, 0)
        // Turn it into a paid online booking: recognised Paymob revenue.
        dsl.execute(
            "UPDATE wego.tours_operator_booking SET channel = 'ONLINE', created_by_user_id = NULL, client_request_id = NULL WHERE id = ?::uuid",
            online,
        )
        val paymentId = UUID.randomUUID()
        dsl.execute(
            """INSERT INTO wego.tours_operator_payment (id, booking_id, amount_eur, amount_minor_units, currency_code, status, created_at, paid_at,
               revenue_recognised_at, provider_reference) VALUES (?, ?::uuid, 35.00, 3500, 'EUR', 'PAID', now(), now(), now(), ?)""",
            paymentId,
            online,
            "sts-$paymentId",
        )
        val officeBooking = office(slot, 1, 0)
        assertThat(collect(officeBooking, "30.00").status).isEqualTo(201)
        val totals = departure(call("GET", "$staff/finance/profitability?from=${today()}&to=${today()}"), slot)
        assertThat(amountOf(totals, "onlineRevenue")).isEqualTo("35.00")
        assertThat(amountOf(totals, "officeRevenue")).isEqualTo("30.00")
        assertThat(amountOf(totals, "revenue")).isEqualTo("65.00")
        assertThat(dsl.fetchCount(TOURS_OPERATOR_PAYMENT, TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(officeBooking)))).isZero()
    }

    @Test
    fun `office summary is the office payments line, net of reversals and refunds`() {
        val path = "$staff/finance/office-summary?from=${today()}&to=${today()}"
        val before = call("GET", path)
        val booking = office(seedSlot(seedTour()), 1, 0)
        val first = collect(booking, "35.00", method = "CARD_TERMINAL", reference = "T-${unique()}")
        assertThat(first.status).isEqualTo(201)
        val after = call("GET", path)
        assertThat(BigDecimal(after.str("$.net.amount")).subtract(BigDecimal(before.str("$.net.amount")))).isEqualByComparingTo("35.00")
        cancel(booking)
        assertThat(refund(booking, "10.00", "CARD_TERMINAL", reference = "RF-${unique()}").status).isEqualTo(201)
        val end = call("GET", path)
        assertThat(BigDecimal(end.str("$.net.amount")).subtract(BigDecimal(before.str("$.net.amount")))).isEqualByComparingTo("25.00")
        assertThat(end.read<Int>("$.refundCount") - before.read<Int>("$.refundCount")).isEqualTo(1)
    }

    // ── refunds ──────────────────────────────────────────────────────────────

    private fun refund(
        bookingId: String,
        amount: String,
        method: String = "CASH",
        currency: String = "EUR",
        reference: String? = null,
        token: String = admin(),
        key: UUID = UUID.randomUUID(),
    ): Reply {
        val ref = reference?.let { ""","reference":"$it"""" } ?: ""
        val rate = if (currency == "EGP") ""","fxRateId":"${rateId()}"""" else ""
        return call(
            "POST",
            "$staff/bookings/$bookingId/refunds",
            token,
            """{"clientRequestId":"$key","method":"$method","currency":"$currency","amount":$amount,"reason":"Cancelled 3 days ahead"$ref$rate}""",
        )
    }

    @Test
    fun `refunds return at most what was collected and mark the cancellation form as money returned`() {
        val tour = seedTour()
        val booking = office(seedSlot(tour))
        assertThat(collect(booking, "87.50").status).isEqualTo(201)
        assertThat(refund(booking, "10.00").str("$.error")).isEqualTo("booking_not_cancelled")
        cancel(booking)
        assertThat(refund(booking, "87.51").str("$.error")).isEqualTo("amount_exceeds_refundable")
        assertThat(refund(booking, "10.00", method = "INSTAPAY").str("$.error")).isEqualTo("reference_required")
        assertThat(refund(booking, "10.00", token = tokenWith("tours-operator.booking:collect-cash")).status).isEqualTo(403)

        val key = UUID.randomUUID()
        val partial = refund(booking, "50.00", key = key)
        assertThat(partial.status).withFailMessage(partial.toString()).isEqualTo(201)
        assertThat(partial.str("$.position.refundable.amount")).isEqualTo("37.50")
        assertThat(refund(booking, "50.00", key = key).status).isEqualTo(200) // idempotent replay
        assertThat(refund(booking, "40.00", key = key).str("$.error")).isEqualTo("idempotency_key_reused")

        var view = call("GET", "/api/v1/tours-operator/bookings/$booking")
        assertThat(view.str("$.officePayment.cashToReturn.amount")).isEqualTo("37.50")
        assertThat(view.str("$.officePayment.refunded.amount")).isEqualTo("50.00")
        var form = call("POST", "/api/v1/tours-operator/documents/bookings/$booking/cancellation-form", body = """{"language":"en"}""")
        assertThat(form.str("$.data.refundedNet.amount")).isEqualTo("50.00")
        assertThat(form.read<List<Any>>("$.data.refunds")).hasSize(1)

        assertThat(refund(booking, "37.50").status).isEqualTo(201)
        assertThat(refund(booking, "1.00").str("$.error")).isEqualTo("nothing_to_refund")
        view = call("GET", "/api/v1/tours-operator/bookings/$booking")
        assertThat(view.read<Any?>("$.officePayment.cashToReturn")).isNull()
        assertThat(view.str("$.officePayment.refunded.amount")).isEqualTo("87.50")
        form = call("POST", "/api/v1/tours-operator/documents/bookings/$booking/cancellation-form", body = """{"language":"ar"}""")
        assertThat(form.str("$.data.refundedNet.amount")).isEqualTo("87.50")
        assertThat(form.str("$.data.returnState")).isEqualTo("RETURNED")
        assertThat(form.read<Boolean>("$.document.revised")).isTrue()
        // The collections ledger and Paymob are untouched.
        assertThat(view.str("$.officePayment.collected.amount")).isEqualTo("87.50")
        assertThat(dsl.fetchCount(TOURS_OPERATOR_PAYMENT, TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(booking)))).isZero()
        val list = call("GET", "$staff/bookings/$booking/refunds")
        assertThat(list.read<List<Any>>("$.entries")).hasSize(2)
        assertThat(list.str("$.position.refundable.amount")).isEqualTo("0.00")
    }

    @Test
    fun `concurrent refunds never return more than was collected`() {
        val booking = office(seedSlot(seedTour()))
        assertThat(collect(booking, "87.50").status).isEqualTo(201)
        cancel(booking)
        val token = admin()
        val replies = concurrently(6) { refund(booking, "30.00", token = token) }
        assertThat(replies.count { it.status == 201 }).isEqualTo(2)
        assertThat(replies.filter { it.status != 201 }.map { it.str("$.error") }).containsOnly("amount_exceeds_refundable")
        assertThat(call("GET", "$staff/bookings/$booking/refunds").str("$.position.refunded.amount")).isEqualTo("60.00")
    }

    @Test
    fun `a refund is reversed only by another manager, once`() {
        val booking = office(seedSlot(seedTour()), 1, 0)
        assertThat(collect(booking, "35.00").status).isEqualTo(201)
        cancel(booking)
        val recorded = refund(booking, "35.00")
        val id: String = recorded.read("$.entry.id")
        val body = """{"clientRequestId":"${UUID.randomUUID()}","reason":"Customer came back"}"""
        assertThat(
            call("POST", "$staff/bookings/$booking/refunds/$id/reverse", admin(), body).str("$.error"),
        ).isEqualTo("cannot_reverse_own_refund")
        val reversed = call("POST", "$staff/bookings/$booking/refunds/$id/reverse", admin2(), body)
        assertThat(reversed.status).withFailMessage(reversed.toString()).isEqualTo(201)
        assertThat(reversed.str("$.position.refundable.amount")).isEqualTo("35.00")
        assertThat(
            call(
                "POST",
                "$staff/bookings/$booking/refunds/$id/reverse",
                admin2(),
                """{"clientRequestId":"${UUID.randomUUID()}","reason":"again"}""",
            ).str("$.error"),
        ).isEqualTo("refund_already_reversed")
        assertThatThrownBy { dsl.execute("UPDATE wego.tours_operator_office_refund SET reason = 'edited' WHERE id = ?::uuid", id) }
            .hasMessageContaining("append-only")
        assertThatThrownBy { dsl.execute("DELETE FROM wego.tours_operator_office_refund WHERE id = ?::uuid", id) }
            .hasMessageContaining("append-only")
        assertThatThrownBy { dsl.execute("TRUNCATE wego.tours_operator_office_refund CASCADE") }.hasMessageContaining("append-only")
    }

    // ── payables and settlements ────────────────────────────────────────────

    @Test
    fun `payables are derived from departures that ran, adjusted and paid down in a statement`() {
        val tour = seedTour()
        assertThat(cost(""""tourId":"$tour"""", "SUPPLIER", "PER_PERSON", "EGP", "1100.00").status).isEqualTo(201)
        val supplier = newSupplier()
        val ratedDriver = newDriver()
        val manualDriver = newDriver()
        assertThat(cost(""""driverId":"$ratedDriver"""", "DRIVER", "PER_DEPARTURE", "EGP", "300.00").status).isEqualTo(201)
        val slot = seedSlot(tour)
        office(slot, 2, 1)
        assign(slot, ratedDriver, listOf(supplier))
        val tour2 = seedTour()
        val slot2 = seedSlot(tour2)
        office(slot2, 1, 0)
        assign(slot2, manualDriver)

        var s = statement("suppliers", supplier)
        assertThat(s.status).withFailMessage(s.toString()).isEqualTo(200)
        assertThat(balance(s, "EGP")).isEqualTo("3300.00")
        assertThat(s.str("$.movements[0].kind")).isEqualTo("DEPARTURE")
        assertThat(s.read<Int>("$.movements[0].guests")).isEqualTo(3)
        assertThat(balance(statement("drivers", ratedDriver), "EGP")).isEqualTo("300.00")
        s = statement("drivers", manualDriver)
        assertThat(s.str("$.issues[0].code")).isEqualTo("MANUAL_AMOUNT_NEEDED")
        assertThat(charge("drivers", manualDriver, "250.00", slotId = slot2).status).isEqualTo(201)
        s = statement("drivers", manualDriver)
        assertThat(s.read<List<Any>>("$.issues")).isEmpty()
        assertThat(balance(s, "EGP")).isEqualTo("250.00")

        assertThat(pay("suppliers", supplier, "1000.00", method = "CASH").status).isEqualTo(201)
        assertThat(charge("suppliers", supplier, "100.00", kind = "DEDUCTION").status).isEqualTo(201)
        s = statement("suppliers", supplier)
        assertThat(balance(s, "EGP")).isEqualTo("2200.00")
        assertThat(s.str("$.balances[0].paid.amount")).isEqualTo("1000.00")
        assertThat(s.str("$.balances[0].owed.amount")).isEqualTo("3200.00")
        assertThat(pay("suppliers", supplier, "2200.01").str("$.error")).isEqualTo("amount_exceeds_balance")
        assertThat(pay("suppliers", supplier, "10.00", method = "INSTAPAY").status).isEqualTo(201) // carries a reference
        assertThat(
            call(
                "POST",
                "$staff/payables/suppliers/$supplier/payments",
                body = """{"clientRequestId":"${UUID.randomUUID()}","method":"INSTAPAY","currency":"EGP","amount":5}""",
            ).str("$.error"),
        ).isEqualTo("reference_required")
        // A statement for an earlier period opens with nothing and shows nothing of today.
        val earlier = statement("suppliers", supplier, today().minusDays(10), today().minusDays(5))
        assertThat(earlier.read<List<Any>>("$.movements")).isEmpty()
        val overview = call("GET", "$staff/payables", tokenWith("tours-operator.payment:view"))
        assertThat(overview.str("$[?(@.partyId == '$supplier')].balances[0].closing.amount")).contains("2190.00")
    }

    @Test
    fun `payments above 5000 EGP need one owner approval, exactly 5000 does not`() {
        setRate("50.0000")
        val tour = seedTour()
        assertThat(cost(""""tourId":"$tour"""", "SUPPLIER", "PER_PERSON", "EGP", "5000.00").status).isEqualTo(201)
        val supplier = newSupplier()
        val slot = seedSlot(tour)
        office(slot, 2, 1) // 15000 EGP owed
        assign(slot, null, listOf(supplier))
        val manager = tokenWith("tours-operator.settlement:pay")
        val owner = tokenWith("tours-operator.settlement:approve")

        assertThat(pay("suppliers", supplier, "5000.00", token = manager).status).isEqualTo(201)
        val over = pay("suppliers", supplier, "5000.01", token = manager)
        assertThat(over.status).isEqualTo(409)
        assertThat(over.str("$.error")).isEqualTo("approval_required")
        assertThat(over.str("$.details.limitEgp")).isEqualTo("5000.00")

        val approval =
            call(
                "POST",
                "$staff/payables/suppliers/$supplier/approvals",
                owner,
                """{"clientRequestId":"${UUID.randomUUID()}","currency":"EGP","amount":5000.01,"note":"Owner OK"}""",
            )
        assertThat(approval.status).withFailMessage(approval.toString()).isEqualTo(201)
        val approvalId: String = approval.read("$.id")
        assertThat(
            pay("suppliers", supplier, "6000.00", approvalId = approvalId, token = manager).str("$.error"),
        ).isEqualTo("approval_mismatch")
        assertThat(pay("suppliers", supplier, "5000.01", approvalId = approvalId, token = manager).status).isEqualTo(201)
        assertThat(
            pay("suppliers", supplier, "5000.01", approvalId = approvalId, token = manager).str("$.error"),
        ).isEqualTo("approval_already_used")
        assertThat(pay("suppliers", supplier, "1.00", approvalId = UUID.randomUUID().toString(), token = manager).str("$.error"))
            .isEqualTo("approval_not_found")
        // Balance is now 4999.99: an approval above it is refused.
        assertThat(
            call(
                "POST",
                "$staff/payables/suppliers/$supplier/approvals",
                owner,
                """{"clientRequestId":"${UUID.randomUUID()}","currency":"EGP","amount":5000.00}""",
            ).str("$.error"),
        ).isEqualTo("amount_exceeds_balance")

        // EUR is valued at today's rate: 100.00 × 50 = 5000.00 passes, 100.01 does not.
        assertThat(charge("suppliers", supplier, "300.00", currency = "EUR").status).isEqualTo(201)
        val eurOk = pay("suppliers", supplier, "100.00", currency = "EUR", token = manager)
        assertThat(eurOk.status).isEqualTo(201)
        assertThat(eurOk.str("$.egpEquivalent")).isEqualTo("5000.00")
        assertThat(pay("suppliers", supplier, "100.01", currency = "EUR", token = manager).str("$.error")).isEqualTo("approval_required")
        // The database itself refuses a payment above the limit without an approval.
        assertThatThrownBy {
            dsl.execute(
                """INSERT INTO wego.tours_operator_settlement_payment (id, party_type, supplier_id, kind, method, currency, amount, egp_equivalent,
                   recorded_by_user_id, client_request_id, recorded_at) VALUES (?, 'SUPPLIER', ?::uuid, 'PAYMENT', 'CASH', 'EGP', 5000.01, 5000.01, ?, ?, now())""",
                UUID.randomUUID(),
                supplier,
                adminId,
                UUID.randomUUID(),
            )
        }.hasMessageContaining("tours_operator_settlement_payment_limit")
    }

    @Test
    fun `concurrent payments never exceed the balance`() {
        val supplier = newSupplier()
        assertThat(charge("suppliers", supplier, "1000.00").status).isEqualTo(201)
        val token = admin()
        val replies = concurrently(5) { pay("suppliers", supplier, "300.00", token = token) }
        assertThat(replies.count { it.status == 201 }).withFailMessage(replies.toString()).isEqualTo(3)
        assertThat(replies.filter { it.status != 201 }.map { it.str("$.error") }).containsOnly("amount_exceeds_balance")
        assertThat(balance(statement("suppliers", supplier), "EGP")).isEqualTo("100.00")
    }

    @Test
    fun `one approval pays exactly once under concurrency`() {
        val supplier = newSupplier()
        assertThat(charge("suppliers", supplier, "20000.00").status).isEqualTo(201)
        val approval =
            call(
                "POST",
                "$staff/payables/suppliers/$supplier/approvals",
                body = """{"clientRequestId":"${UUID.randomUUID()}","currency":"EGP","amount":6000}""",
            )
        val approvalId: String = approval.read("$.id")
        val token = admin()
        val replies = concurrently(4) { pay("suppliers", supplier, "6000.00", approvalId = approvalId, token = token) }
        assertThat(replies.count { it.status == 201 }).withFailMessage(replies.toString()).isEqualTo(1)
        assertThat(replies.filter { it.status != 201 }.map { it.str("$.error") }).containsOnly("approval_already_used")
    }

    @Test
    fun `settlement reversals need the owner permission, happen once, and the ledgers are append-only`() {
        val driver = newDriver()
        assertThat(charge("drivers", driver, "800.00").status).isEqualTo(201)
        val payment = pay("drivers", driver, "500.00")
        val paymentId: String = payment.read("$.id")
        val body = """{"clientRequestId":"${UUID.randomUUID()}","reason":"Paid the wrong driver"}"""
        val path = "$staff/payables/drivers/$driver/payments/$paymentId/reverse"
        assertThat(call("POST", path, tokenWith("tours-operator.settlement:pay"), body).status).isEqualTo(403)
        val reversed = call("POST", path, tokenWith("tours-operator.settlement:approve"), body)
        assertThat(reversed.status).withFailMessage(reversed.toString()).isEqualTo(201)
        assertThat(reversed.str("$.kind")).isEqualTo("REVERSAL")
        assertThat(call("POST", path, admin(), """{"clientRequestId":"${UUID.randomUUID()}","reason":"again"}""").str("$.error"))
            .isEqualTo("entry_already_reversed")
        assertThat(balance(statement("drivers", driver), "EGP")).isEqualTo("800.00")

        val adjustments = statement("drivers", driver).read<List<String>>("$.movements[?(@.kind == 'CHARGE')].adjustment.id")
        val adjustmentPath = "$staff/payables/drivers/$driver/adjustments/${adjustments.first()}/reverse"
        assertThat(call("POST", adjustmentPath, tokenWith("tours-operator.settlement:pay"), body).status).isEqualTo(403)
        assertThat(call("POST", adjustmentPath, admin(), body).status).isEqualTo(201)
        assertThat(call("POST", adjustmentPath, admin(), """{"clientRequestId":"${UUID.randomUUID()}","reason":"x"}""").str("$.error"))
            .isEqualTo("entry_already_reversed")
        assertThat(balance(statement("drivers", driver), "EGP")).isEqualTo("0.00")

        for (table in listOf("settlement_payment", "payable_adjustment", "settlement_approval")) {
            assertThatThrownBy { dsl.execute("TRUNCATE wego.tours_operator_$table CASCADE") }.hasMessageContaining("append-only")
        }
        assertThatThrownBy { dsl.execute("UPDATE wego.tours_operator_settlement_payment SET amount = 1 WHERE id = ?::uuid", paymentId) }
            .hasMessageContaining("append-only")
        assertThatThrownBy { dsl.execute("DELETE FROM wego.tours_operator_payable_adjustment WHERE driver_id = ?::uuid", driver) }
            .hasMessageContaining("append-only")
    }

    @Test
    fun `settlement statement prints numbered, then COPY, then REVISED after a payment`() {
        val supplier = newSupplier()
        assertThat(charge("suppliers", supplier, "700.00").status).isEqualTo(201)
        val path = "/api/v1/tours-operator/documents/payables/suppliers/$supplier/statement"
        val body = """{"from":"${today().minusDays(7)}","to":"${today()}","language":"en"}"""
        val first = call("POST", path, tokenWith("tours-operator.settlement:pay"), body)
        assertThat(first.status).withFailMessage(first.toString()).isEqualTo(200)
        assertThat(first.str("$.document.number")).startsWith("STL-")
        assertThat(first.str("$.data.balances[0].closing.amount")).isEqualTo("700.00")
        assertThat(first.str("$.data.lines[0].kind")).isEqualTo("CHARGE")
        val copy = call("POST", path, admin(), body.replace("\"en\"", "\"ar\""))
        assertThat(copy.read<Boolean>("$.document.copy")).isTrue()
        assertThat(copy.str("$.document.number")).isEqualTo(first.str("$.document.number"))
        assertThat(pay("suppliers", supplier, "200.00").status).isEqualTo(201)
        val revised = call("POST", path, admin(), body)
        assertThat(revised.read<Boolean>("$.document.revised")).isTrue()
        assertThat(revised.str("$.data.balances[0].closing.amount")).isEqualTo("500.00")
        assertThat(revised.str("$.data.lines[1].reference")).startsWith("****")
        assertThat(call("POST", path, tokenWith("tours-operator.document:print-ops"), body).status).isEqualTo(403)
    }

    // ── cash box ─────────────────────────────────────────────────────────────

    private fun cashDay(currency: String = "EUR") = call("GET", "$staff/cash-box?date=${today()}&currency=$currency")

    private fun count(
        counted: String,
        token: String = admin(),
        currency: String = "EUR",
    ) = call("POST", "$staff/cash-box/${today()}/$currency/count", token, """{"counted":$counted,"note":"Drawer"}""")

    private fun confirm(
        token: String,
        currency: String = "EUR",
    ) = call("POST", "$staff/cash-box/${today()}/$currency/confirm", token)

    private fun reopenIfClosed(currency: String = "EUR") {
        if (cashDay(currency).str("$.state") == "CLOSED") {
            assertThat(
                call("POST", "$staff/cash-box/${today()}/$currency/reopen", admin2(), """{"reason":"Test cleanup"}""").status,
            ).isEqualTo(201)
        }
    }

    @Test
    fun `cash box counts, is confirmed by someone else, then refuses cash until reopened`() {
        val booking = office(seedSlot(seedTour()))
        val cashEntry = collect(booking, "50.00")
        assertThat(cashEntry.status).isEqualTo(201)
        try {
            val expected = BigDecimal(cashDay().str("$.expected.amount"))
            val counted = count(expected.subtract(BigDecimal("5.00")).toPlainString())
            assertThat(counted.status).withFailMessage(counted.toString()).isEqualTo(201)
            assertThat(counted.str("$.state")).isEqualTo("COUNTED")
            assertThat(counted.read<List<Map<String, Any?>>>("$.events").last()["difference"].toString()).contains("-5.00")

            assertThat(confirm(admin()).str("$.error")).isEqualTo("cannot_confirm_own_count")
            assertThat(confirm(tokenWith("tours-operator.cash-box:close")).status).isEqualTo(403)
            val closed = confirm(tokenWith("tours-operator.cash-box:confirm"))
            assertThat(closed.status).withFailMessage(closed.toString()).isEqualTo(201)
            assertThat(closed.str("$.state")).isEqualTo("CLOSED")

            // A closed day is immutable: no cash in or out, no recount.
            assertThat(collect(booking, "1.00").str("$.error")).isEqualTo("cash_day_closed")
            val entryId: String = cashEntry.read("$.entry.id")
            assertThat(
                call(
                    "POST",
                    "$staff/bookings/$booking/collections/$entryId/reverse",
                    admin2(),
                    """{"clientRequestId":"${UUID.randomUUID()}","reason":"Wrong"}""",
                ).str("$.error"),
            ).isEqualTo("cash_day_closed")
            assertThat(count("1.00").str("$.error")).isEqualTo("cash_day_closed")
            val supplier = newSupplier()
            assertThat(charge("suppliers", supplier, "100.00", currency = "EUR").status).isEqualTo(201)
            assertThat(pay("suppliers", supplier, "10.00", currency = "EUR", method = "CASH").str("$.error")).isEqualTo("cash_day_closed")
            // Non-cash money is not in the box and still works.
            assertThat(collect(booking, "1.00", method = "CARD_TERMINAL", reference = "C-${unique()}").status).isEqualTo(201)
            assertThatThrownBy {
                dsl.execute(
                    "UPDATE wego.tours_operator_cash_box_event SET note = 'edited' WHERE business_date = ?",
                    today(),
                )
            }.hasMessageContaining("append-only")

            val reopenPath = "$staff/cash-box/${today()}/EUR/reopen"
            assertThat(
                call("POST", reopenPath, tokenWith("tours-operator.cash-box:close"), """{"reason":"Late cash"}""").status,
            ).isEqualTo(403)
            assertThat(call("POST", reopenPath, admin2(), """{"reason":""}""").status).isEqualTo(400)
            val reopened = call("POST", reopenPath, admin2(), """{"reason":"Late cash from the pickup"}""")
            assertThat(reopened.status).isEqualTo(201)
            assertThat(reopened.str("$.state")).isEqualTo("OPEN")
            assertThat(collect(booking, "1.00").status).isEqualTo(201)
        } finally {
            reopenIfClosed()
        }
    }

    @Test
    fun `confirming after more cash was recorded asks for a recount, and two managers cannot both close`() {
        val booking = office(seedSlot(seedTour()))
        assertThat(collect(booking, "40.00").status).isEqualTo(201)
        try {
            assertThat(count(cashDay().str("$.expected.amount")).status).isEqualTo(201)
            assertThat(collect(booking, "10.00").status).isEqualTo(201)
            val stale = confirm(tokenWith("tours-operator.cash-box:confirm"))
            assertThat(stale.status).isEqualTo(409)
            assertThat(stale.str("$.error")).isEqualTo("cash_expected_changed")

            assertThat(count(cashDay().str("$.expected.amount")).status).isEqualTo(201)
            val a = tokenWith("tours-operator.cash-box:confirm", who = "-a")
            val b = tokenWith("tours-operator.cash-box:confirm", who = "-b")
            val replies = concurrently(2) { i -> confirm(if (i == 0) a else b) }
            assertThat(replies.map { it.status }.sorted()).containsExactly(201, 409)
            assertThat(replies.single { it.status == 409 }.str("$.error")).isEqualTo("cash_day_closed")
        } finally {
            reopenIfClosed()
        }
    }

    @Test
    fun `closing races a cash entry without ever closing on the wrong amount`() {
        val booking = office(seedSlot(seedTour()))
        assertThat(collect(booking, "30.00").status).isEqualTo(201)
        try {
            assertThat(count(cashDay().str("$.expected.amount")).status).isEqualTo(201)
            val confirmer = tokenWith("tours-operator.cash-box:confirm")
            val cashier = admin()
            val replies = concurrently(2) { i -> if (i == 0) confirm(confirmer) else collect(booking, "5.00", token = cashier) }
            val (close, cash) = replies
            if (close.status == 201) {
                assertThat(cash.str("$.error")).isEqualTo("cash_day_closed")
            } else {
                assertThat(close.str("$.error")).isEqualTo("cash_expected_changed")
                assertThat(cash.status).isEqualTo(201)
            }
            val day = cashDay()
            if (day.str("$.state") == "CLOSED") {
                val confirmed = day.read<List<Map<String, Any?>>>("$.events").last { it["kind"] == "CONFIRM" }
                assertThat(confirmed["expected"].toString()).contains(day.str("$.expected.amount"))
            }
        } finally {
            reopenIfClosed()
        }
    }

    @Test
    fun `a past day with no cash can be counted and closed, and the day view adds up`() {
        val past = today().minusDays(200)
        val counter = tokenWith("tours-operator.cash-box:close")
        val counted = call("POST", "$staff/cash-box/$past/EGP/count", counter, """{"counted":0}""")
        assertThat(counted.status).withFailMessage(counted.toString()).isEqualTo(201)
        assertThat(counted.str("$.expected.amount")).isEqualTo("0.00")
        assertThat(call("POST", "$staff/cash-box/$past/EGP/confirm", admin()).status).isEqualTo(201)
        assertThat(call("POST", "$staff/cash-box/${today().plusDays(1)}/EGP/count", counter, """{"counted":0}""").str("$.error"))
            .isEqualTo("cash_day_in_future")
        assertThat(call("GET", "$staff/cash-box/recent?days=30").read<List<Any>>("$")).isNotEmpty
    }

    // ── container ────────────────────────────────────────────────────────────

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_finance_ops_test")
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
