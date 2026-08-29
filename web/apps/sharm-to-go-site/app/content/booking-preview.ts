import type { SharmLocale } from "./locales";

export type BookingStep = "options" | "details" | "payment" | "complete";
export type PaymentMethodId = "card" | "wallet" | "fawry" | "cash";

export interface PreviewDate {
  id: string;
  day: Record<SharmLocale, string>;
  number: number;
  priceDelta: number;
  available: boolean;
}

export const previewDates: PreviewDate[] = [
  { id: "2026-08-26", day: { en: "Wed", ar: "الأربعاء" }, number: 26, priceDelta: -100, available: true },
  { id: "2026-08-27", day: { en: "Thu", ar: "الخميس" }, number: 27, priceDelta: 0, available: true },
  { id: "2026-08-28", day: { en: "Fri", ar: "الجمعة" }, number: 28, priceDelta: 150, available: true },
  { id: "2026-08-29", day: { en: "Sat", ar: "السبت" }, number: 29, priceDelta: 150, available: true },
  { id: "2026-08-30", day: { en: "Sun", ar: "الأحد" }, number: 30, priceDelta: 0, available: false },
  { id: "2026-08-31", day: { en: "Mon", ar: "الاثنين" }, number: 31, priceDelta: 0, available: true },
  { id: "2026-09-01", day: { en: "Tue", ar: "الثلاثاء" }, number: 1, priceDelta: 0, available: true },
];

export const previewPricing = Object.freeze({ adult: 1450, child: 850, addOn: 350 });

interface BookingPreviewCopy {
  switchLanguage: string;
  back: string;
  prototype: string;
  prototypeNotice: string;
  title: string;
  subtitle: string;
  exampleService: string;
  stepLabels: Record<BookingStep, string>;
  dateHeading: string;
  month: string;
  unavailable: string;
  from: string;
  guideHeading: string;
  guideOptions: Array<{ id: string; label: string }>;
  timeHeading: string;
  times: string[];
  guestsHeading: string;
  adults: string;
  children: string;
  decrease: string;
  increase: string;
  addOnHeading: string;
  addOnName: string;
  addOnBody: string;
  customerHeading: string;
  customerBody: string;
  fullName: string;
  email: string;
  phone: string;
  hotel: string;
  optional: string;
  requiredError: string;
  paymentHeading: string;
  paymentBody: string;
  paymentMethods: Array<{ id: PaymentMethodId; name: string; detail: string }>;
  providerNote: string;
  policy: string;
  summaryHeading: string;
  selectedDate: string;
  selectedTime: string;
  selectedLanguage: string;
  adultsLine: string;
  childrenLine: string;
  addOnLine: string;
  total: string;
  sampleAmount: string;
  addToCart: string;
  cartNotice: string;
  continue: string;
  continueToPayment: string;
  finishPrototype: string;
  previous: string;
  secureNote: string;
  completeHeading: string;
  completeBody: string;
  restart: string;
}

