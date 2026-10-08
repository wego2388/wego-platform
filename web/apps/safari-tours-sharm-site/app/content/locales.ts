export type StsLocale = "en" | "ru" | "ar" | "it";

export const whatsappUrl = "https://wa.me/201111292690";
export const whatsappPhone = "+201111292690";
export const siteEmail = "safaritourssharm@gmail.com";
export const siteWebsite = "https://safaritourssharm.com";

// Owner-confirmed channels (owner data hub, 2026-10-01).
export const instagramUrl = "https://www.instagram.com/safari_tours_sharm";
export const facebookUrl = "https://www.facebook.com/100063820752767/";
export const googleMapsUrl = "https://www.google.com/maps/place/safari+tours+sharm/data=!4m2!3m1!1s0x0:0x4157d683723a2ede";
export const googleReviewUrl = "https://g.page/r/Cd4uOnKD1ldBEBM/review";
export const tripadvisorUrl =
  "https://www.tripadvisor.com/Attraction_Review-g297555-d34123701-Reviews-Safari_Tours_Sharm-Sharm_El_Sheikh_South_Sinai_Red_Sea_and_Sinai.html";

export const rtlLocales: StsLocale[] = ["ar"];

export function directionFor(locale: StsLocale): "ltr" | "rtl" {
  return rtlLocales.includes(locale) ? "rtl" : "ltr";
}

// ── Locale → display name shown in language switcher ──────────────────
export const localeNames: Record<StsLocale, string> = {
  en: "EN",
  ru: "RU",
  ar: "عر",
  it: "IT",
};

// ── Tour categories ───────────────────────────────────────────────────
export type TourCategory = "DESERT" | "SEA" | "CULTURAL" | "SHOWS" | "TRANSFERS";

export const categoryMeta: Record<
  TourCategory,
  { slug: string; icon: string; colorClass: string }
> = {
  DESERT:    { slug: "desert",    icon: "🏜️", colorClass: "bg-amber-100  text-amber-900"  },
  SEA:       { slug: "sea",       icon: "🤿", colorClass: "bg-cyan-100   text-cyan-900"   },
  CULTURAL:  { slug: "cultural",  icon: "🏛️", colorClass: "bg-stone-100  text-stone-900"  },
  SHOWS:     { slug: "shows",     icon: "🎭", colorClass: "bg-purple-100 text-purple-900" },
  TRANSFERS: { slug: "transfers", icon: "🚗", colorClass: "bg-green-100  text-green-900"  },
};

// ── Site copy ─────────────────────────────────────────────────────────
interface NavCopy {
  home: string; tours: string; about: string; contact: string; menu: string;
}

interface HeroCopy {
  eyebrow: string; title: string; body: string; cta: string; whatsapp: string;
}

interface TrustItem { label: string }

interface CategoryCopy { name: string; description: string }

interface StepCopy { title: string; body: string }

interface SiteCopy {
  languageName: string;
  dir: "ltr" | "rtl";
  whatsappFab: string;
  nav: NavCopy;
  hero: HeroCopy;
  trustItems: TrustItem[];
  categoriesHeading: string;
  categoriesBody: string;
  categories: Record<TourCategory, CategoryCopy>;
  howHeading: string;
  howSteps: StepCopy[];
  cancellationHeading: string;
  cancellationBody: string;
  footerTagline: string;
  footerLinks: { tours: string; contact: string; privacy: string; terms: string };
  footerRights: string;
  notFound: { title: string; body: string; cta: string };
}

