import { describe, it, expect } from "vitest";
import {
  revenueBookings,
  filterByDateRange,
  computeRevenueSummary,
  computeRevenueByTour,
  computeStatusCounts,
  computeDailyRevenue,
} from "../app/composables/useFinanceAggregation";
import type { Booking, Tour } from "../app/composables/useToursApi";

// ── Fixtures ───────────────────────────────────────────────────────────────

function makeBooking(
  overrides: Partial<Booking> & { id: string },
): Booking {
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

// ── revenueBookings ────────────────────────────────────────────────────────

describe("revenueBookings", () => {
  it("includes CONFIRMED and COMPLETED, excludes everything else", () => {
    const bookings = [
      makeBooking({ id: "1", status: "CONFIRMED" }),
      makeBooking({ id: "2", status: "COMPLETED" }),
      makeBooking({ id: "3", status: "NEW" }),
      makeBooking({ id: "4", status: "CANCELLED" }),
      makeBooking({ id: "5", status: "EXPIRED" }),
    ];
    const result = revenueBookings(bookings);
    expect(result).toHaveLength(2);
    expect(result.map((b) => b.id)).toEqual(["1", "2"]);
  });

  it("returns empty array when no revenue bookings exist", () => {
    const bookings = [
      makeBooking({ id: "1", status: "NEW" }),
      makeBooking({ id: "2", status: "CANCELLED" }),
    ];
    expect(revenueBookings(bookings)).toHaveLength(0);
  });
});

// ── filterByDateRange ──────────────────────────────────────────────────────

describe("filterByDateRange", () => {
  const bookings = [
    makeBooking({ id: "1", tourDate: "2026-10-01" }),
    makeBooking({ id: "2", tourDate: "2026-10-15" }),
    makeBooking({ id: "3", tourDate: "2026-10-31" }),
    makeBooking({ id: "4", tourDate: "2026-11-01" }),
  ];

  it("includes bookings on the boundary dates (inclusive)", () => {
    const result = filterByDateRange(bookings, "2026-10-01", "2026-10-31");
    expect(result.map((b) => b.id)).toEqual(["1", "2", "3"]);
  });

  it("excludes bookings outside the range", () => {
    const result = filterByDateRange(bookings, "2026-10-10", "2026-10-20");
    expect(result.map((b) => b.id)).toEqual(["2"]);
  });

  it("returns empty when no bookings fall in range", () => {
    const result = filterByDateRange(bookings, "2026-12-01", "2026-12-31");
    expect(result).toHaveLength(0);
  });
});

// ── computeRevenueSummary ──────────────────────────────────────────────────

describe("computeRevenueSummary", () => {
  it("sums revenue from CONFIRMED and COMPLETED only", () => {
    const bookings = [
      makeBooking({ id: "1", status: "CONFIRMED", totalPrice: { amount: "70.00", currencyCode: "EUR" } }),
      makeBooking({ id: "2", status: "COMPLETED", totalPrice: { amount: "35.00", currencyCode: "EUR" } }),
      makeBooking({ id: "3", status: "CANCELLED", totalPrice: { amount: "90.00", currencyCode: "EUR" } }),
      makeBooking({ id: "4", status: "NEW",       totalPrice: { amount: "45.00", currencyCode: "EUR" } }),
    ];
    const s = computeRevenueSummary(bookings);
    expect(s.totalRevenue.amount).toBe("105.00");
    expect(s.confirmedCount).toBe(2);
    expect(s.totalCount).toBe(4);
  });

  it("calculates correct average per booking", () => {
    const bookings = [
      makeBooking({ id: "1", status: "CONFIRMED", totalPrice: { amount: "60.00", currencyCode: "EUR" } }),
      makeBooking({ id: "2", status: "CONFIRMED", totalPrice: { amount: "40.00", currencyCode: "EUR" } }),
    ];
    const s = computeRevenueSummary(bookings);
    expect(s.averagePerBooking?.amount).toBe("50.00");
  });

  it("returns null averagePerBooking when no revenue bookings", () => {
    const bookings = [
      makeBooking({ id: "1", status: "NEW" }),
      makeBooking({ id: "2", status: "CANCELLED" }),
    ];
    const s = computeRevenueSummary(bookings);
    expect(s.averagePerBooking).toBeNull();
    expect(s.totalRevenue.amount).toBe("0.00");
    expect(s.confirmedCount).toBe(0);
  });

  it("counts pax correctly across revenue bookings", () => {
    const bookings = [
      makeBooking({ id: "1", status: "CONFIRMED", adultsCount: 3, childrenCount: 1 }),
      makeBooking({ id: "2", status: "COMPLETED", adultsCount: 2, childrenCount: 0 }),
      makeBooking({ id: "3", status: "CANCELLED", adultsCount: 5, childrenCount: 2 }),
    ];
    const s = computeRevenueSummary(bookings);
    expect(s.paxTotal).toBe(6); // 3+1 + 2+0 = 6, not the cancelled one
  });
});

// ── computeRevenueByTour ───────────────────────────────────────────────────

describe("computeRevenueByTour", () => {
  const tours = [
    makeTour("tour-a", "super-safari"),
    makeTour("tour-b", "boat-trip"),
  ];

  it("groups revenue by tour and sorts descending by total", () => {
    const bookings = [
      makeBooking({ id: "1", tourId: "tour-a", status: "CONFIRMED", totalPrice: { amount: "70.00", currencyCode: "EUR" } }),
      makeBooking({ id: "2", tourId: "tour-a", status: "CONFIRMED", totalPrice: { amount: "35.00", currencyCode: "EUR" } }),
      makeBooking({ id: "3", tourId: "tour-b", status: "CONFIRMED", totalPrice: { amount: "90.00", currencyCode: "EUR" } }),
    ];
    const rows = computeRevenueByTour(bookings, tours);
    expect(rows).toHaveLength(2);
    // boat-trip has 90, super-safari has 105 — super-safari is first
    expect(rows[0]!.tourSlug).toBe("super-safari");
    expect(rows[0]!.total.amount).toBe("105.00");
    expect(rows[0]!.bookingCount).toBe(2);
    expect(rows[1]!.tourSlug).toBe("boat-trip");
    expect(rows[1]!.total.amount).toBe("90.00");
  });

  it("excludes cancelled/new bookings from tour revenue", () => {
    const bookings = [
      makeBooking({ id: "1", tourId: "tour-a", status: "CONFIRMED", totalPrice: { amount: "70.00", currencyCode: "EUR" } }),
      makeBooking({ id: "2", tourId: "tour-a", status: "CANCELLED", totalPrice: { amount: "70.00", currencyCode: "EUR" } }),
    ];
    const rows = computeRevenueByTour(bookings, tours);
    expect(rows).toHaveLength(1);
    expect(rows[0]!.total.amount).toBe("70.00");
    expect(rows[0]!.bookingCount).toBe(1);
  });

  it("uses null tourSlug for unknown tour IDs", () => {
    const bookings = [
      makeBooking({ id: "1", tourId: "unknown-tour", status: "CONFIRMED", totalPrice: { amount: "50.00", currencyCode: "EUR" } }),
    ];
    const rows = computeRevenueByTour(bookings, tours);
    expect(rows[0]!.tourSlug).toBeNull();
  });

  it("computes sharePercent correctly", () => {
    const bookings = [
      makeBooking({ id: "1", tourId: "tour-a", status: "CONFIRMED", totalPrice: { amount: "75.00", currencyCode: "EUR" } }),
      makeBooking({ id: "2", tourId: "tour-b", status: "CONFIRMED", totalPrice: { amount: "25.00", currencyCode: "EUR" } }),
    ];
    const rows = computeRevenueByTour(bookings, tours);
    const tourA = rows.find((r) => r.tourSlug === "super-safari")!;
    const tourB = rows.find((r) => r.tourSlug === "boat-trip")!;
    expect(tourA.sharePercent).toBe(75);
    expect(tourB.sharePercent).toBe(25);
  });

  it("returns empty array when no revenue bookings", () => {
    const bookings = [makeBooking({ id: "1", status: "NEW" })];
    expect(computeRevenueByTour(bookings, tours)).toHaveLength(0);
  });
});

// ── computeStatusCounts ────────────────────────────────────────────────────

describe("computeStatusCounts", () => {
  it("counts all 5 statuses including zero counts", () => {
    const bookings = [
      makeBooking({ id: "1", status: "CONFIRMED" }),
      makeBooking({ id: "2", status: "CONFIRMED" }),
      makeBooking({ id: "3", status: "CANCELLED" }),
    ];
    const counts = computeStatusCounts(bookings);
    expect(counts).toHaveLength(5);
    const map = Object.fromEntries(counts.map((c) => [c.status, c.count]));
    expect(map["CONFIRMED"]).toBe(2);
    expect(map["CANCELLED"]).toBe(1);
    expect(map["NEW"]).toBe(0);
    expect(map["COMPLETED"]).toBe(0);
    expect(map["EXPIRED"]).toBe(0);
  });

  it("returns all zeros for empty bookings list", () => {
    const counts = computeStatusCounts([]);
    expect(counts.every((c) => c.count === 0)).toBe(true);
  });
});

// ── computeDailyRevenue ────────────────────────────────────────────────────

describe("computeDailyRevenue", () => {
  it("groups revenue by date sorted ascending", () => {
    const bookings = [
      makeBooking({ id: "1", tourDate: "2026-10-03", status: "CONFIRMED", totalPrice: { amount: "70.00", currencyCode: "EUR" } }),
      makeBooking({ id: "2", tourDate: "2026-10-01", status: "CONFIRMED", totalPrice: { amount: "35.00", currencyCode: "EUR" } }),
      makeBooking({ id: "3", tourDate: "2026-10-03", status: "COMPLETED", totalPrice: { amount: "45.00", currencyCode: "EUR" } }),
    ];
    const daily = computeDailyRevenue(bookings);
    expect(daily).toHaveLength(2);
    expect(daily[0]!.date).toBe("2026-10-01");
    expect(daily[0]!.total.amount).toBe("35.00");
    expect(daily[1]!.date).toBe("2026-10-03");
    expect(daily[1]!.total.amount).toBe("115.00");
    expect(daily[1]!.count).toBe(2);
  });

  it("excludes non-revenue bookings from daily breakdown", () => {
    const bookings = [
      makeBooking({ id: "1", tourDate: "2026-10-01", status: "NEW",       totalPrice: { amount: "70.00", currencyCode: "EUR" } }),
      makeBooking({ id: "2", tourDate: "2026-10-01", status: "CONFIRMED", totalPrice: { amount: "35.00", currencyCode: "EUR" } }),
    ];
    const daily = computeDailyRevenue(bookings);
    expect(daily).toHaveLength(1);
    expect(daily[0]!.total.amount).toBe("35.00");
  });

  it("returns empty array for no revenue bookings", () => {
    expect(computeDailyRevenue([])).toHaveLength(0);
  });
});
