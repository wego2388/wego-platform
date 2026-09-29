// API client for the tours-operator product endpoints.
// API shapes are generated from the platform OpenAPI contract and shared with the public site.
import type {
  Booking,
  BookingStatus,
  Tour,
  TourCategory,
  TourSlot,
  PaymentLedgerEntry,
  ToursOperatorPaymentStatus,
} from "@wego/api-contract";

export type {
  Booking,
  BookingCustomer,
  BookingStatus,
  Money,
  TimeSlot,
  Tour,
  TourCategory,
  TourSlot,
  PaymentLedgerEntry,
  ToursOperatorPaymentStatus,
} from "@wego/api-contract";
export { addMoney, divideMoney, formatMoney, minorUnitsToMoney, moneyToMinorUnits } from "@wego/api-contract";

export const PAGE_SIZE = 50;

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

export function listPaymentLedger(
  token: string,
  params: {
    from: string;
    to: string;
    status?: ToursOperatorPaymentStatus;
    /** Keyset cursor: the last paymentId of the previous page. */
    after?: string;
    size?: number;
  },
): Promise<PaymentLedgerEntry[]> {
  const q = new URLSearchParams({
    from: params.from,
    to: params.to,
    size: String(params.size ?? PAGE_SIZE),
  });
  if (params.status) q.set("status", params.status);
  if (params.after) q.set("after", params.after);
  return request<PaymentLedgerEntry[]>(`/api/v1/tours-operator/staff/payments?${q}`, token);
}

// ── Staff tours ────────────────────────────────────────────────────────────

export type CreateTourPayload = {
  slug: string;
  category: TourCategory;
  durationText: string;
  priceAdultCents: number;
  priceChildCents?: number | null;
  capacity: number;
  availableTimeSlots: string[];
  sortOrder: number;
  nameEn?: string | null;
  tourType?: string | null;
  imageUrl?: string | null;
  cancellationPolicy?: string | null;
  pricingNote?: string | null;
};

export type UpdateTourPayload = Omit<CreateTourPayload, "slug">;

export function listStaffTours(
  token: string,
  params: { activeOnly?: boolean; category?: TourCategory; page?: number; size?: number } = {},
): Promise<Tour[]> {
  const q = new URLSearchParams();
  if (params.activeOnly !== undefined) q.set("activeOnly", String(params.activeOnly));
  if (params.category) q.set("category", params.category);
  q.set("page", String(params.page ?? 0));
  q.set("size", String(params.size ?? PAGE_SIZE));
  return request<Tour[]>(`/api/v1/tours-operator/staff/tours?${q}`, token);
}

/** Server-side cap for tour list pages (`@Max(100)` on both tour list endpoints). */
const TOUR_PAGE_MAX = 100;

/**
 * Loads every tour for id→name lookups, including deactivated tours that still
 * own historical bookings and payments. Requires `tours-operator.tour:view`.
 */
export async function listAllStaffTours(token: string): Promise<Tour[]> {
  const tours: Tour[] = [];
  for (let page = 0; ; page++) {
    const batch = await listStaffTours(token, { page, size: TOUR_PAGE_MAX });
    tours.push(...batch);
    if (batch.length < TOUR_PAGE_MAX) return tours;
  }
}

export function getStaffTour(token: string, id: string): Promise<Tour> {
  return request<Tour>(`/api/v1/tours-operator/staff/tours/${id}`, token);
}

export function createTour(token: string, payload: CreateTourPayload): Promise<Tour> {
  return request<Tour>("/api/v1/tours-operator/staff/tours", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function updateTour(token: string, id: string, payload: UpdateTourPayload) {
  return request<undefined>(`/api/v1/tours-operator/staff/tours/${id}`, token, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function activateTour(token: string, id: string) {
  return request<undefined>(`/api/v1/tours-operator/staff/tours/${id}/activate`, token, {
    method: "PATCH",
  });
}

export function deactivateTour(token: string, id: string) {
  return request<undefined>(`/api/v1/tours-operator/staff/tours/${id}/deactivate`, token, {
    method: "PATCH",
  });
}

// ── Staff slots ────────────────────────────────────────────────────────────

export function createSlot(
  token: string,
  tourId: string,
  payload: { date: string; timeSlot: string; capacity: number },
): Promise<TourSlot> {
  return request<TourSlot>(`/api/v1/tours-operator/staff/tours/${tourId}/slots`, token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function blockSlot(token: string, tourId: string, slotId: string) {
  return request<undefined>(
    `/api/v1/tours-operator/staff/tours/${tourId}/slots/${slotId}/block`,
    token,
    { method: "PATCH" },
  );
}

export function unblockSlot(token: string, tourId: string, slotId: string) {
  return request<undefined>(
    `/api/v1/tours-operator/staff/tours/${tourId}/slots/${slotId}/unblock`,
    token,
    { method: "PATCH" },
  );
}
