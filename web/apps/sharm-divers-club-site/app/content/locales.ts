export type SdcLocale = "ar" | "en";

export const whatsappUrl = "https://wa.me/201066461010";
export const tripadvisorUrl = "https://www.tripadvisor.com/Attraction_Review-g297555-d27124030-Reviews-Sharm_Divers_Club-Sharm_El_Sheikh_South_Sinai_Red_Sea_and_Sinai.html";

interface CategoryCopy {
  id: string;
  eyebrow: string;
  title: string;
  description: string;
}

interface PersonaCopy {
  name: string;
  body: string;
}

interface WhyCopy {
  title: string;
  body: string;
}

interface SiteCopy {
  languageName: string;
  preview: string;
  whatsappFab: string;
  nav: { discover: string; about: string; contact: string; home: string };
  hero: { eyebrow: string; title: string; body: string; browse: string; whatsapp: string };
  guarantees: string[];
  trustStrip: string[];
  stats: { categories: string; languages: string; padi: string };
  categoriesHeading: string;
  categoriesBody: string;
  categories: CategoryCopy[];
  personasHeading: string;
  personasBody: string;
  personas: PersonaCopy[];
  whyHeading: string;
  whyBody: string;
  why: WhyCopy[];
  how: { heading: string; body: string; steps: Array<{ title: string; body: string }> };
  bookingNotice: string;
  discover: {
    heading: string;
    body: string;
    back: string;
    whatsapp: string;
    viewSystem: string;
    offeringPreview: string;
    pricingNotice: string;
    filterAll: string;
  };
  about: {
    heading: string;
    body: string;
    factsHeading: string;
    facts: Array<{ label: string; value: string }>;
    languagesHeading: string;
    languagesBody: string;
  };
  contact: {
    heading: string;
    body: string;
    whatsappLabel: string;
    whatsappBody: string;
    phoneLabel: string;
    emailLabel: string;
    locationLabel: string;
    hoursLabel: string;
  };
  footer: {
    tagline: string;
    exploreHeading: string;
    companyHeading: string;
    contactHeading: string;
    exploreLinks: { discover: string; designSystem: string };
    companyLinks: { about: string; contact: string };
    tripadvisor: string;
    rights: string;
  };
}

