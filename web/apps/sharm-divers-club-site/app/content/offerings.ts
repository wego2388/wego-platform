import type { SdcLocale } from "./locales";

export type CategoryId = "shore-diving" | "boat-diving" | "multi-day" | "signature" | "world-class" | "padi-courses";
export type Audience = "beginner" | "certified-diver" | "qualified-certified-diver" | "beginner-course" | "professional-track";

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
 * Diving-only for now, per the owner's explicit scope decision; the catalog's
 * newer non-diving categories (water sports, desert safari, sightseeing,
 * snorkeling, transfers) are deliberately excluded until scope expands.
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
];

const audienceLabels: Record<Audience, Record<SdcLocale, string>> = {
  beginner: { en: "For first-timers", ar: "للمبتدئين" },
  "certified-diver": { en: "For certified divers", ar: "للغواصين المعتمدين" },
  "qualified-certified-diver": { en: "For qualified certified divers", ar: "للغواصين المعتمدين المؤهلين" },
  "beginner-course": { en: "No experience required", ar: "بدون خبرة سابقة" },
  "professional-track": { en: "Professional track", ar: "مسار احترافي" },
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
