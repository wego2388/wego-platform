import type { StsLocale } from "./locales";

/**
 * Copy for the payment-result and confirmation pages (UX-4). EN and AR are
 * final drafts; RU and IT await native review. The English strings
 * "Booking Confirmed" and "Payment is not confirmed" are relied on by the
 * checkout end-to-end test.
 */
export interface BookingResultCopy {
  checking: { title: string; waiting: string; body: string; slow: string; again: string };
  paid: { title: string; body: string };
  failed: { title: string; body: string; retry: string };
  review: { title: string; body: string };
  error: { title: string; missing: string; status: string };
  confirmed: {
    title: string; reference: string; paid: string; ticket: string;
    date: string; time: string; guests: string; units: string; total: string; hotel: string;
    nextTitle: string; next: string[]; addCalendar: string; another: string; later: string; myBooking: string;
  };
  unconfirmed: { title: string; findTitle: string; pending: string; notPaid: string; verify: string; gone: string; check: string };
  support: string;
  whatsapp: { paymentHelp: (ref: string) => string; confirm: (ref: string) => string };
  calendarTitle: (tour: string) => string;
}

const en: BookingResultCopy = {
  checking: { title: "Checking your payment…", waiting: "Waiting for payment confirmation…", body: "This usually takes a few seconds. Please keep this page open.", slow: "Taking longer than expected?", again: "Check again" },
  paid: { title: "Payment confirmed!", body: "Taking you to your booking…" },
  failed: { title: "Payment not completed", body: "Your card was not charged for this booking. The place we held for you will be released shortly.", retry: "Choose a date again" },
  review: { title: "We're checking your payment", body: "We cannot safely confirm the final payment result yet. Please do not pay again until our local team has checked it." },
  error: { title: "Something went wrong", missing: "We could not find your booking in this tab. Check it on the My booking page or contact us.", status: "We could not retrieve the payment status. Please contact us on WhatsApp." },
  confirmed: {
    title: "Booking Confirmed!", reference: "Your booking reference", paid: "Payment confirmed", ticket: "Your booking",
    date: "Date", time: "Departure", guests: "Guests", units: "Booked as", total: "Total paid", hotel: "Pickup hotel",
    nextTitle: "What happens next",
    next: [
      "Keep your phone nearby — we confirm your exact pickup time on WhatsApp.",
      "Show your booking reference to the guide or driver.",
      "Need to change something? Message us with your reference.",
    ],
    addCalendar: "Add to calendar", another: "Book another tour", later: "Want to check your booking later?", myBooking: "My booking",
  },
  unconfirmed: {
    title: "Payment is not confirmed", findTitle: "Find your booking", pending: "Your payment has not been confirmed yet. Please wait a moment or contact our local team.",
    notPaid: "This booking is not confirmed as paid. Please contact our local team before paying again.",
    verify: "We could not verify this payment, so no confirmation has been issued. Please contact our local team.",
    gone: "Your booking details are not stored in this tab any more. Look them up securely on My booking with your reference and phone number.", check: "Check my booking",
  },
  support: "Contact us on WhatsApp",
  whatsapp: {
    paymentHelp: (ref) => `Hello, I just tried to pay for booking ${ref} and I'm not sure the payment went through. Can you help?`,
    confirm: (ref) => `Hello, I just booked with reference ${ref}.`,
  },
  calendarTitle: (tour) => `${tour} — Safari Tours Sharm`,
};

