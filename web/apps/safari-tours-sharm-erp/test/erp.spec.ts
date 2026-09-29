import { describe, it, expect, beforeEach, vi } from "vitest";
import {
  addMoney,
  divideMoney,
  formatMoney,
  moneyToMinorUnits,
  ToursApiError,
  type Booking,
  type Tour,
  type BookingStatus,
  type CreateTourPayload,
  type UpdateTourPayload,
} from "../app/composables/useToursApi";
import {
  writeAuthSession,
  readAuthSession,
  clearAuthSession,
  hasPermission,
  logoutAuthSession,
  type AuthSession,
} from "../app/composables/useAuthSession";

// ── useAuthSession ──────────────────────────────────────────────────────────

describe("useAuthSession", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    clearAuthSession();
  });

  it("returns null when no session is stored", () => {
    expect(readAuthSession()).toBeNull();
  });

  it("persists and retrieves a session", () => {
    const session: AuthSession = {
      token: "tok-test",
      email: "staff@example.com",
      roles: ["staff"],
      permissions: ["tours-operator.booking:view"],
    };
    writeAuthSession(session);
    expect(readAuthSession()).toEqual(session);
  });

  it("clearAuthSession removes the session", () => {
    writeAuthSession({ token: "tok-clear", email: "a@b.com", roles: [], permissions: [] });
    clearAuthSession();
    expect(readAuthSession()).toBeNull();
  });

  it("logout clears the browser session only after backend revocation succeeds", async () => {
    const session: AuthSession = { token: "tok-revoke", email: "a@b.com", roles: [], permissions: [] };
    writeAuthSession(session);
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 204 }));

    await logoutAuthSession(session);

    expect(fetchMock).toHaveBeenCalledWith("/api/v1/identity/logout", {
      method: "POST",
      headers: { Authorization: "Bearer tok-revoke" },
    });
    expect(readAuthSession()).toBeNull();
  });

  it("logout keeps the browser session when backend revocation fails", async () => {
    const session: AuthSession = { token: "tok-keep", email: "a@b.com", roles: [], permissions: [] };
    writeAuthSession(session);
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 503 }));

    await expect(logoutAuthSession(session)).rejects.toThrow("Unable to revoke the staff session");
    expect(readAuthSession()).toEqual(session);
  });

  it("hasPermission returns false for null session", () => {
    expect(hasPermission(null, "tours-operator.booking:view")).toBe(false);
  });

  it("hasPermission returns true when permission exists", () => {
    const session: AuthSession = {
      token: "tok",
      email: "a@b.com",
      roles: ["staff"],
      permissions: ["tours-operator.booking:view", "tours-operator.booking:cancel"],
    };
    expect(hasPermission(session, "tours-operator.booking:view")).toBe(true);
    expect(hasPermission(session, "tours-operator.booking:cancel")).toBe(true);
  });

  it("hasPermission returns false when permission is absent", () => {
    const session: AuthSession = {
      token: "tok",
      email: "a@b.com",
      roles: ["staff"],
      permissions: ["tours-operator.booking:view"],
    };
    expect(hasPermission(session, "tours-operator.booking:cancel")).toBe(false);
    expect(hasPermission(session, "tours-operator.tour:manage")).toBe(false);
  });
});

// ── ToursApiError ──────────────────────────────────────────────────────────

describe("ToursApiError", () => {
  it("has correct status and errorCode", () => {
    const err = new ToursApiError(401, "unauthorized");
    expect(err.status).toBe(401);
    expect(err.errorCode).toBe("unauthorized");
    expect(err).toBeInstanceOf(Error);
  });

  it("can be identified with instanceof", () => {
    const err = new ToursApiError(403, "forbidden");
    expect(err instanceof ToursApiError).toBe(true);
  });
});

// ── Booking status transitions (domain logic) ──────────────────────────────

