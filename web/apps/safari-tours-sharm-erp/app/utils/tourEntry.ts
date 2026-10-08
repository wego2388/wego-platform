import type { CreateTourPayload, TourCategory, TimeSlot } from "../composables/useToursApi";
import { asciiDigits } from "./calendarDate";

export const ENTRY_TIMES: TimeSlot[] = ["SUNRISE", "MORNING", "AFTERNOON", "SUNSET"];
export const ENTRY_CATEGORIES: TourCategory[] = ["DESERT", "SEA", "CULTURAL", "SHOWS", "TRANSFERS"];
export type TourEntry = { name: string; slug: string; category: TourCategory; duration: string; adult: string; child: string; capacity: string; times: TimeSlot[]; order: string; type: "TOUR" | "TRANSFER" | "REQUEST_ONLY"; pricingNote: string; confirmed: boolean };

/** Decimal → exact minor units; never parseFloat/round a business price. */
export function entryPrice(value: string): number | null {
  const text = asciiDigits(value.trim()).replace("٫", ".");
  if (!/^\d+(?:\.\d{1,2})?$/.test(text)) return null;
  const [whole, fraction = ""] = text.split(".");
  const cents = BigInt(whole!) * 100n + BigInt(fraction.padEnd(2, "0"));
  return cents <= BigInt(Number.MAX_SAFE_INTEGER) ? Number(cents) : null;
}

export function tourEntryPayload(form: TourEntry): CreateTourPayload | null {
  const adult = entryPrice(form.adult); const child = form.child.trim() ? entryPrice(form.child) : null;
  if (!form.confirmed || !form.name.trim() || form.name.trim().length > 200 || !/^[a-z0-9][a-z0-9-]{1,78}[a-z0-9]$/.test(form.slug.trim()) || !form.duration.trim() || form.duration.trim().length > 80 || !ENTRY_CATEGORIES.includes(form.category) || adult === null || (form.child.trim() && child === null) || !/^\d+$/.test(form.capacity) || Number(form.capacity) < 1 || Number(form.capacity) > 1000 || !/^\d+$/.test(form.order) || Number(form.order) > 2147483647 || !form.times.length || !form.times.every((time) => ENTRY_TIMES.includes(time)) || !["TOUR", "TRANSFER", "REQUEST_ONLY"].includes(form.type) || form.pricingNote.trim().length > 500) return null;
  return {
    slug: form.slug.trim(), nameEn: form.name.trim(), category: form.category, durationText: form.duration.trim(),
    priceAdultCents: adult, priceChildCents: child, capacity: Number(form.capacity), availableTimeSlots: [...new Set(form.times)],
    sortOrder: Number(form.order), tourType: form.type, cancellationPolicy: "STANDARD", pricingNote: form.pricingNote.trim() || null,
  };
}
