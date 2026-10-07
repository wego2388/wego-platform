package com.wego.toursoperator.application

import com.wego.toursoperator.domain.Booking
import com.wego.toursoperator.domain.BookingChannel
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.BookingStatus
import com.wego.toursoperator.domain.CancellationRefund
import com.wego.toursoperator.domain.ContentLocale
import com.wego.toursoperator.domain.ContentStage
import com.wego.toursoperator.domain.Money
import com.wego.toursoperator.domain.OfficeCollectionKind
import com.wego.toursoperator.domain.OfficePaymentSummary
import com.wego.toursoperator.domain.OfficeRefund
import com.wego.toursoperator.domain.OfficeRefundKind
import com.wego.toursoperator.domain.PaidCurrency
import com.wego.toursoperator.domain.PartyRef
import com.wego.toursoperator.domain.PaymentStatus
import com.wego.toursoperator.domain.Tour
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourSlotId
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * WEGO-016-OPS2-D: the data behind the printable office documents, and the
 * append-only register of every print. The browser renders and prints; no PDF
 * and no customer data is stored here. Every call records one print (version 1
 * the first time, then reprints that keep the document number) in the same
 * transaction that reads the data, so the register can never disagree with
 * what was handed out.
 *
 * Nothing here logs customer data or changes a booking, a payment or a ledger.
 */
