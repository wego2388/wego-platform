export type SharmLocale = "ar" | "en";

interface CategoryCopy {
  eyebrow: string;
  title: string;
  description: string;
}

interface SiteCopy {
  languageName: string;
  preview: string;
  nav: { experiences: string; howItWorks: string; trust: string };
  hero: { eyebrow: string; title: string; body: string; browse: string; plan: string };
  search: { category: string; date: string; guests: string; anyCategory: string; flexible: string; people: string };
  categoriesHeading: string;
  categoriesBody: string;
  categories: CategoryCopy[];
  how: { heading: string; steps: Array<{ title: string; body: string }> };
  trust: { heading: string; body: string; points: string[] };
  marketplaceNotice: string;
  footer: string;
  catalog: { heading: string; body: string; back: string; previewBooking: string; viewSystem: string };
  browse: {
    allCategories: string;
    loading: string;
    loadError: string;
    empty: { heading: string; body: string };
    fromPrice: string;
    perPerson: string;
    perGroup: string;
    perVehicle: string;
    viewDetails: string;
    operatedBy: string;
    photoCount: (count: number) => string;
  };
  detail: {
    back: string;
    notFoundHeading: string;
    notFoundBody: string;
    optionsHeading: string;
    cancellationHeading: string;
    pickupHeading: string;
    inclusionsHeading: string;
    exclusionsHeading: string;
    contactHeading: string;
    contactBody: string;
    whatsappCta: string;
    emailCta: string;
    whatsappMessage: (serviceName: string) => string;
    emailSubject: (serviceName: string) => string;
  };
}

