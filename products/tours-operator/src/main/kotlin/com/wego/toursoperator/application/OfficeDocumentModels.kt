package com.wego.toursoperator.application

import com.wego.toursoperator.domain.BookingChannel
import com.wego.toursoperator.domain.BookingStatus
import com.wego.toursoperator.domain.CollectionMethod
import com.wego.toursoperator.domain.PaidCurrency
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
)

/** What the printed page shows about its own identity (number, version, original date). */
data class DocumentStamp(
    val type: DocumentType,
    val number: String,
    val version: Int,
    val language: DocumentLanguage,
    val copy: Boolean,
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
    /** Filled in later by OPS2-E (assignments); printed as blank lines until then. */
    val driver: String?,
    val vehicle: String?,
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
