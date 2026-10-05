/**
 * Finance aggregation for the Safari Tours Sharm ERP.
 *
 * Financial truth comes from immutable payment activity, never from a booking
 * status. A sale is recognised on revenueRecognisedAt and its refund on
 * refundedAt, which keeps cross-period refunds accurate. A capture that was
 * never recognised (held for review) is never revenue, even once refunded, so
 * a later refund cannot restate a closed period. A captured payment moved to
 * REVIEW_REQUIRED retains its recognition timestamp until reconciliation.
 */
import {
  divideMoney,
  formatMoney,
  minorUnitsToMoney,
  moneyToMinorUnits,
} from "@wego/api-contract";
import type { Booking, Money, PaymentLedgerEntry, Tour } from "@wego/api-contract";

const REPORTING_TIME_ZONE = "Africa/Cairo";

export interface SignedMoney {
  amount: string;
  currencyCode: string;
}

export interface PaymentLedgerEvent {
  paymentId: string;
  bookingId: string;
  tourId: string;
  adultsCount: number;
  childrenCount: number;
  kind: "PAID" | "REFUNDED";
  occurredAt: string;
  amountMinorUnits: bigint;
  currencyCode: string;
}

export interface RevenueSummary {
  grossPaid: Money;
  refunded: Money;
  netRevenue: SignedMoney;
  paidCount: number;
  refundedCount: number;
  totalPaymentCount: number;
  paxTotal: number;
  averagePaidBooking: Money | null;
}

export interface RevenueByTourRow {
  tourId: string;
  tourSlug: string | null;
  paidCount: number;
  refundedCount: number;
  grossPaid: Money;
  refunded: Money;
  netRevenue: SignedMoney;
  sharePercent: number;
}

export interface BookingStatusCount {
  status: string;
  count: number;
}

export interface PaymentStatusCount {
  status: PaymentLedgerEntry["status"];
  count: number;
}

export interface DailyRevenueRow {
  date: string;
  paidCount: number;
  refundedCount: number;
  netRevenue: SignedMoney;
}

function dateInReportingZone(instant: string): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: REPORTING_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(instant));
}

function isInDateRange(instant: string, from: string, to: string): boolean {
  const date = dateInReportingZone(instant);
  return date >= from && date <= to;
}

/**
 * Only recognised events are checked, so a stray pending or review row in
 * another currency cannot break the report. Call it from the loader so a real
 * mix surfaces as a load error, not as a render crash.
 */
export function assertSingleCurrency(events: PaymentLedgerEvent[]): string {
  const currencies = new Set(events.map((event) => event.currencyCode));
  if (currencies.size > 1) throw new Error("Finance report cannot mix currencies");
  return currencies.values().next().value ?? "EUR";
}

/** Keyset pages never overlap, but the report must still count each payment once. */
export function uniqueByPaymentId(payments: PaymentLedgerEntry[]): PaymentLedgerEntry[] {
  return Array.from(new Map(payments.map((payment) => [payment.paymentId, payment])).values());
}

function signedMoney(minorUnits: bigint, currencyCode: string): SignedMoney {
  const sign = minorUnits < 0n ? "-" : "";
  const absolute = minorUnits < 0n ? -minorUnits : minorUnits;
  const whole = absolute / 100n;
  const fraction = (absolute % 100n).toString().padStart(2, "0");
  return { amount: `${sign}${whole}.${fraction}`, currencyCode };
}

function signedMoneyToMinorUnits(money: SignedMoney): bigint {
  const negative = money.amount.startsWith("-");
  const unsigned = negative ? money.amount.slice(1) : money.amount;
  const [whole, fraction] = unsigned.split(".") as [string, string];
  const value = BigInt(whole) * 100n + BigInt(fraction);
  return negative ? -value : value;
}

export function formatSignedMoney(money: SignedMoney): string {
  const negative = money.amount.startsWith("-");
  const unsigned = negative ? money.amount.slice(1) : money.amount;
  const formatted = formatMoney({ amount: unsigned, currencyCode: money.currencyCode });
  return negative ? `-${formatted}` : formatted;
}

/** Operational booking-date filter. It is deliberately separate from finance. */
export function filterByDateRange(bookings: Booking[], from: string, to: string): Booking[] {
  return bookings.filter((booking) => booking.tourDate >= from && booking.tourDate <= to);
}

/**
 * Converts ledger rows to financial events in the requested Cairo-local range.
 * Only captures with revenueRecognisedAt produce events; an unresolved review
 * without that timestamp, reconciliation-required rows, and refunds of
 * never-recognised captures do not.
 */
export function paymentLedgerEvents(
  payments: PaymentLedgerEntry[],
  from: string,
  to: string,
): PaymentLedgerEvent[] {
  const events: PaymentLedgerEvent[] = [];
  for (const payment of uniqueByPaymentId(payments)) {
    const recognisedAt = payment.revenueRecognisedAt;
    if (!recognisedAt) continue;
    if (isInDateRange(recognisedAt, from, to)) {
      events.push({
        paymentId: payment.paymentId,
        bookingId: payment.bookingId,
        tourId: payment.tourId,
        adultsCount: payment.adultsCount,
        childrenCount: payment.childrenCount,
        kind: "PAID",
        occurredAt: recognisedAt,
        amountMinorUnits: moneyToMinorUnits(payment.amount),
        currencyCode: payment.amount.currencyCode,
      });
    }
    if (
      payment.status === "REFUNDED"
      && payment.refundedAt
      && isInDateRange(payment.refundedAt, from, to)
    ) {
      events.push({
        paymentId: payment.paymentId,
        bookingId: payment.bookingId,
        tourId: payment.tourId,
        adultsCount: payment.adultsCount,
        childrenCount: payment.childrenCount,
        kind: "REFUNDED",
        occurredAt: payment.refundedAt,
        amountMinorUnits: -moneyToMinorUnits(payment.amount),
        currencyCode: payment.amount.currencyCode,
      });
    }
  }
  return events;
}

