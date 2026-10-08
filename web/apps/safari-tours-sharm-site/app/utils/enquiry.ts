import type { PublicSalesStatus, TimeSlot, Tour } from "@wego/api-contract";
import { enquiryCopy } from "../content/enquiry";
import { whatsappUrl, type StsLocale } from "../content/locales";

export function readSalesCapability(value: unknown): PublicSalesStatus | null {
  if (!value || typeof value !== "object") return null;
  const status = value as Partial<PublicSalesStatus>;
  if ((status.bookingMode !== "ONLINE_PAYMENT" && status.bookingMode !== "ENQUIRY_ONLY") || typeof status.bookingsOpen !== "boolean" || typeof status.paymentsOpen !== "boolean") return null;
  if (status.bookingMode === "ENQUIRY_ONLY" && (status.bookingsOpen || status.paymentsOpen)) return null;
  return { bookingMode: status.bookingMode, bookingsOpen: status.bookingsOpen, paymentsOpen: status.paymentsOpen };
}

/** Unknown/mismatched capability fails closed; never infer online mode. */
export function onlineSalesAvailable(status: PublicSalesStatus | null | undefined): boolean {
  return status?.bookingMode === "ONLINE_PAYMENT" && status.bookingsOpen === true && status.paymentsOpen === true;
}

/** A preferred date is a request, not a capacity claim or a scheduled slot. */
export function validPreferredDate(value: string | null | undefined, today: string): boolean {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value) || value < today) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

/** Explicit allow-list. Never pass the checkout form, customer, booking or route.query here. */
export function tripEnquiryUrl(locale: StsLocale, tour: Tour | null, selection: {
  date?: string | null; timeSlot?: TimeSlot | null; adults: number; children: number; optionCode?: string | null; units?: number | null;
}): string {
  const c = enquiryCopy[locale];
  const lines = [c.message];
  if (tour) {
    // Stable catalog slug, not arbitrary visitor text or a customer identifier.
    if (/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(tour.slug)) lines.push(`${c.tour}: ${tour.slug}`);
    if (selection.date && /^\d{4}-\d{2}-\d{2}$/.test(selection.date)) lines.push(`${c.date}: ${selection.date}`);
    if (selection.timeSlot && tour.availableTimeSlots.includes(selection.timeSlot)) lines.push(`${c.time}: ${selection.timeSlot}`);
    for (const [label, value] of [[c.adults, selection.adults], [c.children, selection.children]] as const) {
      if (Number.isSafeInteger(value) && value >= 0 && value <= 99) lines.push(`${label}: ${value}`);
    }
    const option = tour.priceBasis === "PER_UNIT" ? tour.priceOptions?.find((o) => o.code === selection.optionCode) : null;
    if (option && /^[a-z0-9_-]+$/i.test(option.code) && selection.units && Number.isSafeInteger(selection.units) && selection.units <= 50 && selection.units > 0) {
      lines.push(`${c.option}: ${option.code}`, `${c.units}: ${selection.units}`);
    }
  }
  return `${whatsappUrl}?${new URLSearchParams({ text: lines.join("\n") })}`;
}
