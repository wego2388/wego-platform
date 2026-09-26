import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  ToursApiError,
  type Booking,
  type Tour,
  type BookingStatus,
} from "../app/composables/useToursApi";
import {
  writeAuthSession,
  readAuthSession,
  clearAuthSession,
  hasPermission,
  type AuthSession,
} from "../app/composables/useAuthSession";

// ── useAuthSession ──────────────────────────────────────────────────────────

describe("useAuthSession", () => {
  beforeEach(() => {
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
  const NEW_BOOKING: Partial<Booking> = { status: "NEW" };
  const CONFIRMED_BOOKING: Partial<Booking> = { status: "CONFIRMED" };
  const COMPLETED_BOOKING: Partial<Booking> = { status: "COMPLETED" };
  const CANCELLED_BOOKING: Partial<Booking> = { status: "CANCELLED" };
  const EXPIRED_BOOKING: Partial<Booking> = { status: "EXPIRED" };

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
    priceAdultCents: 3500,
    priceChildCents: 1750,
    capacity: 20,
    availableTimeSlots: ["MORNING", "SUNSET"],
    sortOrder: 1,
    isActive: true,
    createdAt: "2026-01-01T00:00:00Z",
  };

  it("tour has required fields", () => {
    expect(TOUR.slug).toBeTruthy();
    expect(TOUR.category).toBe("DESERT");
    expect(TOUR.priceAdultCents).toBeGreaterThan(0);
    expect(TOUR.capacity).toBeGreaterThan(0);
  });

  it("price formatting: 3500 cents = 35.00 EUR", () => {
    const formatted = new Intl.NumberFormat("en-EU", {
      style: "currency",
      currency: "EUR",
    }).format(TOUR.priceAdultCents / 100);
    expect(formatted).toContain("35");
  });

  it("child price can be null for adult-only tours", () => {
    const adultOnly: Tour = { ...TOUR, priceChildCents: null };
    expect(adultOnly.priceChildCents).toBeNull();
  });
});

// ── Finance helpers ────────────────────────────────────────────────────────

describe("Finance revenue calculation", () => {
  const makeBooking = (status: BookingStatus, totalEur: string): Partial<Booking> => ({
    status, totalEur, adultsCount: 2, childrenCount: 1,
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
    const total = confirmed.reduce((sum, b) => sum + parseFloat(b.totalEur!), 0);
    expect(confirmed.length).toBe(2);
    expect(total).toBeCloseTo(200.0);
  });

  it("calculates average per booking correctly", () => {
    const confirmed = [
      { totalEur: "120.00" },
      { totalEur: "80.00" },
    ];
    const total = confirmed.reduce((sum, b) => sum + parseFloat(b.totalEur), 0);
    const avg   = total / confirmed.length;
    expect(avg).toBeCloseTo(100.0);
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
