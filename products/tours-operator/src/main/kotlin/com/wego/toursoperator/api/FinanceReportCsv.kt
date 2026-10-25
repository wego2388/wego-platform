package com.wego.toursoperator.api

import com.wego.toursoperator.application.CashVarianceReport
import com.wego.toursoperator.application.DepartureMoneyReport
import com.wego.toursoperator.application.RequestConversionReport
import com.wego.toursoperator.application.ReversalReport
import java.math.BigDecimal
import java.time.Instant

/**
 * CSV for the SAF-04 Slice B reports: UTF-8 with a byte-order mark so Excel opens Arabic correctly,
 * CRLF line ends, quoted text. A text cell that would run as a formula in a spreadsheet is
 * prefixed with an apostrophe; numbers are written as plain numbers. Columns are exactly the
 * report's own fields: never a phone, customer email, customer name or note.
 */
object FinanceReportCsv {
    val BOM: String = 65279.toChar().toString()

    fun render(
        header: List<String>,
        rows: List<List<Any?>>,
    ): ByteArray =
        (
            BOM +
                (listOf(header.map { quote(it) }) + rows.map { row -> row.map(::cell) }).joinToString("\r\n") { it.joinToString(",") } +
                "\r\n"
        ).toByteArray(Charsets.UTF_8)

    private fun cell(value: Any?): String =
        when (value) {
            null -> ""
            is BigDecimal -> value.setScale(2).toPlainString()
            is Int, is Long -> value.toString()
            is Boolean -> if (value) "yes" else "no"
            is Instant -> value.toString()
            else -> quote(guard(value.toString()))
        }

    private fun guard(text: String): String = if (text.isNotEmpty() && text[0] in "=+-@\t\r") "'$text" else text

    private fun quote(text: String): String = "\"" + text.replace("\"", "\"\"") + "\""

    private fun labels(
        ar: Boolean,
        en: List<String>,
        arabic: List<String>,
    ) = if (ar) arabic else en

    fun reversals(
        report: ReversalReport,
        ar: Boolean,
    ): ByteArray =
        render(
            labels(
                ar,
                listOf(
                    "Kind",
                    "Reversed at",
                    "Reversed by",
                    "Reason",
                    "Original at",
                    "Original by",
                    "Lag days",
                    "Currency",
                    "Amount",
                    "Amount EUR",
                    "Cash",
                    "Crossed a closed cash day",
                    "Booking",
                    "Party type",
                    "Party",
                ),
                listOf(
                    "النوع",
                    "وقت العكس",
                    "عكسه",
                    "السبب",
                    "وقت القيد الأصلي",
                    "سجّله",
                    "أيام التأخير",
                    "العملة",
                    "المبلغ",
                    "المبلغ باليورو",
                    "نقدي",
                    "عبر يوم خزينة مغلق",
                    "الحجز",
                    "نوع الطرف",
                    "الطرف",
                ),
            ),
            report.lines.map {
                val e = it.entry
                listOf(
                    e.kind.name,
                    e.reversedAt,
                    e.reversedBy,
                    e.reason,
                    e.originalAt,
                    e.originalBy,
                    it.lagDays,
                    e.currency.name,
                    e.amount,
                    e.amountEur,
                    e.cash,
                    it.crossedClosedDay,
                    e.bookingReference,
                    e.partyType?.name,
                    e.partyName,
                )
            },
        )

    fun cashVariances(
        report: CashVarianceReport,
        ar: Boolean,
    ): ByteArray =
        render(
            labels(
                ar,
                listOf(
                    "Date",
                    "Currency",
                    "State",
                    "Expected",
                    "Counted",
                    "Difference",
                    "Counted by",
                    "Counted at",
                    "Confirmed by",
                    "Confirmed at",
                    "Counts",
                    "Reopens",
                ),
                listOf(
                    "التاريخ",
                    "العملة",
                    "الحالة",
                    "المتوقع",
                    "المعدود",
                    "الفرق",
                    "عدّه",
                    "وقت العد",
                    "أكّده",
                    "وقت التأكيد",
                    "عدد العدّات",
                    "مرات إعادة الفتح",
                ),
            ),
            report.days.map {
                listOf(
                    it.date.toString(),
                    it.currency.name,
                    it.state.name,
                    it.expected,
                    it.counted,
                    it.difference,
                    it.countedBy,
                    it.countedAt,
                    it.confirmedBy,
                    it.confirmedAt,
                    it.countEvents,
                    it.reopenCount,
                )
            },
        )

    fun requestConversion(
        report: RequestConversionReport,
        ar: Boolean,
    ): ByteArray =
        render(
            labels(
                ar,
                listOf("From", "To", "Total", "New", "In progress", "Closed", "Converted", "Conversion %"),
                listOf("من", "إلى", "الإجمالي", "جديد", "قيد المتابعة", "مغلق", "تحوّل إلى حجز", "نسبة التحويل %"),
            ),
            listOf(
                listOf(
                    report.from.toString(),
                    report.to.toString(),
                    report.total,
                    report.new,
                    report.inProgress,
                    report.closed,
                    report.converted,
                    report.conversionPercent?.toPlainString(),
                ),
            ),
        )

    fun departureMoney(
        report: DepartureMoneyReport,
        ar: Boolean,
    ): ByteArray =
        render(
            labels(
                ar,
                listOf(
                    "Tour day",
                    "Live bookings",
                    "Cancelled bookings",
                    "Booked EUR",
                    "Collected EUR",
                    "Outstanding EUR",
                    "Kept on cancelled EUR",
                    "Received EUR",
                    "Received EGP",
                    "EGP settled as EUR",
                    "Payments under review",
                    "Refunds pending",
                ),
                listOf(
                    "يوم الرحلة",
                    "حجوزات سارية",
                    "حجوزات ملغاة",
                    "المحجوز يورو",
                    "المحصّل يورو",
                    "المتبقي يورو",
                    "محتجز على ملغاة يورو",
                    "المستلم يورو",
                    "المستلم جنيه",
                    "الجنيه محسوبًا يورو",
                    "مدفوعات قيد المراجعة",
                    "استردادات معلّقة",
                ),
            ),
            report.days.map { d ->
                val eur = d.received.first { it.currency.name == "EUR" }
                val egp = d.received.first { it.currency.name == "EGP" }
                listOf(
                    d.date.toString(),
                    d.liveBookings,
                    d.cancelledBookings,
                    d.bookedEur,
                    d.collectedEur,
                    d.outstandingEur,
                    d.retainedOnCancelledEur,
                    eur.native,
                    egp.native,
                    egp.eurValue,
                    d.paymentsUnderReview,
                    d.refundsPending,
                )
            },
        )
}
