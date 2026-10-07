package com.wego.toursoperator.application

import com.wego.toursoperator.domain.BookingChannel
import com.wego.toursoperator.domain.BookingStatus
import com.wego.toursoperator.domain.CollectionMethod
import com.wego.toursoperator.domain.ConfirmationChannel
import com.wego.toursoperator.domain.PaidCurrency
import com.wego.toursoperator.domain.SupplierServiceType
import com.wego.toursoperator.domain.TimeSlot
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** WEGO-016-OPS2-D document kinds. The browser prints them; nothing but the print register is stored. */
enum class DocumentType(
    val numberPrefix: String,
) {
    VOUCHER("VCH"),
    RECEIPT("RCT"),
    RUN_SHEET("RUN"),
    PICKUP_MANIFEST("PKM"),
    CANCELLATION_FORM("CXL"),
    DRIVER_SHEET("DRV"),
    SUPPLIER_ORDER("SUP"),
    SETTLEMENT_STATEMENT("STL"),
}

/** Printed languages. Both render from the same data. */
enum class DocumentLanguage(
    val code: String,
) {
    EN("en"),
    AR("ar"),
    ;

    companion object {
        fun fromCode(code: String): DocumentLanguage? = entries.firstOrNull { it.code == code }
    }
}

/** One recorded print. Version 1 is the original; later versions are reprints of the same document number. */
data class DocumentPrintRecord(
    val id: UUID,
    val type: DocumentType,
    val subjectKey: String,
    val number: String,
    val version: Int,
    val language: DocumentLanguage,
    val printedByUserId: UUID?,
    val printedByEmail: String?,
    val printedAt: Instant,
    /** Run sheet / manifest only: hash of the PII-free printed content, to tell a changed reprint from a plain copy. */
    val contentFingerprint: String? = null,
)

/** What the printed page shows about its own identity (number, version, original date). */
data class DocumentStamp(
    val type: DocumentType,
    val number: String,
    val version: Int,
    val language: DocumentLanguage,
    /** A reprint of unchanged content. */
    val copy: Boolean,
    /** A reprint of a run sheet / manifest whose content changed since the previous print: REVISED, not COPY. */
    val revised: Boolean = false,
    val originalPrintedAt: Instant,
    val printedAt: Instant,
    val printedByEmail: String?,
)

data class DocMoney(
    val amount: String,
    val currencyCode: String,
)

data class VoucherUnit(
    val optionLabel: String,
    val unitCount: Int,
)

data class VoucherPayment(
    /** OFFICE (staff-collected) or ONLINE (card through the provider). */
    val kind: String,
    /** Office: UNPAID, PARTIALLY_PAID, PAID. Online: the provider payment status, or NONE. */
    val state: String,
    val collected: DocMoney?,
    val outstanding: DocMoney?,
)

data class VoucherData(
    val reference: String,
    val status: BookingStatus,
    /** False for a cancelled booking: the page prints a CANCELLED banner and no QR. */
    val valid: Boolean,
    val channel: BookingChannel,
    val tourNameEn: String,
    val tourNameAr: String?,
    val tourDate: LocalDate,
    val timeSlot: TimeSlot,
    val adultsCount: Int,
    val childrenCount: Int,
    val unit: VoucherUnit?,
    val customerName: String,
    val hotelName: String,
    val hotelRoom: String?,
    val totalPrice: DocMoney,
    val payment: VoucherPayment,
    val contentLanguage: String,
    val includes: List<String>,
    val excludes: List<String>,
    val knowBeforeYouGo: List<String>,
    val meetingPoint: String?,
    /** The public my-booking page only (no reference, no phone, no token); null when the booking is not valid. */
    val myBookingUrl: String?,
)

data class ReceiptReversal(
    val entryRef: String,
    val recordedAt: Instant,
)