export const siteCopy: Record<SdcLocale, SiteCopy> = {
  en: {
    languageName: "العربية",
    preview: "Product foundation preview",
    whatsappFab: "WhatsApp",
    nav: { discover: "Discover", about: "About", contact: "Contact", home: "Sharm Divers Club home" },
    hero: {
      eyebrow: "Sharm El Sheikh · PADI 5 Star Dive Center",
      title: "Red Sea confidence, personally guided.",
      body: "From a first breath underwater to Ras Mohammed, Tiran and the Thistlegorm — Sharm Divers Club plans it with you directly, one clear WhatsApp conversation at a time.",
      browse: "Discover the categories",
      whatsapp: "Message us on WhatsApp",
    },
    guarantees: [
      "PADI 5 Star Dive Center",
      "CDWS accredited · #100601",
      "Send an inquiry on WhatsApp",
      "Team speaks 5 languages",
    ],
    trustStrip: [
      "PADI 5 Star Dive Center",
      "CDWS accredited · #100601",
      "Open daily 08:00–20:00",
      "Team speaks Arabic, English, Russian, German, Italian",
    ],
    stats: { categories: "Dive categories", languages: "Team languages", padi: "PADI star rating" },
    categoriesHeading: "Start with the kind of dive you want",
    categoriesBody: "These are the real categories Sharm Divers Club runs, with real 2026 prices. Every trip is confirmed by our team on WhatsApp before it's booked.",
    categories: [
      { id: "shore-diving", eyebrow: "From the beach", title: "Shore diving", description: "Intro dives for first-timers and guided shore dives for certified divers, right from Sharm." },
      { id: "boat-diving", eyebrow: "By boat", title: "Boat diving", description: "Day trips to Ras Mohammed and Tiran, for beginners and certified divers alike." },
      { id: "multi-day", eyebrow: "Several days", title: "Multi-day packages", description: "3 to 7 day dive packages combining shore and boat days for divers with more time." },
      { id: "signature", eyebrow: "The full route", title: "Signature packages", description: "\"Red Sea Icons\" — a proposed multi-day route across the region's best-known sites." },
      { id: "world-class", eyebrow: "The highlights", title: "World-class sites", description: "SS Thistlegorm, Dahab's Blue Hole & Canyon, Tiran drift diving, Ras Mohammed's Shark & Yolanda." },
      { id: "padi-courses", eyebrow: "Get certified", title: "PADI courses", description: "Open Water through Divemaster, plus Nitrox and Emergency First Response." },
    ],
    personasHeading: "Built around who you are, not a generic offer list",
    personasBody: "Sharm Divers Club's own brand direction defines who this experience is designed for.",
    personas: [
      { name: "First Breath", body: "Never dived before, and a little nervous about it. Gets a simple explanation, not a wall of jargon." },
      { name: "Certified Explorer", body: "Has a certification and limited time. Wants clear logistics and honest availability." },
      { name: "Red Sea Collector", body: "A repeat diver chasing Tiran, Ras Mohammed, Thistlegorm and Dahab, plus a real dive record." },
      { name: "Partner Planner", body: "A hotel, agent or group organizer who needs speed, clear terms and one responsible contact." },
    ],
    whyHeading: "Clear before. Personal during. Memorable after.",
    whyBody: "Sharm Divers Club's own promise to every diver, not a marketing slogan invented for this site.",
    why: [
      { title: "Before the dive", body: "No ambiguity about the plan or what's expected of you." },
      { title: "During the experience", body: "You always know who's responsible for you and what happens next." },
      { title: "After you surface", body: "We follow up after your dive — and give you a real reason to come back." },
    ],
    how: {
      heading: "A conversation first, a confirmation second",
      body: "There is no self-service checkout on this site yet — every booking is created by our team, so every request starts as a direct WhatsApp conversation.",
      steps: [
        { title: "1. Message us", body: "Tell us what you're after — a first dive, a certification, or a multi-day plan." },
        { title: "2. We confirm details", body: "Our team checks the date, group size and price with you before anything is booked." },
        { title: "3. You're booked", body: "Once confirmed, your dive or course is on our schedule — no separate app or account needed." },
      ],
    },
    bookingNotice: "Important: booking today goes through our team on WhatsApp, not an automatic checkout. This keeps every date, price and safety detail confirmed by a real person before you're booked.",
    discover: {
      heading: "Real 2026 prices, one inquiry away",
      body: "Categories, sites, courses and prices shown here are real, approved 2026 rates. Dates, availability and final details are always confirmed by our team on WhatsApp before anything is booked.",
      back: "Back to the overview",
      whatsapp: "Send an inquiry on WhatsApp",
      viewSystem: "View the living design system",
      offeringPreview: "See a full offering detail page",
      pricingNotice: "2026 price · confirm dates on WhatsApp",
      filterAll: "All categories",
    },
    about: {
      heading: "A PADI 5 Star center, run directly",
      body: "Sharm Divers Club is a PADI 5 Star Dive Center in Sharm El Sheikh, accredited with CDWS. Every fact on this page is approved for publication — nothing here is invented.",
      factsHeading: "What we can confirm today",
      facts: [
        { label: "Positioning", value: "PADI 5 Star Dive Center in Sharm El Sheikh" },
        { label: "Accreditation", value: "CDWS #100601" },
        { label: "Location", value: "Royal Grand Sharm Hotel, Hadabet Um Sid, Sharm El Sheikh, Egypt" },
        { label: "Hours", value: "Daily 08:00–20:00" },
      ],
      languagesHeading: "Team languages",
      languagesBody: "Our PADI profile lists Arabic, English, Russian, German and Italian as team languages. This website itself is only available in Arabic and English for now — a full translation of the site is not published yet.",
    },
    contact: {
      heading: "Talk to us directly",
      body: "Every channel below is the real, approved contact for Sharm Divers Club.",
      whatsappLabel: "WhatsApp",
      whatsappBody: "The fastest way to reach us and the only way to actually book today.",
      phoneLabel: "Phone",
      emailLabel: "Email",
      locationLabel: "Location",
      hoursLabel: "Hours",
    },
    footer: {
      tagline: "Red Sea confidence, personally guided.",
      exploreHeading: "Explore",
      companyHeading: "Company",
      contactHeading: "Contact",
      exploreLinks: { discover: "Discover", designSystem: "Design system" },
      companyLinks: { about: "About", contact: "Contact" },
      tripadvisor: "Find us on TripAdvisor",
      rights: "Sharm Divers Club · Royal Grand Sharm Hotel, Hadabet Um Sid, Sharm El Sheikh",
    },
  },
  ar: {
    languageName: "English",
    preview: "معاينة لأساس المنتج",
    whatsappFab: "واتساب",
    nav: { discover: "استكشف", about: "من نحن", contact: "تواصل", home: "الصفحة الرئيسية لـSharm Divers Club" },
    hero: {
      eyebrow: "شرم الشيخ · مركز PADI 5 نجوم",
      title: "ثقة البحر الأحمر، بترتيب شخصي معاك.",
      body: "من أول نفس تحت الماء لحد رأس محمد وتيران وThistlegorm — Sharm Divers Club بيرتب معاك بشكل مباشر، محادثة واحدة واضحة على واتساب.",
      browse: "استكشف الفئات",
      whatsapp: "راسلنا على واتساب",
    },
    guarantees: [
      "مركز PADI 5 نجوم",
      "معتمد من CDWS · رقم 100601",
      "ابعت طلبك على واتساب",
      "الفريق بيتكلم 5 لغات",
    ],
    trustStrip: [
      "مركز PADI 5 نجوم",
      "معتمد من CDWS · رقم 100601",
      "مفتوح يوميًا 08:00–20:00",
      "الفريق بيتكلم عربي وإنجليزي وروسي وألماني وإيطالي",
    ],
    stats: { categories: "فئات الغطس", languages: "لغات الفريق", padi: "تصنيف PADI بالنجوم" },
    categoriesHeading: "ابدأ بنوع الغطسة اللي تناسبك",
    categoriesBody: "دي الفئات الحقيقية لخدمات Sharm Divers Club، بأسعار 2026 حقيقية. كل رحلة بيتأكدها فريقنا على واتساب قبل ما تتحجز.",
    categories: [
      { id: "shore-diving", eyebrow: "من الشاطئ", title: "غطس الشاطئ", description: "غطسات تجريبية للمبتدئين وغطسات شاطئ موجهة للغواصين المعتمدين، من شرم مباشرة." },
      { id: "boat-diving", eyebrow: "بالقارب", title: "غطس القارب", description: "رحلات يوم كامل لرأس محمد وتيران، للمبتدئين والغواصين المعتمدين." },
      { id: "multi-day", eyebrow: "عدة أيام", title: "باقات متعددة الأيام", description: "باقات من 3 إلى 7 أيام تجمع بين أيام الشاطئ والقارب لمن عنده وقت أطول." },
      { id: "signature", eyebrow: "المسار الكامل", title: "الباقات المميزة", description: "\"أيقونات البحر الأحمر\" — مسار مقترح متعدد الأيام يغطي أشهر مواقع المنطقة." },
      { id: "world-class", eyebrow: "أهم المواقع", title: "مواقع عالمية", description: "حطام SS Thistlegorm، وBlue Hole وCanyon في دهب، وDrift Diving في تيران، وShark & Yolanda في رأس محمد." },
      { id: "padi-courses", eyebrow: "احصل على شهادة", title: "دورات PADI", description: "من Open Water لحد Divemaster، بالإضافة لـNitrox وEmergency First Response." },
    ],
    personasHeading: "مبني حول شخصيتك، مش قائمة عروض عامة",
    personasBody: "توجه العلامة التجارية لـSharm Divers Club بيحدد لمين مصممة التجربة دي.",
    personas: [
      { name: "First Breath", body: "ماغطسش قبل كده وحاسس بتوتر شوية. بياخد شرح بسيط، مش مصطلحات معقدة." },
      { name: "Certified Explorer", body: "معاه شهادة ووقته محدود. عايز لوجستيات واضحة وتوفر حقيقي." },
      { name: "Red Sea Collector", body: "غواص متكرر بيدور على تيران ورأس محمد وThistlegorm ودهب، وسجل غطسات حقيقي." },
      { name: "Partner Planner", body: "فندق أو وكيل أو منظم مجموعات محتاج سرعة وشروط واضحة ومسؤول واحد." },
    ],
    whyHeading: "قبل الرحلة وضوح. أثناءها اهتمام شخصي. وبعدها ذكرى.",
    whyBody: "وعد Sharm Divers Club لكل غواص، مش شعار تسويقي مختلق لأجل الموقع ده.",
    why: [
      { title: "قبل الرحلة", body: "مفيش غموض في البرنامج ولا في المطلوب منك." },
      { title: "أثناء التجربة", body: "دايمًا عارف مين المسؤول عنك والخطوة الجاية إيه." },
      { title: "بعد ما تطلع", body: "بنتابع معاك بعد الغطسة — وبنديك سبب حقيقي للرجوع." },
    ],
    how: {
      heading: "الأول محادثة، وبعدها تأكيد",
      body: "مفيش حجز ذاتي فوري على الموقع لسه — كل حجز بيتعمل من فريقنا، فكل طلب بيبدأ بمحادثة مباشرة على واتساب.",
      steps: [
        { title: "١. راسلنا", body: "قولنا محتاج إيه — غطسة أولى، شهادة، أو خطة عدة أيام." },
        { title: "٢. نأكد التفاصيل", body: "فريقنا بيتأكد معاك من الموعد وعدد الأفراد والسعر قبل أي حجز." },
        { title: "٣. اتحجزت", body: "بعد التأكيد، غطستك أو دورتك تبقى في جدولنا — من غير أي تطبيق أو حساب إضافي." },
      ],
    },
    bookingNotice: "مهم: الحجز حاليًا بيتم من خلال فريقنا على واتساب، مش عن طريق نظام دفع تلقائي. ده بيضمن إن كل موعد وسعر وتفصيلة أمان يتأكد من شخص حقيقي قبل ما تتحجز.",
    discover: {
      heading: "أسعار 2026 حقيقية، على بعد رسالة واحدة",
      body: "الفئات والمواقع والدورات والأسعار الظاهرة هنا حقيقية ومعتمدة لسنة 2026. المواعيد والتوفر والتفاصيل النهائية دايمًا بيأكدها فريقنا على واتساب قبل أي حجز.",
      back: "العودة للنظرة العامة",
      whatsapp: "ابعت طلبك على واتساب",
      viewSystem: "شاهد نظام التصميم الحي",
      offeringPreview: "شاهد صفحة تفاصيل خدمة كاملة",
      pricingNotice: "سعر 2026 · أكّد الموعد على واتساب",
      filterAll: "كل الفئات",
    },
    about: {
      heading: "مركز PADI 5 نجوم، بإدارة مباشرة",
      body: "Sharm Divers Club مركز PADI 5 نجوم في شرم الشيخ، معتمد من CDWS. كل معلومة في الصفحة دي معتمدة للنشر — مفيش حاجة مختلقة.",
      factsHeading: "اللي نقدر نأكده النهاردة",
      facts: [
        { label: "التوجه", value: "مركز PADI 5 نجوم في شرم الشيخ" },
        { label: "الاعتماد", value: "CDWS رقم 100601" },
        { label: "الموقع", value: "فندق Royal Grand Sharm، هضبة أم السيد، شرم الشيخ، مصر" },
        { label: "المواعيد", value: "يوميًا 08:00–20:00" },
      ],
      languagesHeading: "لغات الفريق",
      languagesBody: "ملف PADI بتاعنا بيذكر العربي والإنجليزي والروسي والألماني والإيطالي كلغات الفريق. الموقع ده نفسه متاح بالعربي والإنجليزي فقط حاليًا — الترجمة الكاملة للموقع لسه مش منشورة.",
    },
    contact: {
      heading: "تواصل معنا مباشرة",
      body: "كل قناة تحت دي معتمدة وحقيقية لـSharm Divers Club.",
      whatsappLabel: "واتساب",
      whatsappBody: "أسرع طريقة توصلنا بيها، والطريقة الوحيدة للحجز فعليًا النهاردة.",
      phoneLabel: "الهاتف",
      emailLabel: "البريد الإلكتروني",
      locationLabel: "الموقع",
      hoursLabel: "المواعيد",
    },
    footer: {
      tagline: "ثقة البحر الأحمر، بترتيب شخصي معاك.",
      exploreHeading: "استكشف",
      companyHeading: "الشركة",
      contactHeading: "تواصل",
      exploreLinks: { discover: "استكشف", designSystem: "نظام التصميم" },
      companyLinks: { about: "من نحن", contact: "تواصل" },
      tripadvisor: "شوفنا على TripAdvisor",
      rights: "Sharm Divers Club · فندق Royal Grand Sharm، هضبة أم السيد، شرم الشيخ",
    },
  },
};

export function directionFor(locale: SdcLocale): "ltr" | "rtl" {
  return locale === "ar" ? "rtl" : "ltr";
}
