package com.wego.toursoperator

import com.jayway.jsonpath.JsonPath
import com.wego.generated.jooq.tables.IdentityRole.IDENTITY_ROLE
import com.wego.generated.jooq.tables.IdentityRolePermission.IDENTITY_ROLE_PERMISSION
import com.wego.generated.jooq.tables.ToursOperatorDocumentPrint.TOURS_OPERATOR_DOCUMENT_PRINT
import com.wego.generated.jooq.tables.ToursOperatorOfficeCollection.TOURS_OPERATOR_OFFICE_COLLECTION
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
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
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

/**
 * WEGO-016-OPS2-D: printable office documents and their append-only print
 * register, over real PostgreSQL. Covers the permission split, numbering under
 * concurrency, reprint versioning, voucher validity, receipt masking, PII
 * minimisation per document type and the register itself.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ToursOperatorOfficeDocumentHttpTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var passwordHasher: PasswordHasher

    @Autowired private lateinit var dsl: DSLContext

    private val adminEmail = "doc-admin@example.com"
    private val password = "a-very-long-document-password-123"

    private lateinit var adminId: UUID

    @BeforeEach
    fun seedUsers() {
        adminId = seedUser(adminEmail, setOf("platform-admin"))
        seedUser("doc-admin2@example.com", setOf("platform-admin"))
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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

    private fun tokenWith(permission: String): String {
        val roleCode = "doc-test-${permission.replace(':', '-').replace('.', '-')}"
        if (dsl.fetchOne(IDENTITY_ROLE, IDENTITY_ROLE.CODE.eq(roleCode)) == null) {
            dsl
                .insertInto(IDENTITY_ROLE)
                .set(IDENTITY_ROLE.CODE, roleCode)
                .set(IDENTITY_ROLE.DESCRIPTION, "Test-only role for $permission")
                .execute()
            dsl
                .insertInto(IDENTITY_ROLE_PERMISSION)
                .set(IDENTITY_ROLE_PERMISSION.ROLE_CODE, roleCode)
                .set(IDENTITY_ROLE_PERMISSION.PERMISSION_CODE, permission)
                .execute()
        }
        val email = "$roleCode@example.com"
        seedUser(email, setOf(roleCode))
        return login(email)
    }

    private fun login(email: String): String {
        val body =
            mockMvc
                .post("/api/v1/identity/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"$email","password":"$password"}"""
                }.andReturn()
                .response.contentAsString
        return JsonPath.read(body, "$.token")
    }

    private fun adminToken() = login(adminEmail)

    private fun admin2() = login("doc-admin2@example.com")

    private fun seedTourAndSlot(
        suffix: String,
        date: LocalDate,
        capacity: Int = 40,
        slugTag: String = UUID.randomUUID().toString().take(8),
    ): Triple<UUID, UUID, String> {
        val tourId = UUID.randomUUID()
        val slotId = UUID.randomUUID()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl
            .insertInto(TOURS_OPERATOR_TOUR)
            .set(TOURS_OPERATOR_TOUR.ID, tourId)
            .set(TOURS_OPERATOR_TOUR.SLUG, "doc-test-$suffix-$slugTag")
            .set(TOURS_OPERATOR_TOUR.CATEGORY, "DESERT")
            .set(TOURS_OPERATOR_TOUR.DURATION_TEXT, "4 hours")
            .set(TOURS_OPERATOR_TOUR.PRICE_ADULT_CENTS, 3500L)
            .set(TOURS_OPERATOR_TOUR.PRICE_CHILD_CENTS, 1750L)
            .set(TOURS_OPERATOR_TOUR.CAPACITY, capacity)
            .set(TOURS_OPERATOR_TOUR.AVAILABLE_TIME_SLOTS, "MORNING")
            .set(TOURS_OPERATOR_TOUR.SORT_ORDER, 1)
            .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, true)
            .set(TOURS_OPERATOR_TOUR.CREATED_BY_USER_ID, adminId)
            .set(TOURS_OPERATOR_TOUR.CREATED_AT, now)
            .execute()
        dsl
            .insertInto(TOURS_OPERATOR_TOUR_SLOT)
            .set(TOURS_OPERATOR_TOUR_SLOT.ID, slotId)
            .set(TOURS_OPERATOR_TOUR_SLOT.TOUR_ID, tourId)
            .set(TOURS_OPERATOR_TOUR_SLOT.DATE, date)
            .set(TOURS_OPERATOR_TOUR_SLOT.TIME_SLOT, "MORNING")
            .set(TOURS_OPERATOR_TOUR_SLOT.CAPACITY, capacity)
            .set(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT, 0)
            .set(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED, false)
            .set(TOURS_OPERATOR_TOUR_SLOT.CREATED_AT, now)
            .execute()
        return Triple(tourId, slotId, "doc-test-$suffix-$slugTag")
    }

    private fun publishContent(
        tourId: UUID,
        locale: String,
        name: String,
        includes: String,
    ) {
        dsl.execute(
            """
            INSERT INTO wego.tours_operator_tour_content (tour_id, locale, stage, document, updated_at)
            VALUES (?, ?, 'PUBLISHED', ?::jsonb, now())
            """.trimIndent(),
            tourId,
            locale,
            """{"name":"$name","shortDescription":"Short","description":"Long","includes":["$includes"],"knowBeforeYouGo":["Bring water"],"meetingPoint":"Hotel lobby"}""",
        )
    }

    private fun officeBody(
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
        return """
            {"clientRequestId":"${UUID.randomUUID()}","slotId":"$slotId","adultsCount":$adults,"childrenCount":$children,
             "customer":{"fullName":"$name","phone":"$phone","nationality":"EG"},
             "hotelName":"$hotel","hotelRoom":"$room","locale":"en"$special}
            """.trimIndent()
    }

    private fun newOffice(
        slotId: UUID,
        name: String = "Ahmed Hassan",
        phone: String = "+201234567890",
        hotel: String = "Hilton Sharm Dreams",
        room: String = "312",
        adults: Int = 2,
        children: Int = 1,
        requests: String? = null,
    ): String =
        JsonPath.read(
            mockMvc
                .post("/api/v1/tours-operator/staff/bookings") {
                    header("Authorization", "Bearer ${adminToken()}")
                    contentType = MediaType.APPLICATION_JSON
                    content = officeBody(slotId, name, phone, hotel, room, adults, children, requests)
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString,
            "$.id",
        )

    private fun reference(bookingId: String): String =
        dsl.fetchOne("SELECT reference FROM wego.tours_operator_booking WHERE id = ?", UUID.fromString(bookingId))!!.get(0).toString()

    private fun collect(
        bookingId: String,
        amount: String,
        method: String = "CASH_AT_OFFICE",
        currency: String = "EUR",
        reference: String? = null,
        token: String = adminToken(),
    ): String {
        val ref = reference?.let { ""","reference":"$it"""" } ?: ""
        val rate =
            if (currency == "EGP") {
                val id =
                    dsl
                        .fetchOne(
                            "SELECT id FROM wego.tours_operator_fx_rate WHERE rate_date = (now() AT TIME ZONE 'Africa/Cairo')::date ORDER BY set_at DESC, id DESC LIMIT 1",
                        )!!
                        .get(0)
                        .toString()
                ""","fxRateId":"$id""""
            } else {
                ""
            }
        val body =
            mockMvc
                .post("/api/v1/tours-operator/staff/bookings/$bookingId/collections") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"clientRequestId":"${UUID.randomUUID()}","method":"$method","currency":"$currency","amount":$amount$ref$rate}"""
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        return JsonPath.read(body, "$.entry.id")
    }

    private fun cancel(bookingId: String) {
        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/cancel") {
                header("Authorization", "Bearer ${adminToken()}")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason":"Customer called the office"}"""
            }.andExpect { status { isOk() } }
    }

    private fun print(
        path: String,
        token: String = adminToken(),
        body: String = """{"language":"en"}""",
    ): ResultActionsDsl =
        mockMvc.post("/api/v1/tours-operator/documents/$path") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun printed(
        path: String,
        body: String = """{"language":"en"}""",
    ): String =
        print(path, body = body)
            .andExpect { status { isOk() } }
            .andReturn()
            .response.contentAsString

    private fun registerRows(
        type: String,
        subjectKey: String,
    ) = dsl
        .selectFrom(TOURS_OPERATOR_DOCUMENT_PRINT)
        .where(TOURS_OPERATOR_DOCUMENT_PRINT.DOCUMENT_TYPE.eq(type).and(TOURS_OPERATOR_DOCUMENT_PRINT.SUBJECT_KEY.eq(subjectKey)))
        .orderBy(TOURS_OPERATOR_DOCUMENT_PRINT.VERSION.asc())
        .fetch()

    private fun setRate(rate: String) {
        mockMvc
            .post("/api/v1/tours-operator/staff/fx-rate") {
                header("Authorization", "Bearer ${adminToken()}")
                contentType = MediaType.APPLICATION_JSON
                content = """{"egpPerEur":$rate}"""
            }.andExpect { status { is2xxSuccessful() } }
    }

    private val cairo: ZoneId = ZoneId.of("Africa/Cairo")

    private fun today(): LocalDate = LocalDate.now(cairo)

    // ── permissions ──────────────────────────────────────────────────────────

    @Test
    fun `document permissions split customer documents from operations documents`() {
        val (_, slotId, _) = seedTourAndSlot("perm", LocalDate.of(2027, 9, 1))
        val id = newOffice(slotId)
        val collectionId = collect(id, "10.00")
        val printOnly = tokenWith("tours-operator.document:print")
        val opsOnly = tokenWith("tours-operator.document:print-ops")
        val viewOnly = tokenWith("tours-operator.booking:view")

        mockMvc
            .post("/api/v1/tours-operator/documents/bookings/$id/voucher") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"language":"en"}"""
            }.andExpect { status { isUnauthorized() } }

        for (token in listOf(opsOnly, viewOnly)) {
            print("bookings/$id/voucher", token).andExpect { status { isForbidden() } }
            print("collections/$collectionId/receipt", token).andExpect { status { isForbidden() } }
            print("bookings/$id/cancellation-form", token).andExpect { status { isForbidden() } }
        }
        for (token in listOf(printOnly, viewOnly)) {
            print("slots/$slotId/pickup-manifest", token).andExpect { status { isForbidden() } }
            print("run-sheet", token, """{"date":"2027-09-01","language":"en"}""").andExpect { status { isForbidden() } }
        }
        print("bookings/$id/voucher", printOnly).andExpect { status { isOk() } }
        print("slots/$slotId/pickup-manifest", opsOnly).andExpect { status { isOk() } }
        // Refused calls recorded nothing: only the two allowed prints exist.
        assertThat(registerRows("VOUCHER", UUID.fromString(id).toString())).hasSize(1)
    }

    @Test
    fun `a malformed request is a 400 and unknown subjects are 404`() {
        print("bookings/${UUID.randomUUID()}/voucher").andExpect { status { isNotFound() } }
        print("collections/${UUID.randomUUID()}/receipt").andExpect { status { isNotFound() } }
        print("slots/${UUID.randomUUID()}/pickup-manifest").andExpect { status { isNotFound() } }
        print("bookings/${UUID.randomUUID()}/voucher", body = """{"language":"fr"}""").andExpect { status { isBadRequest() } }
        print("bookings/${UUID.randomUUID()}/voucher", body = "{}").andExpect { status { isBadRequest() } }
        print("run-sheet", body = """{"language":"en"}""").andExpect { status { isBadRequest() } }
    }

    // ── voucher ──────────────────────────────────────────────────────────────

    @Test
    fun `voucher carries the booking, published content and a QR url without personal data`() {
        val (tourId, slotId, _) = seedTourAndSlot("voucher", LocalDate.of(2027, 9, 2))
        publishContent(tourId, "en", "Desert Safari", "Hotel pickup")
        publishContent(tourId, "ar", "سفاري الصحراء", "الانتقال من الفندق")
        val id = newOffice(slotId)
        collect(id, "30.00")

        val response =
            print("bookings/$id/voucher")
                .andExpect {
                    status { isOk() }
                    header { string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")) }
                    jsonPath("$.document.type") { value("VOUCHER") }
                    jsonPath("$.document.number") { value(reference(id)) }
                    jsonPath("$.document.version") { value(1) }
                    jsonPath("$.document.copy") { value(false) }
                    jsonPath("$.document.language") { value("en") }
                    jsonPath("$.document.printedByEmail") { value(adminEmail) }
                    jsonPath("$.data.valid") { value(true) }
                    jsonPath("$.data.status") { value("CONFIRMED") }
                    jsonPath("$.data.tourNameAr") { value("سفاري الصحراء") }
                    jsonPath("$.data.hotelName") { value("Hilton Sharm Dreams") }
                    jsonPath("$.data.hotelRoom") { value("312") }
                    jsonPath("$.data.adultsCount") { value(2) }
                    jsonPath("$.data.includes[0]") { value("Hotel pickup") }
                    jsonPath("$.data.knowBeforeYouGo[0]") { value("Bring water") }
                    jsonPath("$.data.meetingPoint") { value("Hotel lobby") }
                    jsonPath("$.data.payment.kind") { value("OFFICE") }
                    jsonPath("$.data.payment.state") { value("PARTIALLY_PAID") }
                    jsonPath("$.data.payment.collected.amount") { value("30.00") }
                    jsonPath("$.data.payment.outstanding.amount") { value("57.50") }
                }.andReturn()
                .response.contentAsString
        val url: String = JsonPath.read(response, "$.data.myBookingUrl")
        assertThat(url).endsWith("/en/my-booking")
        assertThat(url).doesNotContain("STR-", "?", "#", "+20")
        // The customer's phone is never part of a voucher.
        assertThat(response).doesNotContain("+201234567890").doesNotContain("phone")

        // Arabic uses the Arabic content and the Arabic public page.
        val ar = printed("bookings/$id/voucher", """{"language":"ar"}""")
        assertThat(JsonPath.read<String>(ar, "$.data.contentLanguage")).isEqualTo("ar")
        assertThat(JsonPath.read<String>(ar, "$.data.includes[0]")).isEqualTo("الانتقال من الفندق")
        assertThat(JsonPath.read<String>(ar, "$.data.myBookingUrl")).endsWith("/ar/my-booking")
    }

    @Test
    fun `reprints keep the number, count versions and mark the copy`() {
        val (_, slotId, _) = seedTourAndSlot("reprint", LocalDate.of(2027, 9, 3))
        val id = newOffice(slotId)
        val first = printed("bookings/$id/voucher")
        val second = printed("bookings/$id/voucher", """{"language":"ar"}""")
        val third = printed("bookings/$id/voucher")

        assertThat(JsonPath.read<Int>(first, "$.document.version")).isEqualTo(1)
        assertThat(JsonPath.read<Boolean>(first, "$.document.copy")).isFalse()
        assertThat(JsonPath.read<Int>(second, "$.document.version")).isEqualTo(2)
        assertThat(JsonPath.read<Boolean>(second, "$.document.copy")).isTrue()
        assertThat(JsonPath.read<Int>(third, "$.document.version")).isEqualTo(3)
        assertThat(JsonPath.read<String>(second, "$.document.number")).isEqualTo(JsonPath.read<String>(first, "$.document.number"))
        assertThat(
            JsonPath.read<String>(third, "$.document.originalPrintedAt"),
        ).isEqualTo(JsonPath.read<String>(first, "$.document.printedAt"))

        val rows = registerRows("VOUCHER", UUID.fromString(id).toString())
        assertThat(rows.map { it.get(TOURS_OPERATOR_DOCUMENT_PRINT.VERSION) }).containsExactly(1, 2, 3)
        assertThat(rows.map { it.get(TOURS_OPERATOR_DOCUMENT_PRINT.LANGUAGE) }).containsExactly("en", "ar", "en")
        assertThat(rows.map { it.get(TOURS_OPERATOR_DOCUMENT_PRINT.DOCUMENT_NUMBER) }.toSet()).hasSize(1)
        assertThat(rows.all { it.get(TOURS_OPERATOR_DOCUMENT_PRINT.PRINTED_BY_USER_ID) == adminId }).isTrue()
    }

    @Test
    fun `concurrent reprints of one document get distinct consecutive versions`() {
        val (_, slotId, _) = seedTourAndSlot("reprint-race", LocalDate.of(2027, 9, 4))
        val id = newOffice(slotId)
        val token = adminToken()
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val futures =
            (1..8).map {
                executor.submit(
                    Callable {
                        start.await()
                        JsonPath.read<Int>(
                            print("bookings/$id/voucher", token)
                                .andExpect { status { isOk() } }
                                .andReturn()
                                .response.contentAsString,
                            "$.document.version",
                        )
                    },
                )
            }
        start.countDown()
        val versions = futures.map { it.get() }
        executor.shutdown()
        assertThat(versions.sorted()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8)
        assertThat(registerRows("VOUCHER", UUID.fromString(id).toString())).hasSize(8)
    }

    @Test
    fun `a cancelled booking prints a banner voucher without a QR and a draft is refused`() {
        val (_, slotId, _) = seedTourAndSlot("cancelled", LocalDate.of(2027, 9, 5))
        val id = newOffice(slotId)
        cancel(id)
        val response = printed("bookings/$id/voucher")
        assertThat(JsonPath.read<Boolean>(response, "$.data.valid")).isFalse()
        assertThat(JsonPath.read<String>(response, "$.data.status")).isEqualTo("CANCELLED")
        assertThat(JsonPath.read<Any?>(response, "$.data.myBookingUrl")).isNull()

        // An online booking still awaiting payment (NEW) is a draft: no voucher, nothing recorded.
        val online =
            JsonPath.read<String>(
                mockMvc
                    .post("/api/v1/tours-operator/bookings") {
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            """
                            {"slotId":"$slotId","adultsCount":1,"childrenCount":0,
                             "customer":{"fullName":"Online Customer","phone":"+201111111111","nationality":"EG"},
                             "hotelName":"Some Hotel","locale":"en"}
                            """.trimIndent()
                    }.andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString,
                "$.id",
            )
        print("bookings/$online/voucher")
            .andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("booking_not_confirmed") }
            }
        assertThat(registerRows("VOUCHER", online)).isEmpty()
    }

    // ── receipt ──────────────────────────────────────────────────────────────

    @Test
    fun `receipt masks a non-cash reference, shows rate and settled EUR and never card data`() {
        setRate("50.0000")
        val (_, slotId, _) = seedTourAndSlot("receipt", LocalDate.of(2027, 9, 6))
        val id = newOffice(slotId)
        val terminal = collect(id, "20.00", method = "CARD_TERMINAL", reference = "TERM-98765432")
        val egp = collect(id, "1000.00", method = "INSTAPAY", currency = "EGP", reference = "IPAB")
        val cash = collect(id, "10.00")

        val card = printed("collections/$terminal/receipt")
        assertThat(JsonPath.read<String>(card, "$.data.referenceMasked")).isEqualTo("****5432")
        assertThat(JsonPath.read<String>(card, "$.data.method")).isEqualTo("CARD_TERMINAL")
        assertThat(JsonPath.read<String>(card, "$.data.collectedBy")).isEqualTo(adminEmail)
        assertThat(JsonPath.read<String>(card, "$.data.collectedToDate.amount")).isEqualTo("20.00")
        assertThat(JsonPath.read<String>(card, "$.data.remainingAfter.amount")).isEqualTo("67.50")
        assertThat(JsonPath.read<Boolean>(card, "$.data.reversed")).isFalse()
        assertThat(card).doesNotContain("TERM-98765432").doesNotContain("98765432").doesNotContain("+201234567890")

        val egpDoc = printed("collections/$egp/receipt")
        assertThat(JsonPath.read<String>(egpDoc, "$.data.amountPaid.amount")).isEqualTo("1000.00")
        assertThat(JsonPath.read<String>(egpDoc, "$.data.amountPaid.currencyCode")).isEqualTo("EGP")
        assertThat(JsonPath.read<String>(egpDoc, "$.data.settledEur.amount")).isEqualTo("20.00")
        assertThat(JsonPath.read<String>(egpDoc, "$.data.fxRate")).isEqualTo("50.0000")
        // A short reference is fully masked and the page never reveals its length.
        assertThat(JsonPath.read<String>(egpDoc, "$.data.referenceMasked")).isEqualTo("****")
        // The balance is as of that payment, so a reprint of an earlier receipt never changes.
        assertThat(JsonPath.read<String>(egpDoc, "$.data.collectedToDate.amount")).isEqualTo("40.00")

        val cashDoc = printed("collections/$cash/receipt")
        assertThat(JsonPath.read<Any?>(cashDoc, "$.data.referenceMasked")).isNull()
        assertThat(JsonPath.read<String>(cashDoc, "$.data.remainingAfter.amount")).isEqualTo("37.50")
    }

    @Test
    fun `a reversed payment prints as reversed and links the reversal, a reversal itself has no receipt`() {
        val (_, slotId, _) = seedTourAndSlot("receipt-void", LocalDate.of(2027, 9, 7))
        val id = newOffice(slotId)
        val entry = collect(id, "15.00")
        val reversalBody =
            mockMvc
                .post("/api/v1/tours-operator/staff/bookings/$id/collections/$entry/reverse") {
                    header("Authorization", "Bearer ${admin2()}")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"clientRequestId":"${UUID.randomUUID()}","reason":"Counted wrong"}"""
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        val reversalId: String = JsonPath.read(reversalBody, "$.entry.id")

        val doc = printed("collections/$entry/receipt")
        assertThat(JsonPath.read<Boolean>(doc, "$.data.reversed")).isTrue()
        assertThat(JsonPath.read<String>(doc, "$.data.reversal.entryRef")).isEqualTo("REV-" + reversalId.take(8).uppercase())
        // The reason stays internal.
        assertThat(doc).doesNotContain("Counted wrong")

        print("collections/$reversalId/receipt")
            .andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("not_a_collection") }
            }
    }

    @Test
    fun `receipt numbers are sequential per year, immutable and unique under concurrency`() {
        val (_, slotId, _) = seedTourAndSlot("receipt-seq", LocalDate.of(2027, 9, 8), capacity = 80)
        val ids = (1..6).map { collect(newOffice(slotId, adults = 1, children = 0), "5.00") }
        val token = adminToken()
        val executor = Executors.newFixedThreadPool(6)
        val start = CountDownLatch(1)
        val numbers =
            ids
                .map { entry ->
                    executor.submit(
                        Callable {
                            start.await()
                            JsonPath.read<String>(
                                print(
                                    "collections/$entry/receipt",
                                    token,
                                ).andExpect { status { isOk() } }.andReturn().response.contentAsString,
                                "$.document.number",
                            )
                        },
                    )
                }.also { start.countDown() }
                .map { it.get() }
        executor.shutdown()

        val year = LocalDate.now(cairo).year
        assertThat(numbers).doesNotHaveDuplicates().allMatch { it.matches(Regex("RCT-$year-\\d{6}")) }
        val sequence = numbers.map { it.takeLast(6).toInt() }.sorted()
        assertThat(sequence.zipWithNext().all { (a, b) -> b == a + 1 }).describedAs("gapless: $sequence").isTrue()

        // Reprinting never changes the number; the next new receipt continues the sequence.
        val again = printed("collections/${ids.first()}/receipt")
        assertThat(JsonPath.read<String>(again, "$.document.number")).isEqualTo(numbers[0])
        assertThat(JsonPath.read<Int>(again, "$.document.version")).isEqualTo(2)
        val another = collect(newOffice(slotId, adults = 1, children = 0), "5.00")
        val next = JsonPath.read<String>(printed("collections/$another/receipt"), "$.document.number")
        assertThat(next.takeLast(6).toInt()).isEqualTo(sequence.last() + 1)
    }

    // ── run sheet and pickup manifest ────────────────────────────────────────

    @Test
    fun `run sheet lists guests, hotels and notes without phone numbers and skips cancelled bookings`() {
        val date = LocalDate.of(2027, 9, 9)
        val (tourId, slotId, _) = seedTourAndSlot("runsheet", date)
        publishContent(tourId, "en", "Run Sheet Safari", "x")
        newOffice(
            slotId,
            name = "Zed Zebra",
            phone = "+201000000001",
            hotel = "Zeta Hotel",
            room = "7",
            adults = 2,
            children = 0,
            requests = "Wheelchair user",
        )
        newOffice(slotId, name = "Amy Alpha", phone = "+201000000002", hotel = "Alpha Resort", room = "12", adults = 1, children = 2)
        val dropped =
            newOffice(slotId, name = "Gone Customer", phone = "+201000000003", hotel = "Gone Hotel", room = "1", adults = 1, children = 0)
        cancel(dropped)

        val body = printed("run-sheet", """{"date":"$date","language":"en"}""")
        assertThat(JsonPath.read<Int>(body, "$.data.totalGuests")).isEqualTo(5)
        assertThat(JsonPath.read<String>(body, "$.data.tours[0].tourNameEn")).isEqualTo("Run Sheet Safari")
        assertThat(JsonPath.read<Int>(body, "$.data.tours[0].departures[0].guests")).isEqualTo(5)
        assertThat(JsonPath.read<String>(body, "$.data.tours[0].departures[0].hotels[0].hotelName")).isEqualTo("Alpha Resort")
        assertThat(JsonPath.read<String>(body, "$.data.tours[0].departures[0].notes[0].text")).isEqualTo("Wheelchair user")
        assertThat(body)
            .doesNotContain("+2010000000")
            .doesNotContain("phone")
            .doesNotContain("Gone Customer")
            .doesNotContain("Gone Hotel")
        assertThat(JsonPath.read<String>(body, "$.document.number")).isEqualTo("RUN-20270909")
        assertThat(JsonPath.read<Int>(printed("run-sheet", """{"date":"$date","language":"ar"}"""), "$.document.version")).isEqualTo(2)
    }

    @Test
    fun `pickup manifest orders by hotel, carries phones and leaves driver and vehicle blank`() {
        val date = LocalDate.of(2027, 9, 10)
        val (_, slotId, _) = seedTourAndSlot("manifest", date)
        newOffice(slotId, name = "Zed Zebra", phone = "+201000000011", hotel = "Zeta Hotel", room = "7", adults = 2, children = 0)
        newOffice(slotId, name = "Amy Alpha", phone = "+201000000012", hotel = "Alpha Resort", room = "12", adults = 1, children = 2)
        val (_, otherSlot, _) = seedTourAndSlot("manifest-other", date)
        newOffice(otherSlot, name = "Other Tour", phone = "+201000000013", hotel = "Other Hotel", room = "3", adults = 1, children = 0)

        val body = printed("slots/$slotId/pickup-manifest")
        assertThat(JsonPath.read<Int>(body, "$.data.totalGuests")).isEqualTo(5)
        assertThat(JsonPath.read<List<String>>(body, "$.data.lines[*].hotelName")).containsExactly("Alpha Resort", "Zeta Hotel")
        assertThat(JsonPath.read<List<Int>>(body, "$.data.lines[*].order")).containsExactly(1, 2)
        assertThat(JsonPath.read<String>(body, "$.data.lines[0].phone")).isEqualTo("+201000000012")
        assertThat(JsonPath.read<String>(body, "$.data.lines[0].leadName")).isEqualTo("Amy Alpha")
        assertThat(JsonPath.read<Any?>(body, "$.data.driver")).isNull()
        assertThat(JsonPath.read<Any?>(body, "$.data.vehicle")).isNull()
        assertThat(body).doesNotContain("Other Tour")
        assertThat(JsonPath.read<String>(body, "$.document.number")).matches("PKM-\\d{4}-\\d{6}")
    }

    // ── cancellation / money-to-return form ──────────────────────────────────

    @Test
    fun `cancellation form computes the expected return from the policy and records no refund`() {
        // Tour two days ahead: the tour day starts between 24 and 48 hours away (half back).
        val (_, slotId, _) = seedTourAndSlot("cancel-half", today().plusDays(2))
        val id = newOffice(slotId)
        collect(id, "40.00")
        collect(id, "20.00", method = "MOBILE_WALLET", reference = "WAL-123456")
        cancel(id)
        val ledgerBefore =
            dsl.fetchCount(
                TOURS_OPERATOR_OFFICE_COLLECTION,
                TOURS_OPERATOR_OFFICE_COLLECTION.BOOKING_ID.eq(UUID.fromString(id)),
            )

        val doc = printed("bookings/$id/cancellation-form")
        assertThat(JsonPath.read<String>(doc, "$.data.collectedNet.amount")).isEqualTo("60.00")
        assertThat(JsonPath.read<String>(doc, "$.data.policy")).isEqualTo("STANDARD")
        assertThat(JsonPath.read<Int>(doc, "$.data.refundPercent")).isEqualTo(50)
        assertThat(JsonPath.read<String>(doc, "$.data.expectedReturn.amount")).isEqualTo("30.00")
        assertThat(JsonPath.read<Int>(doc, "$.data.collections.length()")).isEqualTo(2)
        assertThat(doc).doesNotContain("WAL-123456").doesNotContain("+201234567890").doesNotContain("phone")
        assertThat(JsonPath.read<String>(doc, "$.document.number")).matches("CXL-\\d{4}-\\d{6}")
        assertThat(dsl.fetchCount(TOURS_OPERATOR_OFFICE_COLLECTION, TOURS_OPERATOR_OFFICE_COLLECTION.BOOKING_ID.eq(UUID.fromString(id))))
            .isEqualTo(ledgerBefore)

        // Far ahead: full refund; the day before: nothing.
        val (_, farSlot, _) = seedTourAndSlot("cancel-full", LocalDate.of(2027, 9, 11))
        val far = newOffice(farSlot)
        collect(far, "40.00")
        cancel(far)
        assertThat(JsonPath.read<String>(printed("bookings/$far/cancellation-form"), "$.data.expectedReturn.amount")).isEqualTo("40.00")
        val (_, nearSlot, _) = seedTourAndSlot("cancel-none", today().plusDays(1))
        val near = newOffice(nearSlot)
        collect(near, "40.00")
        cancel(near)
        assertThat(JsonPath.read<String>(printed("bookings/$near/cancellation-form"), "$.data.expectedReturn.amount")).isEqualTo("0.00")
    }

    @Test
    fun `cancellation form is refused unless a cancelled office booking still holds collected cash`() {
        val (_, slotId, _) = seedTourAndSlot("cancel-refuse", LocalDate.of(2027, 9, 12))
        val live = newOffice(slotId)
        collect(live, "10.00")
        print("bookings/$live/cancellation-form")
            .andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("booking_not_cancelled") }
            }

        val nothing = newOffice(slotId)
        cancel(nothing)
        print("bookings/$nothing/cancellation-form")
            .andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("no_cash_collected") }
            }

        // Collected then fully reversed: net zero, nothing to return.
        val reversed = newOffice(slotId)
        val entry = collect(reversed, "10.00")
        mockMvc
            .post("/api/v1/tours-operator/staff/bookings/$reversed/collections/$entry/reverse") {
                header("Authorization", "Bearer ${admin2()}")
                contentType = MediaType.APPLICATION_JSON
                content = """{"clientRequestId":"${UUID.randomUUID()}","reason":"Wrong booking"}"""
            }.andExpect { status { isCreated() } }
        cancel(reversed)
        print("bookings/$reversed/cancellation-form")
            .andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("no_cash_collected") }
            }
        assertThat(registerRows("CANCELLATION_FORM", UUID.fromString(reversed).toString())).isEmpty()
    }

    // ── the register ─────────────────────────────────────────────────────────

    @Test
    fun `the print register is append-only and holds no customer data`() {
        val (_, slotId, _) = seedTourAndSlot("register", LocalDate.of(2027, 9, 13))
        val id = newOffice(slotId, name = "Register Customer", phone = "+201999888777", hotel = "Register Hotel")
        printed("bookings/$id/voucher")
        val rows = dsl.fetch("SELECT * FROM wego.tours_operator_document_print WHERE subject_key = ?", id)
        assertThat(rows).hasSize(1)
        val text = rows.toString()
        assertThat(text).doesNotContain("Register Customer", "+201999888777", "Register Hotel")

        assertThatThrownBy {
            dsl.execute("UPDATE wego.tours_operator_document_print SET document_number = 'X' WHERE subject_key = ?", id)
        }.hasMessageContaining("append-only")
        assertThatThrownBy {
            dsl.execute("DELETE FROM wego.tours_operator_document_print WHERE subject_key = ?", id)
        }.hasMessageContaining("append-only")
        assertThat(registerRows("VOUCHER", id)).hasSize(1)
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_office_document_test")
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