data class ReceiptData(
    val bookingReference: String,
    val tourNameEn: String,
    val tourNameAr: String?,
    val tourDate: LocalDate,
    val customerName: String,
    val method: CollectionMethod,
    val currencyPaid: PaidCurrency,
    val amountPaid: DocMoney,
    val settledEur: DocMoney,
    /** EGP per 1 EUR; only for EGP payments. */
    val fxRate: String?,
    /** Terminal/wallet/InstaPay/Fawry receipt number, masked but for the last 4 characters. Never card data. */
    val referenceMasked: String?,
    val recordedAt: Instant,
    val collectedBy: String?,
    val bookingTotal: DocMoney,
    val collectedToDate: DocMoney,
    val remainingAfter: DocMoney,
    /** True when a reversal cancelled this payment: the page prints a VOID / REVERSED watermark. */
    val reversed: Boolean,
    val reversal: ReceiptReversal?,
)

data class RunSheetHotel(
    val hotelName: String,
    val guests: Int,
)

data class RunSheetLine(
    val reference: String,
    val leadName: String,
    val hotelName: String,
    val hotelRoom: String?,
    val guests: Int,
    val paymentDue: Boolean,
)

data class RunSheetNote(
    val reference: String,
    val text: String,
)

data class RunSheetDeparture(
    val timeSlot: TimeSlot,
    val guests: Int,
    val hotels: List<RunSheetHotel>,
    val lines: List<RunSheetLine>,
    val notes: List<RunSheetNote>,
)

data class RunSheetTour(
    val tourId: UUID,
    val tourNameEn: String,
    val tourNameAr: String?,
    val guests: Int,
    val departures: List<RunSheetDeparture>,
)

data class RunSheetData(
    val date: LocalDate,
    val totalGuests: Int,
    val tours: List<RunSheetTour>,
)

data class PickupLine(
    val order: Int,
    val reference: String,
    val hotelName: String,
    val hotelRoom: String?,
    val leadName: String,
    val phone: String,
    val guests: Int,
)

data class PickupManifestData(
    val slotId: UUID,
    val date: LocalDate,
    val timeSlot: TimeSlot,
    val tourNameEn: String,
    val tourNameAr: String?,
    val totalGuests: Int,
    val lines: List<PickupLine>,
    /** The departure's assigned driver (name only) and vehicle (label / plate); null prints a blank line. */
    val driver: String?,
    val vehicle: String?,
)

/** One guest party on the driver's sheet: no phone, no money. */
data class DriverSheetGuest(
    val reference: String,
    val leadName: String,
    val hotelRoom: String?,
    val guests: Int,
)

/** A pickup stop: one hotel (stops alphabetical by hotel, case and spacing ignored), with the parties to collect there. */
data class DriverSheetStop(
    val order: Int,
    val hotelName: String,
    val guests: Int,
    val parties: List<DriverSheetGuest>,
)

data class DriverSheetVehicle(
    val display: String,
    val seats: Int,
)

/**
 * The driver's sheet for one departure. PII decision: customer name, hotel and
 * room only; no customer phone, e-mail, price or payment state. A guest who is not at the hotel is
 * handled by calling the operations contact printed on the sheet, not the guest.
 */
data class DriverSheetData(
    val slotId: UUID,
    val date: LocalDate,
    val timeSlot: TimeSlot,
    val tourNameEn: String,
    val tourNameAr: String?,
    val driverName: String,
    val vehicle: DriverSheetVehicle?,
    val totalGuests: Int,
    val stops: List<DriverSheetStop>,
)

data class SupplierOrderUnit(
    val optionLabel: String,
    val unitCount: Int,
)

data class SupplierOrderSupplier(
    val code: String,
    val name: String,
    val serviceType: SupplierServiceType,
    val contactPerson: String?,
    val confirmationChannel: ConfirmationChannel?,
    val noticeHours: Int?,
)

/**
 * The order sent to a supplier for one departure. PII decision: service, date and window, guest
 * counts, the staff-written [supplierNote] and who to confirm with; no customer name, phone, e-mail,
 * hotel or agreed price (costs are OPS2-F). Customers' own special requests are free text that can
 * carry phone numbers or health details, so they stay internal (run sheet only) and are never forwarded.
 */
