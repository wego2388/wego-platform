import type { StsLocale } from "./locales";

/**
 * Checkout copy (UX-4). EN and AR are final drafts; RU and IT await native
 * review. The English button labels "Continue" and "Confirm & Pay" are
 * relied on by the checkout end-to-end test.
 */
export interface CheckoutCopy {
  title: string;
  steps: [string, string, string];
  summary: { heading: string; date: string; time: string; guests: string; units: string; total: string; change: string };
  details: {
    heading: string; intro: string;
    fullName: string; fullNameHint: string;
    phone: string; phoneHint: string;
    nationality: string; nationalityPlaceholder: string; common: string; all: string;
    email: string; emailHint: string;
    hotel: string; hotelHint: string;
    room: string; requests: string; requestsHint: string;
    optional: string; continue: string;
  };
  review: {
    heading: string; name: string; phone: string; nationality: string; email: string; hotel: string; room: string; requests: string;
    terms: [string, string, string, string]; pay: string; back: string; secure: string; policy: string; paying: string;
  };
  errors: {
    summary: string; required: string; phone: string; email: string; terms: string;
    slotFull: string; slotBlocked: string; tourInactive: string; salesPaused: string; pricing: string; tooMany: string; payment: string; network: string; generic: string;
    missingSlot: string; whatsapp: string;
  };
}

const en: CheckoutCopy = {
  title: "Complete your booking",
  steps: ["Your details", "Review", "Payment"],
  summary: { heading: "Your trip", date: "Date", time: "Departure", guests: "Guests", units: "Booked as", total: "Total", change: "Change" },
  details: {
    heading: "Your details", intro: "We use these details only to confirm your booking and arrange your pickup.",
    fullName: "Full name", fullNameHint: "As on your passport or ID.",
    phone: "Phone / WhatsApp", phoneHint: "With country code, e.g. +44 7700 900123. We confirm the pickup time here.",
    nationality: "Nationality", nationalityPlaceholder: "Select your nationality", common: "Most common", all: "All countries",
    email: "Email", emailHint: "For your confirmation and receipt.",
    hotel: "Hotel in Sharm El Sheikh", hotelHint: "We pick you up from your hotel reception.",
    room: "Room number", requests: "Special requests", requestsHint: "Dietary needs, accessibility, celebrations…",
    optional: "optional", continue: "Continue",
  },
  review: {
    heading: "Review and pay", name: "Name", phone: "Phone", nationality: "Nationality", email: "Email", hotel: "Hotel", room: "Room", requests: "Requests",
    terms: ["I agree to the ", "Terms & Conditions", " and the ", "Privacy Policy"], pay: "Confirm & Pay", back: "Back",
    secure: "You will pay securely on Paymob. We never see your card details.", policy: "Cancellation policy", paying: "Taking you to secure payment…",
  },
  errors: {
    summary: "Please check these fields:", required: "Please fill this in.", phone: "Enter a phone number with country code, e.g. +44 7700 900123.",
    email: "Enter a valid email address.", terms: "Please accept the terms to continue.",
    slotFull: "Sorry — this departure just filled up. Please go back and choose another time.",
    slotBlocked: "This departure is no longer available. Please choose another date.",
    tourInactive: "This tour is not available right now. Please contact us on WhatsApp.",
    salesPaused: "Online booking is paused for a short time. Please contact us on WhatsApp and we will book it for you.",
    pricing: "Your selection no longer matches this tour. Please go back to the tour page and choose again.",
    tooMany: "Too many attempts. Please wait a minute and try again.",
    payment: "The payment service is temporarily unavailable. Please try again, or book on WhatsApp.",
    network: "We could not reach the server. Check your connection and try again.",
    generic: "Something went wrong. Please try again, or book on WhatsApp.",
    missingSlot: "We could not find your trip details. Please choose a date on the tour page.",
    whatsapp: "Book on WhatsApp",
  },
};

