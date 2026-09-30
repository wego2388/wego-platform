package com.wego.toursoperator.application

import com.wego.toursoperator.domain.NotificationKind
import com.wego.toursoperator.domain.TimeSlot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Booking facts a customer message may contain — all taken from the booking itself. */
data class NotificationContext(
    val reference: String,
    val tourName: String,
    val tourDate: LocalDate,
    val timeSlot: TimeSlot,
    val adults: Int,
    val children: Int,
    val totalEur: String,
    val myBookingUrl: String,
    val reviewUrl: String?,
)

data class RenderedEmail(
    val subject: String,
    val body: String,
)

/**
 * Customer email wording in EN/AR/RU/IT; any other booking locale falls back
 * to English.
 *
 * DRAFT wording — awaiting owner approval (ROADMAP_AR.md 2-3b). It states only
 * booking facts; no pickup times, policies, contact numbers or promises are
 * included until the owner approves them.
 */
object NotificationTemplates {
    val SUPPORTED_LOCALES = setOf("en", "ar", "ru", "it")

    fun render(
        kind: NotificationKind,
        locale: String,
        context: NotificationContext,
    ): RenderedEmail {
        val lang = locale.lowercase().takeIf { it in SUPPORTED_LOCALES } ?: "en"
        val text = TEXTS.getValue(lang)
        val date = context.tourDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.forLanguageTag(lang)))
        val slot = text.slots.getValue(context.timeSlot)
        return when (kind) {
            NotificationKind.BOOKING_CONFIRMED ->
                RenderedEmail(
                    subject = text.confirmedSubject.format(context.reference),
                    body =
                        listOf(
                            text.greeting,
                            text.confirmedIntro,
                            "",
                            "${text.referenceLabel}: ${context.reference}",
                            "${text.tourLabel}: ${context.tourName}",
                            "${text.dateLabel}: $date — $slot",
                            "${text.guestsLabel}: ${text.guests(context.adults, context.children)}",
                            "${text.paidLabel}: €${context.totalEur}",
                            "",
                            text.myBookingLine.format(context.myBookingUrl),
                            "",
                            text.signOff,
                        ).joinToString("\n"),
                )
            NotificationKind.BOOKING_CANCELLED ->
                RenderedEmail(
                    subject = text.cancelledSubject.format(context.reference),
                    body =
                        listOf(
                            text.greeting,
                            text.cancelledIntro.format(context.reference, context.tourName, date),
                            "",
                            text.questionsLine,
                            "",
                            text.signOff,
                        ).joinToString("\n"),
                )
            NotificationKind.REVIEW_REQUEST -> {
                val reviewUrl = requireNotNull(context.reviewUrl) { "review request needs a review URL" }
                RenderedEmail(
                    subject = text.reviewSubject,
                    body =
                        listOf(
                            text.greeting,
                            text.reviewIntro.format(context.tourName),
                            "",
                            reviewUrl,
                            "",
                            text.signOff,
                        ).joinToString("\n"),
                )
            }
        }
    }

    private class Texts(
        val greeting: String,
        val confirmedSubject: String,
        val confirmedIntro: String,
        val referenceLabel: String,
        val tourLabel: String,
        val dateLabel: String,
        val guestsLabel: String,
        val paidLabel: String,
        val myBookingLine: String,
        val cancelledSubject: String,
        val cancelledIntro: String,
        val questionsLine: String,
        val reviewSubject: String,
        val reviewIntro: String,
        val signOff: String,
        val slots: Map<TimeSlot, String>,
        val guests: (Int, Int) -> String,
    )

    private val TEXTS =
        mapOf(
            "en" to
                Texts(
                    greeting = "Hello,",
                    confirmedSubject = "Booking confirmed — %s",
                    confirmedIntro = "Thank you — your payment was received and your booking is confirmed.",
                    referenceLabel = "Booking reference",
                    tourLabel = "Tour",
                    dateLabel = "Date",
                    guestsLabel = "Guests",
                    paidLabel = "Paid",
                    myBookingLine = "You can view your booking any time at %s using your reference and phone number.",
                    cancelledSubject = "Booking cancelled — %s",
                    cancelledIntro = "Your booking %s for %s on %s has been cancelled.",
                    questionsLine = "If you have any questions, simply reply to this email.",
                    reviewSubject = "How was your tour?",
                    reviewIntro = "Thank you for joining %s. We would be grateful if you shared your experience:",
                    signOff = "Safari Tours Sharm",
                    slots =
                        mapOf(
                            TimeSlot.SUNRISE to "sunrise",
                            TimeSlot.MORNING to "morning",
                            TimeSlot.AFTERNOON to "afternoon",
                            TimeSlot.SUNSET to "sunset",
                        ),
                    guests = { adults, children -> if (children > 0) "$adults adults, $children children" else "$adults adults" },
                ),
            "ar" to
                Texts(
                    greeting = "مرحبًا،",
                    confirmedSubject = "تم تأكيد الحجز — %s",
                    confirmedIntro = "شكرًا لك — تم استلام الدفع وتأكيد حجزك.",
                    referenceLabel = "رقم الحجز",
                    tourLabel = "الرحلة",
                    dateLabel = "التاريخ",
                    guestsLabel = "الأفراد",
                    paidLabel = "المبلغ المدفوع",
                    myBookingLine = "يمكنك عرض حجزك في أي وقت من %s باستخدام رقم الحجز ورقم هاتفك.",
                    cancelledSubject = "تم إلغاء الحجز — %s",
                    cancelledIntro = "تم إلغاء حجزك رقم %s لرحلة %s بتاريخ %s.",
                    questionsLine = "لأي استفسار، يمكنك الرد على هذا البريد.",
                    reviewSubject = "كيف كانت رحلتك؟",
                    reviewIntro = "شكرًا لانضمامك إلى %s. يسعدنا أن تشاركنا تجربتك:",
                    signOff = "Safari Tours Sharm",
                    slots =
                        mapOf(
                            TimeSlot.SUNRISE to "شروق الشمس",
                            TimeSlot.MORNING to "صباحًا",
                            TimeSlot.AFTERNOON to "بعد الظهر",
                            TimeSlot.SUNSET to "غروب الشمس",
                        ),
                    guests = { adults, children -> if (children > 0) "بالغون ($adults)، أطفال ($children)" else "بالغون ($adults)" },
                ),
            "ru" to
                Texts(
                    greeting = "Здравствуйте,",
                    confirmedSubject = "Бронирование подтверждено — %s",
                    confirmedIntro = "Спасибо — оплата получена, ваше бронирование подтверждено.",
                    referenceLabel = "Номер бронирования",
                    tourLabel = "Тур",
                    dateLabel = "Дата",
                    guestsLabel = "Гости",
                    paidLabel = "Оплачено",
                    myBookingLine = "Вы можете посмотреть бронирование в любое время на %s по номеру бронирования и телефону.",
                    cancelledSubject = "Бронирование отменено — %s",
                    cancelledIntro = "Ваше бронирование %s на тур %s (%s) отменено.",
                    questionsLine = "Если у вас есть вопросы, просто ответьте на это письмо.",
                    reviewSubject = "Как прошёл ваш тур?",
                    reviewIntro = "Спасибо, что были с нами на туре %s. Будем благодарны, если вы поделитесь впечатлениями:",
                    signOff = "Safari Tours Sharm",
                    slots =
                        mapOf(
                            TimeSlot.SUNRISE to "рассвет",
                            TimeSlot.MORNING to "утро",
                            TimeSlot.AFTERNOON to "день",
                            TimeSlot.SUNSET to "закат",
                        ),
                    guests = { adults, children -> if (children > 0) "взрослые ($adults), дети ($children)" else "взрослые ($adults)" },
                ),
            "it" to
                Texts(
                    greeting = "Buongiorno,",
                    confirmedSubject = "Prenotazione confermata — %s",
                    confirmedIntro = "Grazie — abbiamo ricevuto il pagamento e la tua prenotazione è confermata.",
                    referenceLabel = "Codice prenotazione",
                    tourLabel = "Escursione",
                    dateLabel = "Data",
                    guestsLabel = "Ospiti",
                    paidLabel = "Pagato",
                    myBookingLine = "Puoi consultare la prenotazione in qualsiasi momento su %s con il codice e il tuo numero di telefono.",
                    cancelledSubject = "Prenotazione annullata — %s",
                    cancelledIntro = "La tua prenotazione %s per %s del %s è stata annullata.",
                    questionsLine = "Per qualsiasi domanda, rispondi semplicemente a questa email.",
                    reviewSubject = "Com'è andata l'escursione?",
                    reviewIntro = "Grazie per aver partecipato a %s. Ci farebbe piacere conoscere la tua esperienza:",
                    signOff = "Safari Tours Sharm",
                    slots =
                        mapOf(
                            TimeSlot.SUNRISE to "alba",
                            TimeSlot.MORNING to "mattina",
                            TimeSlot.AFTERNOON to "pomeriggio",
                            TimeSlot.SUNSET to "tramonto",
                        ),
                    guests = { adults, children -> if (children > 0) "$adults adulti, $children bambini" else "$adults adulti" },
                ),
        )
}
