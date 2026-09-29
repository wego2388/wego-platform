import { describe, expect, it } from "vitest";
import {
  computeDailyRevenue,
  computePaymentStatusCounts,
  computeRevenueByTour,
  computeRevenueSummary,
  computeStatusCounts,
  filterByDateRange,
  formatSignedMoney,
  paymentLedgerEvents,
} from "../app/composables/useFinanceAggregation";
import type { Booking, PaymentLedgerEntry, Tour } from "../app/composables/useToursApi";

function makeBooking(overrides: Partial<Booking> & { id: string }): Booking {
  return {
    id: overrides.id,
    reference: `STR-2026-${overrides.id}`,
    tourId: overrides.tourId ?? "tour-a",
    slotId: "slot-1",
    tourDate: overrides.tourDate ?? "2026-10-01",
    timeSlot: "MORNING",
    adultsCount: overrides.adultsCount ?? 2,
    childrenCount: overrides.childrenCount ?? 0,
    priceAdult: { amount: "35.00", currencyCode: "EUR" },
    priceChild: null,
    totalPrice: overrides.totalPrice ?? { amount: "70.00", currencyCode: "EUR" },
    customer: {
      fullName: "Test Customer",
      phone: "+20100000001",
      nationality: "EG",
      email: null,
    },
    hotelName: "Test Hotel",
    hotelRoom: null,
    specialRequests: null,
    locale: "en",
    status: overrides.status ?? "CONFIRMED",
    createdAt: "2026-09-28T10:00:00Z",
    confirmedAt: overrides.confirmedAt ?? "2026-09-28T10:01:00Z",
    cancelledAt: overrides.cancelledAt ?? null,
    cancellationReason: overrides.cancellationReason ?? null,
    completedAt: overrides.completedAt ?? null,
    expiredAt: overrides.expiredAt ?? null,
  };
}

function makePayment(
  overrides: Partial<PaymentLedgerEntry> & { paymentId: string; bookingId: string },
): PaymentLedgerEntry {
  const status = overrides.status ?? "PAID";
  const paidAt = overrides.paidAt === undefined ? "2026-10-01T08:01:00Z" : overrides.paidAt;
  // Mirrors the backend invariant: PAID captures are recognised when paid and
  // keep that recognition through a refund; review captures never are.
  const revenueRecognisedAt = "revenueRecognisedAt" in overrides
    ? overrides.revenueRecognisedAt ?? null
    : status === "PAID" || status === "REFUNDED" ? paidAt : null;
  return {
    paymentId: overrides.paymentId,
    bookingId: overrides.bookingId,
    tourId: overrides.tourId ?? "tour-a",
    adultsCount: overrides.adultsCount ?? 2,
    childrenCount: overrides.childrenCount ?? 0,
    amount: overrides.amount ?? { amount: "70.00", currencyCode: "EUR" },
    status,
    createdAt: overrides.createdAt ?? "2026-10-01T08:00:00Z",
    paidAt,
    failedAt: overrides.failedAt ?? null,
    refundedAt: overrides.refundedAt ?? null,
    revenueRecognisedAt,
  };
}

function makeTour(id: string, slug: string): Tour {
  return {
    id,
    slug,
    category: "DESERT",
    durationText: "4 hours",
    priceAdult: { amount: "35.00", currencyCode: "EUR" },
    priceChild: null,
    capacity: 20,
    availableTimeSlots: ["MORNING"],
    sortOrder: 1,
    isActive: true,
  } as Tour;
}