const ar: BookingResultCopy = {
  checking: { title: "جارٍ التحقق من الدفع…", waiting: "في انتظار تأكيد الدفع…", body: "عادةً يستغرق ذلك ثوانٍ قليلة. من فضلك لا تغلق الصفحة.", slow: "استغرق وقتًا أطول من المتوقع؟", again: "تحقق مرة أخرى" },
  paid: { title: "تم تأكيد الدفع!", body: "جارٍ نقلك إلى تفاصيل حجزك…" },
  failed: { title: "لم يكتمل الدفع", body: "لم يتم خصم أي مبلغ من بطاقتك لهذا الحجز، وسيتم إلغاء حجز المكان قريبًا.", retry: "اختر موعدًا من جديد" },
  review: { title: "نراجع عملية الدفع", body: "لا يمكننا تأكيد نتيجة الدفع النهائية بأمان الآن. من فضلك لا تدفع مرة أخرى حتى يراجعها فريقنا." },
  error: { title: "حدث خطأ", missing: "لم نجد حجزك في هذه الصفحة. راجعه من صفحة «حجزي» أو تواصل معنا.", status: "تعذّر معرفة حالة الدفع. من فضلك تواصل معنا على واتساب." },
  confirmed: {
    title: "تم تأكيد الحجز!", reference: "رقم حجزك", paid: "تم تأكيد الدفع", ticket: "حجزك",
    date: "التاريخ", time: "الموعد", guests: "الأفراد", units: "الحجز", total: "المبلغ المدفوع", hotel: "فندق الاستلام",
    nextTitle: "الخطوات التالية",
    next: [
      "خلّي تليفونك قريب — هنأكد لك موعد الاستلام بالظبط على واتساب.",
      "اعرض رقم الحجز على المرشد أو السائق.",
      "محتاج تغيّر حاجة؟ ابعتلنا برقم الحجز.",
    ],
    addCalendar: "أضف إلى التقويم", another: "احجز رحلة أخرى", later: "تريد مراجعة حجزك لاحقًا؟", myBooking: "حجزي",
  },
  unconfirmed: {
    title: "الدفع غير مؤكد", findTitle: "ابحث عن حجزك", pending: "لم يتم تأكيد الدفع بعد. انتظر قليلًا أو تواصل مع فريقنا.",
    notPaid: "هذا الحجز غير مؤكد كمدفوع. تواصل مع فريقنا قبل الدفع مرة أخرى.",
    verify: "تعذّر التحقق من الدفع، لذلك لم يصدر أي تأكيد. تواصل مع فريقنا.",
    gone: "تفاصيل حجزك لم تعد متاحة في هذه الصفحة. استخدم «حجزي» للبحث عنها بأمان.", check: "راجع حجزي",
  },
  support: "تواصل معنا على واتساب",
  whatsapp: {
    paymentHelp: (ref) => `مرحبًا، حاولت الدفع للحجز ${ref} ولست متأكدًا إن كان الدفع تم. ممكن تساعدوني؟`,
    confirm: (ref) => `مرحبًا، حجزت الآن برقم ${ref}.`,
  },
  calendarTitle: (tour) => `${tour} — Safari Tours Sharm`,
};

const ru: BookingResultCopy = {
  checking: { title: "Проверяем оплату…", waiting: "Ждём подтверждения оплаты…", body: "Обычно это занимает несколько секунд. Не закрывайте страницу.", slow: "Занимает больше времени, чем обычно?", again: "Проверить снова" },
  paid: { title: "Оплата подтверждена!", body: "Переходим к вашему бронированию…" },
  failed: { title: "Оплата не завершена", body: "С вашей карты ничего не списано за это бронирование. Удерживаемые места скоро освободятся.", retry: "Выбрать дату снова" },
  review: { title: "Мы проверяем вашу оплату", body: "Сейчас мы не можем надёжно подтвердить результат оплаты. Не оплачивайте повторно, пока наша команда не проверит." },
  error: { title: "Что-то пошло не так", missing: "Мы не нашли бронирование в этой вкладке. Проверьте его на странице «Моё бронирование» или напишите нам.", status: "Не удалось получить статус оплаты. Напишите нам в WhatsApp." },
  confirmed: {
    title: "Бронирование подтверждено!", reference: "Номер бронирования", paid: "Оплата подтверждена", ticket: "Ваше бронирование",
    date: "Дата", time: "Отправление", guests: "Гости", units: "Бронь", total: "Оплачено", hotel: "Отель для трансфера",
    nextTitle: "Что дальше",
    next: [
      "Держите телефон рядом — точное время трансфера мы подтвердим в WhatsApp.",
      "Покажите номер бронирования гиду или водителю.",
      "Нужно что-то изменить? Напишите нам с номером бронирования.",
    ],
    addCalendar: "Добавить в календарь", another: "Забронировать ещё", later: "Хотите проверить бронирование позже?", myBooking: "Моё бронирование",
  },
  unconfirmed: {
    title: "Оплата не подтверждена", findTitle: "Найдите бронирование", pending: "Оплата ещё не подтверждена. Подождите немного или свяжитесь с нашей командой.",
    notPaid: "Это бронирование не подтверждено как оплаченное. Свяжитесь с нами, прежде чем платить снова.",
    verify: "Не удалось проверить оплату, поэтому подтверждение не выдано. Свяжитесь с нашей командой.",
    gone: "Данные бронирования больше недоступны в этой вкладке. Найдите их на странице «Моё бронирование».", check: "Проверить бронирование",
  },
  support: "Написать в WhatsApp",
  whatsapp: {
    paymentHelp: (ref) => `Здравствуйте, я пытался оплатить бронирование ${ref} и не уверен, прошла ли оплата. Поможете?`,
    confirm: (ref) => `Здравствуйте, я только что забронировал, номер ${ref}.`,
  },
  calendarTitle: (tour) => `${tour} — Safari Tours Sharm`,
};

