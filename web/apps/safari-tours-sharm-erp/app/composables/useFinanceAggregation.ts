/**
 * Finance aggregation composable for the Safari Tours Sharm ERP.
 *
 * All functions are pure (no network calls) so they are directly
 * unit-testable with Vitest.  The page layer fetches the bookings
 * list from the API and passes the result here.
 */
import {
  addMoney,
  divideMoney,
  moneyToMinorUnits,
} from "@wego/api-contract";
import type { Booking, Money, Tour } from "@wego/api-contract";

// ── Types ──────────────────────────────────────────────────────────────────

export interface RevenueSummary {
  /** Total revenue from CONFIRMED + COMPLETED bookings. */
  totalRevenue: Money;
  /** Number of revenue-generating bookings. */
  confirmedCount: number;
  /** Total number of bookings (all statuses). */
  totalCount: number;
  /** Total pax (adults + children) across revenue bookings. */
  paxTotal: number;
  /** Average revenue per confirmed/completed booking. */
  averagePerBooking: Money | null;
}

export interface RevenueByTourRow {
  tourId: string;
  tourSlug: string | null;
  bookingCount: number;
  total: Money;
  /** Share of total revenue as a number 0–100. */
  sharePercent: number;
}

export interface BookingStatusCount {
  status: string;
  count: number;
}

// ── Helpers ────────────────────────────────────────────────────────────────

const REVENUE_STATUSES = new Set(["CONFIRMED", "COMPLETED"]);

/** Returns only bookings that contribute to revenue. */
export function revenueBookings(bookings: Booking[]): Booking[] {
  return bookings.filter((b) => REVENUE_STATUSES.has(b.status));
}

/**
 * Filters bookings to the inclusive date range [from, to].
 * Dates are compared as ISO-8601 strings (YYYY-MM-DD) — lexicographic
 * comparison is correct for this format.
 */
export function filterByDateRange(
  bookings: Booking[],
  from: string,
  to: string,
): Booking[] {
  return bookings.filter((b) => b.tourDate >= from && b.tourDate <= to);
}

// ── Aggregation functions ──────────────────────────────────────────────────

/**
 * Computes the top-level revenue KPIs for a set of bookings.
 */
export function computeRevenueSummary(bookings: Booking[]): RevenueSummary {
  const revenue = revenueBookings(bookings);
  const totalRevenue =
    revenue.length > 0
      ? addMoney(revenue.map((b) => b.totalPrice))
      : { amount: "0.00", currencyCode: "EUR" };

  const paxTotal = revenue.reduce(
    (sum, b) => sum + b.adultsCount + b.childrenCount,
    0,
  );

  const averagePerBooking =
    revenue.length > 0 ? divideMoney(totalRevenue, revenue.length) : null;

  return {
    totalRevenue,
    confirmedCount: revenue.length,
    totalCount: bookings.length,
    paxTotal,
    averagePerBooking,
  };
}

/**
 * Groups revenue by tour, sorted descending by total revenue.
 * Tours with no bookings are excluded.
 */
export function computeRevenueByTour(
  bookings: Booking[],
  tours: Tour[],
): RevenueByTourRow[] {
  const tourMap = new Map(tours.map((t) => [t.id, t]));
  const revenue = revenueBookings(bookings);

  const map = new Map<
    string,
    { count: number; total: Money; slug: string | null }
  >();

  for (const b of revenue) {
    const existing = map.get(b.tourId);
    if (!existing) {
      map.set(b.tourId, {
        count: 1,
        total: { ...b.totalPrice },
        slug: tourMap.get(b.tourId)?.slug ?? null,
      });
    } else {
      existing.count++;
      existing.total = addMoney([existing.total, b.totalPrice]);
    }
  }

  // Compute total for share percentage
  const grandTotal = revenue.length > 0
    ? moneyToMinorUnits(addMoney(revenue.map((b) => b.totalPrice)))
    : 0n;

  const rows: RevenueByTourRow[] = Array.from(map.entries()).map(
    ([tourId, { count, total, slug }]) => ({
      tourId,
      tourSlug: slug,
      bookingCount: count,
      total,
      sharePercent:
        grandTotal > 0n
          ? Math.round(
              (Number(moneyToMinorUnits(total)) / Number(grandTotal)) * 100,
            )
          : 0,
    }),
  );

  return rows.sort((a, b) => {
    const diff =
      moneyToMinorUnits(b.total) - moneyToMinorUnits(a.total);
    return diff === 0n ? 0 : diff > 0n ? 1 : -1;
  });
}

/**
 * Counts bookings by status, returns all 5 statuses (0 if none).
 */
export function computeStatusCounts(bookings: Booking[]): BookingStatusCount[] {
  const ALL_STATUSES = ["NEW", "CONFIRMED", "COMPLETED", "CANCELLED", "EXPIRED"];
  const counts: Record<string, number> = {};
  for (const b of bookings) {
    counts[b.status] = (counts[b.status] ?? 0) + 1;
  }
  return ALL_STATUSES.map((status) => ({ status, count: counts[status] ?? 0 }));
}

/**
 * Groups revenue bookings by tourDate (YYYY-MM-DD), sorted ascending.
 * Useful for a revenue-over-time chart or daily breakdown table.
 */
export function computeDailyRevenue(
  bookings: Booking[],
): Array<{ date: string; count: number; total: Money }> {
  const revenue = revenueBookings(bookings);
  const map = new Map<string, { count: number; total: Money }>();

  for (const b of revenue) {
    const existing = map.get(b.tourDate);
    if (!existing) {
      map.set(b.tourDate, { count: 1, total: { ...b.totalPrice } });
    } else {
      existing.count++;
      existing.total = addMoney([existing.total, b.totalPrice]);
    }
  }

  return Array.from(map.entries())
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
    .map(([date, { count, total }]) => ({ date, count, total }));
}
