// API client for the tours-operator product endpoints.
// All paths are relative (/api/...) — Vite proxy in dev, Nginx in production.
// Follows the same pattern as erp/app/composables/useDiversApi.ts.

export type TourCategory = "DESERT" | "SEA" | "CULTURAL" | "SHOWS" | "TRANSFERS";
export type TimeSlot = "SUNRISE" | "MORNING" | "AFTERNOON" | "SUNSET";
export type BookingStatus = "NEW" | "CONFIRMED" | "COMPLETED" | "CANCELLED" | "EXPIRED";

export const PAGE_SIZE = 50;

export interface Tour {
  id: string;
  slug: string;
  category: TourCategory;
  durationText: string;
  priceAdultCents: number;
  priceChildCents: number | null;
  capacity: number;
  availableTimeSlots: TimeSlot[];
  sortOrder: number;
  isActive: boolean;
  createdAt: string;
}

export interface TourSlot {
  id: string;
  tourId: string;
  date: string;       // ISO date — "2026-10-03"
  timeSlot: TimeSlot;
  capacity: number;
  bookedCount: number;
  available: number;  // computed: capacity - bookedCount
  isBlocked: boolean;
}

export interface BookingCustomer {
  fullName: string;
  phone: string;
  nationality: string; // ISO-2, e.g. "EG"
  email: string | null;
}

export interface Booking {
  id: string;
  reference: string;   // STR-YYYY-NNNN  — public identifier
  tourId: string;
  slotId: string;
  tourDate: string;
  timeSlot: TimeSlot;
  adultsCount: number;
  childrenCount: number;
  priceAdultEur: string;    // numeric string: "35.00"
  priceChildEur: string | null;
  totalEur: string;         // numeric string: "90.00"
  customer: BookingCustomer;
  hotelName: string;
  hotelRoom: string | null;
  specialRequests: string | null;
  locale: string;
  status: BookingStatus;
  createdAt: string;
  confirmedAt: string | null;
  cancelledAt: string | null;
  cancellationReason: string | null;
  completedAt: string | null;
  expiredAt: string | null;
}

/** Carries the API's own error code so callers can render a specific message. */
export class ToursApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly errorCode: string,
  ) {
    super(errorCode);
  }
}

async function request<T>(path: string, token: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { ...(init.headers ?? {}), Authorization: `Bearer ${token}` },
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    const errorCode =
      body && typeof body === "object" && "error" in body
        ? String(body.error)
        : `http_${response.status}`;
    throw new ToursApiError(response.status, errorCode);
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

// ── Tours ──────────────────────────────────────────────────────────────────

export function listTours(
  token: string,
  params: { activeOnly?: boolean; category?: TourCategory; page?: number; size?: number } = {},
): Promise<Tour[]> {
  const q = new URLSearchParams();
  if (params.activeOnly !== undefined) q.set("activeOnly", String(params.activeOnly));
  if (params.category) q.set("category", params.category);
  q.set("page", String(params.page ?? 0));
  q.set("size", String(params.size ?? PAGE_SIZE));
  return request<Tour[]>(`/api/v1/tours-operator/tours?${q}`, token);
}

export function getTour(token: string, id: string): Promise<Tour> {
  return request<Tour>(`/api/v1/tours-operator/tours/${id}`, token);
}

// ── Slots ──────────────────────────────────────────────────────────────────

export function listSlotsByDate(
  token: string,
  tourId: string,
  date: string,
): Promise<TourSlot[]> {
  const q = new URLSearchParams({ date });
  return request<TourSlot[]>(`/api/v1/tours-operator/tours/${tourId}/slots/by-date?${q}`, token);
}

export function listSlotsByRange(
  token: string,
  tourId: string,
  from: string,
  to: string,
): Promise<TourSlot[]> {
  const q = new URLSearchParams({ from, to });
  return request<TourSlot[]>(`/api/v1/tours-operator/tours/${tourId}/slots?${q}`, token);
}

// ── Bookings ───────────────────────────────────────────────────────────────

export function listBookings(
  token: string,
  params: {
    tourId?: string;
    status?: BookingStatus;
    date?: string;
    page?: number;
    size?: number;
  } = {},
): Promise<Booking[]> {
  const q = new URLSearchParams();
  if (params.tourId) q.set("tourId", params.tourId);
  if (params.status) q.set("status", params.status);
  if (params.date)   q.set("date", params.date);
  q.set("page", String(params.page ?? 0));
  q.set("size", String(params.size ?? PAGE_SIZE));
  return request<Booking[]>(`/api/v1/tours-operator/bookings?${q}`, token);
}

export function getBooking(token: string, id: string): Promise<Booking> {
  return request<Booking>(`/api/v1/tours-operator/bookings/${id}`, token);
}

export function confirmBooking(token: string, id: string): Promise<Booking> {
  return request<Booking>(`/api/v1/tours-operator/bookings/${id}/confirm`, token, {
    method: "POST",
  });
}

export function cancelBooking(token: string, id: string, reason: string): Promise<Booking> {
  return request<Booking>(`/api/v1/tours-operator/bookings/${id}/cancel`, token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason }),
  });
}

export function completeBooking(token: string, id: string): Promise<Booking> {
  return request<Booking>(`/api/v1/tours-operator/bookings/${id}/complete`, token, {
    method: "POST",
  });
}
