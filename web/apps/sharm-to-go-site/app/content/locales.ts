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
}

export const siteCopy: Record<SharmLocale, SiteCopy> = {
  en: {
    languageName: "العربية",
    preview: "Product foundation preview",
    nav: { experiences: "Experiences", howItWorks: "How it works", trust: "Why Sharm To Go" },
    hero: {
      eyebrow: "One clear starting point for Sharm El Sheikh",
      title: "Find the right Sharm experience, with local coordination you can understand.",
      body: "Explore the sea, desert, transfers and local highlights. Availability and provider responsibility will always be shown before a request is confirmed.",
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
    categoriesBody: "These are discovery categories, not a claim that every service is already live or operated by Sharm To Go.",
    categories: [
      { eyebrow: "Red Sea", title: "Sea adventures", description: "Boat days, snorkelling and water experiences from approved operators." },
      { eyebrow: "Sinai", title: "Desert & stargazing", description: "Canyon, safari and evening experiences with clear pickup details." },
      { eyebrow: "Arrival", title: "Transfers", description: "Airport and local movement requests with vehicle and confirmation details." },
      { eyebrow: "Local", title: "City & culture", description: "Sharm highlights, food and nearby discoveries curated for your time." },
    ],
    how: {
      heading: "A request first, a confirmation second",
      steps: [
        { title: "1. Choose", body: "Select a category, date and group size without guessing the final availability." },
        { title: "2. We verify", body: "Sharm To Go or the responsible approved provider checks capacity, pickup and price." },
        { title: "3. You confirm", body: "You receive one clear summary before payment or final confirmation." },
      ],
    },
    trust: {
      heading: "Built around clarity, not a wall of offers",
      body: "The marketplace foundation separates who provides the service, who coordinates the request and what is actually confirmed.",
      points: ["Provider shown before confirmation", "Arabic and English operations", "No invented availability or ratings", "One support trail for every request"],
    },
    marketplaceNotice: "Important: some experiences will be provided by approved local partners, not directly by Sharm To Go. The responsible provider will be identified before confirmation.",
    footer: "Sharm To Go · Wego Travel Marketplace foundation",
    catalog: {
      heading: "The live catalog is not published yet",
      body: "Services, providers, prices, photos and availability will appear here only after ownership, rights and operational facts are approved in the dashboard.",
      back: "Back to the overview",
      previewBooking: "Try the booking design prototype",
      viewSystem: "View the living design system",
    },
  },
  ar: {
    languageName: "English",
    preview: "معاينة لأساس المنتج",
    nav: { experiences: "التجارب", howItWorks: "طريقة العمل", trust: "لماذا Sharm To Go" },
    hero: {
      eyebrow: "نقطة بداية واحدة وواضحة لشرم الشيخ",
      title: "اختار تجربة شرم المناسبة مع تنسيق محلي مفهوم وواضح.",
      body: "اكتشف البحر والصحراء والانتقالات وأهم الأماكن. سنوضح التوفر والجهة المسؤولة عن الخدمة قبل تأكيد أي طلب.",
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
    categoriesBody: "دي فئات مقترحة للاستكشاف، وليست ادعاءً بأن كل الخدمات متاحة الآن أو تابعة مباشرةً لـSharm To Go.",
    categories: [
      { eyebrow: "البحر الأحمر", title: "مغامرات البحر", description: "رحلات بحرية وسنوركل وتجارب مائية من مشغلين معتمدين." },
      { eyebrow: "سيناء", title: "الصحراء والنجوم", description: "كانـيون وسفاري وسهرات مع تفاصيل انتقال واضحة." },
      { eyebrow: "الوصول", title: "الانتقالات", description: "طلبات مطار وتنقلات محلية مع تفاصيل السيارة والتأكيد." },
      { eyebrow: "محلي", title: "المدينة والثقافة", description: "أهم أماكن شرم والطعام واكتشافات قريبة مناسبة لوقتك." },
    ],
    how: {
      heading: "الأول طلب، وبعد المراجعة تأكيد",
      steps: [
        { title: "١. اختار", body: "حدد الفئة والموعد والعدد بدون افتراض إن التوفر نهائي." },
        { title: "٢. نراجع", body: "Sharm To Go أو مقدم الخدمة المعتمد يراجع السعة والانتقال والسعر." },
        { title: "٣. أكّد", body: "يوصلك ملخص واضح قبل الدفع أو التأكيد النهائي." },
      ],
    },
    trust: {
      heading: "وضوح أكتر بدل زحمة عروض",
      body: "أساس السوق بيفصل بين مقدم الخدمة ومنسق الطلب وما تم تأكيده فعليًا.",
      points: ["توضيح مقدم الخدمة قبل التأكيد", "تشغيل عربي وإنجليزي", "بدون توفر أو تقييمات وهمية", "مسار دعم واحد لكل طلب"],
    },
    marketplaceNotice: "مهم: بعض التجارب سيقدمها شركاء محليون معتمدون وليست خدمات تابعة مباشرةً لـSharm To Go. سنوضح مقدم الخدمة المسؤول قبل التأكيد.",
    footer: "Sharm To Go · أساس Wego Travel Marketplace",
    catalog: {
      heading: "الكتالوج الفعلي لم يُنشر بعد",
      body: "الخدمات ومقدموها والأسعار والصور والتوفر ستظهر هنا فقط بعد اعتماد الملكية والحقوق والبيانات التشغيلية من الداش بورد.",
      back: "العودة للنظرة العامة",
      previewBooking: "جرّب نموذج تصميم الحجز",
      viewSystem: "شاهد نظام التصميم الحي",
    },
  },
};

export function directionFor(locale: SharmLocale): "ltr" | "rtl" {
  return locale === "ar" ? "rtl" : "ltr";
}