const it: BookingResultCopy = {
  checking: { title: "Verifichiamo il pagamento…", waiting: "In attesa della conferma del pagamento…", body: "Di solito servono pochi secondi. Tieni aperta questa pagina.", slow: "Ci vuole più del previsto?", again: "Controlla di nuovo" },
  paid: { title: "Pagamento confermato!", body: "Ti portiamo alla tua prenotazione…" },
  failed: { title: "Pagamento non completato", body: "Non ti è stato addebitato nulla per questa prenotazione. Il posto riservato sarà liberato a breve.", retry: "Scegli di nuovo la data" },
  review: { title: "Stiamo verificando il pagamento", body: "Non possiamo ancora confermare con certezza l'esito del pagamento. Non pagare di nuovo finché il nostro team non lo verifica." },
  error: { title: "Qualcosa è andato storto", missing: "Non troviamo la prenotazione in questa scheda. Controllala in «La mia prenotazione» o scrivici.", status: "Impossibile ottenere lo stato del pagamento. Scrivici su WhatsApp." },
  confirmed: {
    title: "Prenotazione confermata!", reference: "Il tuo codice di prenotazione", paid: "Pagamento confermato", ticket: "La tua prenotazione",
    date: "Data", time: "Partenza", guests: "Partecipanti", units: "Prenotato come", total: "Totale pagato", hotel: "Hotel di prelievo",
    nextTitle: "Cosa succede ora",
    next: [
      "Tieni il telefono a portata di mano: confermiamo l'orario di prelievo su WhatsApp.",
      "Mostra il codice di prenotazione alla guida o all'autista.",
      "Devi cambiare qualcosa? Scrivici con il codice.",
    ],
    addCalendar: "Aggiungi al calendario", another: "Prenota un'altra escursione", later: "Vuoi controllare la prenotazione più tardi?", myBooking: "La mia prenotazione",
  },
  unconfirmed: {
    title: "Pagamento non confermato", findTitle: "Trova la prenotazione", pending: "Il pagamento non è ancora confermato. Attendi un momento o contatta il nostro team.",
    notPaid: "Questa prenotazione non risulta pagata. Contattaci prima di pagare di nuovo.",
    verify: "Non è stato possibile verificare il pagamento, quindi non è stata emessa alcuna conferma. Contatta il nostro team.",
    gone: "I dettagli della prenotazione non sono più disponibili in questa scheda. Usa «La mia prenotazione».", check: "Controlla la prenotazione",
  },
  support: "Scrivici su WhatsApp",
  whatsapp: {
    paymentHelp: (ref) => `Ciao, ho provato a pagare la prenotazione ${ref} e non so se il pagamento è andato a buon fine. Potete aiutarmi?`,
    confirm: (ref) => `Ciao, ho appena prenotato con il codice ${ref}.`,
  },
  calendarTitle: (tour) => `${tour} — Safari Tours Sharm`,
};

export const bookingResultCopy: Record<StsLocale, BookingResultCopy> = { en, ar, ru, it };

/**
 * An all-day calendar entry for the tour date (the exact pickup time is
 * confirmed later on WhatsApp). RFC 5545 text, CRLF line endings.
 */
export function buildCalendarFile(input: { uid: string; date: string; title: string; description: string; now?: Date }): string {
  const escape = (value: string) => value.replace(/\\/g, "\\\\").replace(/;/g, "\\;").replace(/,/g, "\\,").replace(/\r?\n/g, "\\n");
  const day = input.date.replace(/-/g, "");
  const next = new Date(`${input.date}T00:00:00Z`);
  next.setUTCDate(next.getUTCDate() + 1);
  const end = next.toISOString().slice(0, 10).replace(/-/g, "");
  const stamp = (input.now ?? new Date()).toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");
  return [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//Safari Tours Sharm//Booking//EN",
    "CALSCALE:GREGORIAN",
    "BEGIN:VEVENT",
    `UID:${input.uid}@safaritourssharm.com`,
    `DTSTAMP:${stamp}`,
    `DTSTART;VALUE=DATE:${day}`,
    `DTEND;VALUE=DATE:${end}`,
    `SUMMARY:${escape(input.title)}`,
    `DESCRIPTION:${escape(input.description)}`,
    "LOCATION:Sharm El Sheikh\\, Egypt",
    "END:VEVENT",
    "END:VCALENDAR",
    "",
  ]
    .map(foldLine)
    .join("\r\n");
}

/** RFC 5545 line folding: at most 75 octets per line, continuation lines start with a space. */
function foldLine(line: string): string {
  const encoder = new TextEncoder();
  if (encoder.encode(line).length <= 75) return line;
  const parts: string[] = [];
  let current = "";
  for (const char of line) {
    const limit = parts.length === 0 ? 75 : 74;
    if (encoder.encode(current + char).length > limit) {
      parts.push(current);
      current = char;
    } else {
      current += char;
    }
  }
  parts.push(current);
  return parts.join("\r\n ");
}
