import type { StsLocale } from "./locales";

/**
 * Interface copy for the discovery pages (UX-2). EN and AR are final drafts;
 * RU and IT are drafts pending review by a native speaker before launch
 * (ROADMAP_AR.md 5-2). Tour names and descriptions never live here — they come
 * from published tour content.
 */
export interface DiscoveryCopy {
  nav: {
    tours: string; categories: string; contact: string; myBooking: string; main: string;
    menu: string; closeMenu: string; language: string; book: string; whatsapp: string;
    theme: { system: string; light: string; dark: string };
  };
  hero: { eyebrow: string; title: string; body: string; primary: string; secondary: string };
  explore: { label: string; all: string; submit: string };
  categories: { heading: string; body: string; count: (n: number) => string };
  featured: { heading: string; body: string; all: string };
  why: { heading: string; items: { icon: string; title: string; body: string }[] };
  faq: { heading: string; items: { title: string; body: string }[] };
  cta: { title: string; body: string; primary: string; secondary: string };
  tours: {
    title: string; body: string; searchLabel: string; searchPlaceholder: string;
    filters: string; category: string; duration: string; timeOfDay: string; maxPrice: string; any: string;
    durations: { short: string; half: string; full: string; multi: string };
    slots: { SUNRISE: string; MORNING: string; AFTERNOON: string; SUNSET: string };
    sort: string; sorts: { recommended: string; priceAsc: string; priceDesc: string; duration: string };
    results: (n: number) => string; show: (n: number) => string; clear: string;
    emptyTitle: string; emptyBody: string; askWhatsapp: string;
  };
  card: { from: string; onRequest: string; details: string; perPerson: string };
  category: { all: string; back: string };
  footer: {
    tagline: string; explore: string; help: string; company: string;
    myBooking: string; contact: string; privacy: string; terms: string; cancellation: string; rights: string;
  };
  errors: { load: string; retry: string };
}

const en: DiscoveryCopy = {
  nav: {
    tours: "Tours", categories: "Experiences", contact: "Contact", myBooking: "My booking",
    main: "Main navigation", menu: "Open menu", closeMenu: "Close menu", language: "Language", book: "Book a tour", whatsapp: "WhatsApp",
    theme: { system: "Theme: automatic", light: "Theme: light", dark: "Theme: dark" },
  },
  hero: {
    eyebrow: "Sharm El Sheikh · Red Sea · Sinai",
    title: "Sharm El Sheikh Tours & Excursions — Book Direct",
    body: "Desert safaris, Red Sea boat trips and journeys to Cairo and Luxor — clear prices, real availability and a confirmed booking in minutes.",
    primary: "Explore tours",
    secondary: "Ask on WhatsApp",
  },
  explore: { label: "What would you like to do?", all: "Everything", submit: "Show tours" },
  categories: {
    heading: "Choose your kind of adventure",
    body: "From golden dunes to coral reefs — most trips include pickup from your hotel.",
    count: (n) => (n === 1 ? "1 tour" : `${n} tours`),
  },
  featured: { heading: "Featured tours", body: "A good place to start.", all: "See all tours" },
  why: {
    heading: "Why book directly with us",
    items: [
      { icon: "lucide:badge-euro", title: "The price you see is the price you pay", body: "Final prices in euros, calculated before you pay — no surprises at pickup." },
      { icon: "lucide:shield-check", title: "Secure online payment", body: "Card payments are handled by Paymob; we never see your card details." },
      { icon: "lucide:calendar-check", title: "Real availability", body: "Dates and seats come straight from our schedule, so what you book is really there." },
      { icon: "lucide:message-circle", title: "Local people, quick answers", body: "Questions before or after booking? Our Sharm team answers on WhatsApp." },
    ],
  },
  faq: {
    heading: "Good to know",
    items: [
      { title: "Is hotel pickup included?", body: "Most tours include pickup and drop-off at hotels in Sharm El Sheikh; each tour page says exactly what is included." },
      { title: "When is my booking confirmed?", body: "As soon as your payment is verified. You can check your booking any time on the My booking page." },
      { title: "Can I cancel?", body: "Yes. Cancel at least 48 hours before the tour for a full refund, 24–48 hours before for 50%. Later cancellations and no-shows are non-refundable." },
    ],
  },
  cta: { title: "Not sure which trip to choose?", body: "Tell us what you like and we will suggest the right tour.", primary: "Browse all tours", secondary: "Chat on WhatsApp" },
  tours: {
    title: "All tours in Sharm El Sheikh",
    body: "Search, filter and compare — every listed price is final, in euros.",
    searchLabel: "Search tours", searchPlaceholder: "Try “snorkeling”, “quad”, “Cairo”…",
    filters: "Filters", category: "Experience", duration: "Duration", timeOfDay: "Time of day", maxPrice: "Max price", any: "Any",
    durations: { short: "Up to 3 hours", half: "Half day", full: "Full day", multi: "Several days" },
    slots: { SUNRISE: "Sunrise", MORNING: "Morning", AFTERNOON: "Afternoon", SUNSET: "Sunset" },
    sort: "Sort by", sorts: { recommended: "Recommended", priceAsc: "Price: low to high", priceDesc: "Price: high to low", duration: "Shortest first" },
    results: (n) => (n === 1 ? "1 tour" : `${n} tours`),
    show: (n) => (n === 1 ? "Show 1 tour" : `Show ${n} tours`),
    clear: "Clear filters",
    emptyTitle: "No tours match these filters",
    emptyBody: "Try removing a filter — or tell us on WhatsApp what you are looking for.",
    askWhatsapp: "Ask on WhatsApp",
  },
  card: { from: "From", onRequest: "Price on request", details: "View tour", perPerson: "per person" },
  category: { all: "All tours", back: "All experiences" },
  footer: {
    tagline: "Tours and excursions from Sharm El Sheikh, booked directly with the local team.",
    explore: "Explore", help: "Help", company: "Safari Tours Sharm",
    myBooking: "My booking", contact: "Contact", privacy: "Privacy", terms: "Terms", cancellation: "Cancellation policy",
    rights: "All rights reserved.",
  },
  errors: { load: "We could not load the tours right now.", retry: "Try again" },
};

