/**
 * Merges the booking lifecycle history and the payment status history into
 * one chronological timeline for the booking detail page. Pure and
 * side-effect free so ordering and labels are unit-testable.
 */
import type { BookingHistoryEntry, PaymentHistoryEntry } from "@wego/api-contract";
import { erpMessage, type ErpLocale, type ErpMessageKey } from "../utils/erpLocale";

export interface TimelineItem {
  key: string;
  occurredAt: string;
  source: "booking" | "payment";
  title: string;
  detail: string | null;
  actor: string | null;
  /** False for payment steps reconstructed from pre-history state. */
  recorded: boolean;
}

const BOOKING_TITLES: Record<BookingHistoryEntry["eventType"], ErpMessageKey> = {
  BOOKING_CREATED: "timeline.BOOKING_CREATED",
  BOOKING_CREATED_OFFICE: "office.timeline.created",
  BOOKING_CONFIRMED: "timeline.BOOKING_CONFIRMED",
  BOOKING_CANCELLED: "timeline.BOOKING_CANCELLED",
  BOOKING_COMPLETED: "timeline.BOOKING_COMPLETED",
  BOOKING_EXPIRED: "timeline.BOOKING_EXPIRED",
};

const PAYMENT_TITLES: Record<PaymentHistoryEntry["toStatus"], ErpMessageKey> = {
  PENDING: "timeline.PENDING",
  PAID: "timeline.PAID",
  FAILED: "timeline.FAILED",
  REFUNDED: "timeline.REFUNDED",
  REVIEW_REQUIRED: "timeline.REVIEW_REQUIRED",
  RECONCILIATION_REQUIRED: "timeline.RECONCILIATION_REQUIRED",
};

/**
 * Who caused a payment step. Only provider callbacks are Paymob's; starting a
 * payment is the customer's, and expiry/reconciliation are Wego's own jobs.
 */
export function paymentActor(entry: PaymentHistoryEntry, locale: ErpLocale = "en"): string {
  if (entry.toStatus === "PENDING") return erpMessage(locale, "timeline.customer");
  if (entry.toStatus === "RECONCILIATION_REQUIRED") return erpMessage(locale, "timeline.system");
  if (entry.toStatus === "FAILED" && entry.providerStatus === "EXPIRED") return erpMessage(locale, "timeline.system");
  return "Paymob";
}

export function mergeTimeline(
  bookingHistory: BookingHistoryEntry[],
  paymentHistory: PaymentHistoryEntry[],
  locale: ErpLocale = "en",
): TimelineItem[] {
  const items: TimelineItem[] = [
    ...bookingHistory.map((entry, index) => ({
      key: `booking-${index}`,
      occurredAt: entry.occurredAt,
      source: "booking" as const,
      title: erpMessage(locale, BOOKING_TITLES[entry.eventType]),
      detail: entry.reason ?? null,
      // Customer and system actions have no staff actor.
      actor: entry.actorEmail ?? erpMessage(locale, entry.eventType === "BOOKING_CREATED" ? "timeline.customer" : "timeline.system"),
      recorded: true,
    })),
    ...paymentHistory.map((entry, index) => ({
      key: `payment-${index}`,
      occurredAt: entry.occurredAt,
      source: "payment" as const,
      title: erpMessage(locale, PAYMENT_TITLES[entry.toStatus]),
      detail: entry.providerStatus ? erpMessage(locale, "timeline.providerStatus", { status: entry.providerStatus }) : null,
      actor: paymentActor(entry, locale),
      recorded: entry.recorded,
    })),
  ];
  // Stable sort keeps each source's own order for identical instants.
  return items.sort((left, right) => Date.parse(left.occurredAt) - Date.parse(right.occurredAt));
}
