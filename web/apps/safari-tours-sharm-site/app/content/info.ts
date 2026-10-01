import type { StsLocale } from "./locales";

/**
 * Information pages (UX-5): contact, about, FAQ, terms, privacy, trip finder
 * and the error page. Every statement comes from owner-approved facts in the
 * owner data hub; anything still unconfirmed there (support hours, legal
 * identity, weather rules, child prices) is deliberately left out. EN/AR are
 * final drafts; RU/IT await native review. Terms and privacy still need a
 * legal review before launch.
 */
export interface Section { heading: string; body: string[] }

export interface InfoCopy {
  contact: {
    title: string; intro: string;
    whatsapp: string; whatsappBody: string; whatsappCta: string;
    phone: string; phoneBody: string; call: string;
    email: string; emailBody: string; emailCta: string;
    office: string; officeBody: string; map: string;
    follow: string; reviews: string; googleReviews: string; tripadvisor: string; booking: string; bookingBody: string;
  };
  about: { title: string; intro: string; sections: Section[]; cta: string };
  faq: { title: string; intro: string; items: { title: string; body: string }[]; more: string };
  terms: { title: string; updated: string; sections: Section[] };
  privacy: { title: string; updated: string; sections: Section[] };
  finder: {
    title: string; intro: string;
    like: string; likes: Record<"DESERT" | "SEA" | "CULTURAL" | "SHOWS", string>;
    who: string; whos: Record<"family" | "couple" | "friends" | "solo", string>;
    time: string; times: Record<"short" | "half" | "full" | "any", string>;
    budget: string; budgets: Record<"25" | "50" | "100" | "any", string>;
    submit: string; reset: string; results: string; none: string; noneBody: string;
    reasons: { category: string; duration: string; budget: string; family: string; group: string };
  };
  consent: { title: string; body: string; accept: string; decline: string; more: string; analyticsCookies: string };
  error: { notFound: string; notFoundBody: string; generic: string; genericBody: string; home: string; tours: string };
  updated: string;
}

