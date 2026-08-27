export interface OfferingPreviewCopy {
  switchLanguage: string;
  back: string;
  eyebrow: string;
  notice: string;
  category: string;
  name: string;
  audience: string;
  diveCount: string;
  priceLabel: string;
  priceNote: string;
  sourceNote: string;
  whatsapp: string;
}

export const offeringPreviewCopy: Record<"en" | "ar", OfferingPreviewCopy> = {
  en: {
    switchLanguage: "العربية",
    back: "Back to discover",
    eyebrow: "Offering detail page — format example",
    notice: "This route shows the full detail-page format for one real catalog entry. The price is real and approved; exact dates and availability are always confirmed on WhatsApp before booking.",
    category: "PADI course",
    name: "PADI Open Water Diver",
    audience: "For first-time certification, no experience required",
    diveCount: "4 dives",
    priceLabel: "Price",
    priceNote: "Approved 2026 price — final date and availability confirmed on WhatsApp.",
    sourceNote: "Source: catalog.dive-core.v1.json, code PC04 — approved and publishable (decision GOV-003, 2026-08-26).",
    whatsapp: "Send an inquiry on WhatsApp",
  },
  ar: {
    switchLanguage: "English",
    back: "العودة للاستكشاف",
    eyebrow: "صفحة تفاصيل الخدمة — نموذج للشكل",
    notice: "الصفحة دي بتوضح شكل صفحة التفاصيل الكاملة لعنصر حقيقي واحد من الكتالوج. السعر حقيقي ومعتمد؛ الموعد الدقيق والتوفر دايمًا بيتأكدوا على واتساب قبل الحجز.",
    category: "دورة PADI",
    name: "PADI Open Water Diver",
    audience: "للحصول على الشهادة لأول مرة، بدون خبرة سابقة",
    diveCount: "4 غطسات",
    priceLabel: "السعر",
    priceNote: "سعر 2026 معتمد — الموعد النهائي والتوفر بيتأكدوا على واتساب.",
    sourceNote: "المصدر: catalog.dive-core.v1.json، كود PC04 — معتمد ومنشور (قرار GOV-003، بتاريخ 2026-08-26).",
    whatsapp: "ابعت طلبك على واتساب",
  },
};

export const offeringPreviewPrice = { amount: 350, currency: "EUR" };
export const offeringPreviewCode = "PC04";
