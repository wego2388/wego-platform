/**
 * Tour durations are stored as English catalogue text ("2–3 hours",
 * "About 4 hours (18:00–22:00)", "2–3 days"). These helpers read the leading
 * number range so the site can filter by length and show the length in the
 * visitor's language. Text they cannot read is left alone.
 */
export type DurationUnit = "minutes" | "hours" | "days";
export type DurationBucket = "short" | "half" | "full" | "multi";

export interface ParsedDuration {
  min: number;
  max: number;
  unit: DurationUnit;
  approximate: boolean;
}

const PATTERN = /^\s*(about\s+)?(\d+(?:\.\d+)?)(?:\s*[–-]\s*(\d+(?:\.\d+)?))?\s*(minutes?|mins?|hours?|hrs?|days?)\b/i;

export function parseDuration(text: string | null | undefined): ParsedDuration | null {
  const match = text ? PATTERN.exec(text) : null;
  if (!match) return null;
  const min = Number(match[2]);
  const max = match[3] ? Number(match[3]) : min;
  const raw = match[4]!.toLowerCase();
  const unit: DurationUnit = raw.startsWith("m") ? "minutes" : raw.startsWith("d") ? "days" : "hours";
  return { min, max, unit, approximate: Boolean(match[1]) };
}

/** Longest stated length in hours, or null when unknown. */
export function durationHours(text: string | null | undefined): number | null {
  const parsed = parseDuration(text);
  if (!parsed) return null;
  if (parsed.unit === "minutes") return parsed.max / 60;
  if (parsed.unit === "days") return parsed.max * 24;
  return parsed.max;
}

export function durationBucket(text: string | null | undefined): DurationBucket | null {
  const parsed = parseDuration(text);
  if (!parsed) return null;
  if (parsed.unit === "days" && parsed.max >= 2) return "multi";
  const hours = durationHours(text)!;
  if (hours <= 3) return "short";
  if (hours <= 6) return "half";
  return "full";
}

const UNITS: Record<string, Record<DurationUnit, string>> = {
  en: { minutes: "min", hours: "hours", days: "days" },
  ar: { minutes: "دقيقة", hours: "ساعات", days: "أيام" },
  ru: { minutes: "мин", hours: "ч", days: "дн." },
  it: { minutes: "min", hours: "ore", days: "giorni" },
};
const ABOUT: Record<string, string> = { en: "About", ar: "حوالي", ru: "Около", it: "Circa" };

/**
 * The duration in the visitor's language. English keeps the full catalogue
 * text (it may carry details such as the time window); other languages get
 * the number range with a translated unit.
 */
export function formatDuration(text: string, locale: string): string {
  if (locale === "en") return text;
  const parsed = parseDuration(text);
  const units = UNITS[locale];
  if (!parsed || !units) return text;
  const numbers = new Intl.NumberFormat(locale === "ar" ? "ar-EG" : locale, { maximumFractionDigits: 1 });
  const range = parsed.min === parsed.max ? numbers.format(parsed.min) : `${numbers.format(parsed.min)}–${numbers.format(parsed.max)}`;
  const value = `${range} ${units[parsed.unit]}`;
  return parsed.approximate ? `${ABOUT[locale]} ${value}` : value;
}
