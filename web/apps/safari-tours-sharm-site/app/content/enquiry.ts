import type { StsLocale } from "./locales";

export const enquiryCopy = {
  en: {
    title: "Request your booking",
    notice: "Online payment is not available right now. Send a WhatsApp enquiry: our office must confirm availability, the final price and payment arrangements. No places are reserved by sending a message.",
    cta: "Send a booking request",
    dateLabel: "Preferred date", dateHint: "Choose your preferred date. Our office will confirm availability; this is not a reserved departure.",
    dateInvalid: "Choose today or a future date.", timeLabel: "Preferred departure", anyTime: "Confirm the time with the office", estimatedTotal: "Estimated total — subject to office confirmation",
    unknown: "We cannot verify online booking availability. Please contact us on WhatsApp before booking.",
    tour: "Tour", date: "Preferred date", time: "Preferred departure", adults: "Adults", children: "Children", option: "Option", units: "Units",
    message: "Hello Safari Tours Sharm. Please check this trip request. I understand it needs office confirmation and does not reserve places.",
  },
  ar: {
    title: "اطلب حجز رحلتك",
    notice: "الدفع الإلكتروني غير متاح حاليًا. أرسل طلبًا عبر واتساب ليؤكد المكتب التوافر والسعر النهائي وطريقة الدفع. إرسال الرسالة لا يحجز أماكن.",
    cta: "أرسل طلب حجز",
    dateLabel: "التاريخ المطلوب", dateHint: "اختر التاريخ المناسب لك؛ المكتب يؤكد الإتاحة. اختيار التاريخ ليس حجزًا مؤكدًا لموعد انطلاق.",
    dateInvalid: "اختر تاريخ اليوم أو تاريخًا قادمًا.", timeLabel: "وقت الانطلاق المفضل", anyTime: "تأكيد الموعد مع المكتب", estimatedTotal: "الإجمالي التقديري — يحتاج تأكيد المكتب",
    unknown: "تعذّر التحقق من إتاحة الحجز الإلكتروني. تواصل معنا عبر واتساب قبل الحجز.",
    tour: "الرحلة", date: "التاريخ المطلوب", time: "الموعد المطلوب", adults: "البالغون", children: "الأطفال", option: "الخيار", units: "الوحدات",
    message: "مرحبًا سفاري تورز شرم. أرجو مراجعة طلب الرحلة التالي. أفهم أنه يحتاج تأكيد المكتب ولا يحجز أماكن.",
  },
  ru: {
    title: "Запросить бронирование",
    notice: "Онлайн-оплата пока недоступна. Отправьте запрос в WhatsApp: офис должен подтвердить наличие мест, окончательную цену и способ оплаты. Сообщение не резервирует места.",
    cta: "Отправить запрос на бронирование",
    dateLabel: "Желаемая дата", dateHint: "Выберите удобную дату. Офис подтвердит наличие мест; дата не означает подтверждённый выезд.",
    dateInvalid: "Выберите сегодняшнюю или будущую дату.", timeLabel: "Желаемое время выезда", anyTime: "Уточнить время в офисе", estimatedTotal: "Предварительная сумма — требуется подтверждение офиса",
    unknown: "Не удалось проверить доступность онлайн-бронирования. Свяжитесь с нами в WhatsApp перед бронированием.",
    tour: "Экскурсия", date: "Желаемая дата", time: "Желаемое время", adults: "Взрослые", children: "Дети", option: "Вариант", units: "Количество единиц",
    message: "Здравствуйте, Safari Tours Sharm. Пожалуйста, проверьте этот запрос. Я понимаю, что требуется подтверждение офиса и места не резервируются.",
  },
  it: {
    title: "Richiedi la prenotazione",
    notice: "Il pagamento online non è ancora disponibile. Invia una richiesta su WhatsApp: l’ufficio deve confermare disponibilità, prezzo finale e modalità di pagamento. Il messaggio non riserva posti.",
    cta: "Invia una richiesta di prenotazione",
    dateLabel: "Data preferita", dateHint: "Scegli la data che preferisci. L’ufficio confermerà la disponibilità; la data non conferma una partenza.",
    dateInvalid: "Scegli oggi o una data futura.", timeLabel: "Partenza preferita", anyTime: "Conferma l’orario con l’ufficio", estimatedTotal: "Totale indicativo — soggetto alla conferma dell’ufficio",
    unknown: "Non possiamo verificare la disponibilità delle prenotazioni online. Contattaci su WhatsApp prima di prenotare.",
    tour: "Escursione", date: "Data preferita", time: "Partenza preferita", adults: "Adulti", children: "Bambini", option: "Opzione", units: "Unità",
    message: "Ciao Safari Tours Sharm. Vi chiedo di verificare questa richiesta. Comprendo che serve la conferma dell’ufficio e che i posti non sono riservati.",
  },
} satisfies Record<StsLocale, Record<string, string>>;
