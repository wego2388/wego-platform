package com.wego.toursoperator

import com.jayway.jsonpath.JsonPath
import com.wego.generated.jooq.tables.IdentityRole.IDENTITY_ROLE
import com.wego.generated.jooq.tables.IdentityRolePermission.IDENTITY_ROLE_PERMISSION
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
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/** SAF-04 Slice B read-only reports over real PostgreSQL: permissions, figures, CSV and no customer data. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ToursOperatorFinanceReportsHttpTest {
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
        // Re-check F5: EUR is trusted only within 20 % of the previous day's rate, so every test has one (50).
        dsl.execute(
            "INSERT INTO wego.tours_operator_fx_rate (id, rate_date, egp_per_eur, set_at) VALUES (?, ?, 50.0000, now())",
            UUID.randomUUID(),
            today().minusDays(1),
        )
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

    /** The booking API refuses past departures, so a booking is made today and moved onto a past departure. */
    private fun officeInPast(
        tour: UUID,
        pastSlot: UUID,
        day: LocalDate,
    ): String {
        val id = office(seedSlot(tour, today().plusDays(1L + (UUID.randomUUID().hashCode().toLong() and 0xFFF) % 300)), 1, 0)
        dsl.execute("UPDATE wego.tours_operator_booking SET slot_id = ?, tour_date = ? WHERE id = ?::uuid", pastSlot, day, id)
        return id
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
            """{"clientRequestId":"${UUID.randomUUID()}",$owner,"category":"$category","label":"$category ${unique()}","basis":"$basis","currency":"$currency",
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
        // SAF-04 G1: whoever records a CHARGE cannot pay that party the same day, so charges default to the second owner.
        token: String = admin2(),
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

    // ── report helpers ───────────────────────────────────────────────────────

    private val reports = "$staff/reports"
    private val customerPii =
        listOf("Fin Test", "+201234567890", "Hilton", "pii.person@example.com", "SECRET-NOTE", "Pii Person", "+201999888777")

    private fun get(
        path: String,
        token: String? = admin(),
    ) = call("GET", path, token)

    private fun csvOf(
        path: String,
        token: String = admin(),
    ): Pair<ByteArray, org.springframework.mock.web.MockHttpServletResponse> {
        val response =
            mockMvc
                .perform(MockMvcRequestBuilders.get(path).header("Authorization", "Bearer $token"))
                .andReturn()
                .response
        return response.contentAsByteArray to response
    }

    private fun assertNoPii(vararg bodies: String) {
        for (body in bodies) for (pii in customerPii) assertThat(body).doesNotContain(pii)
    }

    private fun reverseCollection(
        booking: String,
        entryId: String,
        reason: String,
        token: String = admin2(),
    ) = call(
        "POST",
        "$staff/bookings/$booking/collections/$entryId/reverse",
        token,
        """{"clientRequestId":"${UUID.randomUUID()}","reason":"$reason"}""",
    )

    private fun range(day: LocalDate = today()) = "from=$day&to=$day"

    // ── permissions ──────────────────────────────────────────────────────────

    @Test
    fun `every report and CSV refuses anonymous and unrelated accounts and enforces its own permission`() {
        val all = listOf("reversals", "cash-variances", "request-conversion", "departure-money")
        val none = tokenWith("tours-operator.tour:view")
        for (name in all) {
            for (suffix in listOf("", ".csv")) {
                val path = "$reports/$name$suffix?${range()}"
                assertThat(call("GET", path, null).status).describedAs(path).isEqualTo(401)
                assertThat(get(path, none).status).describedAs(path).isEqualTo(403)
            }
        }
        // reversals + departure money: payment:view only.
        val finance = tokenWith("tours-operator.payment:view")
        val booking = tokenWith("tours-operator.booking:view")
        val confirm = tokenWith("tours-operator.cash-box:confirm")
        val close = tokenWith("tours-operator.cash-box:close")
        for (name in listOf("reversals", "departure-money")) {
            for (suffix in listOf("", ".csv")) {
                val path = "$reports/$name$suffix?${range()}"
                assertThat(get(path, finance).status).describedAs(path).isEqualTo(200)
                for (other in listOf(booking, confirm, close)) assertThat(get(path, other).status).describedAs(path).isEqualTo(403)
            }
        }
        // cash variances: cash-box:confirm or payment:view, not the reception counter.
        for (suffix in listOf("", ".csv")) {
            val path = "$reports/cash-variances$suffix?${range()}"
            assertThat(get(path, confirm).status).isEqualTo(200)
            assertThat(get(path, finance).status).isEqualTo(200)
            assertThat(get(path, close).status).isEqualTo(403)
            assertThat(get(path, booking).status).isEqualTo(403)
        }
        // request conversion: booking:view like the inbox.
        for (suffix in listOf("", ".csv")) {
            val path = "$reports/request-conversion$suffix?${range()}"
            assertThat(get(path, booking).status).isEqualTo(200)
            assertThat(get(path, finance).status).isEqualTo(403)
            assertThat(get(path, close).status).isEqualTo(403)
        }
    }

    @Test
    fun `ranges are validated and reports are never cached`() {
        for (name in listOf("reversals", "cash-variances", "request-conversion", "departure-money")) {
            assertThat(get("$reports/$name?from=${today()}&to=${today().minusDays(1)}").status).isEqualTo(400)
            assertThat(get("$reports/$name?from=${today().minusDays(400)}&to=${today()}").status).isEqualTo(400)
            assertThat(get("$reports/$name.csv?${range()}&lang=fr").status).isEqualTo(400)
        }
        val (_, response) = csvOf("$reports/reversals.csv?${range()}")
        assertThat(response.getHeader("Cache-Control")).contains("no-store")
        val json =
            mockMvc
                .perform(
                    MockMvcRequestBuilders.get("$reports/reversals?${range()}").header("Authorization", "Bearer ${admin()}"),
                ).andReturn()
                .response
        assertThat(json.getHeader("Cache-Control")).contains("no-store")
    }

    // ── 1. reversals ─────────────────────────────────────────────────────────

    @Test
    fun `reversal report lists all four ledgers with who when why and amount, without customer data`() {
        setRate("50.0000")
        val slot = seedSlot(seedTour())
        val booking = office(slot)
        val eur = collect(booking, "30.00")
        val egp = collect(booking, "1000", currency = "EGP")
        assertThat(eur.status).isEqualTo(201)
        assertThat(egp.status).withFailMessage(egp.toString()).isEqualTo(201)
        val tag = unique()
        assertThat(reverseCollection(booking, eur.read("$.entry.id"), "=HYPERLINK-$tag").status).isEqualTo(201)
        assertThat(reverseCollection(booking, egp.read("$.entry.id"), "EGP-$tag").status).isEqualTo(201)

        val refundBooking = office(seedSlot(seedTour()), 1, 0)
        assertThat(collect(refundBooking, "35.00").status).isEqualTo(201)
        cancel(refundBooking)
        val refund =
            call(
                "POST",
                "$staff/bookings/$refundBooking/refunds",
                body = """{"clientRequestId":"${UUID.randomUUID()}","method":"CASH","amount":35.00,"currency":"EUR","reason":"refund"}""",
            )
        assertThat(refund.status).withFailMessage(refund.toString()).isEqualTo(201)
        val refundReverse =
            call(
                "POST",
                "$staff/bookings/$refundBooking/refunds/${refund.str("$.entry.id")}/reverse",
                admin2(),
                """{"clientRequestId":"${UUID.randomUUID()}","reason":"REFUND-$tag"}""",
            )
        assertThat(refundReverse.status).withFailMessage(refundReverse.toString()).isEqualTo(201)

        val driver = newDriver()
        assertThat(charge("drivers", driver, "800.00").status).isEqualTo(201)
        val payment = pay("drivers", driver, "500.00")
        assertThat(
            call(
                "POST",
                "$staff/payables/drivers/$driver/payments/${payment.str("$.id")}/reverse",
                admin2(),
                """{"clientRequestId":"${UUID.randomUUID()}","reason":"PAY-$tag"}""",
            ).status,
        ).isEqualTo(201)
        val adjustmentId =
            statement("drivers", driver).read<List<String>>("$.movements[?(@.kind == 'CHARGE')].adjustment.id").first()
        assertThat(
            call(
                "POST",
                "$staff/payables/drivers/$driver/adjustments/$adjustmentId/reverse",
                admin(),
                """{"clientRequestId":"${UUID.randomUUID()}","reason":"ADJ-$tag"}""",
            ).status,
        ).isEqualTo(201)

        val report = get("$reports/reversals?${range()}")
        assertThat(report.status).withFailMessage(report.toString()).isEqualTo(200)
        assertThat(report.str("$.basis")).isEqualTo("RECORDED_DAY")

        fun line(reason: String): Map<String, Any?> = report.read<List<Map<String, Any?>>>("$.lines[?(@.reason == '$reason')]").single()
        val collection = line("EGP-$tag")
        assertThat(collection["kind"]).isEqualTo("COLLECTION")
        assertThat(collection["reversedBy"]).isEqualTo("fin-admin2@example.com")
        assertThat(collection["originalBy"]).isEqualTo("fin-admin@example.com")
        assertThat(collection["lagDays"]).isEqualTo(0)
        assertThat(collection["cash"]).isEqualTo(true)
        @Suppress("UNCHECKED_CAST")
        val amount = collection["amount"] as Map<String, Any?>
        assertThat(amount).containsEntry("amount", "1000.00").containsEntry("currencyCode", "EGP")
        @Suppress("UNCHECKED_CAST")
        val amountEur = collection["amountEur"] as Map<String, Any?>
        assertThat(amountEur).containsEntry("amount", "20.00").containsEntry("currencyCode", "EUR")
        assertThat(line("=HYPERLINK-$tag")["cash"]).isEqualTo(true)
        assertThat(line("REFUND-$tag")["kind"]).isEqualTo("OFFICE_REFUND")
        assertThat(line("REFUND-$tag")["cash"]).isEqualTo(true)
        val driverLine = line("PAY-$tag")
        assertThat(driverLine["kind"]).isEqualTo("SETTLEMENT_PAYMENT")
        assertThat(driverLine["partyType"]).isEqualTo("DRIVER")
        assertThat(driverLine["amountEur"]).isNull()
        assertThat(line("ADJ-$tag")["kind"]).isEqualTo("ADJUSTMENT")
        // Totals keep currencies apart and add nothing across them.
        val totals = report.read<List<Map<String, Any?>>>("$.totals")
        assertThat(totals.map { it["currency"] to it["kind"] }).contains(
            "EGP" to "COLLECTION",
            "EUR" to "COLLECTION",
            "EGP" to "SETTLEMENT_PAYMENT",
        )
        // A range that does not include today shows none of them.
        assertThat(get("$reports/reversals?${range(today().minusDays(10))}").read<List<Any>>("$.lines")).isEmpty()
        assertNoPii(report.body)

        // CSV: same permission, BOM, header, formula guard, no PII.
        val (bytes, response) = csvOf("$reports/reversals.csv?${range()}")
        assertThat(response.status).isEqualTo(200)
        assertThat(response.contentType).isEqualToIgnoringCase("text/csv;charset=UTF-8")
        assertThat(response.getHeader("Content-Disposition")).contains("attachment").contains(".csv")
        assertThat(bytes.take(3)).containsExactly(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val csv = String(bytes, Charsets.UTF_8)
        assertThat(csv).startsWith("\uFEFF\"Kind\",\"Reversed at\"")
        assertThat(csv).contains("\"'=HYPERLINK-$tag\"").doesNotContain("\"=HYPERLINK-$tag\"")
        assertNoPii(csv)
        val arabic = String(csvOf("$reports/reversals.csv?${range()}&lang=ar").first, Charsets.UTF_8)
        assertThat(arabic).contains("\"النوع\"").contains("\"السبب\"")
    }

    // ── 2. cash-box variance history ─────────────────────────────────────────

    @Test
    fun `cash variances show expected counted difference and who counted and confirmed per closed day`() {
        val day = today().minusDays(45)
        val count =
            call("POST", "$staff/cash-box/$day/EGP/count", admin(), """{"counted":120.00,"note":"SECRET-NOTE"}""")
        assertThat(count.status).withFailMessage(count.toString()).isEqualTo(201)
        val open = get("$reports/cash-variances?${range(day)}")
        assertThat(open.status).withFailMessage(open.toString()).isEqualTo(200)
        assertThat(open.str("$.days[0].state")).isEqualTo("COUNTED")
        assertThat(open.read<Any?>("$.days[0].confirmedBy")).isNull()
        assertThat(open.read<List<Any>>("$.totals")).isEmpty()
        assertThat(call("POST", "$staff/cash-box/$day/EGP/confirm", admin2()).status).isEqualTo(201)

        val report = get("$reports/cash-variances?${range(day)}")
        assertThat(report.str("$.days[0].state")).isEqualTo("CLOSED")
        assertThat(report.str("$.days[0].expected.amount")).isEqualTo("0.00")
        assertThat(report.str("$.days[0].counted.amount")).isEqualTo("120.00")
        assertThat(report.str("$.days[0].difference.amount")).isEqualTo("120.00")
        assertThat(report.str("$.days[0].difference.currencyCode")).isEqualTo("EGP")
        assertThat(report.str("$.days[0].countedBy")).isEqualTo("fin-admin@example.com")
        assertThat(report.str("$.days[0].confirmedBy")).isEqualTo("fin-admin2@example.com")
        assertThat(report.str("$.days[0].countEvents")).isEqualTo("1")
        assertThat(report.str("$.totals[0].currency")).isEqualTo("EGP")
        assertThat(report.str("$.totals[0].closedDays")).isEqualTo("1")
        assertThat(report.str("$.totals[0].daysWithVariance")).isEqualTo("1")
        assertThat(report.str("$.totals[0].difference.amount")).isEqualTo("120.00")
        // The other currency has no box that day: nothing is mixed in.
        assertThat(report.read<List<Map<String, Any?>>>("$.days").map { it["currency"] }).containsOnly("EGP")

        // Reopen, recount: the history shows the reopen and the latest count.
        assertThat(call("POST", "$staff/cash-box/$day/EGP/reopen", admin2(), """{"reason":"SECRET-NOTE recount"}""").status).isEqualTo(201)
        assertThat(call("POST", "$staff/cash-box/$day/EGP/count", admin(), """{"counted":0.00}""").status).isEqualTo(201)
        val again = get("$reports/cash-variances?${range(day)}")
        assertThat(again.str("$.days[0].reopenCount")).isEqualTo("1")
        assertThat(again.str("$.days[0].countEvents")).isEqualTo("2")
        assertThat(again.str("$.days[0].difference.amount")).isEqualTo("0.00")
        assertNoPii(report.body, again.body)

        val (bytes, response) = csvOf("$reports/cash-variances.csv?${range(day)}")
        assertThat(response.status).isEqualTo(200)
        assertThat(bytes.take(3)).containsExactly(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val csv = String(bytes, Charsets.UTF_8)
        assertThat(csv).contains("\"Expected\"").contains("fin-admin@example.com")
        assertNoPii(csv)
        assertThat(String(csvOf("$reports/cash-variances.csv?${range(day)}&lang=ar").first, Charsets.UTF_8)).contains("\"المتوقع\"")
    }

    // ── 3. request conversion ────────────────────────────────────────────────

    @Test
    fun `request conversion counts statuses and the rate without any customer data`() {
        val tour = seedTour()
        val bookingId = office(seedSlot(tour, today().plusDays(3)), 1, 0)

        fun insert(
            status: String,
            booking: String? = null,
            createdDaysAgo: Int = 0,
        ) {
            val id = UUID.randomUUID()
            dsl.execute(
                """INSERT INTO wego.tours_operator_online_request
                   (id, reference, payload_hash, tour_id, preferred_date, adults_count, children_count, estimated_total, full_name, phone,
                    nationality, email, hotel_name, special_requests, locale, status, revision, booking_id, created_at, updated_at)
                   VALUES (?, ?, 'h', ?, ?, 1, 0, 35, 'Pii Person', '+201999888777', 'EG', 'pii.person@example.com', 'Hilton', 'SECRET-NOTE',
                           'en', ?, 0, ?::uuid, now() - make_interval(days => ?), now())""",
                id,
                "REQ-${unique()}",
                tour,
                today().plusDays(3),
                status,
                booking,
                createdDaysAgo,
            )
        }
        val before = get("$reports/request-conversion?from=${today().minusDays(60)}&to=${today().minusDays(50)}")
        assertThat(before.str("$.total")).isEqualTo("0")
        assertThat(before.read<Any?>("$.conversionPercent")).isNull()

        // Eight days ago: 1 NEW, 1 IN_PROGRESS, 1 CLOSED, 1 CONVERTED -> 25.0 %.
        insert("NEW", createdDaysAgo = 20)
        insert("IN_PROGRESS", createdDaysAgo = 20)
        insert("CLOSED", createdDaysAgo = 20)
        insert("CONVERTED", bookingId, createdDaysAgo = 20)
        // Outside the range: not counted.
        insert("NEW", createdDaysAgo = 30)
        val from = today().minusDays(20)
        val report = get("$reports/request-conversion?from=$from&to=$from")
        assertThat(report.status).withFailMessage(report.toString()).isEqualTo(200)
        assertThat(report.str("$.basis")).isEqualTo("CREATED_DAY")
        assertThat(report.str("$.total")).isEqualTo("4")
        assertThat(report.str("$.new")).isEqualTo("1")
        assertThat(report.str("$.inProgress")).isEqualTo("1")
        assertThat(report.str("$.closed")).isEqualTo("1")
        assertThat(report.str("$.converted")).isEqualTo("1")
        assertThat(report.str("$.conversionPercent")).isEqualTo("25.0")
        assertNoPii(report.body)
        assertThat(report.read<Map<String, Any?>>("$").keys).doesNotContain("fullName", "phone", "email", "specialRequests")

        val csv =
            String(
                csvOf("$reports/request-conversion.csv?from=$from&to=$from", tokenWith("tours-operator.booking:view")).first,
                Charsets.UTF_8,
            )
        assertThat(csv).startsWith("\uFEFF\"From\",\"To\",\"Total\"").contains("25.0")
        assertNoPii(csv)
        assertThat(
            String(csvOf("$reports/request-conversion.csv?from=$from&to=$from&lang=ar").first, Charsets.UTF_8),
        ).contains("\"نسبة التحويل %\"")
    }

    // ── 4. booked / collected / outstanding by tour day ──────────────────────

    @Test
    fun `departure money separates EUR and EGP and never mixes booked with received currencies`() {
        setRate("50.0000")
        val day = today().plusDays(200)
        val slot = seedSlot(seedTour(), day)
        val booking = office(slot, 2, 1)
        assertThat(collect(booking, "40.00").status).isEqualTo(201)
        assertThat(collect(booking, "1000", currency = "EGP").status).isEqualTo(201)
        val cancelled = office(slot, 1, 0)
        assertThat(collect(cancelled, "10.00", method = "CARD_TERMINAL", reference = "C-${unique()}").status).isEqualTo(201)
        cancel(cancelled)

        val report = get("$reports/departure-money?${range(day)}")
        assertThat(report.status).withFailMessage(report.toString()).isEqualTo(200)
        assertThat(report.str("$.basis")).isEqualTo("TOUR_DAY")
        assertThat(report.str("$.days[0].date")).isEqualTo(day.toString())
        assertThat(report.str("$.days[0].liveBookings")).isEqualTo("1")
        assertThat(report.str("$.days[0].cancelledBookings")).isEqualTo("1")
        assertThat(report.str("$.days[0].booked.amount")).isEqualTo("87.50")
        assertThat(report.str("$.days[0].collected.amount")).isEqualTo("60.00")
        assertThat(report.str("$.days[0].outstanding.amount")).isEqualTo("27.50")
        assertThat(report.str("$.days[0].booked.currencyCode")).isEqualTo("EUR")
        assertThat(report.str("$.days[0].retainedOnCancelled.amount")).isEqualTo("10.00")
        val received = report.read<List<Map<String, Any?>>>("$.days[0].received")
        assertThat(received.map { it["currency"] }).containsExactly("EUR", "EGP")
        @Suppress("UNCHECKED_CAST")
        assertThat((received[0]["amount"] as Map<String, Any?>)["amount"]).isEqualTo("40.00")
        @Suppress("UNCHECKED_CAST")
        assertThat((received[1]["amount"] as Map<String, Any?>)).containsEntry("amount", "1000.00").containsEntry("currencyCode", "EGP")
        @Suppress("UNCHECKED_CAST")
        assertThat((received[1]["settledEur"] as Map<String, Any?>)["amount"]).isEqualTo("20.00")
        assertThat(report.str("$.totals.outstanding.amount")).isEqualTo("27.50")
        // A day with nothing: empty, no totals.
        val empty = get("$reports/departure-money?${range(day.plusDays(1))}")
        assertThat(empty.read<List<Any>>("$.days")).isEmpty()
        assertThat(empty.read<Any?>("$.totals")).isNull()
        assertNoPii(report.body)

        val (bytes, response) = csvOf("$reports/departure-money.csv?${range(day)}")
        assertThat(response.status).isEqualTo(200)
        assertThat(bytes.take(3)).containsExactly(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val csv = String(bytes, Charsets.UTF_8)
        assertThat(csv).contains("\"Booked EUR\"").contains("87.50").contains("1000.00")
        assertNoPii(csv)
        assertThat(String(csvOf("$reports/departure-money.csv?${range(day)}&lang=ar").first, Charsets.UTF_8)).contains("\"المحجوز يورو\"")
    }

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
