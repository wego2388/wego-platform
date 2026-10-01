import type { StsLocale } from "./locales";

/** "My booking" lookup copy. EN/AR final drafts; RU/IT await native review. */
export interface MyBookingCopy {
  title: string; intro: string;
  reference: string; referenceHint: string; phone: string; phoneHint: string; submit: string;
  notFound: string; tooMany: string; network: string; required: string;
  result: { heading: string; status: string; date: string; time: string; guests: string; units: string; total: string; hotel: string; reason: string };
  statuses: { NEW: string; CONFIRMED: string; COMPLETED: string; CANCELLED: string; EXPIRED: string };
  statusHelp: { NEW: string; CONFIRMED: string; COMPLETED: string; CANCELLED: string; EXPIRED: string };
  help: string; whatsapp: string; another: string;
}

const en: MyBookingCopy = {
  title: "My booking", intro: "Enter your booking reference and the phone number you booked with.",
  reference: "Booking reference", referenceHint: "Starts with STR-, e.g. STR-2026-1234.", phone: "Phone number", phoneHint: "The number you used when booking.", submit: "Find my booking",
  notFound: "We could not find a booking with these details. Check the reference and phone number.", tooMany: "Too many attempts. Please wait a minute and try again.",
  network: "We could not reach the server. Please try again.", required: "Please fill in both fields.",
  result: { heading: "Your booking", status: "Status", date: "Date", time: "Departure", guests: "Guests", units: "Booked as", total: "Total", hotel: "Pickup hotel", reason: "Reason" },
  statuses: { NEW: "Awaiting payment", CONFIRMED: "Confirmed", COMPLETED: "Completed", CANCELLED: "Cancelled", EXPIRED: "Expired (not paid)" },
  statusHelp: {
    NEW: "We have not received the payment for this booking yet.",
    CONFIRMED: "All set! We confirm the exact pickup time on WhatsApp.",
    COMPLETED: "We hope you enjoyed it. Thank you for travelling with us!",
    CANCELLED: "This booking was cancelled.",
    EXPIRED: "The payment was not completed in time, so the place was released.",
  },
  help: "Questions about your booking?", whatsapp: "Ask on WhatsApp", another: "Look up another booking",
};

const ar: MyBookingCopy = {
  title: "حجزي", intro: "اكتب رقم الحجز ورقم الهاتف الذي حجزت به.",
  reference: "رقم الحجز", referenceHint: "يبدأ بـ STR-، مثل STR-2026-1234.", phone: "رقم الهاتف", phoneHint: "الرقم الذي استخدمته عند الحجز.", submit: "اعرض حجزي",
  notFound: "لم نجد حجزًا بهذه البيانات. تأكد من رقم الحجز ورقم الهاتف.", tooMany: "محاولات كثيرة. انتظر دقيقة ثم حاول مرة أخرى.",
  network: "تعذّر الاتصال بالخادم. حاول مرة أخرى.", required: "من فضلك اكتب الخانتين.",
  result: { heading: "حجزك", status: "الحالة", date: "التاريخ", time: "الموعد", guests: "الأفراد", units: "الحجز", total: "الإجمالي", hotel: "فندق الاستلام", reason: "السبب" },
  statuses: { NEW: "في انتظار الدفع", CONFIRMED: "مؤكد", COMPLETED: "تمت الرحلة", CANCELLED: "ملغي", EXPIRED: "منتهي (لم يُدفع)" },
  statusHelp: {
    NEW: "لم يصلنا الدفع لهذا الحجز بعد.",
    CONFIRMED: "كل شيء جاهز! سنؤكد لك موعد الاستلام بالضبط على واتساب.",
    COMPLETED: "نتمنى أن تكون استمتعت بالرحلة. شكرًا لسفرك معنا!",
    CANCELLED: "تم إلغاء هذا الحجز.",
    EXPIRED: "لم يكتمل الدفع في الوقت المحدد، فتم إتاحة المكان لغيرك.",
  },
  help: "عندك سؤال عن حجزك؟", whatsapp: "اسألنا على واتساب", another: "ابحث عن حجز آخر",
};

const ru: MyBookingCopy = {
  title: "Моё бронирование", intro: "Введите номер бронирования и телефон, указанный при бронировании.",
  reference: "Номер бронирования", referenceHint: "Начинается с STR-, например STR-2026-1234.", phone: "Телефон", phoneHint: "Номер, указанный при бронировании.", submit: "Найти бронирование",
  notFound: "Бронирование с такими данными не найдено. Проверьте номер и телефон.", tooMany: "Слишком много попыток. Подождите минуту и попробуйте снова.",
  network: "Не удалось связаться с сервером. Попробуйте снова.", required: "Заполните оба поля.",
  result: { heading: "Ваше бронирование", status: "Статус", date: "Дата", time: "Отправление", guests: "Гости", units: "Бронь", total: "Итого", hotel: "Отель для трансфера", reason: "Причина" },
  statuses: { NEW: "Ожидает оплаты", CONFIRMED: "Подтверждено", COMPLETED: "Завершено", CANCELLED: "Отменено", EXPIRED: "Истекло (не оплачено)" },
  statusHelp: {
    NEW: "Оплата за это бронирование ещё не поступила.",
    CONFIRMED: "Всё готово! Точное время трансфера подтвердим в WhatsApp.",
    COMPLETED: "Надеемся, вам понравилось. Спасибо, что выбрали нас!",
    CANCELLED: "Это бронирование отменено.",
    EXPIRED: "Оплата не была завершена вовремя, поэтому места освобождены.",
  },
  help: "Есть вопросы по бронированию?", whatsapp: "Спросить в WhatsApp", another: "Найти другое бронирование",
};

const it: MyBookingCopy = {
  title: "La mia prenotazione", intro: "Inserisci il codice di prenotazione e il telefono usato per prenotare.",
  reference: "Codice di prenotazione", referenceHint: "Inizia con STR-, es. STR-2026-1234.", phone: "Telefono", phoneHint: "Il numero usato per prenotare.", submit: "Trova la prenotazione",
  notFound: "Nessuna prenotazione trovata con questi dati. Controlla codice e telefono.", tooMany: "Troppi tentativi. Attendi un minuto e riprova.",
  network: "Impossibile contattare il server. Riprova.", required: "Compila entrambi i campi.",
  result: { heading: "La tua prenotazione", status: "Stato", date: "Data", time: "Partenza", guests: "Partecipanti", units: "Prenotato come", total: "Totale", hotel: "Hotel di prelievo", reason: "Motivo" },
  statuses: { NEW: "In attesa di pagamento", CONFIRMED: "Confermata", COMPLETED: "Completata", CANCELLED: "Annullata", EXPIRED: "Scaduta (non pagata)" },
  statusHelp: {
    NEW: "Non abbiamo ancora ricevuto il pagamento per questa prenotazione.",
    CONFIRMED: "Tutto pronto! Confermiamo l'orario di prelievo su WhatsApp.",
    COMPLETED: "Speriamo ti sia piaciuta. Grazie per aver viaggiato con noi!",
    CANCELLED: "Questa prenotazione è stata annullata.",
    EXPIRED: "Il pagamento non è stato completato in tempo, quindi il posto è stato liberato.",
  },
  help: "Domande sulla prenotazione?", whatsapp: "Chiedi su WhatsApp", another: "Cerca un'altra prenotazione",
};

export const myBookingCopy: Record<StsLocale, MyBookingCopy> = { en, ar, ru, it };
