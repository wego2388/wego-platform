export type SharmLocale = "ar" | "en";

interface CategoryCopy {
  eyebrow: string;
  title: string;
  description: string;
}

interface SiteCopy {
  languageName: string;
  preview: string;
  whatsappFab: string;
  nav: { experiences: string; howItWorks: string; trust: string; about: string; faq: string; contact: string; home: string; menu: string };
  hero: { eyebrow: string; title: string; body: string; browse: string; plan: string };
  proof: { heading: string; body: string; facts: Array<{ value: string; label: string }> };
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
  footerFull: {
    tagline: string;
    exploreHeading: string;
    exploreLinks: { experiences: string; bookingPreview: string; designSystem: string };
    companyHeading: string;
    companyLinks: { faq: string; contact: string };
    contactHeading: string;
    rights: string;
    legal: { privacy: string; terms: string };
  };
  contactPage: {
    heading: string;
    body: string;
    whatsappCta: string;
    emailCta: string;
    responseNote: string;
  };
  aboutPage: {
    eyebrow: string;
    heading: string;
    lead: string;
    story: string[];
    promiseHeading: string;
    promises: string[];
    cta: string;
  };
  faqPage: {
    heading: string;
    body: string;
    knownHeading: string;
    unknownHeading: string;
    unknownBody: string;
    items: Array<{ q: string; a: string }>;
    unknownItems: string[];
  };
  legalPages: {
    updated: string;
    back: string;
    privacy: { heading: string; sections: Array<{ title: string; body: string }> };
    terms: { heading: string; sections: Array<{ title: string; body: string }> };
  };
}

