import type { StsLocale } from "./locales";

export const enquiryCopy = {
  en: {
    title: "Ask about your trip",
    notice: "Online payment is not available right now. Send a WhatsApp enquiry: our office must confirm availability, the final price and payment arrangements. No places are reserved by sending a message.",
    cta: "Send a trip enquiry",
    unknown: "We cannot verify online booking availability. Please contact us on WhatsApp before booking.",
    tour: "Tour", date: "Preferred date", time: "Preferred departure", adults: "Adults", children: "Children", option: "Option", units: "Units",
    message: "Hello Safari Tours Sharm. Please check this trip request. I understand it needs office confirmation and does not reserve places.",
  },
  ar: {
    title: "استفسر عن رحلتك",
    notice: "الدفع الإلكتروني غير متاح حاليًا. أرسل طلبًا عبر واتساب ليؤكد المكتب التوافر والسعر النهائي وطريقة الدفع. إرسال الرسالة لا يحجز أماكن.",
    cta: "أرسل طلب رحلة",
    unknown: "تعذّر التحقق من إتاحة الحجز الإلكتروني. تواصل معنا عبر واتساب قبل الحجز.",
    tour: "الرحلة", date: "التاريخ المطلوب", time: "الموعد المطلوب", adults: "البالغون", children: "الأطفال", option: "الخيار", units: "الوحدات",
    message: "مرحبًا سفاري تورز شرم. أرجو مراجعة طلب الرحلة التالي. أفهم أنه يحتاج تأكيد المكتب ولا يحجز أماكن.",
  },
  ru: {
    title: "Уточните детали поездки",
    notice: "Онлайн-оплата пока недоступна. Отправьте запрос в WhatsApp: офис должен подтвердить наличие мест, окончательную цену и способ оплаты. Сообщение не резервирует места.",
    cta: "Отправить запрос о поездке",
    unknown: "Не удалось проверить доступность онлайн-бронирования. Свяжитесь с нами в WhatsApp перед бронированием.",
    tour: "Экскурсия", date: "Желаемая дата", time: "Желаемое время", adults: "Взрослые", children: "Дети", option: "Вариант", units: "Количество единиц",
    message: "Здравствуйте, Safari Tours Sharm. Пожалуйста, проверьте этот запрос. Я понимаю, что требуется подтверждение офиса и места не резервируются.",
  },
  it: {
    title: "Chiedi informazioni sul viaggio",
    notice: "Il pagamento online non è ancora disponibile. Invia una richiesta su WhatsApp: l’ufficio deve confermare disponibilità, prezzo finale e modalità di pagamento. Il messaggio non riserva posti.",
    cta: "Invia una richiesta di viaggio",
    unknown: "Non possiamo verificare la disponibilità delle prenotazioni online. Contattaci su WhatsApp prima di prenotare.",
    tour: "Escursione", date: "Data preferita", time: "Partenza preferita", adults: "Adulti", children: "Bambini", option: "Opzione", units: "Unità",
    message: "Ciao Safari Tours Sharm. Vi chiedo di verificare questa richiesta. Comprendo che serve la conferma dell’ufficio e che i posti non sono riservati.",
  },
} satisfies Record<StsLocale, Record<string, string>>;