const ar: DiscoveryCopy = {
  nav: {
    tours: "الرحلات", categories: "التجارب", contact: "تواصل معنا", myBooking: "حجزي",
    main: "القائمة الرئيسية", menu: "فتح القائمة", closeMenu: "إغلاق القائمة", language: "اللغة", book: "احجز رحلة", whatsapp: "واتساب",
    theme: { system: "المظهر: تلقائي", light: "المظهر: فاتح", dark: "المظهر: داكن" },
  },
  hero: {
    eyebrow: "شرم الشيخ · البحر الأحمر · سيناء",
    title: "اكتشف البحر الأحمر وصحراء سيناء",
    body: "سفاري في الصحراء، رحلات بحرية في البحر الأحمر، ورحلات إلى القاهرة والأقصر — أسعار واضحة، أماكن متاحة فعلًا، وحجز مؤكد في دقائق.",
    primary: "استكشف الرحلات",
    secondary: "اسألنا على واتساب",
  },
  explore: { label: "ماذا تحب أن تفعل؟", all: "كل التجارب", submit: "اعرض الرحلات" },
  categories: {
    heading: "اختر نوع مغامرتك",
    body: "من الكثبان الذهبية إلى الشعاب المرجانية — معظم الرحلات تشمل الاستلام من فندقك.",
    count: (n) => (n === 1 ? "رحلة واحدة" : n === 2 ? "رحلتان" : n <= 10 ? `${n} رحلات` : `${n} رحلة`),
  },
  featured: { heading: "رحلات مختارة", body: "بداية مثالية لاكتشاف شرم الشيخ.", all: "كل الرحلات" },
  why: {
    heading: "لماذا تحجز معنا مباشرة",
    items: [
      { icon: "lucide:badge-euro", title: "السعر الذي تراه هو ما تدفعه", body: "أسعار نهائية باليورو محسوبة قبل الدفع — بلا مفاجآت عند الاستلام." },
      { icon: "lucide:shield-check", title: "دفع آمن عبر الإنترنت", body: "الدفع بالبطاقة عن طريق Paymob، ولا نطّلع أبدًا على بيانات بطاقتك." },
      { icon: "lucide:calendar-check", title: "أماكن متاحة فعلًا", body: "المواعيد والأماكن من جدولنا مباشرة، فما تحجزه موجود حقًا." },
      { icon: "lucide:message-circle", title: "فريق محلي يرد بسرعة", body: "عندك سؤال قبل الحجز أو بعده؟ فريقنا في شرم يرد عليك على واتساب." },
    ],
  },
  faq: {
    heading: "معلومات تهمّك",
    items: [
      { title: "هل الاستلام من الفندق مشمول؟", body: "معظم الرحلات تشمل الاستلام والتوصيل من فنادق شرم الشيخ، وصفحة كل رحلة توضّح بالضبط ما هو مشمول." },
      { title: "متى يتأكد حجزي؟", body: "بمجرد التحقق من الدفع. ويمكنك متابعة حجزك في أي وقت من صفحة «حجزي»." },
      { title: "هل يمكنني الإلغاء؟", body: "نعم. الإلغاء قبل الرحلة بـ48 ساعة أو أكثر يعيد المبلغ كاملًا، وبين 24 و48 ساعة يعيد 50%. الإلغاء المتأخر أو عدم الحضور بلا استرداد." },
    ],
  },
  cta: { title: "محتار تختار أي رحلة؟", body: "قل لنا ما تحب، ونقترح عليك الرحلة المناسبة.", primary: "تصفّح كل الرحلات", secondary: "راسلنا على واتساب" },
  tours: {
    title: "كل الرحلات في شرم الشيخ",
    body: "ابحث وفلتر وقارن — كل الأسعار المعروضة نهائية وباليورو.",
    searchLabel: "ابحث في الرحلات", searchPlaceholder: "جرّب «سنوركل»، «كواد»، «القاهرة»…",
    filters: "الفلاتر", category: "نوع التجربة", duration: "المدة", timeOfDay: "وقت الرحلة", maxPrice: "أقصى سعر", any: "الكل",
    durations: { short: "حتى 3 ساعات", half: "نصف يوم", full: "يوم كامل", multi: "عدة أيام" },
    slots: { SUNRISE: "الشروق", MORNING: "الصباح", AFTERNOON: "بعد الظهر", SUNSET: "الغروب" },
    sort: "ترتيب حسب", sorts: { recommended: "المقترح", priceAsc: "السعر: من الأقل", priceDesc: "السعر: من الأعلى", duration: "الأقصر أولًا" },
    results: (n) => (n === 1 ? "رحلة واحدة" : n === 2 ? "رحلتان" : n <= 10 ? `${n} رحلات` : `${n} رحلة`),
    show: (n) => `اعرض ${n === 1 ? "رحلة واحدة" : n === 2 ? "رحلتين" : n <= 10 ? `${n} رحلات` : `${n} رحلة`}`,
    clear: "مسح الفلاتر",
    emptyTitle: "لا توجد رحلات بهذه الفلاتر",
    emptyBody: "جرّب إزالة أحد الفلاتر — أو أخبرنا على واتساب بما تبحث عنه.",
    askWhatsapp: "اسألنا على واتساب",
  },
  card: { from: "ابتداءً من", onRequest: "السعر عند الطلب", details: "تفاصيل الرحلة", perPerson: "للفرد" },
  category: { all: "كل الرحلات", back: "كل التجارب" },
  footer: {
    tagline: "رحلات وجولات من شرم الشيخ، تحجزها مباشرة مع الفريق المحلي.",
    explore: "استكشف", help: "مساعدة", company: "Safari Tours Sharm",
    myBooking: "حجزي", contact: "تواصل معنا", privacy: "الخصوصية", terms: "الشروط", cancellation: "سياسة الإلغاء",
    rights: "جميع الحقوق محفوظة.",
  },
  errors: { load: "تعذّر تحميل الرحلات الآن.", retry: "حاول مرة أخرى" },
};