const en: InfoCopy = {
  contact: {
    title: "Contact us", intro: "Our team in Sharm El Sheikh answers questions before and after you book.",
    whatsapp: "WhatsApp", whatsappBody: "The fastest way to reach us — for questions, requests and pickup details.", whatsappCta: "Chat on WhatsApp",
    phone: "Phone", phoneBody: "Prefer to talk? Call our booking line.", call: "Call us",
    email: "Email", emailBody: "For longer questions or documents.", emailCta: "Send an email",
    office: "Our office", officeBody: "Office 238, Building 167, Delta Sharm, Sharm El Sheikh, South Sinai, Egypt.", map: "Open in Google Maps",
    follow: "Follow us", reviews: "Read what guests say", googleReviews: "Google reviews", tripadvisor: "Tripadvisor",
    booking: "Already booked?", bookingBody: "Look up your booking with your reference and phone number.",
  },
  about: {
    title: "About Safari Tours Sharm",
    intro: "Safari Tours Sharm is a local tour company in Sharm El Sheikh. We run desert safaris, Red Sea boat trips and journeys to Cairo, Luxor, Dahab and St Catherine — and you book directly with us.",
    sections: [
      { heading: "Book direct, with the local team", body: ["When you book here there is no middleman: you deal with the people who run your trip, and the price you see is the price you pay."] },
      { heading: "Clear prices", body: ["Every price on this site is final and in euros. Most tours are priced per person; a few are priced per unit — a buggy, a private boat or an airport car — and the tour page always says which."] },
      { heading: "Real availability, confirmed payment", body: ["Dates and seats come straight from our schedule. Your booking is confirmed only once your payment has been verified, and you can check it any time on the My booking page."] },
    ],
    cta: "Explore our tours",
  },
  faq: {
    title: "Frequently asked questions", intro: "Short answers to the questions guests ask us most.",
    items: [
      { title: "Do I need an account to book?", body: "No. You can book as a guest without creating an account." },
      { title: "How do I book?", body: "Choose a tour, a date, a departure time and the number of guests, enter your contact details and pay securely online. Your booking is confirmed once the payment is verified." },
      { title: "Are the dates shown really available?", body: "Yes. The site shows live availability from our schedule. Tours marked \"on request\" are confirmed by our team on WhatsApp before any payment." },
      { title: "Are prices per person or per group?", body: "Each tour shows how it is priced. Most are per person; some are per unit, such as a buggy, a private boat or a transfer vehicle." },
      { title: "When is my booking confirmed?", body: "As soon as our system has verified your payment — not just when a payment page opens. You can check the status on the My booking page." },
      { title: "What is the cancellation policy?", body: "Cancel at least 48 hours before the tour for a full refund, or 24–48 hours before for a 50% refund. Cancellations under 24 hours and no-shows are not refunded, unless the tour's own terms say otherwise." },
      { title: "Can I book a private trip?", body: "Some services, such as a private boat, are arranged on request. Message us on WhatsApp and we will tell you what is possible." },
    ],
    more: "Still have a question?",
  },
  terms: {
    title: "Terms & Conditions", updated: "Last updated",
    sections: [
      { heading: "Who we are", body: ["These terms apply to bookings made on this website with Safari Tours Sharm, a tour company in Sharm El Sheikh, Egypt."] },
      { heading: "Prices and payment", body: ["All prices are in euros (EUR) and are fixed when you book. Most tours are priced per person; some are priced per unit (for example a buggy, a private boat or a transfer vehicle), as shown on each tour page.", "Online payment is processed by Paymob. We never see or store your card details."] },
      { heading: "Your booking", body: ["A booking is confirmed only once your payment has been verified by our system. If the payment is not completed in time, the places held for you are released.", "Please give a phone number you can be reached on. We confirm the exact pickup time on WhatsApp; what is included in each tour, including hotel pickup, is shown on the tour page."] },
      { heading: "Cancellation by you", body: ["At least 48 hours before the tour: full refund.", "Between 24 and 48 hours before the tour: 50% refund.", "Less than 24 hours before the tour, or if you do not show up: no refund.", "Some tours may state different terms on their own page; those terms then apply."] },
      { heading: "Changes for safety and weather", body: ["Sea and desert trips depend on weather and safety conditions. If our team has to change or cancel a trip for safety reasons, we will contact you with the options available."] },
      { heading: "Contact", body: ["Questions about a booking? Contact us on WhatsApp or by email with your booking reference."] },
    ],
  },
  privacy: {
    title: "Privacy Policy", updated: "Last updated",
    sections: [
      { heading: "What we collect", body: ["To arrange your trip we collect your name, phone number, nationality, hotel and, if you give them, your email address, room number and special requests."] },
      { heading: "How we use it", body: ["Only to confirm and run your booking, arrange pickup, answer your questions and send booking messages. We do not sell your data."] },
      { heading: "Who else sees it", body: ["Our staff who organise your trip. Payment is handled by Paymob, which processes your card details on our behalf; we never see them. If you message us on WhatsApp, WhatsApp's own terms apply to that conversation."] },
      { heading: "Cookies", body: ["This site uses only the cookies it needs to work: one remembers your language and one your light/dark display choice. We do not use advertising or tracking cookies. If that ever changes, we will ask for your consent first."] },
      { heading: "How long we keep it", body: ["As long as needed to deliver your booking and to meet our accounting and legal obligations."] },
      { heading: "Your rights", body: ["You can ask us for a copy of your data, to correct it or to delete it. Contact us on WhatsApp or by email with your booking reference."] },
    ],
  },
  finder: {
    title: "Help me choose", intro: "Answer three quick questions and we will suggest tours that fit — with the reason for each.",
    like: "What do you enjoy?", likes: { DESERT: "Desert & adventure", SEA: "Sea & water", CULTURAL: "History & culture", SHOWS: "Shows & relaxing" },
    who: "Who is travelling?", whos: { family: "Family with children", couple: "A couple", friends: "Friends / group", solo: "Just me" },
    time: "How much time do you have?", times: { short: "A few hours", half: "Half a day", full: "A full day", any: "Flexible" },
    budget: "Budget per person", budgets: { "25": "Up to €25", "50": "Up to €50", "100": "Up to €100", any: "Any" },
    submit: "Show my suggestions", reset: "Start again", results: "Our suggestions for you", none: "Nothing matches all of that",
    noneBody: "Try a different time or budget — or tell us on WhatsApp what you are looking for.",
    reasons: { category: "matches what you enjoy", duration: "fits your time", budget: "within your budget", family: "short enough for children", group: "good for a group" },
  },
  consent: {
    title: "Can we measure visits?", body: "With your permission we use Google Analytics and Meta Pixel to see which pages and tours people use, so we can improve the site. Nothing is loaded unless you agree.",
    accept: "Allow", decline: "No thanks", more: "Privacy policy",
    analyticsCookies: "If you allow it, we also use Google Analytics and Meta Pixel cookies to understand how the site is used. They load only after you agree, and you can change your choice at any time from the link at the bottom of the page.",
  },
  error: {
    notFound: "Page not found", notFoundBody: "The page you are looking for does not exist or has moved.",
    generic: "Something went wrong", genericBody: "Please try again in a moment, or contact us on WhatsApp.",
    home: "Back to home", tours: "See all tours",
  },
  updated: "Last updated",
};

