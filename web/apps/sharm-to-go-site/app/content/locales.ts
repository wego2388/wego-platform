export type SharmLocale = "ar" | "en";

interface CategoryCopy {
  eyebrow: string;
  title: string;
  description: string;
}

interface SiteCopy {
  languageName: string;
  skipToContent: string;
  preview: string;
  whatsappFab: string;
  nav: { experiences: string; howItWorks: string; trust: string; about: string; faq: string; contact: string; home: string; menu: string };
  hero: { eyebrow: string; title: string; body: string; browse: string; plan: string };
  proof: { heading: string; body: string; facts: Array<{ value: string; label: string }> };
  search: { category: string; date: string; guests: string; anyCategory: string; flexible: string; people: string; searchButton: string };
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
    flatRate: string;
    approxUsd: (amount: string) => string;
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
    requestCta: string;
  };
  request: {
    backToService: string;
    steps: { partyDate: string; contact: string; review: string };
    partyDateHeading: string;
    optionLabel: string;
    dateLabel: string;
    dateHelp: string;
    adultsLabel: string;
    childrenLabel: string;
    decreaseGuestLabel: string;
    increaseGuestLabel: string;
    partyExceedsCapacity: (max: number) => string;
    pickupLabel: string;
    pickupPlaceholder: string;
    notesLabel: string;
    continueButton: string;
    contactHeading: string;
    nameLabel: string;
    phoneLabel: string;
    emailLabel: string;
    contactHelp: string;
    contactRequiredError: string;
    contactPhoneInvalidError: string;
    consentLabelPrefix: string;
    consentLabelSuffix: string;
    consentRequiredError: string;
    backButton: string;
    reviewHeading: string;
    reviewNote: string;
    reviewService: string;
    reviewDate: string;
    reviewParty: string;
    reviewPickup: string;
    reviewPrice: string;
    submitButton: string;
    submittingButton: string;
    errorServiceNotFound: string;
    errorOptionNotFound: string;
    errorPartyTooLarge: string;
    errorPriceChanged: string;
    errorGeneric: string;
    successHeadingConfirmed: string;
    successHeadingAwaiting: string;
    successBodyConfirmed: string;
    successBodyAwaiting: string;
    referenceLabel: string;
    trackLink: string;
    copySummary: string;
    copied: string;
    whatsappShare: string;
    startOver: string;
  };
  track: {
    heading: string;
    body: string;
    inputLabel: string;
    inputPlaceholder: string;
    searchButton: string;
    searching: string;
    notFound: string;
    statusLabel: string;
  };
  footerFull: {
    tagline: string;
    exploreHeading: string;
    exploreLinks: { experiences: string; trackRequest: string; designSystem: string };
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
  finderNav: string;
  finderPage: {
    eyebrow: string;
    heading: string;
    body: string;
    loading: string;
    loadError: string;
    cta: (title: string) => string;
    back: string;
  };
}

/**
 * Keyed by the real, live category `code` (see
 * clients/sharm-to-go/content-research/import-manifest.json's own 7
 * categories) — not positional, so the finder page still reads correctly
 * if categories are ever reordered. A code with no entry here falls back
 * to the live category's own name/description from the API rather than
 * crashing, so a future new category degrades gracefully instead of
 * silently disappearing from the finder.
 */
export interface FinderCategoryCopy {
  eyebrow: string;
  title: string;
  description: string;
}