const ar: CheckoutCopy = {
  title: "أكمل حجزك",
  steps: ["بياناتك", "المراجعة", "الدفع"],
  summary: { heading: "رحلتك", date: "التاريخ", time: "الموعد", guests: "الأفراد", units: "الحجز", total: "الإجمالي", change: "تغيير" },
  details: {
    heading: "بياناتك", intro: "نستخدم هذه البيانات فقط لتأكيد حجزك وترتيب الاستلام من الفندق.",
    fullName: "الاسم الكامل", fullNameHint: "كما في جواز السفر أو البطاقة.",
    phone: "الهاتف / واتساب", phoneHint: "مع كود الدولة، مثل \u2066+20 100 123 4567\u2069. نؤكد لك موعد الاستلام عليه.",
    nationality: "الجنسية", nationalityPlaceholder: "اختر جنسيتك", common: "الأكثر شيوعًا", all: "كل الدول",
    email: "البريد الإلكتروني", emailHint: "لإرسال التأكيد والإيصال.",
    hotel: "الفندق في شرم الشيخ", hotelHint: "نستلمك من استقبال الفندق.",
    room: "رقم الغرفة", requests: "طلبات خاصة", requestsHint: "طعام خاص، احتياجات حركية، مناسبة…",
    optional: "اختياري", continue: "متابعة",
  },
  review: {
    heading: "المراجعة والدفع", name: "الاسم", phone: "الهاتف", nationality: "الجنسية", email: "البريد", hotel: "الفندق", room: "الغرفة", requests: "الطلبات",
    terms: ["أوافق على ", "الشروط والأحكام", " و", "سياسة الخصوصية"], pay: "تأكيد والدفع", back: "رجوع",
    secure: "الدفع آمن عبر Paymob، ولا نطّلع على بيانات بطاقتك.", policy: "سياسة الإلغاء", paying: "جارٍ تحويلك إلى صفحة الدفع الآمن…",
  },
  errors: {
    summary: "من فضلك راجع هذه الخانات:", required: "هذه الخانة مطلوبة.", phone: "اكتب رقم الهاتف مع كود الدولة، مثل \u2066+20 100 123 4567\u2069.",
    email: "اكتب بريدًا إلكترونيًا صحيحًا.", terms: "من فضلك وافق على الشروط للمتابعة.",
    slotFull: "عذرًا — اكتمل هذا الموعد للتو. ارجع واختر موعدًا آخر.",
    slotBlocked: "هذا الموعد لم يعد متاحًا. اختر تاريخًا آخر.",
    tourInactive: "هذه الرحلة غير متاحة الآن. تواصل معنا على واتساب.",
    salesPaused: "الحجز عبر الموقع متوقف مؤقتًا. تواصل معنا على واتساب وسنحجز لك بأنفسنا.",
    pricing: "اختيارك لم يعد مطابقًا لهذه الرحلة. ارجع لصفحة الرحلة واختر من جديد.",
    tooMany: "محاولات كثيرة. انتظر دقيقة ثم حاول مرة أخرى.",
    payment: "خدمة الدفع غير متاحة مؤقتًا. حاول مرة أخرى أو احجز عبر واتساب.",
    network: "تعذّر الاتصال بالخادم. تأكد من الإنترنت وحاول مرة أخرى.",
    generic: "حدث خطأ. حاول مرة أخرى أو احجز عبر واتساب.",
    missingSlot: "لم نجد تفاصيل رحلتك. اختر موعدًا من صفحة الرحلة.",
    whatsapp: "احجز عبر واتساب",
  },
};