class OfficeDocumentService(
    private val bookingRepository: BookingRepository,
    private val tourRepository: TourRepository,
    private val slotRepository: TourSlotRepository,
    private val contentRepository: TourContentRepository,
    private val collectionRepository: OfficeCollectionRepository,
    private val fxRateRepository: FxRateRepository,
    private val paymentRepository: PaymentRepository,
    private val printRepository: DocumentPrintRepository,
    private val assignmentService: AssignmentService,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
    private val siteBaseUrl: String,
    private val refundRepository: OfficeRefundRepository? = null,
    private val payablesService: PayablesService? = null,
) {
    fun voucher(
        bookingId: BookingId,
        language: DocumentLanguage,
        actorUserId: UUID,
    ): DocumentResult<VoucherData> =
        transactionRunner.runInTransaction {
            val booking = bookingRepository.findById(bookingId) ?: return@runInTransaction DocumentResult.NotFound
            // A draft (NEW, awaiting online payment) or an expired booking is never a voucher.
            if (booking.status == BookingStatus.NEW || booking.status == BookingStatus.EXPIRED) {
                return@runInTransaction DocumentResult.Refused("booking_not_confirmed")
            }
            val valid = booking.status != BookingStatus.CANCELLED
            val names = tourNames(listOf(booking.tourId))
            val content = publishedContent(booking.tourId, language)
            val payment = voucherPayment(booking)
            val data =
                VoucherData(
                    reference = booking.reference,
                    status = booking.status,
                    valid = valid,
                    channel = booking.channel,
                    tourNameEn = names.en(booking.tourId),
                    tourNameAr = names.ar(booking.tourId),
                    tourDate = booking.tourDate,
                    timeSlot = booking.timeSlot,
                    adultsCount = booking.pricing.adultsCount,
                    childrenCount = booking.pricing.childrenCount,
                    unit = booking.pricing.unit?.let { VoucherUnit(it.optionLabel, it.unitCount) },
                    customerName = booking.customer.fullName,
                    hotelName = booking.hotelName,
                    hotelRoom = booking.hotelRoom,
                    totalPrice = eur(booking.pricing.totalEur),
                    payment = payment,
                    contentLanguage = content?.first?.code ?: language.code,
                    includes = content?.second?.includes.orEmpty(),
                    excludes = content?.second?.excludes.orEmpty(),
                    knowBeforeYouGo = content?.second?.knowBeforeYouGo.orEmpty(),
                    meetingPoint = content?.second?.meetingPoint,
                    myBookingUrl = if (valid) "${siteBaseUrl.trimEnd('/')}/${language.code}/my-booking" else null,
                )
            val stamp = stamp(DocumentType.VOUCHER, booking.id.value.toString(), language, actorUserId) { booking.reference }
            DocumentResult.Ready(stamp, data)
        }

    fun receipt(
        collectionId: UUID,
        language: DocumentLanguage,
        actorUserId: UUID,
    ): DocumentResult<ReceiptData> =
        transactionRunner.runInTransaction {
            val entry = collectionRepository.findById(collectionId) ?: return@runInTransaction DocumentResult.NotFound
            if (entry.kind != OfficeCollectionKind.COLLECTION) return@runInTransaction DocumentResult.Refused("not_a_collection")
            val booking = bookingRepository.findById(entry.bookingId) ?: return@runInTransaction DocumentResult.NotFound
            val ledger = collectionRepository.findByBooking(booking.id)
            val reversal = ledger.firstOrNull { it.reversesCollectionId == entry.id }
            // Balance as of this payment, so a reprint never shows a different "remaining" than the original.
            val upToHere = ledger.takeWhile { it.id != entry.id } + entry
            val summary = OfficePaymentSummary.of(booking.pricing.totalEur, upToHere)
            val names = tourNames(listOf(booking.tourId))
            val data =
                ReceiptData(
                    bookingReference = booking.reference,
                    tourNameEn = names.en(booking.tourId),
                    tourNameAr = names.ar(booking.tourId),
                    tourDate = booking.tourDate,
                    customerName = booking.customer.fullName,
                    method = entry.method,
                    currencyPaid = entry.currencyPaid,
                    amountPaid = DocMoney(entry.amountPaid.amount.toPlainString(), entry.currencyPaid.name),
                    settledEur = eur(entry.amount),
                    fxRate = entry.fxRate?.egpPerEur?.toPlainString(),
                    referenceMasked = entry.reference?.let(::maskReference),
                    recordedAt = entry.recordedAt,
                    collectedBy = entry.recordedByEmail,
                    bookingTotal = eur(booking.pricing.totalEur),
                    collectedToDate = eur(summary.collected),
                    remainingAfter = eur(summary.outstanding),
                    reversed = reversal != null,
                    reversal = reversal?.let { ReceiptReversal(entryRef(it.id), it.recordedAt) },
                )
            val stamp =
                stamp(DocumentType.RECEIPT, entry.id.toString(), language, actorUserId) {
                    printRepository.allocateNumber(DocumentType.RECEIPT, year())
                }
            DocumentResult.Ready(stamp, data)
        }

    fun runSheet(
        date: LocalDate,
        language: DocumentLanguage,
        actorUserId: UUID,
    ): DocumentResult<RunSheetData> =
        transactionRunner.runInTransaction {
            val live = liveBookings(date)
            val names = tourNames(live.map { it.tourId }.distinct())
            val nets = officeNets(live)
            val tours =
                live
                    .groupBy { it.tourId }
                    .map { (tourId, bookings) ->
                        val departures =
                            bookings
                                .groupBy { it.timeSlot }
                                .toSortedMap()
                                .map { (slot, inSlot) ->
                                    val sorted =
                                        inSlot.sortedWith(
                                            compareBy({ it.hotelName.lowercase() }, { it.hotelRoom ?: "" }, { it.reference }),
                                        )
                                    RunSheetDeparture(
                                        timeSlot = slot,
                                        guests = sorted.sumOf { it.pricing.guests },
                                        hotels =
                                            sorted
                                                .groupBy { it.hotelName }
                                                .map { (hotel, rows) -> RunSheetHotel(hotel, rows.sumOf { it.pricing.guests }) },
                                        lines =
                                            sorted.map {
                                                RunSheetLine(
                                                    it.reference,
                                                    it.customer.fullName,
                                                    it.hotelName,
                                                    it.hotelRoom,
                                                    it.pricing.guests,
                                                    paymentDue(it, nets),
                                                )
                                            },
                                        notes =
                                            sorted.mapNotNull { b ->
                                                b.specialRequests
                                                    ?.trim()
                                                    ?.takeIf { it.isNotEmpty() }
                                                    ?.let { RunSheetNote(b.reference, it) }
                                            },
                                    )
                                }
                        RunSheetTour(tourId.value, names.en(tourId), names.ar(tourId), departures.sumOf { it.guests }, departures)
                    }.sortedBy { it.tourNameEn }
            val data = RunSheetData(date, tours.sumOf { it.guests }, tours)
            val stamp =
                stamp(
                    DocumentType.RUN_SHEET,
                    date.toString(),
                    language,
                    actorUserId,
                    fingerprint(
                        live.map {
                            "${it.id.value}|${it.slotId.value}|${it.timeSlot}|${it.pricing.guests}|${paymentDue(it, nets)}|" +
                                "${it.hotelName}|${it.hotelRoom}|${it.specialRequests}"
                        },
                    ),
                ) { "RUN-${date.format(DateTimeFormatter.BASIC_ISO_DATE)}" }
            DocumentResult.Ready(stamp, data)
        }

    fun pickupManifest(
        slotId: TourSlotId,
        language: DocumentLanguage,
        actorUserId: UUID,
    ): DocumentResult<PickupManifestData> =
        transactionRunner.runInTransaction {
            val slot = slotRepository.findById(slotId) ?: return@runInTransaction DocumentResult.NotFound
            val rows =
                liveBookings(slot.date)
                    .filter { it.slotId == slot.id }
                    .sortedWith(compareBy({ it.hotelName.lowercase() }, { it.hotelRoom ?: "" }, { it.reference }))
            val names = tourNames(listOf(slot.tourId))
            val assigned = assignmentService.resolved(slot.id)
            val data =
                PickupManifestData(
                    slotId = slot.id.value,
                    date = slot.date,
                    timeSlot = slot.timeSlot,
                    tourNameEn = names.en(slot.tourId),
                    tourNameAr = names.ar(slot.tourId),
                    totalGuests = rows.sumOf { it.pricing.guests },
                    lines =
                        rows.mapIndexed { index, b ->
                            PickupLine(
                                index + 1,
                                b.reference,
                                b.hotelName,
                                b.hotelRoom,
                                b.customer.fullName,
                                b.customer.phone,
                                b.pricing.guests,
                            )
                        },
                    driver = assigned?.driver?.name,
                    vehicle = assigned?.vehicle?.display,
                )
            val stamp =
                stamp(
                    DocumentType.PICKUP_MANIFEST,
                    slot.id.value.toString(),
                    language,
                    actorUserId,
                    fingerprint(
                        rows.map {
                            "${it.id.value}|${it.pricing.guests}|${it.hotelName}|${it.hotelRoom}"
                        } + "assignment|${assigned?.driver?.id}|${assigned?.vehicle?.id}",
                    ),
                ) {
                    printRepository.allocateNumber(DocumentType.PICKUP_MANIFEST, year())
                }
            DocumentResult.Ready(stamp, data)
        }

    /**
     * The driver's sheet of one departure (OPS2-E). Needs an assigned driver. Shows customer name,
     * hotel and room, stops alphabetical by hotel; never a customer phone, e-mail, price or payment state.
     */
    fun driverSheet(
        slotId: TourSlotId,
        language: DocumentLanguage,
        actorUserId: UUID,
    ): DocumentResult<DriverSheetData> =
        transactionRunner.runInTransaction {
            val slot = slotRepository.findById(slotId) ?: return@runInTransaction DocumentResult.NotFound
            val assigned = assignmentService.resolved(slot.id)
            val driver = assigned?.driver ?: return@runInTransaction DocumentResult.Refused("no_driver_assigned")
            val rows =
                liveBookings(slot.date)
                    .filter { it.slotId == slot.id }
                    .sortedWith(compareBy({ hotelKey(it.hotelName) }, { it.hotelRoom ?: "" }, { it.reference }))
            val names = tourNames(listOf(slot.tourId))
            // Stops are alphabetical by hotel; "Hilton " and "hilton" are one stop, shown with the first spelling.
            val stops =
                rows
                    .groupBy { hotelKey(it.hotelName) }
                    .values
                    .mapIndexed { index, parties ->
                        DriverSheetStop(
                            order = index + 1,
                            hotelName = parties.first().hotelName.trim(),
                            guests = parties.sumOf { it.pricing.guests },
                            parties = parties.map { DriverSheetGuest(it.reference, it.customer.fullName, it.hotelRoom, it.pricing.guests) },
                        )
                    }
            val data =
                DriverSheetData(
                    slotId = slot.id.value,
                    date = slot.date,
                    timeSlot = slot.timeSlot,
                    tourNameEn = names.en(slot.tourId),
                    tourNameAr = names.ar(slot.tourId),
                    driverName = driver.name,
                    vehicle = assigned.vehicle?.let { DriverSheetVehicle(it.display, it.seats) },
                    totalGuests = rows.sumOf { it.pricing.guests },
                    stops = stops,
                )
            val stamp =
                stamp(
                    DocumentType.DRIVER_SHEET,
                    slot.id.value.toString(),
                    language,
                    actorUserId,
                    fingerprint(
                        rows.map { "${it.id.value}|${it.pricing.guests}|${it.hotelName}|${it.hotelRoom}|${it.customer.fullName}" } +
                            "assignment|${driver.id}|${assigned.vehicle?.id}",
                    ),
                ) { printRepository.allocateNumber(DocumentType.DRIVER_SHEET, year()) }
            DocumentResult.Ready(stamp, data)
        }

    /**
     * The order for one supplier of one departure (OPS2-E). The supplier must be assigned to the
     * departure. No customer personal data, no customer special requests and no agreed price: only
     * guest counts and the staff-written supplier note of the assignment.
     */
    fun supplierOrder(
        slotId: TourSlotId,
        supplierId: UUID,
        language: DocumentLanguage,
        actorUserId: UUID,
    ): DocumentResult<SupplierOrderData> =
        transactionRunner.runInTransaction {
            val slot = slotRepository.findById(slotId) ?: return@runInTransaction DocumentResult.NotFound
            val assigned = assignmentService.resolved(slot.id)
            val supplier =
                assigned?.suppliers?.firstOrNull { it.id == supplierId }
                    ?: return@runInTransaction DocumentResult.Refused("supplier_not_assigned")
            val note = assigned.assignment.supplierNote
            val rows = liveBookings(slot.date).filter { it.slotId == slot.id }
            val names = tourNames(listOf(slot.tourId))
            val units =
                rows
                    .mapNotNull { it.pricing.unit }
                    .groupBy { it.optionLabel }
                    .map { (label, list) -> SupplierOrderUnit(label, list.sumOf { it.unitCount }) }
                    .sortedBy { it.optionLabel }
            val data =
                SupplierOrderData(
                    slotId = slot.id.value,
                    date = slot.date,
                    timeSlot = slot.timeSlot,
                    tourNameEn = names.en(slot.tourId),
                    tourNameAr = names.ar(slot.tourId),
                    supplier =
                        SupplierOrderSupplier(
                            supplier.code,
                            supplier.name,
                            supplier.serviceType,
                            supplier.contactPerson,
                            supplier.confirmationChannel,
                            supplier.noticeHours,
                        ),
                    totalGuests = rows.sumOf { it.pricing.guests },
                    adults = rows.sumOf { it.pricing.adultsCount },
                    children = rows.sumOf { it.pricing.childrenCount },
                    units = units,
                    // Never the customers' own special requests (they stay on the internal run sheet).
                    supplierNote = note,
                )
            val stamp =
                stamp(
                    DocumentType.SUPPLIER_ORDER,
                    "${slot.id.value}:$supplierId",
                    language,
                    actorUserId,
                    fingerprint(
                        rows.map {
                            "${it.id.value}|${it.pricing.adultsCount}|${it.pricing.childrenCount}|${it.pricing.unit?.optionLabel}|${it.pricing.unit?.unitCount}"
                        } + "note|${note.orEmpty()}",
                    ),
                ) { printRepository.allocateNumber(DocumentType.SUPPLIER_ORDER, year()) }
            DocumentResult.Ready(stamp, data)
        }

    fun cancellationForm(
        bookingId: BookingId,
        language: DocumentLanguage,
        actorUserId: UUID,
    ): DocumentResult<CancellationFormData> =
        transactionRunner.runInTransaction {
            val booking = bookingRepository.findById(bookingId) ?: return@runInTransaction DocumentResult.NotFound
            if (booking.channel != BookingChannel.OFFICE) return@runInTransaction DocumentResult.Refused("not_an_office_booking")
            val cancelledAt = booking.cancelledAt
            if (booking.status != BookingStatus.CANCELLED || cancelledAt == null) {
                return@runInTransaction DocumentResult.Refused("booking_not_cancelled")
            }
            val ledger = collectionRepository.findByBooking(booking.id)
            val net = OfficePaymentSummary.netCollected(ledger)
            if (net.signum() <= 0) return@runInTransaction DocumentResult.Refused("no_cash_collected")
            val tour = tourRepository.findById(booking.tourId)
            val policy = tour?.cancellationPolicy ?: com.wego.toursoperator.domain.CancellationPolicy.STANDARD
            // Owner-approved assumed departure hour per time slot (2026-10-07).
            val departure =
                booking.tourDate
                    .atTime(booking.timeSlot.assumedDepartureHour, 0)
                    .atZone(CAIRO)
                    .toInstant()
            val hours = Duration.between(cancelledAt, departure).toHours()
            val percent = CancellationRefund.percent(policy, hours)
            val expected = net.multiply(BigDecimal(percent)).divide(BigDecimal(100), Money.REQUIRED_SCALE, RoundingMode.HALF_UP)
            val rate = fxRateRepository.latestFor(FxRateService.todayInSharm(clock))
            val egp = rate?.let { expected.multiply(it.egpPerEur).setScale(Money.REQUIRED_SCALE, RoundingMode.HALF_UP) }
            val names = tourNames(listOf(booking.tourId))
            val refunds = refundRepository?.findByBooking(booking.id).orEmpty()
            val refunded = OfficeRefund.netRefunded(refunds)
            val remaining = expected.subtract(refunded).max(BigDecimal.ZERO.setScale(Money.REQUIRED_SCALE))
            val returnState =
                when {
                    expected.signum() == 0 && refunded.signum() == 0 -> "NONE_DUE"
                    refunded.signum() == 0 -> "NOT_RETURNED"
                    refunded >= expected -> "RETURNED"
                    else -> "PARTIALLY_RETURNED"
                }
            val data =
                CancellationFormData(
                    reference = booking.reference,
                    tourNameEn = names.en(booking.tourId),
                    tourNameAr = names.ar(booking.tourId),
                    tourDate = booking.tourDate,
                    timeSlot = booking.timeSlot,
                    customerName = booking.customer.fullName,
                    cancelledAt = cancelledAt,
                    cancellationReason = checkNotNull(booking.cancellationReason),
                    collections =
                        ledger.map {
                            CancellationCollectionLine(
                                it.recordedAt,
                                it.kind == OfficeCollectionKind.REVERSAL,
                                it.method,
                                it.currencyPaid,
                                DocMoney(it.amountPaid.amount.toPlainString(), it.currencyPaid.name),
                                eur(it.amount),
                            )
                        },
                    collectedNet = DocMoney(net.setScale(Money.REQUIRED_SCALE).toPlainString(), "EUR"),
                    policy = policy.name,
                    hoursBeforeTour = hours,
                    refundPercent = percent,
                    expectedReturn = DocMoney(expected.toPlainString(), "EUR"),
                    todayRate = rate?.egpPerEur?.toPlainString(),
                    expectedReturnEgp = egp?.let { DocMoney(it.toPlainString(), "EGP") },
                    refunds =
                        refunds.map {
                            CancellationRefundLine(
                                it.recordedAt,
                                it.kind == OfficeRefundKind.REVERSAL,
                                it.method,
                                DocMoney(it.amountPaid.amount.toPlainString(), it.currencyPaid.name),
                                eur(it.amount),
                            )
                        },
                    refundedNet = DocMoney(refunded.toPlainString(), "EUR"),
                    remainingToReturn = DocMoney(remaining.toPlainString(), "EUR"),
                    returnState = returnState,
                )
            // Once money has been returned a reprint must say REVISED, not COPY; before that it stays a plain copy.
            val refundPrint = if (refunds.isEmpty()) null else fingerprint(refunds.map { "${it.id}|${it.kind}|${it.amount.amount}" })
            val stamp =
                stamp(DocumentType.CANCELLATION_FORM, booking.id.value.toString(), language, actorUserId, refundPrint) {
                    printRepository.allocateNumber(DocumentType.CANCELLATION_FORM, year())
                }
            DocumentResult.Ready(stamp, data)
        }

    /**
     * Settlement statement of a supplier or driver for [from]..[to] (OPS2-F):
     * opening balance, what each departure, adjustment and payment added or
     * removed, and the closing balance, per currency (EGP and EUR never mixed).
     * No customer data: departures show tour, window and guest count only.
     */
    fun settlementStatement(
        party: PartyRef,
        from: LocalDate,
        to: LocalDate,
        language: DocumentLanguage,
        actorUserId: UUID,
    ): DocumentResult<SettlementStatementData> =
        transactionRunner.runInTransaction {
            val payables = checkNotNull(payablesService)
            val statement = payables.statement(party, from, to) ?: return@runInTransaction DocumentResult.NotFound
            val names = tourNames(statement.movements.mapNotNull { it.owedLine?.tourId }.distinct())

            fun money(
                amount: BigDecimal,
                currency: PaidCurrency,
            ) = DocMoney(amount.setScale(Money.REQUIRED_SCALE).toPlainString(), currency.name)
            val lines =
                statement.movements.map { m ->
                    val owed = m.owedLine
                    StatementLine(
                        date = m.date,
                        kind = m.kind.name,
                        amount = money(m.signed, m.currency),
                        tourNameEn = owed?.let { names.en(it.tourId) },
                        tourNameAr = owed?.let { names.ar(it.tourId) },
                        timeSlot = owed?.timeSlot,
                        guests = owed?.guests,
                        method = m.payment?.method?.name,
                        reference = m.payment?.reference?.let(::maskReference),
                        text = m.adjustment?.reason ?: m.payment?.let { it.reason ?: it.note },
                    )
                }
            val data =
                SettlementStatementData(
                    partyType = party.type.name,
                    partyName = statement.info.name,
                    partyCode = statement.info.code,
                    from = from,
                    to = to,
                    balances =
                        statement.balances.map {
                            StatementBalance(
                                it.currency,
                                money(it.opening, it.currency),
                                money(it.owed, it.currency),
                                money(it.paid, it.currency),
                                money(it.closing, it.currency),
                            )
                        },
                    lines = lines,
                    openIssues = statement.issues.size,
                )
            val stamp =
                stamp(
                    DocumentType.SETTLEMENT_STATEMENT,
                    "${party.key}:$from:$to",
                    language,
                    actorUserId,
                    fingerprint(
                        lines.map { "${it.date}|${it.kind}|${it.amount.amount}|${it.amount.currencyCode}|${it.guests}" } +
                            "issues|${data.openIssues}",
                    ),
                ) { printRepository.allocateNumber(DocumentType.SETTLEMENT_STATEMENT, year()) }
            DocumentResult.Ready(stamp, data)
        }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** One driver-sheet stop per hotel whatever the case or spacing staff typed. */
    private fun hotelKey(hotel: String): String = hotel.trim().replace(WHITESPACE, " ").lowercase()

    /** Records this print. The first one allocates the immutable number; reprints reuse it and count up. */
    private fun stamp(
        type: DocumentType,
        subjectKey: String,
        language: DocumentLanguage,
        actorUserId: UUID,
        fingerprint: String? = null,
        newNumber: () -> String,
    ): DocumentStamp {
        printRepository.lockSubject(type, subjectKey)
        val original = printRepository.findOriginal(type, subjectKey)
        val latest = printRepository.findLatest(type, subjectKey)
        // The register stores microseconds; print exactly what is stored so a reprint's "original" date matches.
        val now = Instant.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS)
        // A run sheet / manifest is rebuilt from live bookings: a reprint whose content differs from the
        // previous print is REVISED, an identical one stays a COPY. Vouchers and receipts carry no fingerprint.
        val revised = latest != null && fingerprint != null && latest.contentFingerprint != fingerprint
        val record =
            DocumentPrintRecord(
                id = UUID.randomUUID(),
                type = type,
                subjectKey = subjectKey,
                number = original?.number ?: newNumber(),
                version = (latest?.version ?: 0) + 1,
                language = language,
                printedByUserId = actorUserId,
                printedByEmail = printRepository.emailOf(actorUserId),
                printedAt = now,
                contentFingerprint = fingerprint,
            )
        printRepository.append(record)
        return DocumentStamp(
            type = type,
            number = record.number,
            version = record.version,
            language = language,
            copy = record.version > 1 && !revised,
            revised = revised,
            originalPrintedAt = original?.printedAt ?: now,
            printedAt = now,
            printedByEmail = record.printedByEmail,
        )
    }

    /**
     * SHA-256 over every value the document prints that can change (booking,
     * slot, guests, payment due, hotel/room, requests). Only the digest is
     * stored, never the inputs, so the register holds no customer data.
     */
    private fun fingerprint(parts: List<String>): String =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(parts.sorted().joinToString("\n").toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun year(): Int = LocalDate.now(clock.withZone(CAIRO)).year

    private fun voucherPayment(booking: Booking): VoucherPayment {
        if (booking.channel == BookingChannel.OFFICE) {
            val net = collectionRepository.netCollected(listOf(booking.id))[booking.id] ?: BigDecimal.ZERO
            val summary = OfficePaymentSummary.fromNet(booking.pricing.totalEur, net)
            return VoucherPayment("OFFICE", summary.state.name, eur(summary.collected), eur(summary.outstanding))
        }
        val status = paymentRepository.findByBookingId(booking.id)?.status ?: return VoucherPayment("ONLINE", "NONE", null, null)
        // Never expose provider detail: only the coarse state.
        val state = if (status == PaymentStatus.PAID) "PAID" else status.name
        return VoucherPayment("ONLINE", state, null, null)
    }

    private fun paymentDue(
        booking: Booking,
        nets: Map<BookingId, BigDecimal>,
    ): Boolean =
        if (booking.channel == BookingChannel.OFFICE) {
            OfficePaymentSummary
                .fromNet(booking.pricing.totalEur, nets[booking.id] ?: BigDecimal.ZERO)
                .outstanding.amount
                .signum() > 0
        } else {
            false
        }

    private fun officeNets(bookings: List<Booking>): Map<BookingId, BigDecimal> =
        collectionRepository.netCollected(bookings.filter { it.channel == BookingChannel.OFFICE }.map { it.id })

    /** Confirmed or completed bookings of a day: what actually runs. Cancelled, expired and unpaid online drafts never print. */
    private fun liveBookings(date: LocalDate): List<Booking> {
        val all = mutableListOf<Booking>()
        var offset = 0
        while (true) {
            val page = bookingRepository.findAll(null, null, date, PAGE, offset)
            all += page
            if (page.size < PAGE) break
            offset += PAGE
        }
        return all.filter { it.status == BookingStatus.CONFIRMED || it.status == BookingStatus.COMPLETED }
    }

    private class Names(
        private val tours: Map<TourId, Tour>,
        private val en: Map<TourId, String>,
        private val ar: Map<TourId, String>,
    ) {
        fun en(id: TourId): String = tours[id]?.nameEn ?: en[id] ?: tours[id]?.slug ?: id.value.toString()

        fun ar(id: TourId): String? = ar[id]
    }

    private fun tourNames(ids: List<TourId>): Names {
        val tours = ids.mapNotNull { id -> tourRepository.findById(id)?.let { id to it } }.toMap()
        val en = contentRepository.findPublishedSummaries(ids, ContentLocale.EN).mapValues { it.value.name }
        // The lookup falls back to English; keep only genuinely Arabic names.
        val ar =
            contentRepository
                .findPublishedSummaries(ids, ContentLocale.AR)
                .filterValues { it.locale == ContentLocale.AR }
                .mapValues { it.value.name }
        return Names(tours, en, ar)
    }

    /** Published content in the requested language, falling back to English; the language actually served comes with it. */
    private fun publishedContent(
        tourId: TourId,
        language: DocumentLanguage,
    ): Pair<ContentLocale, com.wego.toursoperator.domain.TourContentDocument>? {
        val wanted = if (language == DocumentLanguage.AR) ContentLocale.AR else ContentLocale.EN
        return listOf(wanted, ContentLocale.EN)
            .distinct()
            .firstNotNullOfOrNull { locale ->
                contentRepository.findContent(tourId, locale, ContentStage.PUBLISHED)?.let { locale to it.document }
            }
    }

    private fun eur(money: Money) = DocMoney(money.amount.toPlainString(), "EUR")

    companion object {
        private const val PAGE = 200
        private val CAIRO: ZoneId = ZoneId.of("Africa/Cairo")
        private val WHITESPACE = Regex("\\s+")

        /** Only the last 4 characters of a receipt number are ever printed; shorter ones are fully masked and the length is not revealed. */
        fun maskReference(reference: String): String = if (reference.length <= 4) "****" else "****" + reference.takeLast(4)

        fun entryRef(id: UUID): String = "REV-" + id.toString().take(8).uppercase()
    }
}