export const finderCategories: Record<SharmLocale, Record<string, FinderCategoryCopy>> = {
  en: {
    sea: { eyebrow: "Red Sea", title: "Sea adventures", description: "Boat days, snorkelling, parasailing and everything that gets you on (or under) the water." },
    desert: { eyebrow: "Sinai", title: "Desert adventures", description: "Quad bikes, camel rides and sunset dunes — the wild side of Sinai, with every pickup detail sorted." },
    culture: { eyebrow: "History", title: "City & culture", description: "Museums, Cairo and Luxor day trips, and Sinai's own ancient sites — for the traveler who wants the story, not just the view." },
    family: { eyebrow: "Everyone", title: "Family activities", description: "Aqua park days and easy outings built for a group with kids, not just adults." },
    entertainment: { eyebrow: "Unwind", title: "Relaxation & shows", description: "An evening show or a proper hammam reset — the slower side of a Sharm holiday." },
    transfers: { eyebrow: "Arrival", title: "Transfers", description: "Airport pickups and private rides around Sharm — simple, on time, no haggling." },
    "heritage-day-trips": { eyebrow: "Beyond Sinai", title: "Heritage & day trips", description: "Petra, the Pyramids and multi-day journeys that take you further than a single day in Sharm." },
  },
  ar: {
    sea: { eyebrow: "البحر الأحمر", title: "مغامرات البحر", description: "أيام مركب، سنوركل، باراسيلنج، وكل حاجة بتوديك فوق المية أو تحتها." },
    desert: { eyebrow: "سيناء", title: "مغامرات الصحراء", description: "كواد، جمال، وكثبان عند الغروب — وش سيناء البري، بكل تفاصيل الاستلام واضحة." },
    culture: { eyebrow: "تاريخ", title: "المدينة والثقافة", description: "متاحف، رحلات القاهرة والأقصر، ومواقع سيناء القديمة — للمسافر اللي عايز الحكاية مش بس المنظر." },
    family: { eyebrow: "العيلة كلها", title: "أنشطة العائلة", description: "أيام أكوا بارك وخرجات سهلة متظبطة لجروب فيه أطفال، مش بس كبار." },
    entertainment: { eyebrow: "استرخاء", title: "استرخاء وعروض", description: "سهرة فيها عرض أو حمام تركي يريّحك — الوش الأهدى لإجازتك في شرم." },
    transfers: { eyebrow: "وصول", title: "الانتقالات", description: "استلام من المطار وانتقالات خاصة جوه شرم — بسيطة وفي ميعادها من غير أي فصال." },
    "heritage-day-trips": { eyebrow: "أبعد من سيناء", title: "رحلات التراث واليوم الكامل", description: "البتراء، الأهرامات، ورحلات بأكتر من يوم بتاخدك أبعد من شرم نفسها." },
  },
};

