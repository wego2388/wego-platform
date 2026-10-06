/**
 * Company facts printed on every office document. Source: the owner's data
 * hub, sheet «بيانات الشركة» (confirmed by the owner 2026-10-01). Only facts
 * present there are used. Deliberately absent: a tourism-ministry licence (the
 * owner states none exists) and a commercial-register number (still missing),
 * so neither is ever printed or implied.
 */
export const COMPANY = {
  brand: { en: "Safari Tours Sharm", ar: "سفاري تورز شرم" },
  /** The owner supplied the legal name in Arabic only; it is printed as given in both languages. */
  legalName: "شركة إبراهيم السيد عبد السلام علي وشريكه (شركة تضامن)",
  taxId: "779-150-155",
  address: {
    en: "Office 238, Building 167, Delta Sharm, Sharm El Sheikh, South Sinai, Egypt",
    ar: "شرم الشيخ، دلتا شرم، مبنى ١٦٧، مكتب ٢٣٨",
  },
  phone: "+20 111 129 2690",
  email: "safaritourssharm@gmail.com",
  supportHours: { en: "24 hours a day", ar: "٢٤ ساعة يوميًا" },
  supportLanguages: { en: "Arabic, English, Russian", ar: "العربية والإنجليزية والروسية" },
} as const;