const ru: DiscoveryCopy = {
  nav: {
    tours: "Туры", categories: "Впечатления", contact: "Контакты", myBooking: "Моё бронирование",
    main: "Основная навигация", menu: "Открыть меню", closeMenu: "Закрыть меню", language: "Язык", book: "Забронировать", whatsapp: "WhatsApp",
    theme: { system: "Тема: авто", light: "Тема: светлая", dark: "Тема: тёмная" },
  },
  hero: {
    eyebrow: "Шарм-эль-Шейх · Красное море · Синай",
    title: "Туры и экскурсии в Шарм-эль-Шейхе без посредников",
    body: "Сафари в пустыне, морские прогулки по Красному морю, поездки в Каир и Луксор — понятные цены, реальные места и подтверждённое бронирование за несколько минут.",
    primary: "Смотреть туры",
    secondary: "Спросить в WhatsApp",
  },
  explore: { label: "Чем хотите заняться?", all: "Все впечатления", submit: "Показать туры" },
  categories: {
    heading: "Выберите своё приключение",
    body: "От золотых дюн до коралловых рифов — трансфер из вашего отеля включён в большинство туров.",
    count: (n) => `${n} ${n % 10 === 1 && n % 100 !== 11 ? "тур" : n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 12 || n % 100 > 14) ? "тура" : "туров"}`,
  },
  featured: { heading: "Избранные туры", body: "С чего стоит начать.", all: "Все туры" },
  why: {
    heading: "Почему стоит бронировать напрямую",
    items: [
      { icon: "lucide:badge-euro", title: "Цена на сайте — окончательная", body: "Итоговая цена в евро рассчитывается до оплаты — без сюрпризов на месте." },
      { icon: "lucide:shield-check", title: "Безопасная онлайн-оплата", body: "Оплату картой обрабатывает Paymob; мы не видим данные вашей карты." },
      { icon: "lucide:calendar-check", title: "Реальные свободные места", body: "Даты и места берутся прямо из нашего расписания." },
      { icon: "lucide:message-circle", title: "Местная команда на связи", body: "Вопросы до или после бронирования? Наша команда в Шарме ответит в WhatsApp." },
    ],
  },
  faq: {
    heading: "Полезно знать",
    items: [
      { title: "Трансфер из отеля включён?", body: "Большинство туров включают трансфер из отелей Шарм-эль-Шейха; на странице тура указано, что именно входит." },
      { title: "Когда бронирование подтверждается?", body: "Сразу после проверки оплаты. Статус можно посмотреть в любое время на странице «Моё бронирование»." },
      { title: "Можно ли отменить?", body: "Да. Полный возврат при отмене не позднее чем за 48 часов, 50% — за 24–48 часов. Более поздняя отмена и неявка не возвращаются." },
    ],
  },
  cta: { title: "Не знаете, что выбрать?", body: "Расскажите, что вам нравится, — мы подскажем подходящий тур.", primary: "Все туры", secondary: "Написать в WhatsApp" },
  tours: {
    title: "Все туры в Шарм-эль-Шейхе",
    body: "Ищите, фильтруйте и сравнивайте — все цены окончательные, в евро.",
    searchLabel: "Поиск туров", searchPlaceholder: "Например, «снорклинг», «квадроцикл», «Каир»…",
    filters: "Фильтры", category: "Тип", duration: "Длительность", timeOfDay: "Время", maxPrice: "Макс. цена", any: "Любое",
    durations: { short: "До 3 часов", half: "Полдня", full: "Целый день", multi: "Несколько дней" },
    slots: { SUNRISE: "Рассвет", MORNING: "Утро", AFTERNOON: "День", SUNSET: "Закат" },
    sort: "Сортировка", sorts: { recommended: "Рекомендуемые", priceAsc: "Сначала дешевле", priceDesc: "Сначала дороже", duration: "Сначала короткие" },
    results: (n) => `${n} ${n % 10 === 1 && n % 100 !== 11 ? "тур" : n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 12 || n % 100 > 14) ? "тура" : "туров"}`,
    show: (n) => `Показать ${n} ${n % 10 === 1 && n % 100 !== 11 ? "тур" : n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 12 || n % 100 > 14) ? "тура" : "туров"}`,
    clear: "Сбросить фильтры",
    emptyTitle: "Нет туров по этим фильтрам",
    emptyBody: "Уберите один из фильтров — или напишите нам в WhatsApp, что вы ищете.",
    askWhatsapp: "Спросить в WhatsApp",
  },
  card: { from: "От", onRequest: "Цена по запросу", details: "Подробнее", perPerson: "за человека" },
  category: { all: "Все туры", back: "Все впечатления" },
  footer: {
    tagline: "Туры и экскурсии из Шарм-эль-Шейха напрямую у местной команды.",
    explore: "Туры", help: "Помощь", company: "Safari Tours Sharm",
    myBooking: "Моё бронирование", contact: "Контакты", privacy: "Конфиденциальность", terms: "Условия", cancellation: "Правила отмены",
    rights: "Все права защищены.",
  },
  errors: { load: "Не удалось загрузить туры.", retry: "Повторить" },
};