export const siteCopy: Record<SharmLocale, SiteCopy> = {
  en: {
    languageName: "العربية",
    skipToContent: "Skip to content",
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
        { value: "1", label: "point of contact, from request to return" },
      ],
    },
    search: {
      category: "What do you want to do?",
      date: "When?",
      guests: "Who is going?",
      anyCategory: "Any category",
      flexible: "Flexible dates",
      people: "Travellers",
      searchButton: "Search experiences",
    },
    categoriesHeading: "Start with the kind of day you want",
    categoriesBody: "Sea, desert, culture and more — start with an idea or let our trip finder shape the day around you.",
    categories: [
      { eyebrow: "Red Sea", title: "Sea adventures", description: "Boat days, snorkelling, parasailing and everything that gets you on (or under) the water." },
      { eyebrow: "Sinai", title: "Desert adventures", description: "Quad bikes, camel rides and sunset dunes — the wild side of Sinai, with every pickup detail sorted." },
      { eyebrow: "History", title: "City & culture", description: "Museums, Cairo and Luxor day trips, and Sinai's own ancient sites — for the traveler who wants the story, not just the view." },
      { eyebrow: "Everyone", title: "Family activities", description: "Aqua park days and easy outings built for a group with kids, not just adults." },
      { eyebrow: "Unwind", title: "Relaxation & shows", description: "An evening show or a proper hammam reset — the slower side of a Sharm holiday." },
      { eyebrow: "Arrival", title: "Transfers", description: "Airport pickups and private rides around Sharm — simple, on time, no haggling." },
      { eyebrow: "Beyond Sinai", title: "Heritage & day trips", description: "Petra, the Pyramids and multi-day journeys that take you further than a single day in Sharm." },
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
      flatRate: "flat rate",
      approxUsd: (amount: string) => `(approx. $${amount})`,
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
      contactHeading: "Prefer to ask first?",
      contactBody: "Send a real request above and we'll confirm it, or message us directly if you have questions before deciding.",
      whatsappCta: "Message us on WhatsApp",
      emailCta: "Email us",
      whatsappMessage: (serviceName: string) => `Hello Sharm To Go, I'm interested in: ${serviceName}. Is it available on my dates?`,
      emailSubject: (serviceName: string) => `Enquiry: ${serviceName}`,
      requestCta: "Request this experience",
    },
    request: {
      backToService: "Back to experience",
      steps: { partyDate: "Date & party", contact: "Your details", review: "Review & send" },
      partyDateHeading: "When, and for how many?",
      optionLabel: "Option",
      dateLabel: "Date",
      dateHelp: "Pick a date in the future — we'll confirm availability.",
      adultsLabel: "Adults",
      childrenLabel: "Children",
      decreaseGuestLabel: "Decrease",
      increaseGuestLabel: "Increase",
      partyExceedsCapacity: (max: number) => `This option fits up to ${max} people. Please reduce your party or message us on WhatsApp for a larger group.`,
      pickupLabel: "Hotel / pickup point (optional)",
      pickupPlaceholder: "e.g. Four Seasons Sharm El Sheikh",
      notesLabel: "Anything else we should know? (optional)",
      continueButton: "Continue",
      contactHeading: "How should we reach you?",
      nameLabel: "Full name",
      phoneLabel: "Phone (WhatsApp works best)",
      emailLabel: "Email (optional if you gave a phone)",
      contactHelp: "We'll only use this to confirm your request — never shared, never sold.",
      contactRequiredError: "Please add your name and at least one way to reach you (phone or email).",
      contactPhoneInvalidError: "That doesn't look like a valid phone number. Include your country code, e.g. +20 10 0141 3469.",
      consentLabelPrefix: "I agree that Sharm To Go can use this information to confirm my request, per the",
      consentLabelSuffix: ".",
      consentRequiredError: "Please confirm you agree to the Privacy Policy before continuing.",
      backButton: "Back",
      reviewHeading: "Review your request",
      reviewNote: "This is a request, not a confirmed booking yet. Depending on the experience, you'll either be confirmed instantly or after a quick check by our team.",
      reviewService: "Experience",
      reviewDate: "Date",
      reviewParty: "Party",
      reviewPickup: "Pickup",
      reviewPrice: "Price",
      submitButton: "Send request",
      submittingButton: "Sending…",
      errorServiceNotFound: "This experience is no longer available. Please go back and choose another.",
      errorOptionNotFound: "This option is no longer available. Please go back and choose another.",
      errorPartyTooLarge: "Your party is larger than this option allows. Please go back and reduce it.",
      errorPriceChanged: "The price for this experience just changed. We've updated this screen with the real current price — please review it and try again.",
      errorGeneric: "Something went wrong sending your request. Please try again, or message us on WhatsApp.",
      successHeadingConfirmed: "Confirmed!",
      successHeadingAwaiting: "Request received",
      successBodyConfirmed: "Your request is confirmed. Save your reference below — our team may still reach out with pickup details.",
      successBodyAwaiting: "Our team will review your request and confirm availability shortly. Save your reference below to check the status any time.",
      referenceLabel: "Your reference",
      trackLink: "Check this request later",
      copySummary: "Copy summary",
      copied: "Copied!",
      whatsappShare: "Send this to WhatsApp",
      startOver: "Request another experience",
    },
    track: {
      heading: "Track your request",
      body: "Enter the reference we gave you when you sent your request.",
      inputLabel: "Reference",
      inputPlaceholder: "e.g. STG-AB12CD34",
      searchButton: "Check status",
      searching: "Checking…",
      notFound: "We couldn't find a request with that reference. Double-check it, or message us on WhatsApp.",
      statusLabel: "Status",
    },
    footerFull: {
      tagline: "One clear starting point for Sharm El Sheikh — Sharm To Go operates and confirms every experience directly.",
      exploreHeading: "Explore",
      exploreLinks: { experiences: "Experiences", trackRequest: "Track a request", designSystem: "Design system" },
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
          a: "Most experiences confirm instantly once you send your request. A few that depend on flights, ferries or other outside providers are reviewed by our team first, usually quickly. Either way, a WhatsApp message alone is never itself a confirmed booking — only a request submitted through the site is.",
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
            body: "Browsing this website does not collect any personal data from you on its own — no cookies, no analytics. Submitting a real experience request is different: it collects your name and at least one way to reach you (phone and/or email), plus the date, party size and any pickup or note details you choose to add. We use this only to review, confirm and deliver that request.",
          },
          {
            title: "How we contact you",
            body: "We reach you the way you asked to be reached — by the phone or email you gave us — to confirm or follow up on your request. You can also message us on WhatsApp or email directly at any time; anything you send that way is handled under WhatsApp's or your email provider's own privacy policy, not ours — we only see what you choose to send us in that conversation.",
          },
          {
            title: "Who sees your request",
            body: "Your name and contact details are visible to our staff operating your request, never published publicly. A request's own tracking page and public status never show your name, phone or email — only the service, date, party size and status.",
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
            body: "No account is needed. Most experiences confirm instantly once you send your request. A few need Sharm To Go to verify the service, date, party, pickup and price first — those show as awaiting confirmation until we send you a clear confirmation.",
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
    finderNav: "Not sure? Let us help",
    finderPage: {
      eyebrow: "Trip finder",
      heading: "Not sure what kind of day you want?",
      body: "Pick the one that sounds like you, and we'll show you real experiences that match it — like a local friend pointing you the right way, not a list of filters.",
      loading: "Loading your options…",
      loadError: "We could not reach the live catalog just now. Please try again shortly.",
      cta: (title: string) => `Show me ${title}`,
      back: "Back to the overview",
    },
  },
  ar: {
    languageName: "English",
    skipToContent: "انتقل إلى المحتوى",
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
        { value: "1", label: "نقطة تواصل واحدة من الطلب لحد الرجوع" },
      ],
    },
    search: {
      category: "حابب تعمل إيه؟",
      date: "إمتى؟",
      guests: "مين معاك؟",
      anyCategory: "أي فئة",
      flexible: "مواعيد مرنة",
      people: "عدد المسافرين",
      searchButton: "ابحث عن تجارب",
    },
    categoriesHeading: "ابدأ بشكل اليوم اللي يناسبك",
    categoriesBody: "بحر وصحراء وثقافة وأكتر — ابدأ بفكرة أو خلّي مرشد الرحلات يصمملك اليوم حسب رغبتك.",
    categories: [
      { eyebrow: "البحر الأحمر", title: "مغامرات البحر", description: "أيام مركب، سنوركل، باراسيلنج، وكل حاجة بتوديك فوق المية أو تحتها." },
      { eyebrow: "سيناء", title: "مغامرات الصحراء", description: "كواد، جمال، وكثبان عند الغروب — وش سيناء البري، بكل تفاصيل الاستلام واضحة." },
      { eyebrow: "تاريخ", title: "المدينة والثقافة", description: "متاحف، رحلات القاهرة والأقصر، ومواقع سيناء القديمة — للمسافر اللي عايز الحكاية مش بس المنظر." },
      { eyebrow: "العيلة كلها", title: "أنشطة العائلة", description: "أيام أكوا بارك وخرجات سهلة متظبطة لجروب فيه أطفال، مش بس كبار." },
      { eyebrow: "استرخاء", title: "استرخاء وعروض", description: "سهرة فيها عرض أو حمام تركي يريّحك — الوش الأهدى لإجازتك في شرم." },
      { eyebrow: "وصول", title: "الانتقالات", description: "استلام من المطار وانتقالات خاصة جوه شرم — بسيطة وفي ميعادها من غير أي فصال." },
      { eyebrow: "أبعد من سيناء", title: "رحلات التراث واليوم الكامل", description: "البتراء، الأهرامات، ورحلات بأكتر من يوم بتاخدك أبعد من شرم نفسها." },
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
      flatRate: "سعر ثابت",
      approxUsd: (amount: string) => `(≈ ${amount}$ تقريبي)`,
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
      contactHeading: "تفضل تسأل الأول؟",
      contactBody: "ابعت طلب حقيقي فوق وهنأكده، أو راسلنا مباشرة لو عندك أسئلة قبل ما تقرر.",
      whatsappCta: "راسلنا على واتساب",
      emailCta: "راسلنا بالإيميل",
      whatsappMessage: (serviceName: string) => `مرحبًا Sharm To Go، مهتم بـ: ${serviceName}. هل متاحة في موعدي؟`,
      emailSubject: (serviceName: string) => `استفسار: ${serviceName}`,
      requestCta: "اطلب هذه التجربة",
    },
    request: {
      backToService: "العودة للتجربة",
      steps: { partyDate: "الموعد وعدد الأفراد", contact: "بياناتك", review: "المراجعة والإرسال" },
      partyDateHeading: "إمتى، ولعدد كام؟",
      optionLabel: "الخيار",
      dateLabel: "التاريخ",
      dateHelp: "اختر تاريخ في المستقبل — هنأكد التوفر.",
      adultsLabel: "البالغين",
      childrenLabel: "الأطفال",
      decreaseGuestLabel: "تقليل",
      increaseGuestLabel: "زيادة",
      partyExceedsCapacity: (max: number) => `الخيار ده بيستوعب حتى ${max} فرد. قلّل عدد الأفراد أو راسلنا على واتساب لمجموعة أكبر.`,
      pickupLabel: "الفندق / نقطة الاستلام (اختياري)",
      pickupPlaceholder: "مثال: فندق Four Seasons شرم الشيخ",
      notesLabel: "أي حاجة تانية تحب نعرفها؟ (اختياري)",
      continueButton: "التالي",
      contactHeading: "إزاي نوصلك؟",
      nameLabel: "الاسم بالكامل",
      phoneLabel: "رقم الهاتف (واتساب أفضل وسيلة)",
      emailLabel: "الإيميل (اختياري لو كتبت رقم هاتف)",
      contactHelp: "هنستخدم البيانات دي بس عشان نأكد طلبك — من غير مشاركة أو بيع أبدًا.",
      contactRequiredError: "اكتب اسمك ووسيلة تواصل واحدة على الأقل (هاتف أو إيميل).",
      contactPhoneInvalidError: "رقم التليفون ده مش شكله صح. اكتب كود الدولة كمان، زي +20 10 0141 3469.",
      consentLabelPrefix: "أوافق على إن شرم تو جو تستخدم البيانات دي عشان تأكد طلبي، حسب",
      consentLabelSuffix: ".",
      consentRequiredError: "من فضلك أكّد موافقتك على سياسة الخصوصية قبل ما تكمل.",
      backButton: "رجوع",
      reviewHeading: "راجع طلبك",
      reviewNote: "ده طلب، لسه مش حجز مؤكد. حسب التجربة، هيتأكد فورًا أو بعد مراجعة سريعة من فريقنا.",
      reviewService: "التجربة",
      reviewDate: "التاريخ",
      reviewParty: "عدد الأفراد",
      reviewPickup: "الاستلام",
      reviewPrice: "السعر",
      submitButton: "ابعت الطلب",
      submittingButton: "جاري الإرسال…",
      errorServiceNotFound: "التجربة دي مش متاحة دلوقتي. ارجع واختار تجربة تانية.",
      errorOptionNotFound: "الخيار ده مش متاح دلوقتي. ارجع واختار خيار تاني.",
      errorPartyTooLarge: "عدد الأفراد أكبر من المسموح للخيار ده. ارجع وقلل العدد.",
      errorPriceChanged: "سعر التجربة دي اتغيّر لتوه. حدّثنا الصفحة بالسعر الحقيقي الحالي — راجعه وحاول تاني.",
      errorGeneric: "حصلت مشكلة في إرسال طلبك. حاول تاني، أو راسلنا على واتساب.",
      successHeadingConfirmed: "تم التأكيد!",
      successHeadingAwaiting: "استلمنا طلبك",
      successBodyConfirmed: "طلبك اتأكد. احتفظ برقم المرجع تحت — فريقنا ممكن يتواصل معاك لتفاصيل الاستلام.",
      successBodyAwaiting: "فريقنا هيراجع طلبك ويأكد التوفر قريبًا. احتفظ برقم المرجع تحت عشان تتابع الحالة في أي وقت.",
      referenceLabel: "رقم المرجع بتاعك",
      trackLink: "تابع الطلب ده بعدين",
      copySummary: "انسخ الملخص",
      copied: "تم النسخ!",
      whatsappShare: "ابعته على واتساب",
      startOver: "اطلب تجربة تانية",
    },
    track: {
      heading: "تابع طلبك",
      body: "اكتب رقم المرجع اللي اديناهولك لما بعتت طلبك.",
      inputLabel: "رقم المرجع",
      inputPlaceholder: "مثال: STG-AB12CD34",
      searchButton: "اعرف الحالة",
      searching: "بنبحث…",
      notFound: "معلش، مش لاقيين طلب بالمرجع ده. تأكد منه، أو راسلنا على واتساب.",
      statusLabel: "الحالة",
    },
    footerFull: {
      tagline: "نقطة بداية واحدة وواضحة لشرم الشيخ — Sharm To Go تشغّل وتؤكد كل تجربة مباشرةً.",
      exploreHeading: "استكشف",
      exploreLinks: { experiences: "التجارب", trackRequest: "تابع طلبك", designSystem: "نظام التصميم" },
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
          a: "أغلب التجارب بتتأكد فورًا لما تبعت طلبك. عدد قليل بيعتمد على طيران أو عبّارات أو جهات خارجية بيراجعها فريقنا الأول، وعادةً بسرعة. في الحالتين، رسالة واتساب لوحدها مش معناها إن الحجز اتأكد — بس الطلب اللي اتبعت من الموقع هو اللي بيتأكد.",
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
            body: "تصفحك للموقع لوحده مش بيجمع أي بيانات شخصية عنك — مفيش كوكيز، ومفيش تحليلات بيانات. إرسال طلب تجربة حقيقي حاجة تانية: بيجمع اسمك ووسيلة تواصل واحدة على الأقل (تليفون و/أو إيميل)، زائد التاريخ وعدد الأفراد وأي تفاصيل استلام أو ملاحظات تختار تضيفها. بنستخدم ده بس عشان نراجع ونأكد ونوصّل الطلب ده.",
          },
          {
            title: "إزاي بنتواصل معاك",
            body: "بنتواصل معاك بالطريقة اللي طلبتها — على التليفون أو الإيميل اللي بعتهولنا — عشان نأكد أو نتابع طلبك. تقدر كمان تراسلنا على واتساب أو الإيميل مباشرة في أي وقت؛ أي حاجة تبعتها كده بتتعامل حسب سياسة الخصوصية بتاعة واتساب أو مزود الإيميل بتاعك، مش سياستنا — إحنا بس بنشوف اللي بتختار تبعته في المحادثة.",
          },
          {
            title: "مين بيشوف طلبك",
            body: "اسمك وبيانات التواصل بتاعتك ظاهرة بس لفريقنا اللي بيشتغل على طلبك، ومتنشرش علنًا أبدًا. صفحة متابعة الطلب وحالته العامة مبيظهرش فيهم اسمك أو تليفونك أو إيميلك خالص — بس الخدمة والتاريخ وعدد الأفراد والحالة.",
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
            body: "مفيش داعي لحساب. أغلب التجارب بتتأكد فورًا لحظة ما تبعت طلبك. في تجارب قليلة محتاجة Sharm To Go تراجع الخدمة والموعد والعدد والانتقال والسعر الأول — دي بتظهر «بانتظار التأكيد» لحد ما نبعتلك تأكيد واضح.",
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
    finderNav: "مش عارف تختار؟ خلّينا نساعدك",
    finderPage: {
      eyebrow: "مرشد الرحلات",
      heading: "مش عارف عايز يومك يكون إزاي؟",
      body: "اختار اللي بيشبهك، وهنوريك تجارب حقيقية تناسبه — زي ما صاحبك اللي عايش هنا يدلّك على الصح، مش ليستة فلاتر.",
      loading: "بنحمّل اختياراتك…",
      loadError: "مش قادرين نوصل للكتالوج الحي دلوقتي. جرّب تاني بعد شوية.",
      cta: (title: string) => `وريني ${title}`,
      back: "ارجع للمقدمة",
    },
  },
};

export function directionFor(locale: SharmLocale): "ltr" | "rtl" {
  return locale === "ar" ? "rtl" : "ltr";
}
