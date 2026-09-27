export type StsLocale = "en" | "ru" | "ar" | "it";

export const whatsappUrl = "https://wa.me/201111292690";
export const whatsappPhone = "+201111292690";
export const siteEmail = "safaritourssharm@gmail.com";
export const siteWebsite = "https://safaritourssharm.com";

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
      title: "Explore the Red Sea & Sinai Desert",
      body: "Professional tours from your hotel door — desert safaris, boat trips, diving, cultural excursions and more.",
      cta: "Book Your Adventure",
      whatsapp: "Ask on WhatsApp",
    },
    trustItems: [
      { label: "⭐ 4.9 Google Rating" },
      { label: "🌍 500+ Happy Travelers" },
      { label: "🏨 Hotel Pickup Included" },
      { label: "📱 WhatsApp 24/7" },
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
      { title: "3. Pay Securely Online",    body: "Pay with card, Vodafone Cash or Fawry — 100% secure checkout." },
      { title: "4. We Pick You Up",         body: "Our team comes to your hotel door at the agreed time. Relax and enjoy." },
    ],
    cancellationHeading: "Free cancellation",
    cancellationBody: "Cancel up to 24 hours before your trip for a full refund. No questions asked.",
    footerTagline: "Professional tours in Sharm El Sheikh since 2010.",
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
      body: "Профессиональные туры прямо от вашего отеля — сафари, морские прогулки, дайвинг и культурные экскурсии.",
      cta: "Забронировать тур",
      whatsapp: "Написать в WhatsApp",
    },
    trustItems: [
      { label: "⭐ Рейтинг Google 4.9" },
      { label: "🌍 500+ довольных путешественников" },
      { label: "🏨 Трансфер от отеля включён" },
      { label: "📱 WhatsApp 24/7" },
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
      { title: "3. Безопасная оплата онлайн",  body: "Оплата картой или через Vodafone Cash — 100% безопасно." },
      { title: "4. Мы заберём вас",            body: "Наша команда приедет к вашему отелю в назначенное время." },
    ],
    cancellationHeading: "Бесплатная отмена",
    cancellationBody: "Отмените бронирование за 24 часа до тура и получите полный возврат средств.",
    footerTagline: "Профессиональные туры в Шарм-эль-Шейхе с 2010 года.",
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
      body: "جولات احترافية من باب فندقك — سفاري صحراوي، رحلات بحرية، غوص، جولات ثقافية والمزيد.",
      cta: "احجز مغامرتك",
      whatsapp: "تواصل عبر واتساب",
    },
    trustItems: [
      { label: "⭐ تقييم Google 4.9" },
      { label: "🌍 أكثر من 500 رحلة ناجحة" },
      { label: "🏨 التوصيل من الفندق مجاناً" },
      { label: "📱 واتساب 24/7" },
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
      { title: "٣. ادفع بأمان",              body: "الدفع ببطاقة الائتمان أو فودافون كاش أو فوري — آمن 100%." },
      { title: "٤. نحن نأتي إليك",           body: "فريقنا سيصل إلى فندقك في الوقت المتفق عليه." },
    ],
    cancellationHeading: "إلغاء مجاني",
    cancellationBody: "يمكنك الإلغاء حتى 24 ساعة قبل الرحلة واسترداد المبلغ كاملاً.",
    footerTagline: "جولات احترافية في شرم الشيخ منذ 2010.",
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
      body: "Tour professionali direttamente dall'ingresso del tuo hotel — safari, gite in barca, immersioni, escursioni culturali e altro ancora.",
      cta: "Prenota la tua avventura",
      whatsapp: "Scrivici su WhatsApp",
    },
    trustItems: [
      { label: "⭐ Valutazione Google 4.9" },
      { label: "🌍 500+ viaggiatori soddisfatti" },
      { label: "🏨 Pickup dall'hotel incluso" },
      { label: "📱 WhatsApp 24/7" },
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
      { title: "3. Paga in modo sicuro",        body: "Pagamento con carta o Vodafone Cash — checkout sicuro al 100%." },
      { title: "4. Ti veniamo a prendere",      body: "Il nostro team arriverà al tuo hotel all'orario concordato." },
    ],
    cancellationHeading: "Cancellazione gratuita",
    cancellationBody: "Cancella fino a 24 ore prima del tour per un rimborso completo.",
    footerTagline: "Tour professionali a Sharm el-Sheikh dal 2010.",
    footerLinks: { tours: "Tutti i tour", contact: "Contatti", privacy: "Privacy", terms: "Termini" },
    footerRights: "© 2026 Safari Tours Sharm. Tutti i diritti riservati.",
    notFound: {
      title: "Pagina non trovata",
      body: "La pagina che stai cercando non esiste.",
      cta: "Torna alla home",
    },
  },
};
