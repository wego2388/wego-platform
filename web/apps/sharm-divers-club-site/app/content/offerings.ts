import type { SdcLocale } from "./locales";

export type CategoryId = "shore-diving" | "boat-diving" | "multi-day" | "signature" | "world-class" | "padi-courses" | "water-sports";
export type Audience = "beginner" | "certified-diver" | "qualified-certified-diver" | "beginner-course" | "professional-track" | "general" | "private-group";

export interface OfferingSummary {
  code: string;
  categoryId: CategoryId;
  name: Record<SdcLocale, string>;
  audience: Audience;
  durationMinutes: number | null;
  diveCount: number | null;
  priceEur: number;
}

/**
 * Sourced verbatim from data/catalog.dive-core.v1.json — status "approved",
 * publishable:true as of 2026-08-26 (decision GOV-003, governance/DECISION_LOG.md).
 * Diving + water sports, per the owner's explicit scope decisions (2026-08-27:
 * "diving only for now"; 2026-08-28: add water sports). The catalog's other
 * newer non-diving categories (desert safari, sightseeing, snorkeling,
 * transfers) remain deliberately excluded until scope expands further.
 */
export const offerings: OfferingSummary[] = [
  { code: "SD02", categoryId: "shore-diving", name: { en: "Intro Dive — 30 minutes", ar: "غطسة تجريبية من الشاطئ — 30 دقيقة" }, audience: "beginner", durationMinutes: 30, diveCount: 1, priceEur: 50 },
  { code: "SD05", categoryId: "shore-diving", name: { en: "Two guided shore dives", ar: "غطستا شاطئ للغواص المعتمد" }, audience: "certified-diver", durationMinutes: null, diveCount: 2, priceEur: 75 },
  { code: "BD02", categoryId: "boat-diving", name: { en: "Ras Mohammed beginner dive — 30 minutes", ar: "رأس محمد للمبتدئ — 30 دقيقة" }, audience: "beginner", durationMinutes: 30, diveCount: 1, priceEur: 60 },
  { code: "BD08", categoryId: "boat-diving", name: { en: "Tiran — two certified dives", ar: "تيران — غطستان للمعتمد" }, audience: "certified-diver", durationMinutes: null, diveCount: 2, priceEur: 120 },
  { code: "MP01", categoryId: "multi-day", name: { en: "3 days / 6 shore dives", ar: "3 أيام / 6 غطسات شاطئ" }, audience: "certified-diver", durationMinutes: null, diveCount: 6, priceEur: 199 },
  { code: "MP04", categoryId: "multi-day", name: { en: "7 days / 14 dives", ar: "7 أيام / 14 غطسة" }, audience: "certified-diver", durationMinutes: null, diveCount: 14, priceEur: 590 },
  { code: "HP01", categoryId: "signature", name: { en: "Red Sea Icons — 3 days / 6 dives", ar: "أيقونات البحر الأحمر — 3 أيام / 6 غطسات" }, audience: "certified-diver", durationMinutes: null, diveCount: 6, priceEur: 430 },
  { code: "HP03", categoryId: "signature", name: { en: "Red Sea Icons — 5 days / 10 dives", ar: "أيقونات البحر الأحمر — 5 أيام / 10 غطسات" }, audience: "certified-diver", durationMinutes: null, diveCount: 10, priceEur: 575 },
  { code: "WC01", categoryId: "world-class", name: { en: "SS Thistlegorm — two dives", ar: "حطام SS Thistlegorm — غطستان" }, audience: "qualified-certified-diver", durationMinutes: null, diveCount: 2, priceEur: 225 },
  { code: "WC02", categoryId: "world-class", name: { en: "Dahab Blue Hole & Canyon — two dives", ar: "دهب Blue Hole وCanyon — غطستان" }, audience: "qualified-certified-diver", durationMinutes: null, diveCount: 2, priceEur: 205 },
  { code: "PC04", categoryId: "padi-courses", name: { en: "PADI Open Water Diver", ar: "PADI Open Water Diver" }, audience: "beginner-course", durationMinutes: null, diveCount: 4, priceEur: 350 },
  { code: "PC11", categoryId: "padi-courses", name: { en: "PADI Divemaster", ar: "PADI Divemaster" }, audience: "professional-track", durationMinutes: null, diveCount: null, priceEur: 1499 },
  { code: "WS01", categoryId: "water-sports", name: { en: "Parasailing", ar: "باراسيلينج" }, audience: "general", durationMinutes: null, diveCount: null, priceEur: 30 },
  { code: "WS02", categoryId: "water-sports", name: { en: "Banana Boat", ar: "بانانا بوت" }, audience: "general", durationMinutes: null, diveCount: null, priceEur: 35 },
  { code: "WS03", categoryId: "water-sports", name: { en: "Tube Ride", ar: "تيوب رايد" }, audience: "general", durationMinutes: null, diveCount: null, priceEur: 30 },
  { code: "WS04", categoryId: "water-sports", name: { en: "Private Speed Boat", ar: "سبيد بوت خاص" }, audience: "private-group", durationMinutes: null, diveCount: null, priceEur: 60 },
  { code: "WS05", categoryId: "water-sports", name: { en: "Glass Bottom Boat", ar: "قارب زجاجي" }, audience: "general", durationMinutes: null, diveCount: null, priceEur: 20 },
  { code: "WS06", categoryId: "water-sports", name: { en: "Submarine Tour", ar: "رحلة الغواصة" }, audience: "general", durationMinutes: null, diveCount: null, priceEur: 40 },
];