const ru: CheckoutCopy = {
  title: "Завершите бронирование",
  steps: ["Ваши данные", "Проверка", "Оплата"],
  summary: { heading: "Ваша поездка", date: "Дата", time: "Отправление", guests: "Гости", units: "Бронь", total: "Итого", change: "Изменить" },
  details: {
    heading: "Ваши данные", intro: "Мы используем эти данные только для подтверждения брони и трансфера из отеля.",
    fullName: "Полное имя", fullNameHint: "Как в паспорте.",
    phone: "Телефон / WhatsApp", phoneHint: "С кодом страны, например +7 900 123-45-67. Здесь мы подтвердим время трансфера.",
    nationality: "Гражданство", nationalityPlaceholder: "Выберите гражданство", common: "Популярные", all: "Все страны",
    email: "Эл. почта", emailHint: "Для подтверждения и квитанции.",
    hotel: "Отель в Шарм-эль-Шейхе", hotelHint: "Мы заберём вас с ресепшена отеля.",
    room: "Номер комнаты", requests: "Особые пожелания", requestsHint: "Питание, доступность, праздник…",
    optional: "необязательно", continue: "Продолжить",
  },
  review: {
    heading: "Проверка и оплата", name: "Имя", phone: "Телефон", nationality: "Гражданство", email: "Эл. почта", hotel: "Отель", room: "Номер", requests: "Пожелания",
    terms: ["Я принимаю ", "Условия", " и ", "Политику конфиденциальности"], pay: "Подтвердить и оплатить", back: "Назад",
    secure: "Оплата проходит безопасно через Paymob; мы не видим данные вашей карты.", policy: "Условия отмены", paying: "Переходим к безопасной оплате…",
  },
  errors: {
    summary: "Проверьте эти поля:", required: "Заполните это поле.", phone: "Введите номер с кодом страны, например +7 900 123-45-67.",
    email: "Введите корректный адрес эл. почты.", terms: "Примите условия, чтобы продолжить.",
    slotFull: "Извините — места на это время только что закончились. Выберите другое время.",
    slotBlocked: "Это время больше недоступно. Выберите другую дату.",
    tourInactive: "Этот тур сейчас недоступен. Напишите нам в WhatsApp.",
    salesPaused: "Онлайн-бронирование временно приостановлено. Напишите нам в WhatsApp — мы забронируем для вас.",
    pricing: "Ваш выбор больше не подходит к туру. Вернитесь на страницу тура и выберите заново.",
    tooMany: "Слишком много попыток. Подождите минуту и попробуйте снова.",
    payment: "Платёжный сервис временно недоступен. Попробуйте снова или забронируйте в WhatsApp.",
    network: "Не удалось связаться с сервером. Проверьте подключение и попробуйте снова.",
    generic: "Что-то пошло не так. Попробуйте снова или забронируйте в WhatsApp.",
    missingSlot: "Не нашли данные поездки. Выберите дату на странице тура.",
    whatsapp: "Забронировать в WhatsApp",
  },
};