export const siteCopy: Record<SharmLocale, SiteCopy> = {
  en: {
    languageName: "العربية",
    preview: "Product foundation preview",
    nav: { experiences: "Experiences", howItWorks: "How it works", trust: "Why Sharm To Go" },
    hero: {
      eyebrow: "One clear starting point for Sharm El Sheikh",
      title: "Find the right Sharm experience, with local coordination you can understand.",
      body: "Explore the sea, desert, transfers and local highlights. Sharm To Go operates and confirms every experience — one team to contact from request to return.",
      browse: "Explore categories",
      plan: "See how booking will work",
    },
    search: {
      category: "What do you want to do?",
      date: "When?",
      guests: "Who is going?",
      anyCategory: "Choose a category",
      flexible: "Flexible dates",
      people: "Travellers",
    },
    categoriesHeading: "Start with the kind of day you want",
    categoriesBody: "These are discovery categories, not a claim that every service is already live.",
    categories: [
      { eyebrow: "Red Sea", title: "Sea adventures", description: "Boat days, snorkelling and water experiences run by Sharm To Go." },
      { eyebrow: "Sinai", title: "Desert & stargazing", description: "Canyon, safari and evening experiences with clear pickup details." },
      { eyebrow: "Arrival", title: "Transfers", description: "Airport and local movement requests with vehicle and confirmation details." },
      { eyebrow: "Local", title: "City & culture", description: "Sharm highlights, food and nearby discoveries curated for your time." },
    ],
    how: {
      heading: "A request first, a confirmation second",
      steps: [
        { title: "1. Choose", body: "Select a category, date and group size without guessing the final availability." },
        { title: "2. We verify", body: "Sharm To Go checks capacity, pickup and price." },
        { title: "3. You confirm", body: "You receive one clear summary before payment or final confirmation." },
      ],
    },
    trust: {
      heading: "Built around clarity, not a wall of offers",
      body: "One accountable operator for every request, and a clear line between what is confirmed and what is not.",
      points: ["One operator: Sharm To Go", "Arabic and English operations", "No invented availability or ratings", "One support trail for every request"],
    },
    marketplaceNotice: "Sharm To Go operates and coordinates every experience directly — one point of contact, from your request to your return.",
    footer: "Sharm To Go · Wego Travel Marketplace foundation",
    catalog: {
      heading: "The live catalog is not published yet",
      body: "Services, providers, prices, photos and availability will appear here only after ownership, rights and operational facts are approved in the dashboard.",
      back: "Back to the overview",
      previewBooking: "Try the booking design prototype",
      viewSystem: "View the living design system",
    },
    browse: {
      allCategories: "All categories",
      loading: "Loading live experiences…",
      loadError: "We could not reach the live catalog just now. Please try again shortly.",
      empty: {
        heading: "No live experiences yet",
        body: "Nothing has been published to this catalog yet — services appear here only after an owner approves them in the dashboard.",
      },
      fromPrice: "From",
      perPerson: "per person",
      perGroup: "per group",
      perVehicle: "per vehicle",
      viewDetails: "View details",
      operatedBy: "Operated by",
      photoCount: (count: number) => (count === 1 ? "1 photo" : `${count} photos`),
    },
    detail: {
      back: "Back to experiences",
      notFoundHeading: "This experience isn't available",
      notFoundBody: "It may have been unpublished, or the link may be incorrect. Browse the current live experiences instead.",
      optionsHeading: "Options & pricing",
      cancellationHeading: "Cancellation policy",
      pickupHeading: "Pickup",
      inclusionsHeading: "Included",
      exclusionsHeading: "Not included",
      contactHeading: "Interested?",
      contactBody: "Online booking for this experience isn't live yet. Message us directly and we will confirm availability for your date.",
      whatsappCta: "Message us on WhatsApp",
      emailCta: "Email us",
      whatsappMessage: (serviceName: string) => `Hello Sharm To Go, I'm interested in: ${serviceName}. Is it available on my dates?`,
      emailSubject: (serviceName: string) => `Enquiry: ${serviceName}`,
    },
  },
  ar: {
    languageName: "English",
    preview: "معاينة لأساس المنتج",
    nav: { experiences: "التجارب", howItWorks: "طريقة العمل", trust: "لماذا Sharm To Go" },
    hero: {
      eyebrow: "نقطة بداية واحدة وواضحة لشرم الشيخ",
      title: "اختار تجربة شرم المناسبة مع تنسيق محلي مفهوم وواضح.",
      body: "اكتشف البحر والصحراء والانتقالات وأهم الأماكن. Sharm To Go هي المشغّل والمسؤول عن كل تجربة — فريق واحد تتواصل معاه من الطلب لحد الرجوع.",
      browse: "استكشف الفئات",
      plan: "اعرف طريقة الحجز",
    },
    search: {
      category: "حابب تعمل إيه؟",
      date: "إمتى؟",
      guests: "مين معاك؟",
      anyCategory: "اختر فئة",
      flexible: "مواعيد مرنة",
      people: "عدد المسافرين",
    },
    categoriesHeading: "ابدأ بشكل اليوم اللي يناسبك",
    categoriesBody: "دي فئات مقترحة للاستكشاف، وليست ادعاءً بأن كل الخدمات متاحة الآن.",
    categories: [
      { eyebrow: "البحر الأحمر", title: "مغامرات البحر", description: "رحلات بحرية وسنوركل وتجارب مائية تُشغّلها Sharm To Go." },
      { eyebrow: "سيناء", title: "الصحراء والنجوم", description: "كانـيون وسفاري وسهرات مع تفاصيل انتقال واضحة." },
      { eyebrow: "الوصول", title: "الانتقالات", description: "طلبات مطار وتنقلات محلية مع تفاصيل السيارة والتأكيد." },
      { eyebrow: "محلي", title: "المدينة والثقافة", description: "أهم أماكن شرم والطعام واكتشافات قريبة مناسبة لوقتك." },
    ],
    how: {
      heading: "الأول طلب، وبعد المراجعة تأكيد",
      steps: [
        { title: "١. اختار", body: "حدد الفئة والموعد والعدد بدون افتراض إن التوفر نهائي." },
        { title: "٢. نراجع", body: "Sharm To Go تراجع السعة والانتقال والسعر." },
        { title: "٣. أكّد", body: "يوصلك ملخص واضح قبل الدفع أو التأكيد النهائي." },
      ],
    },
    trust: {
      heading: "وضوح أكتر بدل زحمة عروض",
      body: "مشغّل واحد مسؤول عن كل طلب، وفصل واضح بين ما تم تأكيده وما لم يتم.",
      points: ["مشغّل واحد: Sharm To Go", "تشغيل عربي وإنجليزي", "بدون توفر أو تقييمات وهمية", "مسار دعم واحد لكل طلب"],
    },
    marketplaceNotice: "Sharm To Go تشغّل وتنسّق كل تجربة مباشرةً — نقطة تواصل واحدة من الطلب لحد الرجوع.",
    footer: "Sharm To Go · أساس Wego Travel Marketplace",
    catalog: {
      heading: "الكتالوج الفعلي لم يُنشر بعد",
      body: "الخدمات ومقدموها والأسعار والصور والتوفر ستظهر هنا فقط بعد اعتماد الملكية والحقوق والبيانات التشغيلية من الداش بورد.",
      back: "العودة للنظرة العامة",
      previewBooking: "جرّب نموذج تصميم الحجز",
      viewSystem: "شاهد نظام التصميم الحي",
    },
    browse: {
      allCategories: "كل الفئات",
      loading: "جاري تحميل التجارب المتاحة…",
      loadError: "تعذّر الوصول للكتالوج الفعلي الآن. برجاء المحاولة بعد قليل.",
      empty: {
        heading: "لا توجد تجارب منشورة بعد",
        body: "لم يتم نشر أي خدمة في هذا الكتالوج حتى الآن — تظهر الخدمات هنا فقط بعد اعتمادها من الداش بورد.",
      },
      fromPrice: "يبدأ من",
      perPerson: "للفرد",
      perGroup: "للمجموعة",
      perVehicle: "للسيارة",
      viewDetails: "عرض التفاصيل",
      operatedBy: "مقدَّمة من",
      photoCount: (count: number) => (count === 1 ? "صورة واحدة" : `${count} صور`),
    },
    detail: {
      back: "العودة للتجارب",
      notFoundHeading: "هذه التجربة غير متاحة",
      notFoundBody: "ربما تم إلغاء نشرها أو أن الرابط غير صحيح. تصفح التجارب المتاحة حاليًا بدلاً من ذلك.",
      optionsHeading: "الخيارات والأسعار",
      cancellationHeading: "سياسة الإلغاء",
      pickupHeading: "الانتقال",
      inclusionsHeading: "يشمل",
      exclusionsHeading: "لا يشمل",
      contactHeading: "مهتم؟",
      contactBody: "الحجز الإلكتروني لهذه التجربة غير متاح بعد. راسلنا مباشرة وسنؤكد لك التوفر في موعدك.",
      whatsappCta: "راسلنا على واتساب",
      emailCta: "راسلنا بالإيميل",
      whatsappMessage: (serviceName: string) => `مرحبًا Sharm To Go، مهتم بـ: ${serviceName}. هل متاحة في موعدي؟`,
      emailSubject: (serviceName: string) => `استفسار: ${serviceName}`,
    },
  },
};

export function directionFor(locale: SharmLocale): "ltr" | "rtl" {
  return locale === "ar" ? "rtl" : "ltr";
}
