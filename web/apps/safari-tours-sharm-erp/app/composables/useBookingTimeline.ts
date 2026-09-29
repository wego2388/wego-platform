/**
 * Merges the booking lifecycle history and the payment status history into
 * one chronological timeline for the booking detail page. Pure and
 * side-effect free so ordering and labels are unit-testable.
 */
import type { BookingHistoryEntry, PaymentHistoryEntry } from "@wego/api-contract";

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

const BOOKING_TITLES: Record<BookingHistoryEntry["eventType"], string> = {
  BOOKING_CREATED: "Booking created",
  BOOKING_CONFIRMED: "Booking confirmed",
  BOOKING_CANCELLED: "Booking cancelled",
  BOOKING_COMPLETED: "Booking completed",
  BOOKING_EXPIRED: "Booking expired",
};

const PAYMENT_TITLES: Record<PaymentHistoryEntry["toStatus"], string> = {
  PENDING: "Payment started",
  PAID: "Payment captured",
  FAILED: "Payment failed",
  REFUNDED: "Payment refunded",
  REVIEW_REQUIRED: "Payment held for review",
  RECONCILIATION_REQUIRED: "Payment needs reconciliation",
};

/**
 * Who caused a payment step. Only provider callbacks are Paymob's; starting a
 * payment is the customer's, and expiry/reconciliation are Wego's own jobs.
 */
export function paymentActor(entry: PaymentHistoryEntry): string {
  if (entry.toStatus === "PENDING") return "Customer";
  if (entry.toStatus === "RECONCILIATION_REQUIRED") return "System";
  if (entry.toStatus === "FAILED" && entry.providerStatus === "EXPIRED") return "System";
  return "Paymob";
}

export function mergeTimeline(
  bookingHistory: BookingHistoryEntry[],
  paymentHistory: PaymentHistoryEntry[],
): TimelineItem[] {
  const items: TimelineItem[] = [
    ...bookingHistory.map((entry, index) => ({
      key: `booking-${index}`,
      occurredAt: entry.occurredAt,
      source: "booking" as const,
      title: BOOKING_TITLES[entry.eventType],
      detail: entry.reason ?? null,
      // Customer and system actions have no staff actor.
      actor: entry.actorEmail ?? (entry.eventType === "BOOKING_CREATED" ? "Customer" : "System"),
      recorded: true,
    })),
    ...paymentHistory.map((entry, index) => ({
      key: `payment-${index}`,
      occurredAt: entry.occurredAt,
      source: "payment" as const,
      title: PAYMENT_TITLES[entry.toStatus],
      detail: entry.providerStatus ? `Provider status: ${entry.providerStatus}` : null,
      actor: paymentActor(entry),
      recorded: entry.recorded,
    })),
  ];
  // Stable sort keeps each source's own order for identical instants.
  return items.sort((left, right) => Date.parse(left.occurredAt) - Date.parse(right.occurredAt));
}
