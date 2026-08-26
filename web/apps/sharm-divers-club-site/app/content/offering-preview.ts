export interface OfferingPreviewCopy {
  switchLanguage: string;
  back: string;
  prototype: string;
  prototypeNotice: string;
  category: string;
  name: string;
  audience: string;
  pricedHeading: string;
  pricedBody: string;
  pendingHeading: string;
  pendingBody: string;
  showPriced: string;
  showPending: string;
  illustrativeLabel: string;
  whatsapp: string;
  sourceNote: string;
}

export const offeringPreviewCopy: Record<"en" | "ar", OfferingPreviewCopy> = {
  en: {
    switchLanguage: "العربية",
    back: "Back to discover",
    prototype: "Offering detail — design evidence",
    prototypeNotice: "This page is design evidence for one real catalog entry. No price shown here is approved for publication yet.",
    category: "PADI course",
    name: "PADI Open Water Diver",
    audience: "For first-time certification, no experience required",
    pricedHeading: "Illustrative price shown",
    pricedBody: "This state shows the internal price list amount, visibly marked as illustrative — never as a final, bookable rate.",
    pendingHeading: "Price hidden until approved",
    pendingBody: "This state hides the amount entirely and asks the visitor to confirm current pricing directly with the team.",
    showPriced: "Show illustrative price",
    showPending: "Hide price (pending state)",
    illustrativeLabel: "Illustrative price — not yet approved for publication",
    whatsapp: "Confirm current price on WhatsApp",
    sourceNote: "Source: internal price list, code PC04 — owner review required before this may be shown as a real price.",
  },
  ar: {
    switchLanguage: "English",
    back: "العودة للاستكشاف",
    prototype: "تفاصيل الخدمة — نموذج تصميم",
    prototypeNotice: "الصفحة دي نموذج تصميم لعنصر حقيقي واحد من الكتالوج. مفيش سعر هنا معتمد للنشر لسه.",
    category: "دورة PADI",
    name: "PADI Open Water Diver",
    audience: "للحصول على الشهادة لأول مرة، بدون خبرة سابقة",
    pricedHeading: "عرض السعر الاسترشادي",
    pricedBody: "الحالة دي بتعرض قيمة قائمة الأسعار الداخلية، موضح إنه استرشادي فقط — مش سعر نهائي قابل للحجز.",
    pendingHeading: "السعر مخفي لحد الموافقة",
    pendingBody: "الحالة دي بتخفي القيمة تمامًا وتطلب من الزائر يتأكد من السعر الحالي مباشرة مع الفريق.",
    showPriced: "اعرض السعر الاسترشادي",
    showPending: "اخفِ السعر (حالة الانتظار)",
    illustrativeLabel: "سعر استرشادي — لسه مش معتمد للنشر",
    whatsapp: "تأكد من السعر الحالي على واتساب",
    sourceNote: "المصدر: قائمة الأسعار الداخلية، كود PC04 — يحتاج مراجعة المالك قبل عرضه كسعر حقيقي.",
  },
};

export const offeringPreviewPrice = { amount: 350, currency: "EUR" };