const ar: InfoCopy = {
  contact: {
    title: "تواصل معنا", intro: "فريقنا في شرم الشيخ يجيب عن أسئلتك قبل الحجز وبعده.",
    whatsapp: "واتساب", whatsappBody: "أسرع طريقة للتواصل — للأسئلة والطلبات وتفاصيل الاستلام.", whatsappCta: "راسلنا على واتساب",
    phone: "الهاتف", phoneBody: "تفضّل المكالمة؟ اتصل بخط الحجز.", call: "اتصل بنا",
    email: "البريد الإلكتروني", emailBody: "للأسئلة الطويلة أو المستندات.", emailCta: "أرسل بريدًا",
    office: "مكتبنا", officeBody: "مكتب ٢٣٨، مبنى ١٦٧، دلتا شرم، شرم الشيخ، جنوب سيناء، مصر.", map: "افتح في خرائط جوجل",
    follow: "تابعنا", reviews: "اقرأ آراء ضيوفنا", googleReviews: "تقييمات جوجل", tripadvisor: "تريب أدفايزر",
    booking: "حجزت بالفعل؟", bookingBody: "ابحث عن حجزك برقم الحجز ورقم الهاتف.",
  },
  about: {
    title: "عن سفاري تورز شرم",
    intro: "سفاري تورز شرم شركة رحلات محلية في شرم الشيخ. ننظّم رحلات السفاري في الصحراء، والرحلات البحرية في البحر الأحمر، ورحلات القاهرة والأقصر ودهب وسانت كاترين — وتحجز معنا مباشرة.",
    sections: [
      { heading: "احجز مباشرة مع الفريق المحلي", body: ["عندما تحجز هنا لا يوجد وسيط: تتعامل مع من ينظّمون رحلتك، والسعر الذي تراه هو ما تدفعه."] },
      { heading: "أسعار واضحة", body: ["كل الأسعار على الموقع نهائية وباليورو. معظم الرحلات بسعر للفرد، وبعضها بسعر للوحدة — باجي أو مركب خاص أو سيارة مطار — وصفحة الرحلة توضّح ذلك دائمًا."] },
      { heading: "أماكن متاحة فعلًا ودفع مؤكد", body: ["المواعيد والأماكن من جدولنا مباشرة. يتأكد حجزك فقط بعد التحقق من الدفع، ويمكنك متابعته في أي وقت من صفحة «حجزي»."] },
    ],
    cta: "استكشف رحلاتنا",
  },
  faq: {
    title: "الأسئلة الشائعة", intro: "إجابات قصيرة عن أكثر ما يسألنا عنه الضيوف.",
    items: [
      { title: "هل أحتاج حسابًا للحجز؟", body: "لا. يمكنك الحجز كضيف دون إنشاء حساب." },
      { title: "كيف أحجز؟", body: "اختر الرحلة والتاريخ والموعد وعدد الأفراد، وأدخل بيانات التواصل، ثم ادفع بأمان أونلاين. يتأكد حجزك بعد التحقق من الدفع." },
      { title: "هل المواعيد المعروضة متاحة فعلًا؟", body: "نعم. الموقع يعرض الأماكن المتاحة مباشرة من جدولنا. الرحلات «بالطلب» يؤكدها فريقنا على واتساب قبل أي دفع." },
      { title: "الأسعار للفرد أم للمجموعة؟", body: "كل رحلة توضّح طريقة تسعيرها. معظمها للفرد، وبعضها للوحدة مثل الباجي أو المركب الخاص أو سيارة النقل." },
      { title: "متى يتأكد حجزي؟", body: "بمجرد أن يتحقق نظامنا من الدفع — وليس بمجرد فتح صفحة الدفع. يمكنك متابعة الحالة من صفحة «حجزي»." },
      { title: "ما سياسة الإلغاء؟", body: "الإلغاء قبل الرحلة بـ48 ساعة على الأقل يعيد المبلغ كاملًا، وبين 24 و48 ساعة يعيد 50%. الإلغاء قبل أقل من 24 ساعة أو عدم الحضور بلا استرداد، ما لم تنص شروط الرحلة على غير ذلك." },
      { title: "هل يمكنني حجز رحلة خاصة؟", body: "بعض الخدمات مثل المركب الخاص تُرتَّب عند الطلب. راسلنا على واتساب ونخبرك بالمتاح." },
    ],
    more: "عندك سؤال آخر؟",
  },
  terms: {
    title: "الشروط والأحكام", updated: "آخر تحديث",
    sections: [
      { heading: "من نحن", body: ["تنطبق هذه الشروط على الحجوزات التي تتم عبر هذا الموقع مع سفاري تورز شرم، شركة رحلات في شرم الشيخ، مصر."] },
      { heading: "الأسعار والدفع", body: ["كل الأسعار باليورو وتُثبَّت لحظة الحجز. معظم الرحلات بسعر للفرد، وبعضها بسعر للوحدة (مثل الباجي أو المركب الخاص أو سيارة النقل) كما توضّح صفحة كل رحلة.", "يتم الدفع أونلاين عبر Paymob، ولا نطّلع على بيانات بطاقتك ولا نحتفظ بها."] },
      { heading: "حجزك", body: ["يتأكد الحجز فقط بعد أن يتحقق نظامنا من الدفع. إذا لم يكتمل الدفع في الوقت المحدد، تُتاح الأماكن المحجوزة لغيرك.", "من فضلك اكتب رقم هاتف يمكن الوصول إليك عليه. نؤكد موعد الاستلام بالضبط على واتساب، وما تشمله كل رحلة — ومنه الاستلام من الفندق — موضّح في صفحة الرحلة."] },
      { heading: "الإلغاء من طرفك", body: ["قبل الرحلة بـ48 ساعة على الأقل: استرداد كامل.", "بين 24 و48 ساعة قبل الرحلة: استرداد 50%.", "أقل من 24 ساعة قبل الرحلة أو عدم الحضور: لا يوجد استرداد.", "قد توضّح بعض الرحلات شروطًا مختلفة في صفحتها، وعندها تُطبَّق تلك الشروط."] },
      { heading: "التغيير بسبب السلامة أو الطقس", body: ["الرحلات البحرية والصحراوية تعتمد على الطقس وظروف السلامة. إذا اضطر فريقنا لتغيير رحلة أو إلغائها لأسباب تتعلق بالسلامة، سنتواصل معك بالخيارات المتاحة."] },
      { heading: "التواصل", body: ["لديك سؤال عن حجزك؟ تواصل معنا على واتساب أو بالبريد مع ذكر رقم الحجز."] },
    ],
  },
  privacy: {
    title: "سياسة الخصوصية", updated: "آخر تحديث",
    sections: [
      { heading: "ما الذي نجمعه", body: ["لترتيب رحلتك نجمع اسمك ورقم هاتفك وجنسيتك والفندق، وإن أضفتها: بريدك الإلكتروني ورقم الغرفة وطلباتك الخاصة."] },
      { heading: "كيف نستخدمه", body: ["فقط لتأكيد حجزك وتنفيذه وترتيب الاستلام والرد على أسئلتك وإرسال رسائل الحجز. لا نبيع بياناتك."] },
      { heading: "من يطّلع عليها أيضًا", body: ["موظفونا الذين ينظّمون رحلتك. يتولى Paymob معالجة الدفع وبيانات بطاقتك نيابةً عنا ولا نطّلع عليها. إذا راسلتنا على واتساب فتسري شروط واتساب على المحادثة."] },
      { heading: "ملفات تعريف الارتباط (الكوكيز)", body: ["يستخدم الموقع فقط الكوكيز اللازمة لعمله: واحدة تتذكر لغتك وأخرى تتذكر اختيارك للمظهر الفاتح أو الداكن. لا نستخدم كوكيز إعلانية أو تتبع، وإذا تغيّر ذلك سنطلب موافقتك أولًا."] },
      { heading: "مدة الاحتفاظ", body: ["طوال المدة اللازمة لتنفيذ حجزك والوفاء بالتزاماتنا المحاسبية والقانونية."] },
      { heading: "حقوقك", body: ["يمكنك أن تطلب نسخة من بياناتك أو تصحيحها أو حذفها. تواصل معنا على واتساب أو بالبريد مع ذكر رقم الحجز."] },
    ],
  },
  finder: {
    title: "ساعدني أختار", intro: "أجب عن ثلاثة أسئلة سريعة ونقترح عليك الرحلات المناسبة — مع سبب كل اقتراح.",
    like: "ماذا تحب؟", likes: { DESERT: "الصحراء والمغامرة", SEA: "البحر والأنشطة المائية", CULTURAL: "التاريخ والثقافة", SHOWS: "العروض والاسترخاء" },
    who: "من سيسافر؟", whos: { family: "عائلة مع أطفال", couple: "زوجان", friends: "أصدقاء / مجموعة", solo: "أنا فقط" },
    time: "كم من الوقت لديك؟", times: { short: "ساعات قليلة", half: "نصف يوم", full: "يوم كامل", any: "مرن" },
    budget: "الميزانية للفرد", budgets: { "25": "حتى €25", "50": "حتى €50", "100": "حتى €100", any: "أي ميزانية" },
    submit: "اعرض اقتراحاتي", reset: "ابدأ من جديد", results: "اقتراحاتنا لك", none: "لا توجد رحلة تطابق كل ذلك",
    noneBody: "جرّب وقتًا أو ميزانية مختلفة — أو أخبرنا على واتساب بما تبحث عنه.",
    reasons: { category: "تطابق ما تحب", duration: "تناسب وقتك", budget: "ضمن ميزانيتك", family: "قصيرة ومناسبة للأطفال", group: "مناسبة للمجموعات" },
  },
  consent: {
    title: "هل تسمح لنا بقياس الزيارات؟", body: "بإذنك نستخدم Google Analytics وMeta Pixel لمعرفة الصفحات والرحلات الأكثر استخدامًا لتحسين الموقع. لا يتم تحميل أي شيء دون موافقتك.",
    accept: "أسمح", decline: "لا، شكرًا", more: "سياسة الخصوصية",
    analyticsCookies: "إذا سمحت بذلك، نستخدم أيضًا كوكيز Google Analytics وMeta Pixel لفهم طريقة استخدام الموقع. لا تُحمَّل إلا بعد موافقتك، ويمكنك تغيير اختيارك في أي وقت من الرابط أسفل الصفحة.",
  },
  error: {
    notFound: "الصفحة غير موجودة", notFoundBody: "الصفحة التي تبحث عنها غير موجودة أو تم نقلها.",
    generic: "حدث خطأ", genericBody: "حاول مرة أخرى بعد قليل، أو تواصل معنا على واتساب.",
    home: "العودة للرئيسية", tours: "كل الرحلات",
  },
  updated: "آخر تحديث",
};