const it: CheckoutCopy = {
  title: "Completa la prenotazione",
  steps: ["I tuoi dati", "Riepilogo", "Pagamento"],
  summary: { heading: "Il tuo viaggio", date: "Data", time: "Partenza", guests: "Partecipanti", units: "Prenotato come", total: "Totale", change: "Modifica" },
  details: {
    heading: "I tuoi dati", intro: "Usiamo questi dati solo per confermare la prenotazione e organizzare il prelievo.",
    fullName: "Nome e cognome", fullNameHint: "Come sul passaporto o documento.",
    phone: "Telefono / WhatsApp", phoneHint: "Con prefisso internazionale, es. +39 333 123 4567. Qui confermiamo l'orario di prelievo.",
    nationality: "Nazionalità", nationalityPlaceholder: "Seleziona la nazionalità", common: "Più comuni", all: "Tutti i paesi",
    email: "Email", emailHint: "Per la conferma e la ricevuta.",
    hotel: "Hotel a Sharm El Sheikh", hotelHint: "Ti preleviamo alla reception dell'hotel.",
    room: "Numero di camera", requests: "Richieste speciali", requestsHint: "Esigenze alimentari, accessibilità, ricorrenze…",
    optional: "facoltativo", continue: "Continua",
  },
  review: {
    heading: "Riepilogo e pagamento", name: "Nome", phone: "Telefono", nationality: "Nazionalità", email: "Email", hotel: "Hotel", room: "Camera", requests: "Richieste",
    terms: ["Accetto i ", "Termini e condizioni", " e l'", "Informativa sulla privacy"], pay: "Conferma e paga", back: "Indietro",
    secure: "Paghi in sicurezza su Paymob; non vediamo mai i dati della tua carta.", policy: "Politica di cancellazione", paying: "Ti portiamo al pagamento sicuro…",
  },
  errors: {
    summary: "Controlla questi campi:", required: "Compila questo campo.", phone: "Inserisci il numero con prefisso, es. +39 333 123 4567.",
    email: "Inserisci un indirizzo email valido.", terms: "Accetta i termini per continuare.",
    slotFull: "Spiacenti — questa partenza si è appena riempita. Scegli un altro orario.",
    slotBlocked: "Questa partenza non è più disponibile. Scegli un'altra data.",
    tourInactive: "Questa escursione non è disponibile al momento. Scrivici su WhatsApp.",
    salesPaused: "La prenotazione online è sospesa per poco tempo. Scrivici su WhatsApp e prenotiamo noi per te.",
    pricing: "La tua scelta non corrisponde più a questa escursione. Torna alla pagina e scegli di nuovo.",
    tooMany: "Troppi tentativi. Attendi un minuto e riprova.",
    payment: "Il servizio di pagamento non è disponibile al momento. Riprova o prenota su WhatsApp.",
    network: "Impossibile contattare il server. Controlla la connessione e riprova.",
    generic: "Qualcosa è andato storto. Riprova o prenota su WhatsApp.",
    missingSlot: "Non troviamo i dettagli del viaggio. Scegli una data nella pagina dell'escursione.",
    whatsapp: "Prenota su WhatsApp",
  },
};

export const checkoutCopy: Record<StsLocale, CheckoutCopy> = { en, ar, ru, it };

/** Nationalities shown first: the operator's main markets. */
export const COMMON_NATIONALITIES = ["EG", "GB", "DE", "IT", "RU", "UA", "PL", "FR", "SA", "AE", "US", "TR"];

/** ISO 3166-1 alpha-2 codes; names come from Intl.DisplayNames in the visitor's language. */
export const ALL_NATIONALITIES = (
  "AD AE AF AG AL AM AO AR AT AU AZ BA BB BD BE BF BG BH BI BJ BN BO BR BS BT BW BY BZ CA CD CF CG CH CI CL CM CN CO CR CU CV CY CZ " +
  "DE DJ DK DM DO DZ EC EE EG ER ES ET FI FJ FM FR GA GB GD GE GH GM GN GQ GR GT GW GY HK HN HR HT HU ID IE IL IN IQ IR IS IT JM JO JP " +
  "KE KG KH KI KM KN KP KR KW KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MG MH MK ML MM MN MR MT MU MV MW MX MY MZ NA NE NG NI NL " +
  "NO NP NR NZ OM PA PE PG PH PK PL PS PT PW PY QA RO RS RU RW SA SB SC SD SE SG SI SK SL SM SN SO SR SS ST SV SY SZ TD TG TH TJ TL TM " +
  "TN TO TR TT TV TW TZ UA UG US UY UZ VA VC VE VN VU WS XK YE ZA ZM ZW"
).split(" ");

/** Loose international phone check: optional +, 8–15 digits, common separators. */
export function isPlausiblePhone(value: string): boolean {
  const trimmed = value.trim();
  if (!/^\+?[0-9 ()./-]+$/.test(trimmed)) return false;
  const digits = trimmed.replace(/\D/g, "");
  return digits.length >= 8 && digits.length <= 15;
}

/**
 * One stored form per phone number so a later lookup matches however the
 * guest types it: separators dropped, a leading "00" becomes "+".
 */
export function normalizePhone(value: string): string {
  const trimmed = value.trim();
  const digits = trimmed.replace(/\D/g, "");
  if (trimmed.startsWith("+")) return `+${digits}`;
  if (digits.startsWith("00")) return `+${digits.slice(2)}`;
  return digits;
}

export function isPlausibleEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value.trim());
}