describe("Booking status logic", () => {
  const _NEW_BOOKING: Partial<Booking> = { status: "NEW" };
  const _CONFIRMED_BOOKING: Partial<Booking> = { status: "CONFIRMED" };
  const _COMPLETED_BOOKING: Partial<Booking> = { status: "COMPLETED" };
  const _CANCELLED_BOOKING: Partial<Booking> = { status: "CANCELLED" };
  const _EXPIRED_BOOKING: Partial<Booking> = { status: "EXPIRED" };

  function canConfirm(status: BookingStatus): boolean {
    return status === "NEW";
  }
  function canComplete(status: BookingStatus): boolean {
    return status === "CONFIRMED";
  }
  function canCancel(status: BookingStatus): boolean {
    return status === "NEW" || status === "CONFIRMED";
  }

  it("only NEW can be confirmed", () => {
    expect(canConfirm("NEW")).toBe(true);
    expect(canConfirm("CONFIRMED")).toBe(false);
    expect(canConfirm("COMPLETED")).toBe(false);
    expect(canConfirm("CANCELLED")).toBe(false);
    expect(canConfirm("EXPIRED")).toBe(false);
  });

  it("only CONFIRMED can be completed", () => {
    expect(canComplete("CONFIRMED")).toBe(true);
    expect(canComplete("NEW")).toBe(false);
    expect(canComplete("COMPLETED")).toBe(false);
  });

  it("NEW and CONFIRMED can be cancelled", () => {
    expect(canCancel("NEW")).toBe(true);
    expect(canCancel("CONFIRMED")).toBe(true);
    expect(canCancel("COMPLETED")).toBe(false);
    expect(canCancel("CANCELLED")).toBe(false);
    expect(canCancel("EXPIRED")).toBe(false);
  });
});

// ── Tour data shape ────────────────────────────────────────────────────────