const audienceLabels: Record<Audience, Record<SdcLocale, string>> = {
  beginner: { en: "For first-timers", ar: "للمبتدئين" },
  "certified-diver": { en: "For certified divers", ar: "للغواصين المعتمدين" },
  "qualified-certified-diver": { en: "For qualified certified divers", ar: "للغواصين المعتمدين المؤهلين" },
  "beginner-course": { en: "No experience required", ar: "بدون خبرة سابقة" },
  "professional-track": { en: "Professional track", ar: "مسار احترافي" },
  general: { en: "For everyone", ar: "للجميع" },
  "private-group": { en: "Private group", ar: "مجموعة خاصة" },
};

export function audienceLabel(locale: SdcLocale, audience: Audience): string {
  return audienceLabels[audience][locale];
}

export function durationLabel(locale: SdcLocale, minutes: number | null): string | null {
  if (minutes === null) return null;
  return locale === "ar" ? `${minutes} دقيقة` : `${minutes} minutes`;
}

export function diveCountLabel(locale: SdcLocale, count: number | null): string | null {
  if (count === null) return null;
  if (count === 1) return locale === "ar" ? "غطسة واحدة" : "1 dive";
  return locale === "ar" ? `${count} غطسات` : `${count} dives`;
}

export function formatEur(amount: number): string {
  return `€${amount}`;
}

const categoryIcons: Record<CategoryId, string> = {
  "shore-diving": "M2 15c2-2 4-2 6 0s4 2 6 0 4-2 6 0 M2 10c2-2 4-2 6 0s4 2 6 0 4-2 6 0",
  "boat-diving": "M4 15h16l-2 5H6l-2-5Zm8-11v11M8 8l4-4 4 4",
  "multi-day": "M4 5h16v15H4V5Zm0 5h16M8 3v4M16 3v4",
  signature: "M12 3l2.2 6.8H21l-5.6 4.2L17.6 21 12 16.8 6.4 21l2.2-7-5.6-4.2h6.8L12 3Z",
  "world-class": "M12 3v4m0 14v-4m-9-7h4m10 0h4M6.3 6.3l2.8 2.8m5.8 5.8 2.8 2.8m0-11.4-2.8 2.8m-5.8 5.8-2.8 2.8M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z",
  "padi-courses": "M12 3 3 7l9 4 9-4-9-4Zm-6 6v6l6 3 6-3V9M6 13v4M18 13v4",
  "water-sports": "M3 18c1.5-1.5 3-1.5 4.5 0s3 1.5 4.5 0 3-1.5 4.5 0 3-1.5 4.5 0M12 3v9m0 0-3-3m3 3 3-3",
};

export function categoryIcon(categoryId: CategoryId): string {
  return categoryIcons[categoryId];
}
