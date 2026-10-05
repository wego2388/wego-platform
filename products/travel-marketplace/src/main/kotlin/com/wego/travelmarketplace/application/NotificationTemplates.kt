package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.NotificationKind
import java.time.LocalDate
import java.time.LocalTime

/** Request facts a message may contain, all taken from the request itself. */
data class NotificationContext(
    val reference: String,
    val serviceName: String,
    val optionLabel: String,
    val requestedDate: LocalDate,
    val requestedTime: LocalTime?,
    val adults: Int,
    val children: Int,
    val customerName: String,
    val confirmed: Boolean,
    val trackUrl: String,
    val whatsapp: String,
    val contactEmail: String,
)

data class RenderedEmail(
    val subject: String,
    val body: String,
)

/**
 * Plain-text wording, English and Arabic (the site's two languages), chosen
 * by the request's locale; anything else falls back to English. The staff
 * alert is English only.
 *
 * Every request-supplied value passes through [clean]: control characters
 * (so no value can add a header or a line) and bidi overrides (so no value
 * can reorder the text around it) are stripped. Output is plain text, never
 * HTML. Subjects carry only the reference, never a customer value.
 *
 * DRAFT wording, pending owner approval: it states request facts only, no
 * price, pickup time or policy promise.
 */
object NotificationTemplates {
    const val MAX_VALUE_LENGTH = 120

    private val DISALLOWED = Regex("[\\p{Cntrl}\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")

    fun clean(value: String): String =
        value
            .replace(DISALLOWED, " ")
            .replace(Regex(" {2,}"), " ")
            .trim()
            .take(MAX_VALUE_LENGTH)

    fun render(
        kind: NotificationKind,
        locale: String,
        context: NotificationContext,
    ): RenderedEmail {
        val c =
            context.copy(
                serviceName = clean(context.serviceName),
                optionLabel = clean(context.optionLabel),
                customerName = clean(context.customerName),
            )
        if (kind == NotificationKind.STAFF_NEW_REQUEST) return staffAlert(c)
        val t = if (locale == "ar") AR else EN
        val subject =
            when (kind) {
                NotificationKind.CUSTOMER_REQUEST_RECEIVED -> if (c.confirmed) t.confirmedSubject else t.receivedSubject
                NotificationKind.CUSTOMER_REQUEST_CONFIRMED -> t.confirmedSubject
                else -> t.cancelledSubject
            }.format(c.reference)
        val intro =
            when (kind) {
                NotificationKind.CUSTOMER_REQUEST_RECEIVED -> if (c.confirmed) t.confirmedIntro else t.receivedIntro
                NotificationKind.CUSTOMER_REQUEST_CONFIRMED -> t.confirmedIntro
                else -> t.cancelledIntro
            }
        val lines = mutableListOf<String>()
        lines += t.greeting(c.customerName)
        lines += intro
        lines += ""
        lines += "${t.referenceLabel}: ${c.reference}"
        lines += "${t.serviceLabel}: ${c.serviceName} - ${c.optionLabel}"
        lines += "${t.dateLabel}: ${c.requestedDate}" + (c.requestedTime?.let { " $it" } ?: "")
        lines += "${t.guestsLabel}: ${t.guests(c.adults, c.children)}"
        lines += ""
        if (kind != NotificationKind.CUSTOMER_REQUEST_CANCELLED) lines += t.trackLine.format(c.trackUrl)
        lines += t.contactLine.format(c.whatsapp, c.contactEmail)
        lines += ""
        lines += t.signOff
        return RenderedEmail(subject, lines.joinToString("\n"))
    }

    private fun staffAlert(c: NotificationContext): RenderedEmail =
        RenderedEmail(
            subject = "New travel request ${c.reference}",
            body =
                listOf(
                    "A new travel request arrived${if (c.confirmed) " (auto-confirmed)" else ""}.",
                    "",
                    "Reference: ${c.reference}",
                    "Service: ${c.serviceName} - ${c.optionLabel}",
                    "Date: ${c.requestedDate}" + (c.requestedTime?.let { " $it" } ?: ""),
                    "Guests: ${c.adults} adults, ${c.children} children",
                    "",
                    "Open the staff ERP Requests queue for the customer details.",
                ).joinToString("\n"),
        )

    private class Texts(
        val greeting: (String) -> String,
        val receivedSubject: String,
        val receivedIntro: String,
        val confirmedSubject: String,
        val confirmedIntro: String,
        val cancelledSubject: String,
        val cancelledIntro: String,
        val referenceLabel: String,
        val serviceLabel: String,
        val dateLabel: String,
        val guestsLabel: String,
        val trackLine: String,
        val contactLine: String,
        val signOff: String,
        val guests: (Int, Int) -> String,
    )

    private val EN =
        Texts(
            greeting = { name -> if (name.isBlank()) "Hello," else "Hello $name," },
            receivedSubject = "We received your request - %s",
            receivedIntro = "Thank you - we received your request. It is not confirmed yet; we will confirm it with you shortly.",
            confirmedSubject = "Your request is confirmed - %s",
            confirmedIntro = "Good news - your request is confirmed.",
            cancelledSubject = "Your request was cancelled - %s",
            cancelledIntro = "Your request has been cancelled.",
            referenceLabel = "Reference",
            serviceLabel = "Experience",
            dateLabel = "Date",
            guestsLabel = "Guests",
            trackLine = "You can follow your request any time at %s",
            contactLine = "Questions? WhatsApp %s or email %s.",
            signOff = "Sharm To Go",
            guests = { adults, children -> if (children > 0) "$adults adults, $children children" else "$adults adults" },
        )

    private val AR =
        Texts(
            greeting = { name -> if (name.isBlank()) "مرحبًا،" else "مرحبًا $name،" },
            receivedSubject = "استلمنا طلبك - %s",
            receivedIntro = "شكرًا لك - استلمنا طلبك. الطلب لم يتم تأكيده بعد، وسنتواصل معك لتأكيده قريبًا.",
            confirmedSubject = "تم تأكيد طلبك - %s",
            confirmedIntro = "يسعدنا إبلاغك أنه تم تأكيد طلبك.",
            cancelledSubject = "تم إلغاء طلبك - %s",
            cancelledIntro = "تم إلغاء طلبك.",
            referenceLabel = "رقم الطلب",
            serviceLabel = "التجربة",
            dateLabel = "التاريخ",
            guestsLabel = "الأفراد",
            trackLine = "يمكنك متابعة طلبك في أي وقت من %s",
            contactLine = "لأي استفسار: واتساب %s أو البريد %s.",
            signOff = "Sharm To Go",
            guests = { adults, children -> if (children > 0) "بالغون ($adults)، أطفال ($children)" else "بالغون ($adults)" },
        )
}