data class SupplierOrderData(
    val slotId: UUID,
    val date: LocalDate,
    val timeSlot: TimeSlot,
    val tourNameEn: String,
    val tourNameAr: String?,
    val supplier: SupplierOrderSupplier,
    val totalGuests: Int,
    val adults: Int,
    val children: Int,
    val units: List<SupplierOrderUnit>,
    /** Written by staff on the departure's assignment; null when none. */
    val supplierNote: String?,
)

data class CancellationCollectionLine(
    val recordedAt: Instant,
    val reversal: Boolean,
    val method: CollectionMethod,
    val currencyPaid: PaidCurrency,
    val amountPaid: DocMoney,
    val settledEur: DocMoney,
)

data class CancellationFormData(
    val reference: String,
    val tourNameEn: String,
    val tourNameAr: String?,
    val tourDate: LocalDate,
    val timeSlot: TimeSlot,
    val customerName: String,
    val cancelledAt: Instant,
    val cancellationReason: String,
    val collections: List<CancellationCollectionLine>,
    /** Net of reversals, EUR. */
    val collectedNet: DocMoney,
    /** STANDARD, FLEXIBLE or NON_REFUNDABLE (the tour's policy). */
    val policy: String,
    /** Whole hours from the cancellation to the start of the tour day (Africa/Cairo); the exact departure time is set per departure. */
    val hoursBeforeTour: Long,
    val refundPercent: Int,
    val expectedReturn: DocMoney,
    /** EGP per 1 EUR at today's manager-set rate; null when no rate is set today. */
    val todayRate: String?,
    /** The expected return at [todayRate], half-up to cents; null when no rate is set. */
    val expectedReturnEgp: DocMoney?,
    /** Money handed back so far (OPS2-F refunds, oldest first; reversals listed and netted). */
    val refunds: List<CancellationRefundLine> = emptyList(),
    /** Net EUR returned. */
    val refundedNet: DocMoney = DocMoney("0.00", "EUR"),
    /** What the policy still expects to be returned (never below zero). */
    val remainingToReturn: DocMoney = DocMoney("0.00", "EUR"),
    /** NONE_DUE, NOT_RETURNED, PARTIALLY_RETURNED or RETURNED ("money returned"). */
    val returnState: String = "NOT_RETURNED",
)

data class CancellationRefundLine(
    val recordedAt: Instant,
    val reversal: Boolean,
    val method: com.wego.toursoperator.domain.RefundMethod,
    val amountPaid: DocMoney,
    val returnedEur: DocMoney,
)

data class StatementBalance(
    val currency: PaidCurrency,
    val opening: DocMoney,
    val owed: DocMoney,
    val paid: DocMoney,
    val closing: DocMoney,
)

data class StatementLine(
    val date: LocalDate,
    /** DEPARTURE, CHARGE, DEDUCTION, CHARGE_REVERSED, DEDUCTION_REVERSED, PAYMENT, PAYMENT_REVERSED. */
    val kind: String,
    /** Raises (+) or lowers (−) what is owed, in the line's currency. */
    val amount: DocMoney,
    val tourNameEn: String?,
    val tourNameAr: String?,
    val timeSlot: TimeSlot?,
    val guests: Int?,
    val method: String?,
    /** Payment reference, masked to the last 4 characters. */
    val reference: String?,
    /** Adjustment reason, payment note or reversal reason (staff-written). */
    val text: String?,
)

/** Supplier / driver settlement statement (OPS2-F): owed, paid and balance per currency for a period. */
data class SettlementStatementData(
    val partyType: String,
    val partyName: String,
    val partyCode: String?,
    val from: LocalDate,
    val to: LocalDate,
    val balances: List<StatementBalance>,
    val lines: List<StatementLine>,
    /** Departures in the period whose amount could not be derived (entered by hand or split). */
    val openIssues: Int,
)

sealed class DocumentResult<out T> {
    data class Ready<T>(
        val stamp: DocumentStamp,
        val data: T,
    ) : DocumentResult<T>()

    data object NotFound : DocumentResult<Nothing>()

    /** A conflict the caller can show, e.g. booking_not_confirmed. */
    data class Refused(
        val code: String,
    ) : DocumentResult<Nothing>()
}