export const siteCopy: Record<SharmLocale, SiteCopy> = {
  en: {
    languageName: "العربية",
    preview: "25+ years of local tourism experience",
    whatsappFab: "WhatsApp",
    nav: {
      experiences: "Experiences",
      howItWorks: "How it works",
      trust: "Why Sharm To Go",
      about: "About",
      faq: "FAQ",
      contact: "Contact",
      home: "Sharm To Go home",
      menu: "Menu",
    },
    hero: {
      eyebrow: "One clear starting point for Sharm El Sheikh",
      title: "Your Sharm holiday, shaped around you.",
      body: "Tell us what you enjoy and what you want to spend. We help you choose, organise the details and stay with you from the first question until you return.",
      browse: "Explore experiences",
      plan: "How we help",
    },
    proof: {
      heading: "Experience you can feel from the first conversation",
      body: "Our team's accumulated experience in tourism and Sharm El Sheikh helps us match different travellers and budgets with a clearer, more personal holiday.",
      facts: [
        { value: "25+", label: "years of tourism experience" },
        { value: "3M+", label: "customers who placed their trust in us" },
        { value: "5M+", label: "successful trips we helped organise" },
        { value: "24/7", label: "continuous support" },
      ],
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
    categoriesBody: "Sea, desert, transfers and local discoveries — start with an idea or ask us to shape the day around you.",
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
      heading: "A local friend, not a wall of offers",
      body: "We listen first, protect your budget and keep one clear support relationship from planning to return.",
      points: ["Advice shaped around your budget", "Arabic and English support", "Clear price and confirmation details", "One support trail for every request"],
    },
    marketplaceNotice: "Sharm To Go operates and coordinates every experience directly — one point of contact, from your request to your return.",
    footer: "Sharm To Go · Where you must go.",
    catalog: {
      heading: "Find your Sharm experience",
      body: "Browse published experiences, compare clear options, or ask our team to help you choose for your dates and budget.",
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
    footerFull: {
      tagline: "One clear starting point for Sharm El Sheikh — Sharm To Go operates and confirms every experience directly.",
      exploreHeading: "Explore",
      exploreLinks: { experiences: "Experiences", bookingPreview: "Booking prototype", designSystem: "Design system" },
      companyHeading: "Company",
      companyLinks: { faq: "FAQ", contact: "Contact" },
      contactHeading: "Contact",
      rights: "© 2026 Sharm To Go. All rights reserved.",
      legal: { privacy: "Privacy Policy", terms: "Terms of Use" },
    },
    contactPage: {
      heading: "Contact us",
      body: "Sharm To Go operates every experience directly, so there is one real team behind every request — reach us on WhatsApp for the fastest reply, or by email.",
      whatsappCta: "Message us on WhatsApp",
      emailCta: "Email us",
      responseNote: "We reply as soon as we can. We don't have a published support-hours commitment yet — ask us on WhatsApp if your request is urgent.",
    },
    aboutPage: {
      eyebrow: "More than a booking website",
      heading: "The local friend behind your Sharm holiday",
      lead: "For more than twenty-five years, our team has worked across tourism and Sharm El Sheikh, building trust through personal service, practical local knowledge and support that continues after a sale.",
      story: [
        "We have built bridges of trust with more than three million customers and participated in organising more than five million successful trips.",
        "Sharm To Go does not simply list tourism services. We listen to different needs and budgets, help shape the right holiday and stay available around the clock when support is needed.",
        "We want to be the friend you remember when you leave Sharm — and the one waiting when you return.",
      ],
      promiseHeading: "What that means for you",
      promises: ["Choices explained clearly", "A holiday shaped around your budget", "One accountable team to ask", "Support before, during and after your experience"],
      cta: "Tell us what you want to do",
    },
    faqPage: {
      heading: "Frequently asked questions",
      body: "Real answers to what is already decided. Anything not listed here is genuinely not decided yet — ask us directly and we'll confirm.",
      knownHeading: "What we can answer now",
      unknownHeading: "Still to confirm",
      unknownBody: "These depend on details we don't have published yet — message us on WhatsApp and we'll confirm for your specific request.",
      items: [
        {
          q: "Do I need to create an account to book?",
          a: "No. Booking is anonymous and kept as simple as possible — just your name, one reachable contact, and your request details. No sign-up, no login.",
        },
        {
          q: "When is my request confirmed?",
          a: "Only after our team verifies the service, date, party, pickup and price and sends you a clear confirmation. Sending a request or WhatsApp message is not itself a confirmed booking.",
        },
        {
          q: "Who actually runs the experience?",
          a: "Sharm To Go operates every listed experience directly — one accountable team, not a network of unnamed third-party partners.",
        },
        {
          q: "What is the cancellation policy?",
          a: "Free cancellation up to 24 hours before your confirmed pickup or start time for most experiences (72 hours for flight-inclusive or overnight ones). Cancelling later, or a no-show, is charged in full.",
        },
        {
          q: "Can I pay online right now?",
          a: "Not yet. Online payment isn't live — once your request is confirmed, our team arranges payment with you directly.",
        },
      ],
      unknownItems: [
        "Exact pricing and availability for a specific date and group size",
        "Which experiences are open for real requests today",
        "How long a refund actually takes after a cancellation",
      ],
    },
    legalPages: {
      updated: "Last updated: 2026-09-24",
      back: "Back home",
      privacy: {
        heading: "Privacy Policy",
        sections: [
          {
            title: "What this site collects",
            body: "This website does not use cookies, does not run analytics, and has no accounts or contact form. Browsing it does not collect any personal data from you on its own.",
          },
          {
            title: "WhatsApp and email",
            body: "The only ways to contact us from this site are WhatsApp and email. Anything you send through them is handled under WhatsApp's or your email provider's own privacy policy, not ours — we only see what you choose to send us in that conversation.",
          },
          {
            title: "When online booking goes live",
            body: "Once real booking exists, confirming a request will need your name and one reachable contact. This page will say exactly what is collected and why before that feature ships.",
          },
        ],
      },
      terms: {
        heading: "Terms of Use",
        sections: [
          {
            title: "What this site is",
            body: "This site helps you discover real Sharm El Sheikh experiences that Sharm To Go operates and confirms directly. Browsing it does not by itself create a booking or take payment.",
          },
          {
            title: "How a request works",
            body: "No account is needed. A request becomes confirmed only after Sharm To Go verifies the service, date, party, pickup and price and sends a clear confirmation.",
          },
          {
            title: "Prices",
            body: "Prices shown are initial launch prices in Egyptian Pounds (EGP), set directly by Sharm To Go and subject to change. The price confirmed with you at the time of your request is the one that applies.",
          },
          {
            title: "Cancellation",
            body: "Free cancellation up to 24 hours before your confirmed pickup or start time for most experiences (72 hours for flight-inclusive or overnight ones). Cancelling later, or a no-show, is charged in full.",
          },
          {
            title: "Payment",
            body: "Online payment is not live yet. Nothing is charged automatically by this website — payment is arranged directly with our team once your request is confirmed.",
          },
        ],
      },
    },
  },
  ar: {
    languageName: "English",
    preview: "أكثر من 25 سنة خبرة في السياحة",
    whatsappFab: "واتساب",
    nav: {
      experiences: "التجارب",
      howItWorks: "طريقة العمل",
      trust: "لماذا Sharm To Go",
      about: "من نحن",
      faq: "الأسئلة الشائعة",
      contact: "تواصل",
      home: "الصفحة الرئيسية لـSharm To Go",
      menu: "القائمة",
    },
    hero: {
      eyebrow: "نقطة بداية واحدة وواضحة لشرم الشيخ",
      title: "إجازتك في شرم… معمولة على مقاسك.",
      body: "قول لنا بتحب إيه وميزانيتك إيه، وإحنا نساعدك تختار ونرتب التفاصيل ونفضل معاك من أول سؤال لحد ما ترجع.",
      browse: "استكشف التجارب",
      plan: "إزاي بنساعدك",
    },
    proof: {
      heading: "خبرة هتحس بيها من أول كلام بينا",
      body: "خبرة فريقنا المتراكمة في السياحة وشرم الشيخ بتساعدنا نفهم اختلاف المسافرين والميزانيات ونرتب إجازة أوضح وأقرب لك.",
      facts: [
        { value: "+25", label: "سنة خبرة في السياحة" },
        { value: "+3 مليون", label: "عميل وثقوا فينا" },
        { value: "+5 مليون", label: "رحلة ناجحة شاركنا في تنظيمها" },
        { value: "24/7", label: "دعم مستمر" },
      ],
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
    categoriesBody: "بحر وصحراء وانتقالات واكتشافات محلية — ابدأ بفكرة أو خلّينا نصمملك اليوم حسب رغبتك.",
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
      heading: "صديق محلي مش مجرد زحمة عروض",
      body: "بنسمعك الأول، ونحافظ على ميزانيتك، ونفضل نقطة التواصل الواضحة معاك من التخطيط لحد الرجوع.",
      points: ["اختيارات مناسبة لميزانيتك", "دعم بالعربي والإنجليزي", "سعر وتأكيد واضحين", "مسار دعم واحد لكل طلب"],
    },
    marketplaceNotice: "Sharm To Go تشغّل وتنسّق كل تجربة مباشرةً — نقطة تواصل واحدة من الطلب لحد الرجوع.",
    footer: "Sharm To Go · وجهتك اللي لازم تروحها.",
    catalog: {
      heading: "اختار تجربتك في شرم",
      body: "تصفح التجارب المنشورة وقارن الخيارات الواضحة، أو اسأل فريقنا يساعدك تختار حسب موعدك وميزانيتك.",
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
    footerFull: {
      tagline: "نقطة بداية واحدة وواضحة لشرم الشيخ — Sharm To Go تشغّل وتؤكد كل تجربة مباشرةً.",
      exploreHeading: "استكشف",
      exploreLinks: { experiences: "التجارب", bookingPreview: "نموذج الحجز", designSystem: "نظام التصميم" },
      companyHeading: "الشركة",
      companyLinks: { faq: "الأسئلة الشائعة", contact: "تواصل" },
      contactHeading: "تواصل",
      rights: "© 2026 Sharm To Go. جميع الحقوق محفوظة.",
      legal: { privacy: "سياسة الخصوصية", terms: "شروط الاستخدام" },
    },
    contactPage: {
      heading: "تواصل معنا",
      body: "Sharm To Go تشغّل كل تجربة مباشرةً، يعني فيه فريق حقيقي واحد وراء كل طلب — راسلنا على واتساب لأسرع رد، أو بالإيميل.",
      whatsappCta: "راسلنا على واتساب",
      emailCta: "راسلنا بالإيميل",
      responseNote: "بنرد بأسرع ما يمكن. لسه مفيش مواعيد دعم رسمية معلنة — لو طلبك مستعجل قولنا على واتساب.",
    },
    aboutPage: {
      eyebrow: "أكتر من مجرد موقع حجز",
      heading: "الصديق المحلي اللي ورا إجازتك في شرم",
      lead: "على مدار أكتر من خمسة وعشرين سنة، اشتغل فريقنا في السياحة وشرم الشيخ وبنى الثقة بالخدمة الشخصية والخبرة المحلية والدعم اللي ما بينتهيش مع البيع.",
      story: [
        "بنينا جسور ثقة مع أكتر من ثلاثة مليون عميل، وشاركنا في تنظيم أكتر من خمسة مليون رحلة ناجحة.",
        "Sharm To Go مش مجرد قائمة خدمات سياحية. بنسمع احتياجاتك وميزانيتك، ونساعدك تصمم الإجازة المناسبة، ونفضل متاحين وقت ما تحتاجنا.",
        "هدفنا نكون الصديق اللي تفتكره لما تسيب شرم — وتلاقيه مستنيك لما ترجع.",
      ],
      promiseHeading: "وده معناه إيه ليك؟",
      promises: ["اختيارات مشروحة بوضوح", "إجازة مناسبة لميزانيتك", "فريق واحد مسؤول تسأله", "دعم قبل التجربة وأثناءها وبعدها"],
      cta: "قول لنا حابب تعمل إيه",
    },
    faqPage: {
      heading: "الأسئلة الشائعة",
      body: "إجابات حقيقية لكل حاجة تقررت بالفعل. أي حاجة مش موجودة هنا يبقى فعلًا لسه ما اتقررتش — اسألنا مباشرة وهنأكدلك.",
      knownHeading: "اللي نقدر نجاوب عليه دلوقتي",
      unknownHeading: "لسه محتاج تأكيد",
      unknownBody: "دي حاجات بتعتمد على تفاصيل لسه مش معلنة — راسلنا على واتساب ونأكدلك بالظبط لطلبك.",
      items: [
        {
          q: "محتاج أعمل حساب عشان أحجز؟",
          a: "لأ. الحجز بدون تسجيل وبأبسط شكل ممكن — بس اسمك، وسيلة تواصل واحدة، وتفاصيل طلبك. من غير تسجيل أو دخول.",
        },
        {
          q: "إمتى بيتأكد طلبي؟",
          a: "بعد ما فريقنا يراجع الخدمة والموعد والعدد والانتقال والسعر ويبعتلك تأكيد واضح. إرسال الطلب أو رسالة واتساب لوحده مش معناه إن الحجز اتأكد.",
        },
        {
          q: "مين اللي بيشغّل التجربة فعليًا؟",
          a: "Sharm To Go بتشغّل كل تجربة معروضة مباشرةً — فريق واحد مسؤول، مش شبكة شركاء غير معروفين.",
        },
        {
          q: "إيه سياسة الإلغاء؟",
          a: "إلغاء مجاني حتى 24 ساعة قبل موعد الاستلام أو بدء الخدمة المؤكد لمعظم التجارب (72 ساعة للتجارب المرتبطة بطيران أو المبيت). الإلغاء بعد كده، أو عدم الحضور، يُحتسب كاملاً.",
        },
        {
          q: "أقدر أدفع أونلاين دلوقتي؟",
          a: "لسه لأ. الدفع الإلكتروني مش متاح حاليًا — بعد ما يتأكد طلبك، فريقنا بيرتب معاك طريقة الدفع مباشرة.",
        },
      ],
      unknownItems: [
        "السعر والتوفر بالظبط لموعد ومجموعة معينة",
        "أي تجارب متاحة فعليًا للطلب الحقيقي دلوقتي",
        "المدة الفعلية لاسترجاع المبلغ بعد الإلغاء",
      ],
    },
    legalPages: {
      updated: "آخر تحديث: 2026-09-24",
      back: "العودة للرئيسية",
      privacy: {
        heading: "سياسة الخصوصية",
        sections: [
          {
            title: "اللي الموقع ده بيجمعه",
            body: "الموقع ده مش بيستخدم كوكيز، ومفيش تحليلات بيانات، ومفيش حسابات أو نموذج تواصل. تصفحك للموقع لوحده مش بيجمع أي بيانات شخصية عنك.",
          },
          {
            title: "واتساب والإيميل",
            body: "الوسيلتين الوحيدتين للتواصل معنا من الموقع هما واتساب والإيميل. أي حاجة تبعتها من خلالهم بتتعامل حسب سياسة الخصوصية بتاعة واتساب أو مزود الإيميل بتاعك، مش سياستنا — إحنا بس بنشوف اللي بتختار تبعته في المحادثة.",
          },
          {
            title: "لما الحجز الإلكتروني يشتغل",
            body: "لما نظام الحجز الحقيقي يبقى موجود، تأكيد الطلب هيحتاج اسمك ووسيلة تواصل واحدة. الصفحة دي هتوضح بالظبط اللي بيتجمع وليه قبل ما الميزة دي تشتغل.",
          },
        ],
      },
      terms: {
        heading: "شروط الاستخدام",
        sections: [
          {
            title: "الموقع ده إيه بالظبط",
            body: "الموقع ده بيساعدك تكتشف تجارب حقيقية في شرم الشيخ بتشغّلها وتؤكدها Sharm To Go مباشرةً. تصفح الموقع لوحده مش بيعمل حجز ولا بياخد دفع.",
          },
          {
            title: "طلبك بيتنفذ إزاي",
            body: "مفيش داعي لحساب. الطلب بيتأكد فقط بعد ما Sharm To Go تراجع الخدمة والموعد والعدد والانتقال والسعر وتبعتلك تأكيد واضح.",
          },
          {
            title: "الأسعار",
            body: "الأسعار المعروضة أسعار إطلاق مبدئية بالجنيه المصري، حددتها Sharm To Go مباشرةً وممكن تتغير. السعر اللي بيتأكد معاك وقت طلبك هو اللي يتم التعامل بيه.",
          },
          {
            title: "الإلغاء",
            body: "إلغاء مجاني حتى 24 ساعة قبل موعد الاستلام أو بدء الخدمة المؤكد لمعظم التجارب (72 ساعة للتجارب المرتبطة بطيران أو المبيت). الإلغاء بعد كده، أو عدم الحضور، يُحتسب كاملاً.",
          },
          {
            title: "الدفع",
            body: "الدفع الإلكتروني مش شغال حاليًا. الموقع مش بياخد أي مبلغ تلقائيًا — الدفع بيتم ترتيبه مباشرة مع فريقنا بعد ما يتأكد طلبك.",
          },
        ],
      },
    },
  },
};

export function directionFor(locale: SharmLocale): "ltr" | "rtl" {
  return locale === "ar" ? "rtl" : "ltr";
}