const ru: InfoCopy = {
  contact: {
    title: "Контакты", intro: "Наша команда в Шарм-эль-Шейхе ответит на вопросы до и после бронирования.",
    whatsapp: "WhatsApp", whatsappBody: "Самый быстрый способ связаться — вопросы, запросы и детали трансфера.", whatsappCta: "Написать в WhatsApp",
    phone: "Телефон", phoneBody: "Хотите поговорить? Позвоните на линию бронирования.", call: "Позвонить",
    email: "Эл. почта", emailBody: "Для длинных вопросов и документов.", emailCta: "Написать письмо",
    office: "Наш офис", officeBody: "Офис 238, здание 167, Дельта Шарм, Шарм-эль-Шейх, Южный Синай, Египет.", map: "Открыть в Google Картах",
    follow: "Мы в соцсетях", reviews: "Отзывы гостей", googleReviews: "Отзывы в Google", tripadvisor: "Tripadvisor",
    booking: "Уже забронировали?", bookingBody: "Найдите бронирование по номеру и телефону.",
  },
  about: {
    title: "О Safari Tours Sharm",
    intro: "Safari Tours Sharm — местная туристическая компания в Шарм-эль-Шейхе. Мы организуем сафари в пустыне, морские прогулки по Красному морю и поездки в Каир, Луксор, Дахаб и Святую Екатерину — и вы бронируете напрямую у нас.",
    sections: [
      { heading: "Бронируйте напрямую у местной команды", body: ["Здесь нет посредников: вы общаетесь с теми, кто проводит вашу поездку, а цена на сайте — окончательная."] },
      { heading: "Понятные цены", body: ["Все цены на сайте окончательные и в евро. Большинство туров — за человека; некоторые — за единицу (багги, частный катер или машина в аэропорт), и это всегда указано на странице тура."] },
      { heading: "Реальные места и подтверждённая оплата", body: ["Даты и места берутся прямо из нашего расписания. Бронирование подтверждается только после проверки оплаты; проверить его можно на странице «Моё бронирование»."] },
    ],
    cta: "Смотреть туры",
  },
  faq: {
    title: "Частые вопросы", intro: "Короткие ответы на самые частые вопросы гостей.",
    items: [
      { title: "Нужен ли аккаунт для бронирования?", body: "Нет. Можно бронировать как гость без регистрации." },
      { title: "Как забронировать?", body: "Выберите тур, дату, время и количество гостей, укажите контакты и оплатите онлайн. Бронирование подтверждается после проверки оплаты." },
      { title: "Показанные даты действительно свободны?", body: "Да. Сайт показывает реальную доступность из нашего расписания. Туры «по запросу» наша команда подтверждает в WhatsApp до оплаты." },
      { title: "Цены за человека или за группу?", body: "На каждом туре указан способ расчёта. Большинство — за человека; некоторые — за единицу: багги, частный катер или машину для трансфера." },
      { title: "Когда бронирование подтверждено?", body: "Как только наша система проверила оплату — а не когда просто открылась страница оплаты. Статус можно посмотреть на странице «Моё бронирование»." },
      { title: "Какие условия отмены?", body: "При отмене не позднее чем за 48 часов — полный возврат, за 24–48 часов — 50%. При отмене менее чем за 24 часа и неявке деньги не возвращаются, если в условиях тура не сказано иное." },
      { title: "Можно ли заказать частную поездку?", body: "Некоторые услуги, например частный катер, организуются по запросу. Напишите нам в WhatsApp — расскажем, что возможно." },
    ],
    more: "Остались вопросы?",
  },
  terms: {
    title: "Условия", updated: "Обновлено",
    sections: [
      { heading: "Кто мы", body: ["Эти условия действуют для бронирований на этом сайте у Safari Tours Sharm — туристической компании в Шарм-эль-Шейхе, Египет."] },
      { heading: "Цены и оплата", body: ["Все цены в евро (EUR) и фиксируются при бронировании. Большинство туров — за человека; некоторые — за единицу (например, багги, частный катер или машина для трансфера), как указано на странице тура.", "Онлайн-оплату обрабатывает Paymob. Мы не видим и не храним данные вашей карты."] },
      { heading: "Ваше бронирование", body: ["Бронирование подтверждается только после проверки оплаты нашей системой. Если оплата не завершена вовремя, удерживаемые места освобождаются.", "Укажите телефон, по которому с вами можно связаться. Точное время трансфера мы подтверждаем в WhatsApp; что входит в тур, включая трансфер из отеля, указано на странице тура."] },
      { heading: "Отмена с вашей стороны", body: ["Не позднее чем за 48 часов до тура: полный возврат.", "За 24–48 часов до тура: возврат 50%.", "Менее чем за 24 часа или неявка: без возврата.", "У некоторых туров на их странице могут быть иные условия; тогда действуют они."] },
      { heading: "Изменения из-за безопасности и погоды", body: ["Морские и пустынные поездки зависят от погоды и условий безопасности. Если наша команда должна изменить или отменить поездку по соображениям безопасности, мы свяжемся с вами и предложим доступные варианты."] },
      { heading: "Контакты", body: ["Вопросы по бронированию? Напишите нам в WhatsApp или на почту, указав номер бронирования."] },
    ],
  },
  privacy: {
    title: "Политика конфиденциальности", updated: "Обновлено",
    sections: [
      { heading: "Что мы собираем", body: ["Для организации поездки мы собираем имя, телефон, гражданство, отель и, если вы их указали, эл. почту, номер комнаты и пожелания."] },
      { heading: "Как мы это используем", body: ["Только чтобы подтвердить и провести бронирование, организовать трансфер, ответить на вопросы и отправить сообщения о бронировании. Мы не продаём ваши данные."] },
      { heading: "Кто ещё их видит", body: ["Наши сотрудники, которые организуют поездку. Оплату обрабатывает Paymob — он обрабатывает данные карты от нашего имени, мы их не видим. Если вы пишете нам в WhatsApp, к переписке применяются условия WhatsApp."] },
      { heading: "Cookies", body: ["Сайт использует только необходимые cookies: одна запоминает язык, другая — светлую или тёмную тему. Мы не используем рекламные и отслеживающие cookies; если это изменится, сначала спросим ваше согласие."] },
      { heading: "Как долго мы храним данные", body: ["Столько, сколько нужно для бронирования и выполнения бухгалтерских и юридических обязательств."] },
      { heading: "Ваши права", body: ["Вы можете запросить копию данных, их исправление или удаление. Напишите нам в WhatsApp или на почту с номером бронирования."] },
    ],
  },
  finder: {
    title: "Помогите выбрать", intro: "Ответьте на три вопроса — и мы предложим подходящие туры с объяснением.",
    like: "Что вам нравится?", likes: { DESERT: "Пустыня и приключения", SEA: "Море и вода", CULTURAL: "История и культура", SHOWS: "Шоу и отдых" },
    who: "Кто едет?", whos: { family: "Семья с детьми", couple: "Пара", friends: "Друзья / группа", solo: "Я один" },
    time: "Сколько у вас времени?", times: { short: "Несколько часов", half: "Полдня", full: "Целый день", any: "Неважно" },
    budget: "Бюджет на человека", budgets: { "25": "До €25", "50": "До €50", "100": "До €100", any: "Любой" },
    submit: "Показать варианты", reset: "Начать заново", results: "Наши предложения", none: "Ничего не подходит под всё сразу",
    noneBody: "Попробуйте другое время или бюджет — или напишите нам в WhatsApp, что вы ищете.",
    reasons: { category: "то, что вам нравится", duration: "подходит по времени", budget: "в рамках бюджета", family: "недолго, подходит детям", group: "хорошо для группы" },
  },
  consent: {
    title: "Можно учитывать посещения?", body: "С вашего разрешения мы используем Google Analytics и Meta Pixel, чтобы понимать, какие страницы и туры смотрят, и улучшать сайт. Без вашего согласия ничего не загружается.",
    accept: "Разрешить", decline: "Нет, спасибо", more: "Политика конфиденциальности",
    analyticsCookies: "Если вы разрешите, мы также используем cookies Google Analytics и Meta Pixel, чтобы понимать, как используется сайт. Они загружаются только после согласия; изменить выбор можно в любой момент по ссылке внизу страницы.",
  },
  error: {
    notFound: "Страница не найдена", notFoundBody: "Страница не существует или была перемещена.",
    generic: "Что-то пошло не так", genericBody: "Попробуйте ещё раз чуть позже или напишите нам в WhatsApp.",
    home: "На главную", tours: "Все туры",
  },
  updated: "Обновлено",
};