describe("Tour data shape", () => {
  const TOUR: Tour = {
    id: "uuid-001",
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

  it("tour has required fields", () => {
    expect(TOUR.slug).toBeTruthy();
    expect(TOUR.category).toBe("DESERT");
    expect(moneyToMinorUnits(TOUR.priceAdult)).toBeGreaterThan(0n);
    expect(TOUR.capacity).toBeGreaterThan(0);
  });

  it("price formatting: 3500 cents = 35.00 EUR", () => {
    expect(formatMoney(TOUR.priceAdult)).toBe("€35");
  });

  it("child price can be null for adult-only tours", () => {
    const adultOnly: Tour = { ...TOUR, priceChild: null };
    expect(adultOnly.priceChild).toBeNull();
  });
});

// ── Customer derivation ────────────────────────────────────────────────────

describe("Customer derivation from bookings", () => {
  function buildCustomers(bookings: Array<{
    customer: { fullName: string; phone: string; nationality: string; email: string | null };
    tourDate: string;
    totalPrice: { amount: string; currencyCode: string };
  }>) {
    const map = new Map<string, {
      fullName: string; phone: string; nationality: string;
      bookingCount: number; lastBooking: string;
      totalSpent: { amount: string; currencyCode: string };
    }>();
    for (const b of bookings) {
      const key = b.customer.phone;
      const existing = map.get(key);
      if (existing) {
        existing.bookingCount++;
        existing.totalSpent = addMoney([existing.totalSpent, b.totalPrice]);
        if (b.tourDate > existing.lastBooking) existing.lastBooking = b.tourDate;
      } else {
        map.set(key, {
          fullName: b.customer.fullName,
          phone: b.customer.phone,
          nationality: b.customer.nationality,
          bookingCount: 1,
          lastBooking: b.tourDate,
          totalSpent: b.totalPrice,
        });
      }
    }
    return [...map.values()];
  }

  it("merges multiple bookings for the same phone number", () => {
    const bookings = [
      { customer: { fullName: "Anna S.", phone: "+7900111", nationality: "RU", email: null }, tourDate: "2026-10-01", totalPrice: { amount: "70.00", currencyCode: "EUR" } },
      { customer: { fullName: "Anna S.", phone: "+7900111", nationality: "RU", email: null }, tourDate: "2026-10-05", totalPrice: { amount: "50.00", currencyCode: "EUR" } },
    ];
    const customers = buildCustomers(bookings);
    expect(customers.length).toBe(1);
    expect(customers[0]!.bookingCount).toBe(2);
    expect(customers[0]!.totalSpent).toEqual({ amount: "120.00", currencyCode: "EUR" });
    expect(customers[0]!.lastBooking).toBe("2026-10-05");
  });

  it("creates separate entries for different phone numbers", () => {
    const bookings = [
      { customer: { fullName: "Ali M.", phone: "+201001", nationality: "EG", email: null }, tourDate: "2026-10-01", totalPrice: { amount: "35.00", currencyCode: "EUR" } },
      { customer: { fullName: "Bob K.", phone: "+441234", nationality: "GB", email: null }, tourDate: "2026-10-02", totalPrice: { amount: "70.00", currencyCode: "EUR" } },
    ];
    const customers = buildCustomers(bookings);
    expect(customers.length).toBe(2);
  });
});

// ── Notification helpers ────────────────────────────────────────────────────

describe("Notification time ago", () => {
  function timeAgo(iso: string, nowMs: number): string {
    const diff = Math.floor((nowMs - new Date(iso).getTime()) / 1000);
    if (diff < 60)    return `${diff}s ago`;
    if (diff < 3600)  return `${Math.floor(diff / 60)}m ago`;
    if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
    return new Date(iso).toLocaleDateString();
  }

  it("shows seconds for < 1 minute", () => {
    const now = Date.now();
    expect(timeAgo(new Date(now - 30000).toISOString(), now)).toBe("30s ago");
  });

  it("shows minutes for < 1 hour", () => {
    const now = Date.now();
    expect(timeAgo(new Date(now - 5 * 60 * 1000).toISOString(), now)).toBe("5m ago");
  });

  it("shows hours for < 1 day", () => {
    const now = Date.now();
    expect(timeAgo(new Date(now - 2 * 3600 * 1000).toISOString(), now)).toBe("2h ago");
  });
});

// ── Finance helpers ────────────────────────────────────────────────────────

describe("Finance revenue calculation", () => {
  const makeBooking = (status: BookingStatus, amount: string): Partial<Booking> => ({
    status, totalPrice: { amount, currencyCode: "EUR" }, adultsCount: 2, childrenCount: 1,
  });

  it("counts only CONFIRMED and COMPLETED bookings as revenue", () => {
    const bookings = [
      makeBooking("NEW", "50.00"),
      makeBooking("CONFIRMED", "120.00"),
      makeBooking("COMPLETED", "80.00"),
      makeBooking("CANCELLED", "90.00"),
      makeBooking("EXPIRED", "60.00"),
    ];
    const confirmed = bookings.filter(
      (b) => b.status === "CONFIRMED" || b.status === "COMPLETED",
    );
    const total = addMoney(confirmed.map((b) => b.totalPrice!));
    expect(confirmed.length).toBe(2);
    expect(total).toEqual({ amount: "200.00", currencyCode: "EUR" });
  });

  it("calculates average per booking correctly", () => {
    const confirmed = [
      { totalPrice: { amount: "120.00", currencyCode: "EUR" } },
      { totalPrice: { amount: "80.00", currencyCode: "EUR" } },
    ];
    const total = addMoney(confirmed.map((b) => b.totalPrice));
    const avg = divideMoney(total, confirmed.length);
    expect(avg).toEqual({ amount: "100.00", currencyCode: "EUR" });
  });
});

// ── Calendar week helpers ──────────────────────────────────────────────────

describe("Week calendar helpers", () => {
  function getMonday(d: Date): Date {
    const day  = d.getDay();
    const diff = day === 0 ? -6 : 1 - day;
    const m    = new Date(d);
    m.setDate(d.getDate() + diff);
    m.setHours(0, 0, 0, 0);
    return m;
  }

  /** Build a Date at local midnight to avoid UTC-offset surprises */
  function localDate(y: number, m: number, d: number): Date {
    return new Date(y, m - 1, d, 12, 0, 0, 0); // noon — unambiguous day
  }

  function isoDate(d: Date): string {
    const y  = d.getFullYear();
    const mo = String(d.getMonth() + 1).padStart(2, "0");
    const da = String(d.getDate()).padStart(2, "0");
    return `${y}-${mo}-${da}`;
  }

  it("getMonday returns Monday for a Wednesday", () => {
    const wed = localDate(2026, 9, 23); // Wednesday 2026-09-23
    const mon = getMonday(wed);
    expect(isoDate(mon)).toBe("2026-09-21");
  });

  it("getMonday returns Monday for a Sunday", () => {
    const sun = localDate(2026, 9, 27); // Sunday 2026-09-27
    const mon = getMonday(sun);
    expect(isoDate(mon)).toBe("2026-09-21");
  });

  it("getMonday returns itself for a Monday", () => {
    const mon = localDate(2026, 9, 21);
    expect(isoDate(getMonday(mon))).toBe("2026-09-21");
  });

  it("generates 7 days from Monday to Sunday", () => {
    const start = localDate(2026, 9, 21);
    const days  = Array.from({ length: 7 }, (_, i) => {
      const d = new Date(start);
      d.setDate(d.getDate() + i);
      return isoDate(d);
    });
    expect(days.length).toBe(7);
    expect(days[0]).toBe("2026-09-21");
    expect(days[6]).toBe("2026-09-27");
  });
});

describe("Booking reference format", () => {
  it("matches STR-YYYY-N pattern", () => {
    const refs = ["STR-2026-1", "STR-2026-100", "STR-2026-9999"];
    const pattern = /^STR-\d{4}-\d+$/;
    for (const ref of refs) {
      expect(ref).toMatch(pattern);
    }
  });

  it("UUID booking ID is not the same as reference", () => {
    const id = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";
    const ref = "STR-2026-42";
    expect(id).not.toBe(ref);
    expect(ref).toMatch(/^STR-/);
    expect(id).toMatch(/^[0-9a-f-]{36}$/i);
  });
});

// ── Staff tour payload shapes ──────────────────────────────────────────────

describe("CreateTourPayload shape", () => {
  it("accepts a minimal valid payload", () => {
    const payload: CreateTourPayload = {
      slug: "super-safari-adventure",
      category: "DESERT",
      durationText: "5–6 hours",
      priceAdultCents: 3500,
      capacity: 20,
      availableTimeSlots: ["MORNING", "SUNSET"],
      sortOrder: 1,
    };
    expect(payload.slug).toBe("super-safari-adventure");
    expect(payload.priceAdultCents).toBe(3500);
    expect(payload.priceChildCents).toBeUndefined();
  });

  it("accepts optional fields", () => {
    const payload: CreateTourPayload = {
      slug: "private-boat",
      category: "SEA",
      durationText: "4–8 hours",
      priceAdultCents: 0,
      capacity: 20,
      availableTimeSlots: ["MORNING"],
      sortOrder: 12,
      nameEn: "Private Boat",
      tourType: "REQUEST_ONLY",
      imageUrl: "https://example.com/img.jpg",
      cancellationPolicy: "STANDARD",
      pricingNote: "Price on request — contact via WhatsApp",
    };
    expect(payload.tourType).toBe("REQUEST_ONLY");
    expect(payload.pricingNote).toBeTruthy();
  });

  it("UpdateTourPayload omits slug", () => {
    const payload: UpdateTourPayload = {
      category: "DESERT",
      durationText: "3 hours",
      priceAdultCents: 2500,
      capacity: 10,
      availableTimeSlots: ["MORNING"],
      sortOrder: 5,
    };
    // slug should not exist on UpdateTourPayload at runtime
    expect("slug" in payload).toBe(false);
  });
});

// ── Staff tour activate/deactivate logic ───────────────────────────────────

describe("Tour active toggle logic", () => {
  function canActivate(tour: Pick<Tour, "isActive" | "tourType">): boolean {
    return !tour.isActive && tour.tourType !== "REQUEST_ONLY";
  }
  function canDeactivate(tour: Pick<Tour, "isActive">): boolean {
    return tour.isActive;
  }

  it("inactive TOUR can be activated", () => {
    expect(canActivate({ isActive: false, tourType: "TOUR" })).toBe(true);
  });

  it("active TOUR can be deactivated", () => {
    expect(canDeactivate({ isActive: true })).toBe(true);
  });

  it("REQUEST_ONLY cannot be activated", () => {
    expect(canActivate({ isActive: false, tourType: "REQUEST_ONLY" })).toBe(false);
  });

  it("already active tour cannot be activated again (no-op guard)", () => {
    expect(canActivate({ isActive: true, tourType: "TOUR" })).toBe(false);
  });

  it("already inactive tour cannot be deactivated again (no-op guard)", () => {
    expect(canDeactivate({ isActive: false })).toBe(false);
  });
});
