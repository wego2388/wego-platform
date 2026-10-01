import type { TimeSlot, TourSlot } from "@wego/api-contract";

/**
 * Calendar helpers for the booking card. Dates are plain ISO calendar days
 * (YYYY-MM-DD) in Sharm El Sheikh time, never Date objects, so a visitor in
 * another time zone sees the same days the operator scheduled.
 */
export const OPERATOR_TIME_ZONE = "Africa/Cairo";
export const BOOKING_WINDOW_DAYS = 60;
const SLOT_ORDER: TimeSlot[] = ["SUNRISE", "MORNING", "AFTERNOON", "SUNSET"];

/** Today's date where the tours run. */
export function operatorToday(now: Date = new Date()): string {
  // en-CA formats as YYYY-MM-DD.
  return new Intl.DateTimeFormat("en-CA", { timeZone: OPERATOR_TIME_ZONE, year: "numeric", month: "2-digit", day: "2-digit" }).format(now);
}

export function addDays(iso: string, days: number): string {
  const [y, m, d] = iso.split("-").map(Number) as [number, number, number];
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

export function monthOf(iso: string): string {
  return iso.slice(0, 7);
}

export function addMonths(month: string, delta: number): string {
  const [y, m] = month.split("-").map(Number) as [number, number];
  const date = new Date(Date.UTC(y, m - 1 + delta, 1));
  return date.toISOString().slice(0, 7);
}

/**
 * Weeks of a month as ISO days (null = padding), starting on the given
 * weekday (0 = Sunday, 1 = Monday, 6 = Saturday).
 */
export function monthGrid(month: string, firstDayOfWeek: number): (string | null)[][] {
  const [y, m] = month.split("-").map(Number) as [number, number];
  const first = new Date(Date.UTC(y, m - 1, 1));
  const daysInMonth = new Date(Date.UTC(y, m, 0)).getUTCDate();
  const lead = (first.getUTCDay() - firstDayOfWeek + 7) % 7;
  const cells: (string | null)[] = Array.from({ length: lead }, () => null);
  for (let day = 1; day <= daysInMonth; day++) cells.push(`${month}-${String(day).padStart(2, "0")}`);
  while (cells.length % 7) cells.push(null);
  const weeks: (string | null)[][] = [];
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7));
  return weeks;
}

/**
 * Bookable slots grouped by day: not blocked, not in the past, and with at
 * least [minPlaces] places left (one guest, or one whole unit for per-unit tours).
 */
export function bookableByDay(slots: TourSlot[], today: string, minPlaces = 1): Map<string, TourSlot[]> {
  const byDay = new Map<string, TourSlot[]>();
  for (const slot of slots) {
    if (slot.isBlocked || slot.available < minPlaces || slot.date < today) continue;
    const list = byDay.get(slot.date) ?? [];
    list.push(slot);
    byDay.set(slot.date, list);
  }
  for (const list of byDay.values()) list.sort((a, b) => SLOT_ORDER.indexOf(a.timeSlot) - SLOT_ORDER.indexOf(b.timeSlot));
  return byDay;
}

/** Locale week start for the calendar (Arabic/Egypt: Saturday; Russian/Italian: Monday; English: Sunday). */
export function firstDayOfWeek(locale: string): number {
  if (locale === "ar") return 6;
  if (locale === "ru" || locale === "it") return 1;
  return 0;
}