describe("payment-ledger finance truth", () => {
  it("does not treat a confirmed booking without captured payment as revenue", () => {
    const summary = computeRevenueSummary(
      [],
      "2026-10-01",
      "2026-10-31",
    );
    expect(summary.grossPaid.amount).toBe("0.00");
    expect(summary.netRevenue.amount).toBe("0.00");
    expect(summary.paidCount).toBe(0);
  });

  it("recognises a captured payment even if the booking is later cancelled", () => {
    const payments = [makePayment({
      paymentId: "payment-1",
      bookingId: "booking-1",
      adultsCount: 2,
      childrenCount: 1,
    })];
    const summary = computeRevenueSummary(payments, "2026-10-01", "2026-10-31");
    expect(summary.grossPaid.amount).toBe("70.00");
    expect(summary.netRevenue.amount).toBe("70.00");
    expect(summary.paxTotal).toBe(3);
  });

  it("excludes pending, failed and unresolved review payments", () => {
    const statuses = ["PENDING", "FAILED", "REVIEW_REQUIRED", "RECONCILIATION_REQUIRED"] as const;
    const payments = statuses.map((status, index) => makePayment({
      paymentId: `payment-${index}`,
      bookingId: `booking-${index}`,
      status,
      paidAt: status === "REVIEW_REQUIRED" ? "2026-10-02T10:00:00Z" : null,
    }));
    const summary = computeRevenueSummary(payments, "2026-10-01", "2026-10-31");
    expect(summary.grossPaid.amount).toBe("0.00");
    expect(summary.paidCount).toBe(0);
  });

  it("uses Cairo-local dates at the UTC day boundary", () => {
    const payments = [makePayment({
      paymentId: "payment-1",
      bookingId: "booking-1",
      paidAt: "2026-09-30T22:30:00Z",
    })];
    expect(paymentLedgerEvents(payments, "2026-10-01", "2026-10-01")).toHaveLength(1);
    expect(paymentLedgerEvents(payments, "2026-09-30", "2026-09-30")).toHaveLength(0);
  });

  it("records payment and refund separately when both occur in one period", () => {
    const payments = [makePayment({
      paymentId: "payment-1",
      bookingId: "booking-1",
      status: "REFUNDED",
      paidAt: "2026-10-02T10:00:00Z",
      refundedAt: "2026-10-05T10:00:00Z",
    })];
    const summary = computeRevenueSummary(payments, "2026-10-01", "2026-10-31");
    expect(summary.grossPaid.amount).toBe("70.00");
    expect(summary.refunded.amount).toBe("70.00");
    expect(summary.netRevenue.amount).toBe("0.00");
    expect(summary.paidCount).toBe(1);
    expect(summary.refundedCount).toBe(1);
    expect(summary.paxTotal).toBe(0);
  });

  it("shows a later-period refund as negative net revenue", () => {
    const payments = [makePayment({
      paymentId: "payment-1",
      bookingId: "booking-1",
      status: "REFUNDED",
      paidAt: "2026-09-15T10:00:00Z",
      refundedAt: "2026-10-05T10:00:00Z",
    })];
    const summary = computeRevenueSummary(payments, "2026-10-01", "2026-10-31");
    expect(summary.grossPaid.amount).toBe("0.00");
    expect(summary.refunded.amount).toBe("70.00");
    expect(summary.netRevenue.amount).toBe("-70.00");
    expect(formatSignedMoney(summary.netRevenue)).toBe("-€70");
    expect(summary.paxTotal).toBe(-2);
  });

  it("calculates average captured value without using booking totals", () => {
    const payments = [
      makePayment({ paymentId: "payment-1", bookingId: "booking-1", amount: { amount: "60.00", currencyCode: "EUR" } }),
      makePayment({ paymentId: "payment-2", bookingId: "booking-2", amount: { amount: "40.00", currencyCode: "EUR" } }),
    ];
    const summary = computeRevenueSummary(payments, "2026-10-01", "2026-10-31");
    expect(summary.averagePaidBooking?.amount).toBe("50.00");
  });

  it("never recognises a refunded review capture, so no closed period is restated", () => {
    const payments = [makePayment({
      paymentId: "payment-1",
      bookingId: "booking-1",
      status: "REFUNDED",
      paidAt: "2026-09-20T10:00:00Z",
      refundedAt: "2026-10-05T10:00:00Z",
      revenueRecognisedAt: null,
    })];
    const september = computeRevenueSummary(payments, "2026-09-01", "2026-09-30");
    const october = computeRevenueSummary(payments, "2026-10-01", "2026-10-31");
    expect(september.grossPaid.amount).toBe("0.00");
    expect(september.paidCount).toBe(0);
    expect(october.refunded.amount).toBe("0.00");
    expect(october.netRevenue.amount).toBe("0.00");
  });

  it("counts a payment once even when overlapping pages return it twice", () => {
    const payment = makePayment({ paymentId: "payment-1", bookingId: "booking-1" });
    const summary = computeRevenueSummary([payment, { ...payment }], "2026-10-01", "2026-10-31");
    expect(summary.grossPaid.amount).toBe("70.00");
    expect(summary.paidCount).toBe(1);
    expect(summary.totalPaymentCount).toBe(1);
    expect(computePaymentStatusCounts([payment, { ...payment }]).find((row) => row.status === "PAID")?.count).toBe(1);
  });

  it("ignores the currency of rows that are not recognised revenue", () => {
    const payments = [
      makePayment({ paymentId: "payment-1", bookingId: "booking-1" }),
      makePayment({
        paymentId: "payment-2",
        bookingId: "booking-2",
        status: "PENDING",
        paidAt: null,
        amount: { amount: "70.00", currencyCode: "USD" },
      }),
    ];
    expect(computeRevenueSummary(payments, "2026-10-01", "2026-10-31").grossPaid.amount).toBe("70.00");
  });

  it("rejects mixed currencies instead of silently combining them", () => {
    const payments = [
      makePayment({ paymentId: "payment-1", bookingId: "booking-1" }),
      makePayment({
        paymentId: "payment-2",
        bookingId: "booking-2",
        amount: { amount: "70.00", currencyCode: "USD" },
      }),
    ];
    expect(() => computeRevenueSummary(payments, "2026-10-01", "2026-10-31")).toThrow("cannot mix currencies");
  });

  it("keeps large revenue shares in bigint arithmetic", () => {
    const payments = [
      makePayment({
        paymentId: "payment-1",
        bookingId: "booking-1",
        tourId: "tour-a",
        amount: { amount: "9999999999999999.99", currencyCode: "EUR" },
      }),
      makePayment({
        paymentId: "payment-2",
        bookingId: "booking-2",
        tourId: "tour-b",
        amount: { amount: "1.01", currencyCode: "EUR" },
      }),
    ];
    const rows = computeRevenueByTour(
      payments,
      [makeTour("tour-a", "super-safari"), makeTour("tour-b", "boat-trip")],
      "2026-10-01",
      "2026-10-31",
    );
    expect(rows.map((row) => row.sharePercent)).toEqual([100, 0]);
  });
});