export const siteCopy: Record<StsLocale, SiteCopy> = {
  en: {
    languageName: "English",
    dir: "ltr",
    whatsappFab: "WhatsApp Us",
    nav: {
      home: "Home",
      tours: "Tours",
      about: "About",
      contact: "Contact",
      menu: "Open menu",
    },
    hero: {
      eyebrow: "Sharm El Sheikh · Egypt",
      title: "Sharm El Sheikh Tours & Excursions — Book Direct",
      body: "Browse desert safaris, Red Sea activities and cultural excursions with clear catalog prices and online booking.",
      cta: "Book Your Adventure",
      whatsapp: "Ask on WhatsApp",
    },
    trustItems: [
      { label: "💶 Catalog prices shown" },
      { label: "📅 Live slot availability" },
      { label: "🔎 Booking status lookup" },
      { label: "💬 Local WhatsApp support" },
    ],
    categoriesHeading: "What would you like to do?",
    categoriesBody: "From desert adventures to Red Sea boat trips — we have something for everyone.",
    categories: {
      DESERT:    { name: "Desert & Safari",       description: "Quad bikes, camel rides, Bedouin dinner & stargazing in the Sinai desert." },
      SEA:       { name: "Sea & Water Activities", description: "Snorkeling, boat trips, diving and island hopping on the Red Sea." },
      CULTURAL:  { name: "Cultural & Historical",  description: "Cairo, Luxor, Mount Sinai, Saint Catherine and more." },
      SHOWS:     { name: "Shows & Relax",           description: "Dolphin show, Alf Leila, relaxation trips and entertainment." },
      TRANSFERS: { name: "Transfers & Rooms",       description: "Airport transfers, hotel bookings and car rental." },
    },
    howHeading: "How it works",
    howSteps: [
      { title: "1. Choose Your Tour",       body: "Browse our catalog and pick the experience that excites you." },
      { title: "2. Pick Date & Group Size", body: "Select your preferred date, time slot and number of travelers." },
      { title: "3. Continue to Payment",    body: "Use the payment option presented by the booking flow. Your booking is confirmed only after payment is verified." },
      { title: "4. Get Confirmation",       body: "Keep your booking reference and contact local support if your plans change." },
    ],
    cancellationHeading: "Cancellation & refunds",
    cancellationBody: "Cancel at least 48 hours before departure for a full refund; 24–48 hours for a 50% refund; less than 24 hours or no-show is non-refundable.",
    footerTagline: "Tours and excursions in Sharm El Sheikh.",
    footerLinks: { tours: "All Tours", contact: "Contact", privacy: "Privacy", terms: "Terms" },
    footerRights: "© 2026 Safari Tours Sharm. All rights reserved.",
    notFound: {
      title: "Page not found",
      body: "The page you are looking for does not exist.",
      cta: "Back to Home",
    },
  },

  ru: {
    languageName: "Русский",
    dir: "ltr",
    whatsappFab: "WhatsApp",
    nav: {
      home: "Главная",
      tours: "Туры",
      about: "О нас",
      contact: "Контакты",
      menu: "Открыть меню",
    },
    hero: {
      eyebrow: "Шарм-эль-Шейх · Египет",
      title: "Откройте для себя Красное море и Синайскую пустыню",
      body: "Сафари в пустыне, развлечения на Красном море и культурные экскурсии с понятными ценами и онлайн-бронированием.",
      cta: "Забронировать тур",
      whatsapp: "Написать в WhatsApp",
    },
    trustItems: [
      { label: "💶 Цены каталога указаны" },
      { label: "📅 Актуальные свободные места" },
      { label: "🔎 Проверка статуса брони" },
      { label: "💬 Местная поддержка в WhatsApp" },
    ],
    categoriesHeading: "Что вы хотите сделать?",
    categoriesBody: "От пустынных приключений до морских прогулок — у нас есть тур для каждого.",
    categories: {
      DESERT:    { name: "Сафари и пустыня",       description: "Квадроциклы, верблюды, ужин у бедуинов и звёздное небо Синая." },
      SEA:       { name: "Море и вода",             description: "Снорклинг, морские прогулки, дайвинг и острова Красного моря." },
      CULTURAL:  { name: "Культура и история",      description: "Каир, Луксор, гора Синай, монастырь Святой Екатерины." },
      SHOWS:     { name: "Шоу и отдых",             description: "Шоу дельфинов, Альф Лейла, развлечения и релакс-туры." },
      TRANSFERS: { name: "Трансферы и номера",      description: "Трансферы в аэропорт, бронирование отелей и аренда авто." },
    },
    howHeading: "Как это работает",
    howSteps: [
      { title: "1. Выберите тур",              body: "Просмотрите наш каталог и выберите то, что вас привлекает." },
      { title: "2. Дата и количество гостей",  body: "Выберите удобную дату, время и количество человек." },
      { title: "3. Перейдите к оплате",         body: "Используйте способ оплаты, предложенный при бронировании. Бронь подтверждается только после проверки платежа." },
      { title: "4. Получите подтверждение",     body: "Сохраните номер бронирования и свяжитесь с местной поддержкой, если планы изменятся." },
    ],
    cancellationHeading: "Отмена и возврат",
    cancellationBody: "При отмене не менее чем за 48 часов — полный возврат; за 24–48 часов — 50%; менее чем за 24 часа или при неявке возврат не производится.",
    footerTagline: "Туры и экскурсии в Шарм-эль-Шейхе.",
    footerLinks: { tours: "Все туры", contact: "Контакты", privacy: "Политика", terms: "Условия" },
    footerRights: "© 2026 Safari Tours Sharm. Все права защищены.",
    notFound: {
      title: "Страница не найдена",
      body: "Запрашиваемая страница не существует.",
      cta: "На главную",
    },
  },

  ar: {
    languageName: "العربية",
    dir: "rtl",
    whatsappFab: "واتساب",
    nav: {
      home: "الرئيسية",
      tours: "الجولات",
      about: "من نحن",
      contact: "اتصل بنا",
      menu: "فتح القائمة",
    },
    hero: {
      eyebrow: "شرم الشيخ · مصر",
      title: "اكتشف البحر الأحمر وصحراء سيناء",
      body: "تصفح رحلات السفاري وأنشطة البحر الأحمر والجولات الثقافية بأسعار كتالوج واضحة وحجز عبر الإنترنت.",
      cta: "احجز مغامرتك",
      whatsapp: "تواصل عبر واتساب",
    },
    trustItems: [
      { label: "💶 أسعار الكتالوج ظاهرة" },
      { label: "📅 المواعيد المتاحة مباشرة" },
      { label: "🔎 متابعة حالة الحجز" },
      { label: "💬 دعم محلي عبر واتساب" },
    ],
    categoriesHeading: "ماذا تريد أن تفعل؟",
    categoriesBody: "من مغامرات الصحراء إلى رحلات البحر الأحمر — لدينا جولة لكل شخص.",
    categories: {
      DESERT:    { name: "سفاري الصحراء",          description: "دراجات رباعية، ركوب الجمال، عشاء بدوي ومشاهدة النجوم في سيناء." },
      SEA:       { name: "البحر والأنشطة المائية", description: "الغطس، رحلات القوارب، الغوص والجزر في البحر الأحمر." },
      CULTURAL:  { name: "الجولات الثقافية",       description: "القاهرة، الأقصر، جبل سيناء ودير سانت كاترين." },
      SHOWS:     { name: "العروض والاسترخاء",      description: "عروض الدلافين، ألف ليلة، رحلات الاسترخاء والترفيه." },
      TRANSFERS: { name: "النقل والغرف",           description: "نقل المطار، حجز الفنادق وتأجير السيارات." },
    },
    howHeading: "كيف يعمل الحجز؟",
    howSteps: [
      { title: "١. اختر جولتك",              body: "تصفح كتالوجنا واختر التجربة التي تثير اهتمامك." },
      { title: "٢. حدد التاريخ وعدد الأفراد", body: "اختر التاريخ والوقت المفضل وعدد المسافرين." },
      { title: "٣. انتقل إلى الدفع",         body: "استخدم وسيلة الدفع التي تظهر أثناء الحجز. لا يتأكد الحجز إلا بعد التحقق من الدفع." },
      { title: "٤. استلم التأكيد",           body: "احتفظ برقم الحجز وتواصل مع الدعم المحلي إذا تغيرت خططك." },
    ],
    cancellationHeading: "سياسة الإلغاء والاسترداد",
    cancellationBody: "الإلغاء قبل 48 ساعة أو أكثر يستحق استردادًا كاملًا؛ ومن 24 إلى 48 ساعة استرداد 50٪؛ وأقل من 24 ساعة أو عدم الحضور غير قابل للاسترداد.",
    footerTagline: "جولات ورحلات في شرم الشيخ.",
    footerLinks: { tours: "جميع الجولات", contact: "اتصل بنا", privacy: "الخصوصية", terms: "الشروط" },
    footerRights: "© 2026 سافاري تورز شرم. جميع الحقوق محفوظة.",
    notFound: {
      title: "الصفحة غير موجودة",
      body: "الصفحة التي تبحث عنها غير موجودة.",
      cta: "العودة للرئيسية",
    },
  },

  it: {
    languageName: "Italiano",
    dir: "ltr",
    whatsappFab: "Scrivici su WhatsApp",
    nav: {
      home: "Home",
      tours: "Tour",
      about: "Chi siamo",
      contact: "Contatti",
      menu: "Apri menu",
    },
    hero: {
      eyebrow: "Sharm el-Sheikh · Egitto",
      title: "Esplora il Mar Rosso e il Deserto del Sinai",
      body: "Scopri safari nel deserto, attività sul Mar Rosso ed escursioni culturali con prezzi di catalogo chiari e prenotazione online.",
      cta: "Prenota la tua avventura",
      whatsapp: "Scrivici su WhatsApp",
    },
    trustItems: [
      { label: "💶 Prezzi di catalogo visibili" },
      { label: "📅 Disponibilità aggiornata" },
      { label: "🔎 Verifica dello stato prenotazione" },
      { label: "💬 Assistenza locale su WhatsApp" },
    ],
    categoriesHeading: "Cosa vuoi fare?",
    categoriesBody: "Dalle avventure nel deserto alle gite in barca — abbiamo un tour per tutti.",
    categories: {
      DESERT:    { name: "Safari e Deserto",       description: "Quad, dromedari, cena beduina e cielo stellato nel deserto del Sinai." },
      SEA:       { name: "Mare e Attività Acquatiche", description: "Snorkeling, gite in barca, immersioni e isole nel Mar Rosso." },
      CULTURAL:  { name: "Tour Culturali e Storici", description: "Cairo, Luxor, Monte Sinai e il monastero di Santa Caterina." },
      SHOWS:     { name: "Spettacoli e Relax",      description: "Spettacolo dei delfini, Alf Leila, gite relax e intrattenimento." },
      TRANSFERS: { name: "Trasferimenti e Camere",  description: "Trasferimenti aeroporto, prenotazioni hotel e noleggio auto." },
    },
    howHeading: "Come funziona",
    howSteps: [
      { title: "1. Scegli il tuo tour",        body: "Sfoglia il catalogo e scegli l'esperienza che ti entusiasma." },
      { title: "2. Scegli data e gruppo",       body: "Seleziona la data, l'orario preferito e il numero di partecipanti." },
      { title: "3. Procedi al pagamento",       body: "Usa l'opzione di pagamento presentata durante la prenotazione. La prenotazione è confermata solo dopo la verifica del pagamento." },
      { title: "4. Ricevi la conferma",         body: "Conserva il riferimento della prenotazione e contatta l'assistenza locale se i piani cambiano." },
    ],
    cancellationHeading: "Cancellazione e rimborsi",
    cancellationBody: "Cancellazione almeno 48 ore prima: rimborso completo; tra 24 e 48 ore: rimborso del 50%; meno di 24 ore o mancata presentazione: nessun rimborso.",
    footerTagline: "Tour ed escursioni a Sharm el-Sheikh.",
    footerLinks: { tours: "Tutti i tour", contact: "Contatti", privacy: "Privacy", terms: "Termini" },
    footerRights: "© 2026 Safari Tours Sharm. Tutti i diritti riservati.",
    notFound: {
      title: "Pagina non trovata",
      body: "La pagina che stai cercando non esiste.",
      cta: "Torna alla home",
    },
  },
};
