import type { StsLocale } from "./locales";

/** "My booking" lookup copy. EN/AR final drafts; RU/IT await native review. */
export interface MyBookingCopy {
  title: string; intro: string;
  reference: string; referenceHint: string; phone: string; phoneHint: string; submit: string;
  notFound: string; tooMany: string; network: string; required: string;
  result: { heading: string; status: string; date: string; time: string; guests: string; units: string; total: string; hotel: string };
  statuses: { NEW: string; CONFIRMED: string; COMPLETED: string; CANCELLED: string; EXPIRED: string };
  statusHelp: { NEW: string; CONFIRMED: string; COMPLETED: string; CANCELLED: string; EXPIRED: string };
  help: string; whatsapp: string; another: string;
  request: {
    heading: string; date: string; total: string;
    statuses: Record<"NEW" | "IN_PROGRESS" | "CONVERTED" | "CLOSED", string>;
    help: Record<"NEW" | "IN_PROGRESS" | "CONVERTED" | "CLOSED", string>;
  };
}

const en: MyBookingCopy = {
  title: "My booking", intro: "Track your request or booking using its reference and your original phone number.",
  reference: "Request or booking reference", referenceHint: "Use the complete STQ- request reference or STR- booking reference.", phone: "Phone number", phoneHint: "Use your original number including the country code, for example +20.", submit: "Find my booking",
  notFound: "We could not find a booking with these details. Check the reference and phone number.", tooMany: "Too many attempts. Please wait a minute and try again.",
  network: "We could not reach the server. Please try again.", required: "Please fill in both fields.",
  result: { heading: "Your booking", status: "Status", date: "Date", time: "Departure", guests: "Guests", units: "Booked as", total: "Total", hotel: "Pickup hotel" },
  statuses: { NEW: "Awaiting payment", CONFIRMED: "Confirmed", COMPLETED: "Completed", CANCELLED: "Cancelled", EXPIRED: "Expired (not paid)" },
  statusHelp: {
    NEW: "We have not received the payment for this booking yet.",
    CONFIRMED: "All set! We confirm the exact pickup time on WhatsApp.",
    COMPLETED: "We hope you enjoyed it. Thank you for travelling with us!",
    CANCELLED: "This booking was cancelled.",
    EXPIRED: "The payment was not completed in time, so the place was released.",
  },
  help: "Questions about your booking?", whatsapp: "Ask on WhatsApp", another: "Look up another booking",
  request: {
    heading: "Your booking request", date: "Preferred date", total: "Estimated total at request",
    statuses: { NEW: "Request received", IN_PROGRESS: "Under review", CONVERTED: "Booking created", CLOSED: "Request closed" },
    help: {
      NEW: "Your request reached our team. The preferred date is not confirmed and no payment has been recorded by submitting it.",
      IN_PROGRESS: "Our team is checking availability and the details of your request. Your departure is not confirmed yet.",
      CONVERTED: "A booking was created from this request. Its latest status and final total appear below; confirmation is not proof of payment.",
      CLOSED: "This request is closed. Contact the team if you would like to arrange a new request.",
    },
  },
};

const ar: MyBookingCopy = {
  title: "حجزي", intro: "تابع طلبك أو حجزك بكتابة المرجع ورقم الهاتف الذي استخدمته.",
  reference: "رقم الطلب أو الحجز", referenceHint: "اكتب المرجع كاملًا: STQ- لطلب الموقع، أو STR- للحجز.", phone: "رقم الهاتف", phoneHint: "نفس الرقم الذي استخدمته، شاملًا كود الدولة مثل +20.", submit: "اعرض حجزي",
  notFound: "لم نجد حجزًا بهذه البيانات. تأكد من رقم الحجز ورقم الهاتف.", tooMany: "محاولات كثيرة. انتظر دقيقة ثم حاول مرة أخرى.",
  network: "تعذّر الاتصال بالخادم. حاول مرة أخرى.", required: "من فضلك اكتب الخانتين.",
  result: { heading: "حجزك", status: "الحالة", date: "التاريخ", time: "الموعد", guests: "الأفراد", units: "الحجز", total: "الإجمالي", hotel: "فندق الاستلام" },
  statuses: { NEW: "في انتظار الدفع", CONFIRMED: "مؤكد", COMPLETED: "تمت الرحلة", CANCELLED: "ملغي", EXPIRED: "منتهي (لم يُدفع)" },
  statusHelp: {
    NEW: "لم يصلنا الدفع لهذا الحجز بعد.",
    CONFIRMED: "كل شيء جاهز! سنؤكد لك موعد الاستلام بالضبط على واتساب.",
    COMPLETED: "نتمنى أن تكون استمتعت بالرحلة. شكرًا لسفرك معنا!",
    CANCELLED: "تم إلغاء هذا الحجز.",
    EXPIRED: "لم يكتمل الدفع في الوقت المحدد، فتم إتاحة المكان لغيرك.",
  },
  help: "عندك سؤال عن حجزك؟", whatsapp: "اسألنا على واتساب", another: "ابحث عن حجز آخر",
  request: {
    heading: "طلب حجزك", date: "التاريخ المفضل", total: "الإجمالي التقديري وقت الطلب",
    statuses: { NEW: "تم استلام الطلب", IN_PROGRESS: "قيد المراجعة", CONVERTED: "تم إنشاء الحجز", CLOSED: "الطلب مغلق" },
    help: {
      NEW: "طلبك وصل للفريق. التاريخ المفضل لم يتأكد بعد، وإرسال الطلب لا يعني سداد أي مبلغ.",
      IN_PROGRESS: "الفريق يراجع التوافر وتفاصيل طلبك. موعد الانطلاق غير مؤكد حتى الآن.",
      CONVERTED: "تم إنشاء حجز من طلبك. ستجد أحدث حالته وسعره النهائي بالأسفل؛ تأكيد الحجز لا يعني سداد المبلغ.",
      CLOSED: "تم إغلاق هذا الطلب. تواصل مع الفريق لو حابب ترتب طلبًا جديدًا.",
    },
  },
};