export function computeRevenueSummary(
  payments: PaymentLedgerEntry[],
  from: string,
  to: string,
): RevenueSummary {
  const events = paymentLedgerEvents(payments, from, to);
  const currencyCode = assertSingleCurrency(events);
  const paidEvents = events.filter((event) => event.kind === "PAID");
  const refundEvents = events.filter((event) => event.kind === "REFUNDED");
  const grossMinor = paidEvents.reduce((sum, event) => sum + event.amountMinorUnits, 0n);
  const refundMinor = refundEvents.reduce((sum, event) => sum - event.amountMinorUnits, 0n);
  // Net pax mirrors net revenue: a refund removes its travellers in the
  // period the refund happened, so a cross-period refund can make it negative.
  const pax = (event: PaymentLedgerEvent) => event.adultsCount + event.childrenCount;
  const paxTotal = paidEvents.reduce((sum, event) => sum + pax(event), 0)
    - refundEvents.reduce((sum, event) => sum + pax(event), 0);
  const grossPaid = minorUnitsToMoney(grossMinor, currencyCode);

  return {
    grossPaid,
    refunded: minorUnitsToMoney(refundMinor, currencyCode),
    netRevenue: signedMoney(grossMinor - refundMinor, currencyCode),
    paidCount: paidEvents.length,
    refundedCount: refundEvents.length,
    totalPaymentCount: uniqueByPaymentId(payments).length,
    paxTotal,
    averagePaidBooking: paidEvents.length > 0 ? divideMoney(grossPaid, paidEvents.length) : null,
  };
}

export function computeRevenueByTour(
  payments: PaymentLedgerEntry[],
  tours: Tour[],
  from: string,
  to: string,
): RevenueByTourRow[] {
  const events = paymentLedgerEvents(payments, from, to);
  const currencyCode = assertSingleCurrency(events);
  const tourMap = new Map(tours.map((tour) => [tour.id, tour]));
  const rows = new Map<string, { paid: bigint; refunded: bigint; paidCount: number; refundedCount: number }>();

  for (const event of events) {
    const tourId = event.tourId;
    const row = rows.get(tourId) ?? { paid: 0n, refunded: 0n, paidCount: 0, refundedCount: 0 };
    if (event.kind === "PAID") {
      row.paid += event.amountMinorUnits;
      row.paidCount++;
    } else {
      row.refunded -= event.amountMinorUnits;
      row.refundedCount++;
    }
    rows.set(tourId, row);
  }

  const totalGross = Array.from(rows.values()).reduce((sum, row) => sum + row.paid, 0n);
  return Array.from(rows.entries())
    .map(([tourId, row]) => ({
      tourId,
      tourSlug: tourMap.get(tourId)?.slug ?? null,
      paidCount: row.paidCount,
      refundedCount: row.refundedCount,
      grossPaid: minorUnitsToMoney(row.paid, currencyCode),
      refunded: minorUnitsToMoney(row.refunded, currencyCode),
      netRevenue: signedMoney(row.paid - row.refunded, currencyCode),
      sharePercent: totalGross > 0n ? Number((row.paid * 100n + totalGross / 2n) / totalGross) : 0,
    }))
    .sort((left, right) => {
      const difference = signedMoneyToMinorUnits(right.netRevenue) - signedMoneyToMinorUnits(left.netRevenue);
      return difference === 0n ? 0 : difference > 0n ? 1 : -1;
    });
}

export function computeStatusCounts(bookings: Booking[]): BookingStatusCount[] {
  const statuses = ["NEW", "CONFIRMED", "COMPLETED", "CANCELLED", "EXPIRED"];
  const counts: Record<string, number> = {};
  for (const booking of bookings) counts[booking.status] = (counts[booking.status] ?? 0) + 1;
  return statuses.map((status) => ({ status, count: counts[status] ?? 0 }));
}

export function computePaymentStatusCounts(payments: PaymentLedgerEntry[]): PaymentStatusCount[] {
  const statuses: PaymentLedgerEntry["status"][] = [
    "PENDING",
    "PAID",
    "REFUNDED",
    "FAILED",
    "REVIEW_REQUIRED",
    "RECONCILIATION_REQUIRED",
  ];
  const counts = new Map<PaymentLedgerEntry["status"], number>();
  for (const payment of uniqueByPaymentId(payments)) {
    counts.set(payment.status, (counts.get(payment.status) ?? 0) + 1);
  }
  return statuses.map((status) => ({ status, count: counts.get(status) ?? 0 }));
}

export function computeDailyRevenue(
  payments: PaymentLedgerEntry[],
  from: string,
  to: string,
): DailyRevenueRow[] {
  const events = paymentLedgerEvents(payments, from, to);
  const currencyCode = assertSingleCurrency(events);
  const rows = new Map<string, { net: bigint; paidCount: number; refundedCount: number }>();
  for (const event of events) {
    const date = dateInReportingZone(event.occurredAt);
    const row = rows.get(date) ?? { net: 0n, paidCount: 0, refundedCount: 0 };
    row.net += event.amountMinorUnits;
    if (event.kind === "PAID") row.paidCount++;
    else row.refundedCount++;
    rows.set(date, row);
  }
  return Array.from(rows.entries())
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([date, row]) => ({
      date,
      paidCount: row.paidCount,
      refundedCount: row.refundedCount,
      netRevenue: signedMoney(row.net, currencyCode),
    }));
}
