import { describe, expect, it } from "vitest";
import type { Booking, Tour, TourSlot } from "@wego/api-contract";
import { buildRunSheet, isLive } from "../app/utils/runSheet";

function booking(overrides: Partial<Booking>): Booking {
  return {
    id: overrides.reference ?? "b", reference: "STR-2026-1", tourId: "t1", slotId: "s1", tourDate: "2026-10-05", timeSlot: "MORNING",
    adultsCount: 2, childrenCount: 0, priceAdult: { amount: "35.00", currencyCode: "EUR" }, priceChild: null,
    totalPrice: { amount: "70.00", currencyCode: "EUR" }, unit: null,
    customer: { fullName: "Guest", phone: "+201000000000", nationality: "EG", email: null },
    hotelName: "Hilton", hotelRoom: null, specialRequests: null, locale: "en", status: "CONFIRMED",
    createdAt: "2026-10-01T10:00:00Z", confirmedAt: "2026-10-01T10:05:00Z", cancelledAt: null, cancellationReason: null, completedAt: null, expiredAt: null,
    ...overrides,
  } as Booking;
}

const tours = {
  t1: { id: "t1", slug: "quad", nameEn: "Quad", sortOrder: 2, priceOptions: [] } as unknown as Tour,
  t2: { id: "t2", slug: "buggy", nameEn: "Buggy", sortOrder: 1, priceOptions: [{ code: "buggy", label: "Two-seat buggy", seatsPerUnit: 2 }] } as unknown as Tour,
};
const slots: Record<string, TourSlot[]> = { t1: [{ id: "s1", tourId: "t1", date: "2026-10-05", timeSlot: "MORNING", capacity: 10, bookedCount: 5, available: 5, isBlocked: false }] };

describe("run sheet", () => {
  it("keeps paid and completed bookings, and unpaid ones only on request", () => {
    expect(isLive(booking({ status: "CONFIRMED" }), false)).toBe(true);
    expect(isLive(booking({ status: "NEW" }), false)).toBe(false);
    expect(isLive(booking({ status: "NEW" }), true)).toBe(true);
    expect(isLive(booking({ status: "CANCELLED" }), true)).toBe(false);
    expect(isLive(booking({ status: "EXPIRED" }), true)).toBe(false);
  });

  it("groups by tour and departure in time order, with guests, units and capacity", () => {
    const runs = buildRunSheet(
      [
        booking({ reference: "STR-2026-1", hotelName: "Rixos", adultsCount: 2, childrenCount: 1 }),
        booking({ reference: "STR-2026-2", hotelName: "Hilton", timeSlot: "SUNSET" }),
        booking({ reference: "STR-2026-3", hotelName: "Baron", adultsCount: 1 }),
        booking({ reference: "STR-2026-4", tourId: "t2", adultsCount: 3, unit: { optionCode: "buggy", optionLabel: "Two-seat buggy", seatsPerUnit: 2, unitCount: 2, unitPrice: { amount: "30.00", currencyCode: "EUR" } } }),
        booking({ reference: "STR-2026-5", status: "CANCELLED" }),
      ],
      tours,
      slots,
      false,
    );
    expect(runs.map((r) => r.tourName)).toEqual(["Buggy", "Quad"]);
    const quad = runs[1]!;
    expect(quad.departures.map((d) => d.timeSlot)).toEqual(["MORNING", "SUNSET"]);
    expect(quad.departures[0]!.bookings.map((b) => b.hotelName)).toEqual(["Baron", "Rixos"]);
    expect(quad.departures[0]!.guests).toBe(4);
    expect(quad.departures[0]!.slot?.capacity).toBe(10);
    expect(quad.guests).toBe(6);
    expect(runs[0]!.departures[0]!.units).toEqual({ buggy: 2 });
  });

  it("counts unpaid bookings when they are shown", () => {
    const runs = buildRunSheet([booking({ status: "NEW" }), booking({ reference: "STR-2026-9" })], tours, slots, true);
    expect(runs[0]!.departures[0]!.unpaid).toBe(1);
  });
});