export const bookingPreviewCopy: Record<SharmLocale, BookingPreviewCopy> = {
  en: {
    switchLanguage: "العربية",
    back: "Back to experiences",
    prototype: "Booking design prototype",
    prototypeNotice: "Interaction sample only — no live service, availability, booking or payment is created.",
    title: "A clear booking flow from date to payment",
    subtitle: "Try the approved interaction pattern using visibly fictional design data.",
    exampleService: "Example Red Sea day experience",
    stepLabels: { options: "Options", details: "Your details", payment: "Payment", complete: "Result" },
    dateHeading: "Pick your date",
    month: "August / September 2026",
    unavailable: "Unavailable",
    from: "Example from",
    guideHeading: "Guide language",
    guideOptions: [
      { id: "english", label: "English" },
      { id: "arabic", label: "Arabic" },
      { id: "italian", label: "Italian" },
      { id: "russian", label: "Russian" },
    ],
    timeHeading: "Start time",
    times: ["08:30", "10:30", "14:00"],
    guestsHeading: "Who is going?",
    adults: "Adults",
    children: "Children (4–11)",
    decrease: "Decrease",
    increase: "Increase",
    addOnHeading: "Optional add-on",
    addOnName: "Private hotel pickup",
    addOnBody: "Example fixed add-on for the whole booking.",
    customerHeading: "Customer and pickup details",
    customerBody: "Only the minimum contact fields are represented in this prototype.",
    fullName: "Full name",
    email: "Email",
    phone: "Phone / WhatsApp",
    hotel: "Hotel or pickup point",
    optional: "optional",
    requiredError: "Add a name and phone number to continue this design flow.",
    paymentHeading: "Choose how you would pay",
    paymentBody: "Methods shown here are planned. Live methods depend on approved merchant accounts and service policy.",
    paymentMethods: [
      { id: "card", name: "Card", detail: "Hosted Paymob checkout; direct CIB gateway can be evaluated later." },
      { id: "wallet", name: "Mobile wallet", detail: "An enabled Egyptian wallet through the payment provider." },
      { id: "fawry", name: "Fawry reference", detail: "Receive a reference code and pay before its expiry." },
      { id: "cash", name: "Pay on arrival", detail: "Only when the selected service explicitly allows it." },
    ],
    providerNote: "CIB is the proposed settlement account, subject to signed merchant terms. It is not customer card storage.",
    policy: "I reviewed the example cancellation and confirmation wording.",
    summaryHeading: "Booking summary",
    selectedDate: "Date",
    selectedTime: "Time",
    selectedLanguage: "Language",
    adultsLine: "Adults",
    childrenLine: "Children",
    addOnLine: "Private pickup",
    total: "Example total",
    sampleAmount: "Design sample amount — not a public price",
    addToCart: "Add to cart · design only",
    cartNotice: "The interaction was demonstrated. Nothing was saved to a real cart.",
    continue: "Continue to details",
    continueToPayment: "Review payment methods",
    finishPrototype: "Complete design preview",
    previous: "Previous step",
    secureNote: "A live checkout will use provider-hosted secure payment fields. Sharm To Go will not store card numbers or CVVs.",
    completeHeading: "The design flow is complete",
    completeBody: "No booking or payment was created. The production result will show a verified booking reference and payment state.",
    restart: "Start the preview again",
  },
  ar: {
    switchLanguage: "English",
    back: "العودة للتجارب",
    prototype: "نموذج تصميم الحجز",
    prototypeNotice: "تجربة تفاعلية فقط — لا توجد خدمة أو مواعيد أو حجز أو عملية دفع فعلية.",
    title: "رحلة حجز واضحة من اختيار التاريخ حتى الدفع",
    subtitle: "جرّب شكل الاستخدام المعتمد ببيانات تصميم تجريبية موضحة بوضوح.",
    exampleService: "مثال لتجربة يوم في البحر الأحمر",
    stepLabels: { options: "الاختيارات", details: "بياناتك", payment: "الدفع", complete: "النتيجة" },
    dateHeading: "اختار التاريخ",
    month: "أغسطس / سبتمبر 2026",
    unavailable: "غير متاح",
    from: "مثال يبدأ من",
    guideHeading: "لغة المرشد",
    guideOptions: [
      { id: "english", label: "الإنجليزية" },
      { id: "arabic", label: "العربية" },
      { id: "italian", label: "الإيطالية" },
      { id: "russian", label: "الروسية" },
    ],
    timeHeading: "موعد البداية",
    times: ["08:30", "10:30", "14:00"],
    guestsHeading: "مين معاك؟",
    adults: "البالغون",
    children: "الأطفال (٤–١١)",
    decrease: "تقليل",
    increase: "زيادة",
    addOnHeading: "إضافة اختيارية",
    addOnName: "استلام خاص من الفندق",
    addOnBody: "مثال لإضافة ثابتة على الحجز بالكامل.",
    customerHeading: "بيانات العميل والاستلام",
    customerBody: "النموذج يعرض أقل قدر مطلوب من بيانات التواصل.",
    fullName: "الاسم بالكامل",
    email: "البريد الإلكتروني",
    phone: "الهاتف / واتساب",
    hotel: "الفندق أو نقطة الاستلام",
    optional: "اختياري",
    requiredError: "أدخل الاسم ورقم الهاتف لإكمال تجربة التصميم.",
    paymentHeading: "اختار طريقة الدفع",
    paymentBody: "الوسائل المعروضة مخططة فقط. التفعيل الفعلي يعتمد على حسابات التاجر وسياسة الخدمة.",
    paymentMethods: [
      { id: "card", name: "بطاقة بنكية", detail: "صفحة Paymob الآمنة، ويمكن تقييم بوابة CIB المباشرة لاحقًا." },
      { id: "wallet", name: "محفظة موبايل", detail: "محفظة مصرية مفعلة من خلال مقدم الدفع." },
      { id: "fawry", name: "كود فوري", detail: "تحصل على كود مرجعي وتدفع قبل انتهاء صلاحيته." },
      { id: "cash", name: "الدفع عند الوصول", detail: "فقط إذا كانت الخدمة المختارة تسمح بذلك." },
    ],
    providerNote: "حساب CIB مقترح لاستلام التسويات حسب عقد التاجر، وليس لتخزين بيانات بطاقة العميل.",
    policy: "راجعت مثال شروط الإلغاء والتأكيد.",
    summaryHeading: "ملخص الحجز",
    selectedDate: "التاريخ",
    selectedTime: "الوقت",
    selectedLanguage: "اللغة",
    adultsLine: "البالغون",
    childrenLine: "الأطفال",
    addOnLine: "الاستلام الخاص",
    total: "الإجمالي التجريبي",
    sampleAmount: "مبلغ لتجربة التصميم — ليس سعرًا منشورًا",
    addToCart: "أضف للسلة · تصميم فقط",
    cartNotice: "تم عرض التفاعل فقط، ولم يُحفظ شيء في سلة فعلية.",
    continue: "متابعة للبيانات",
    continueToPayment: "مراجعة وسائل الدفع",
    finishPrototype: "إنهاء تجربة التصميم",
    previous: "الخطوة السابقة",
    secureNote: "الدفع الفعلي سيستخدم حقولًا آمنة تابعة لمقدم الدفع. لن يخزن Sharm To Go أرقام البطاقات أو رمز CVV.",
    completeHeading: "اكتملت تجربة التصميم",
    completeBody: "لم يتم إنشاء حجز أو دفع. النتيجة الفعلية ستعرض رقم حجز وحالة دفع تم التحقق منهما.",
    restart: "ابدأ التجربة من جديد",
  },
};

export function formatPreviewMoney(locale: SharmLocale, amount: number): string {
  return new Intl.NumberFormat(locale === "ar" ? "ar-EG" : "en-EG", {
    style: "currency",
    currency: "EGP",
    maximumFractionDigits: 0,
  }).format(amount);
}