const ru: MyBookingCopy = {
  title: "Моё бронирование", intro: "Проверьте заявку или бронирование по номеру и указанному вами телефону.",
  reference: "Номер заявки или бронирования", referenceHint: "Полный номер STQ- для заявки или STR- для бронирования.", phone: "Телефон", phoneHint: "Исходный номер с кодом страны, например +20.", submit: "Найти бронирование",
  notFound: "Бронирование с такими данными не найдено. Проверьте номер и телефон.", tooMany: "Слишком много попыток. Подождите минуту и попробуйте снова.",
  network: "Не удалось связаться с сервером. Попробуйте снова.", required: "Заполните оба поля.",
  result: { heading: "Ваше бронирование", status: "Статус", date: "Дата", time: "Отправление", guests: "Гости", units: "Бронь", total: "Итого", hotel: "Отель для трансфера" },
  statuses: { NEW: "Ожидает оплаты", CONFIRMED: "Подтверждено", COMPLETED: "Завершено", CANCELLED: "Отменено", EXPIRED: "Истекло (не оплачено)" },
  statusHelp: {
    NEW: "Оплата за это бронирование ещё не поступила.",
    CONFIRMED: "Всё готово! Точное время трансфера подтвердим в WhatsApp.",
    COMPLETED: "Надеемся, вам понравилось. Спасибо, что выбрали нас!",
    CANCELLED: "Это бронирование отменено.",
    EXPIRED: "Оплата не была завершена вовремя, поэтому места освобождены.",
  },
  help: "Есть вопросы по бронированию?", whatsapp: "Спросить в WhatsApp", another: "Найти другое бронирование",
  request: {
    heading: "Ваша заявка", date: "Желаемая дата", total: "Ориентировочная сумма при подаче",
    statuses: { NEW: "Заявка получена", IN_PROGRESS: "На рассмотрении", CONVERTED: "Бронирование создано", CLOSED: "Заявка закрыта" },
    help: {
      NEW: "Команда получила заявку. Желаемая дата ещё не подтверждена; подача заявки не означает оплату.",
      IN_PROGRESS: "Команда проверяет доступность и детали. Выезд пока не подтверждён.",
      CONVERTED: "По заявке создано бронирование. Его текущий статус и итоговая сумма указаны ниже. Подтверждение не означает оплату.",
      CLOSED: "Заявка закрыта. Свяжитесь с командой, если хотите оформить новую.",
    },
  },
};

const it: MyBookingCopy = {
  title: "La mia prenotazione", intro: "Controlla la richiesta o prenotazione con il codice e il telefono usato all'invio.",
  reference: "Codice richiesta o prenotazione", referenceHint: "Codice completo STQ- per la richiesta o STR- per la prenotazione.", phone: "Telefono", phoneHint: "Il numero originale con prefisso internazionale, ad esempio +20.", submit: "Trova la prenotazione",
  notFound: "Nessuna prenotazione trovata con questi dati. Controlla codice e telefono.", tooMany: "Troppi tentativi. Attendi un minuto e riprova.",
  network: "Impossibile contattare il server. Riprova.", required: "Compila entrambi i campi.",
  result: { heading: "La tua prenotazione", status: "Stato", date: "Data", time: "Partenza", guests: "Partecipanti", units: "Prenotato come", total: "Totale", hotel: "Hotel di prelievo" },
  statuses: { NEW: "In attesa di pagamento", CONFIRMED: "Confermata", COMPLETED: "Completata", CANCELLED: "Annullata", EXPIRED: "Scaduta (non pagata)" },
  statusHelp: {
    NEW: "Non abbiamo ancora ricevuto il pagamento per questa prenotazione.",
    CONFIRMED: "Tutto pronto! Confermiamo l'orario di prelievo su WhatsApp.",
    COMPLETED: "Speriamo ti sia piaciuta. Grazie per aver viaggiato con noi!",
    CANCELLED: "Questa prenotazione è stata annullata.",
    EXPIRED: "Il pagamento non è stato completato in tempo, quindi il posto è stato liberato.",
  },
  help: "Domande sulla prenotazione?", whatsapp: "Chiedi su WhatsApp", another: "Cerca un'altra prenotazione",
  request: {
    heading: "La tua richiesta", date: "Data preferita", total: "Totale stimato all'invio",
    statuses: { NEW: "Richiesta ricevuta", IN_PROGRESS: "In verifica", CONVERTED: "Prenotazione creata", CLOSED: "Richiesta chiusa" },
    help: {
      NEW: "La richiesta è arrivata al team. La data preferita non è confermata e l'invio non comporta un pagamento.",
      IN_PROGRESS: "Il team verifica disponibilità e dettagli. La partenza non è ancora confermata.",
      CONVERTED: "Dalla richiesta è stata creata una prenotazione. Stato attuale e totale finale sono riportati sotto; la conferma non dimostra il pagamento.",
      CLOSED: "La richiesta è chiusa. Contatta il team per organizzare una nuova richiesta.",
    },
  },
};

export const myBookingCopy: Record<StsLocale, MyBookingCopy> = { en, ar, ru, it };