describe("revenue breakdowns", () => {
  const tours = [makeTour("tour-a", "super-safari"), makeTour("tour-b", "boat-trip")];
  it("groups ledger events by the booking's tour", () => {
    const payments = [
      makePayment({ paymentId: "payment-a", bookingId: "booking-a", tourId: "tour-a", amount: { amount: "100.00", currencyCode: "EUR" } }),
      makePayment({
        paymentId: "payment-b",
        bookingId: "booking-b",
        tourId: "tour-b",
        amount: { amount: "60.00", currencyCode: "EUR" },
        status: "REFUNDED",
        refundedAt: "2026-10-03T10:00:00Z",
      }),
    ];
    const rows = computeRevenueByTour(payments, tours, "2026-10-01", "2026-10-31");
    expect(rows[0]?.tourSlug).toBe("super-safari");
    expect(rows[0]?.netRevenue.amount).toBe("100.00");
    expect(rows[0]?.sharePercent).toBe(63);
    expect(rows[1]?.tourSlug).toBe("boat-trip");
    expect(rows[1]?.netRevenue.amount).toBe("0.00");
    expect(rows[1]?.refundedCount).toBe(1);
  });

  it("uses a null label when a historical tour is absent from the current catalog", () => {
    const payments = [makePayment({ paymentId: "payment-x", bookingId: "booking-x", tourId: "retired-tour" })];
    const rows = computeRevenueByTour(payments, tours, "2026-10-01", "2026-10-31");
    expect(rows[0]?.tourSlug).toBeNull();
  });

  it("shows non-revenue payment states separately", () => {
    const payments = [
      makePayment({ paymentId: "payment-a", bookingId: "booking-a", status: "PENDING", paidAt: null }),
      makePayment({ paymentId: "payment-b", bookingId: "booking-b", status: "REVIEW_REQUIRED" }),
    ];
    const counts = Object.fromEntries(computePaymentStatusCounts(payments).map((row) => [row.status, row.count]));
    expect(counts.PENDING).toBe(1);
    expect(counts.REVIEW_REQUIRED).toBe(1);
    expect(counts.PAID).toBe(0);
  });

  it("groups daily net activity by Cairo date", () => {
    const payments = [
      makePayment({ paymentId: "payment-a", bookingId: "booking-a", paidAt: "2026-09-30T22:30:00Z" }),
      makePayment({
        paymentId: "payment-b",
        bookingId: "booking-b",
        status: "REFUNDED",
        paidAt: "2026-10-02T09:00:00Z",
        refundedAt: "2026-10-03T09:00:00Z",
      }),
    ];
    const rows = computeDailyRevenue(payments, "2026-10-01", "2026-10-31");
    expect(rows.map((row) => row.date)).toEqual(["2026-10-01", "2026-10-02", "2026-10-03"]);
    expect(rows[2]?.netRevenue.amount).toBe("-70.00");
  });
});

describe("operational booking breakdown", () => {
  it("filters tour dates inclusively", () => {
    const bookings = [
      makeBooking({ id: "1", tourDate: "2026-10-01" }),
      makeBooking({ id: "2", tourDate: "2026-10-31" }),
      makeBooking({ id: "3", tourDate: "2026-11-01" }),
    ];
    expect(filterByDateRange(bookings, "2026-10-01", "2026-10-31").map((booking) => booking.id)).toEqual(["1", "2"]);
  });

  it("counts every booking status including zero values", () => {
    const counts = computeStatusCounts([
      makeBooking({ id: "1", status: "CONFIRMED" }),
      makeBooking({ id: "2", status: "CANCELLED" }),
    ]);
    expect(Object.fromEntries(counts.map((row) => [row.status, row.count]))).toEqual({
      NEW: 0,
      CONFIRMED: 1,
      COMPLETED: 0,
      CANCELLED: 1,
      EXPIRED: 0,
    });
  });
});