const it: InfoCopy = {
  contact: {
    title: "Contatti", intro: "Il nostro team a Sharm El Sheikh risponde prima e dopo la prenotazione.",
    whatsapp: "WhatsApp", whatsappBody: "Il modo più veloce per contattarci — domande, richieste e dettagli del prelievo.", whatsappCta: "Scrivici su WhatsApp",
    phone: "Telefono", phoneBody: "Preferisci parlare? Chiama la nostra linea prenotazioni.", call: "Chiamaci",
    email: "Email", emailBody: "Per domande lunghe o documenti.", emailCta: "Invia un'email",
    office: "Il nostro ufficio", officeBody: "Ufficio 238, edificio 167, Delta Sharm, Sharm El Sheikh, Sinai del Sud, Egitto.", map: "Apri in Google Maps",
    follow: "Seguici", reviews: "Cosa dicono gli ospiti", googleReviews: "Recensioni Google", tripadvisor: "Tripadvisor",
    booking: "Hai già prenotato?", bookingBody: "Cerca la prenotazione con codice e telefono.",
  },
  about: {
    title: "Chi è Safari Tours Sharm",
    intro: "Safari Tours Sharm è un'agenzia locale di Sharm El Sheikh. Organizziamo safari nel deserto, gite in barca sul Mar Rosso e viaggi al Cairo, a Luxor, Dahab e Santa Caterina — e prenoti direttamente con noi.",
    sections: [
      { heading: "Prenota direttamente con il team locale", body: ["Nessun intermediario: tratti con chi organizza la tua escursione, e il prezzo che vedi è quello che paghi."] },
      { heading: "Prezzi chiari", body: ["Tutti i prezzi sono finali e in euro. Quasi tutte le escursioni sono a persona; alcune sono a unità — un buggy, una barca privata o un'auto per l'aeroporto — e la pagina dell'escursione lo indica sempre."] },
      { heading: "Disponibilità reale, pagamento confermato", body: ["Date e posti arrivano dal nostro calendario. La prenotazione è confermata solo dopo la verifica del pagamento, e puoi controllarla quando vuoi in «La mia prenotazione»."] },
    ],
    cta: "Scopri le escursioni",
  },
  faq: {
    title: "Domande frequenti", intro: "Risposte brevi alle domande più comuni.",
    items: [
      { title: "Serve un account per prenotare?", body: "No. Puoi prenotare come ospite senza registrarti." },
      { title: "Come prenoto?", body: "Scegli escursione, data, orario e numero di persone, inserisci i contatti e paga online in sicurezza. La prenotazione è confermata dopo la verifica del pagamento." },
      { title: "Le date mostrate sono davvero disponibili?", body: "Sì. Il sito mostra la disponibilità reale del nostro calendario. Le escursioni «su richiesta» vengono confermate dal nostro team su WhatsApp prima del pagamento." },
      { title: "I prezzi sono a persona o a gruppo?", body: "Ogni escursione indica come è calcolato il prezzo. Quasi tutte sono a persona; alcune sono a unità, come un buggy, una barca privata o un veicolo per il transfer." },
      { title: "Quando è confermata la prenotazione?", body: "Appena il nostro sistema ha verificato il pagamento — non solo quando si apre la pagina di pagamento. Puoi controllare lo stato in «La mia prenotazione»." },
      { title: "Qual è la politica di cancellazione?", body: "Rimborso totale se cancelli almeno 48 ore prima, 50% tra 24 e 48 ore. Nessun rimborso per cancellazioni con meno di 24 ore o mancata presentazione, salvo diversa indicazione nelle condizioni dell'escursione." },
      { title: "Posso prenotare un'escursione privata?", body: "Alcuni servizi, come la barca privata, si organizzano su richiesta. Scrivici su WhatsApp e ti diremo cosa è possibile." },
    ],
    more: "Hai ancora una domanda?",
  },
  terms: {
    title: "Termini e condizioni", updated: "Ultimo aggiornamento",
    sections: [
      { heading: "Chi siamo", body: ["Questi termini valgono per le prenotazioni fatte su questo sito con Safari Tours Sharm, agenzia di escursioni a Sharm El Sheikh, Egitto."] },
      { heading: "Prezzi e pagamento", body: ["Tutti i prezzi sono in euro (EUR) e sono fissati al momento della prenotazione. Quasi tutte le escursioni sono a persona; alcune sono a unità (per esempio un buggy, una barca privata o un veicolo per il transfer), come indicato in ogni pagina.", "Il pagamento online è gestito da Paymob. Non vediamo né conserviamo i dati della tua carta."] },
      { heading: "La tua prenotazione", body: ["La prenotazione è confermata solo dopo la verifica del pagamento da parte del nostro sistema. Se il pagamento non viene completato in tempo, i posti riservati vengono liberati.", "Indica un numero di telefono su cui possiamo raggiungerti. Confermiamo l'orario di prelievo su WhatsApp; cosa include ogni escursione, compreso il prelievo in hotel, è indicato nella sua pagina."] },
      { heading: "Cancellazione da parte tua", body: ["Almeno 48 ore prima: rimborso totale.", "Tra 24 e 48 ore prima: rimborso del 50%.", "Meno di 24 ore prima o mancata presentazione: nessun rimborso.", "Alcune escursioni possono indicare condizioni diverse nella propria pagina; in quel caso valgono quelle."] },
      { heading: "Modifiche per sicurezza e meteo", body: ["Le escursioni in mare e nel deserto dipendono dal meteo e dalle condizioni di sicurezza. Se il nostro team deve modificare o annullare un'escursione per motivi di sicurezza, ti contatteremo con le opzioni disponibili."] },
      { heading: "Contatti", body: ["Domande su una prenotazione? Scrivici su WhatsApp o via email indicando il codice di prenotazione."] },
    ],
  },
  privacy: {
    title: "Informativa sulla privacy", updated: "Ultimo aggiornamento",
    sections: [
      { heading: "Cosa raccogliamo", body: ["Per organizzare l'escursione raccogliamo nome, telefono, nazionalità, hotel e, se li indichi, email, numero di camera e richieste speciali."] },
      { heading: "Come li usiamo", body: ["Solo per confermare e gestire la prenotazione, organizzare il prelievo, rispondere alle domande e inviare messaggi sulla prenotazione. Non vendiamo i tuoi dati."] },
      { heading: "Chi altro li vede", body: ["Il nostro personale che organizza l'escursione. Il pagamento è gestito da Paymob, che tratta i dati della carta per nostro conto: noi non li vediamo. Se ci scrivi su WhatsApp, alla conversazione si applicano i termini di WhatsApp."] },
      { heading: "Cookie", body: ["Il sito usa solo i cookie necessari: uno ricorda la lingua e uno la scelta del tema chiaro o scuro. Non usiamo cookie pubblicitari o di tracciamento; se cambierà, chiederemo prima il tuo consenso."] },
      { heading: "Per quanto tempo", body: ["Per il tempo necessario a gestire la prenotazione e a rispettare gli obblighi contabili e di legge."] },
      { heading: "I tuoi diritti", body: ["Puoi chiederci una copia dei tuoi dati, correggerli o cancellarli. Scrivici su WhatsApp o via email con il codice di prenotazione."] },
    ],
  },
  finder: {
    title: "Aiutami a scegliere", intro: "Rispondi a tre domande e ti suggeriamo le escursioni adatte, con il motivo di ognuna.",
    like: "Cosa ti piace?", likes: { DESERT: "Deserto e avventura", SEA: "Mare e acqua", CULTURAL: "Storia e cultura", SHOWS: "Spettacoli e relax" },
    who: "Chi viaggia?", whos: { family: "Famiglia con bambini", couple: "Coppia", friends: "Amici / gruppo", solo: "Solo io" },
    time: "Quanto tempo hai?", times: { short: "Qualche ora", half: "Mezza giornata", full: "Una giornata", any: "Flessibile" },
    budget: "Budget a persona", budgets: { "25": "Fino a €25", "50": "Fino a €50", "100": "Fino a €100", any: "Qualsiasi" },
    submit: "Mostra i suggerimenti", reset: "Ricomincia", results: "I nostri suggerimenti", none: "Nessuna escursione corrisponde a tutto",
    noneBody: "Prova un altro tempo o budget — oppure scrivici su WhatsApp cosa cerchi.",
    reasons: { category: "in linea con i tuoi gusti", duration: "adatta al tuo tempo", budget: "nel tuo budget", family: "breve, adatta ai bambini", group: "ideale per gruppi" },
  },
  consent: {
    title: "Possiamo misurare le visite?", body: "Con il tuo permesso usiamo Google Analytics e Meta Pixel per capire quali pagine ed escursioni vengono usate e migliorare il sito. Senza il tuo consenso non viene caricato nulla.",
    accept: "Consenti", decline: "No, grazie", more: "Informativa sulla privacy",
    analyticsCookies: "Se lo consenti, usiamo anche i cookie di Google Analytics e Meta Pixel per capire come viene usato il sito. Vengono caricati solo dopo il tuo consenso e puoi cambiare scelta in qualsiasi momento dal link in fondo alla pagina.",
  },
  error: {
    notFound: "Pagina non trovata", notFoundBody: "La pagina che cerchi non esiste o è stata spostata.",
    generic: "Qualcosa è andato storto", genericBody: "Riprova tra poco, oppure scrivici su WhatsApp.",
    home: "Torna alla home", tours: "Tutte le escursioni",
  },
  updated: "Ultimo aggiornamento",
};

export const infoCopy: Record<StsLocale, InfoCopy> = { en, ar, ru, it };

/** Date the legal texts were last changed (shown on the pages). */
export const LEGAL_UPDATED = "2026-10-01";
