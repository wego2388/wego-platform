import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  createBooking,
  getTourBySlug,
  readStoredBookingConfirmation,
  storeBookingConfirmation,
  type BookingConfirmation,
  type CreateBookingPayload,
  type Tour,
} from "../app/composables/usePublicToursApi";

const TOUR: Tour = {
  id: "00000000-0000-0000-0000-000000000001",
  slug: "desert-quad-bike",
  category: "DESERT",
  durationText: "4 hours",
  priceAdult: { amount: "35.00", currencyCode: "EUR" },
  priceChild: { amount: "17.50", currencyCode: "EUR" },
  capacity: 20,
  availableTimeSlots: ["MORNING", "SUNSET"],
  sortOrder: 1,
  isActive: true,
};

const BOOKING: BookingConfirmation = {
  id: "00000000-0000-0000-0000-000000000010",
  reference: "STR-2026-10",
  tourId: TOUR.id,
  slotId: "00000000-0000-0000-0000-000000000011",
  tourDate: "2026-10-03",
  timeSlot: "MORNING",
  adultsCount: 2,
  childrenCount: 1,
  priceAdult: TOUR.priceAdult,
  priceChild: TOUR.priceChild,
  totalPrice: { amount: "87.50", currencyCode: "EUR" },
  customer: {
    fullName: "Ahmed Hassan",
    phone: "+201234567890",
    nationality: "EG",
    email: "ahmed@example.com",
  },
  hotelName: "Hilton Sharm Dreams",
  hotelRoom: "312",
  specialRequests: null,
  locale: "en",
  status: "NEW",
  createdAt: "2026-09-27T10:00:00Z",
  confirmedAt: null,
  cancelledAt: null,
  cancellationReason: null,
  completedAt: null,
  expiredAt: null,
};

function okJson(body: unknown) {
  return {
    ok: true,
    status: 200,
    json: async () => body,
  } as Response;
}

describe("public tours API contract", () => {
  beforeEach(() => sessionStorage.clear());

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("uses the dedicated slug endpoint instead of downloading the catalog", async () => {
    const fetchMock = vi.fn().mockResolvedValue(okJson(TOUR));
    vi.stubGlobal("fetch", fetchMock);

    await expect(getTourBySlug("desert-quad-bike")).resolves.toEqual(TOUR);
    expect(fetchMock).toHaveBeenCalledOnce();
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/tours-operator/tours/by-slug?slug=desert-quad-bike",
    );
  });

  it("posts the nested customer and Money-compatible booking contract", async () => {
    const payload: CreateBookingPayload = {
      slotId: BOOKING.slotId,
      adultsCount: 2,
      childrenCount: 1,
      customer: {
        fullName: BOOKING.customer.fullName,
        phone: BOOKING.customer.phone,
        nationality: BOOKING.customer.nationality,
        email: BOOKING.customer.email ?? undefined,
      },
      hotelName: BOOKING.hotelName,
      hotelRoom: BOOKING.hotelRoom ?? undefined,
      locale: "en",
    };
    const fetchMock = vi.fn().mockResolvedValue(okJson(BOOKING));
    vi.stubGlobal("fetch", fetchMock);

    await expect(createBooking(payload)).resolves.toEqual(BOOKING);
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/tours-operator/bookings",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify(payload),
      }),
    );
  });

  it("keeps confirmation PII in session storage rather than requiring it in the URL", () => {
    storeBookingConfirmation(BOOKING);
    expect(readStoredBookingConfirmation(BOOKING.reference)).toEqual(BOOKING);
    expect(readStoredBookingConfirmation(BOOKING.reference)).toBeNull();
  });
});