const it: DiscoveryCopy = {
  nav: {
    tours: "Escursioni", categories: "Esperienze", contact: "Contatti", myBooking: "La mia prenotazione",
    main: "Navigazione principale", menu: "Apri menu", closeMenu: "Chiudi menu", language: "Lingua", book: "Prenota", whatsapp: "WhatsApp",
    theme: { system: "Tema: automatico", light: "Tema: chiaro", dark: "Tema: scuro" },
  },
  hero: {
    eyebrow: "Sharm El Sheikh · Mar Rosso · Sinai",
    title: "Escursioni a Sharm El Sheikh — prenota direttamente",
    body: "Safari nel deserto, gite in barca sul Mar Rosso e viaggi al Cairo e a Luxor — prezzi chiari, disponibilità reale e prenotazione confermata in pochi minuti.",
    primary: "Scopri le escursioni",
    secondary: "Chiedi su WhatsApp",
  },
  explore: { label: "Cosa ti piacerebbe fare?", all: "Tutte le esperienze", submit: "Mostra escursioni" },
  categories: {
    heading: "Scegli la tua avventura",
    body: "Dalle dune dorate alle barriere coralline — la maggior parte delle escursioni parte dal tuo hotel.",
    count: (n) => (n === 1 ? "1 escursione" : `${n} escursioni`),
  },
  featured: { heading: "Escursioni in evidenza", body: "Un ottimo punto di partenza.", all: "Tutte le escursioni" },
  why: {
    heading: "Perché prenotare direttamente con noi",
    items: [
      { icon: "lucide:badge-euro", title: "Il prezzo che vedi è quello che paghi", body: "Prezzi finali in euro calcolati prima del pagamento — nessuna sorpresa." },
      { icon: "lucide:shield-check", title: "Pagamento online sicuro", body: "I pagamenti con carta sono gestiti da Paymob; non vediamo mai i dati della tua carta." },
      { icon: "lucide:calendar-check", title: "Disponibilità reale", body: "Date e posti arrivano direttamente dal nostro calendario." },
      { icon: "lucide:message-circle", title: "Un team locale che risponde", body: "Domande prima o dopo la prenotazione? Il nostro team a Sharm risponde su WhatsApp." },
    ],
  },
  faq: {
    heading: "Buono a sapersi",
    items: [
      { title: "Il transfer dall'hotel è incluso?", body: "La maggior parte delle escursioni include il transfer dagli hotel di Sharm El Sheikh; ogni pagina indica cosa è incluso." },
      { title: "Quando è confermata la prenotazione?", body: "Appena il pagamento è verificato. Puoi controllarla in qualsiasi momento nella pagina «La mia prenotazione»." },
      { title: "Posso cancellare?", body: "Sì. Rimborso totale se cancelli almeno 48 ore prima, 50% tra 24 e 48 ore. Cancellazioni successive e mancate presentazioni non sono rimborsabili." },
    ],
  },
  cta: { title: "Non sai quale escursione scegliere?", body: "Dicci cosa ti piace e ti suggeriamo l'escursione giusta.", primary: "Tutte le escursioni", secondary: "Scrivici su WhatsApp" },
  tours: {
    title: "Tutte le escursioni a Sharm El Sheikh",
    body: "Cerca, filtra e confronta — tutti i prezzi indicati sono finali, in euro.",
    searchLabel: "Cerca escursioni", searchPlaceholder: "Prova «snorkeling», «quad», «Cairo»…",
    filters: "Filtri", category: "Esperienza", duration: "Durata", timeOfDay: "Momento", maxPrice: "Prezzo max", any: "Qualsiasi",
    durations: { short: "Fino a 3 ore", half: "Mezza giornata", full: "Giornata intera", multi: "Più giorni" },
    slots: { SUNRISE: "Alba", MORNING: "Mattina", AFTERNOON: "Pomeriggio", SUNSET: "Tramonto" },
    sort: "Ordina per", sorts: { recommended: "Consigliate", priceAsc: "Prezzo crescente", priceDesc: "Prezzo decrescente", duration: "Più brevi" },
    results: (n) => (n === 1 ? "1 escursione" : `${n} escursioni`),
    show: (n) => (n === 1 ? "Mostra 1 escursione" : `Mostra ${n} escursioni`),
    clear: "Azzera filtri",
    emptyTitle: "Nessuna escursione con questi filtri",
    emptyBody: "Prova a rimuovere un filtro — oppure scrivici su WhatsApp cosa cerchi.",
    askWhatsapp: "Chiedi su WhatsApp",
  },
  card: { from: "Da", onRequest: "Prezzo su richiesta", details: "Vedi escursione", perPerson: "a persona" },
  category: { all: "Tutte le escursioni", back: "Tutte le esperienze" },
  footer: {
    tagline: "Escursioni da Sharm El Sheikh, prenotate direttamente con il team locale.",
    explore: "Esplora", help: "Aiuto", company: "Safari Tours Sharm",
    myBooking: "La mia prenotazione", contact: "Contatti", privacy: "Privacy", terms: "Termini", cancellation: "Politica di cancellazione",
    rights: "Tutti i diritti riservati.",
  },
  errors: { load: "Impossibile caricare le escursioni.", retry: "Riprova" },
};

export const discoveryCopy: Record<StsLocale, DiscoveryCopy> = { en, ar, ru, it };
