// Public API client — no authentication required.
// API shapes are generated from the platform OpenAPI contract and shared with the ERP.
import { formatMoney } from "@wego/api-contract";
import type {
  Booking,
  CreateBookingPayload,
  TimeSlot,
  Tour,
  TourCategory,
  TourSlot,
} from "@wego/api-contract";

export type {
  Booking,
  BookingStatus,
  CreateBookingPayload,
  TimeSlot,
  Tour,
  TourCategory,
  TourSlot,
} from "@wego/api-contract";

/** Compatibility name used by the public pages. */
export type BookingConfirmation = Booking;
export type PublicBookingLookup = Pick<
  Booking,
  | "reference"
  | "tourDate"
  | "timeSlot"
  | "adultsCount"
  | "childrenCount"
  | "totalPrice"
  | "hotelName"
  | "status"
  | "cancellationReason"
  | "unit"
>;
export { calculateBookingTotal, formatMoney, moneyToMinorUnits, multiplyMoney } from "@wego/api-contract";

export class PublicApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly errorCode: string,
  ) {
    super(errorCode);
  }
}

// ── HTTP helper ────────────────────────────────────────────────────────────

async function get<T>(path: string): Promise<T> {
  const res = await fetch(path);
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    const code =
      body && typeof body === "object" && "error" in body
        ? String(body.error)
        : `http_${res.status}`;
    throw new PublicApiError(res.status, code);
  }
  return res.json() as Promise<T>;
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const errBody = await res.json().catch(() => null);
    const code =
      errBody && typeof errBody === "object" && "error" in errBody
        ? String(errBody.error)
        : `http_${res.status}`;
    throw new PublicApiError(res.status, code);
  }
  return res.json() as Promise<T>;
}

// ── Public Tour endpoints ──────────────────────────────────────────────────

export function listToursPublic(params: {
  category?: TourCategory;
  page?: number;
  size?: number;
} = {}): Promise<Tour[]> {
  const q = new URLSearchParams();
  q.set("activeOnly", "true");
  if (params.category) q.set("category", params.category);
  q.set("page", String(params.page ?? 0));
  q.set("size", String(params.size ?? 50));
  return get<Tour[]>(`/api/v1/tours-operator/tours?${q}`);
}

export function getTourPublic(id: string): Promise<Tour> {
  return get<Tour>(`/api/v1/tours-operator/tours/${id}`);
}

export async function getTourBySlug(slug: string): Promise<Tour | null> {
  const q = new URLSearchParams({ slug });
  try {
    return await get<Tour>(`/api/v1/tours-operator/tours/by-slug?${q}`);
  } catch (error) {
    if (error instanceof PublicApiError && error.status === 404) return null;
    throw error;
  }
}

// ── Public Slot endpoints ──────────────────────────────────────────────────

export function getAvailableSlots(
  tourId: string,
  from: string,
  to: string,
): Promise<TourSlot[]> {
  const q = new URLSearchParams({ from, to });
  return get<TourSlot[]>(`/api/v1/tours-operator/tours/${tourId}/slots?${q}`);
}

// ── Public Booking endpoints ───────────────────────────────────────────────

/** Create a NEW booking — no authentication required. */
export function createBooking(payload: CreateBookingPayload): Promise<BookingConfirmation> {
  return post<BookingConfirmation>("/api/v1/tours-operator/bookings", payload);
}

// ── Payment endpoints ──────────────────────────────────────────────────────

export interface InitiatePaymentResponse {
  paymentId: string;
  bookingId: string;
  checkoutUrl: string;
  amountEur: string;
  currencyCode: string;
  status: "PENDING" | "PAID" | "FAILED" | "REFUNDED" | "REVIEW_REQUIRED" | "RECONCILIATION_REQUIRED";
}

export interface PaymentStatusResponse {
  paymentId: string;
  bookingId: string;
  amountEur: string;
  currencyCode: string;
  status: "PENDING" | "PAID" | "FAILED" | "REFUNDED" | "REVIEW_REQUIRED" | "RECONCILIATION_REQUIRED";
  paidAt: string | null;
  failedAt: string | null;
}

/** Whether a manager paused online booking or payment (emergency switch). */
export function getSalesStatus(): Promise<{ bookingsOpen: boolean; paymentsOpen: boolean }> {
  return get("/api/v1/tours-operator/sales-status");
}

/**
 * Initiate Paymob payment for a NEW booking.
 * Returns a checkout URL to redirect the customer to.
 * Idempotent — safe to call again if the customer refreshes.
 */
export function initiatePayment(bookingId: string): Promise<InitiatePaymentResponse> {
  return post<InitiatePaymentResponse>(
    `/api/v1/tours-operator/bookings/${bookingId}/pay`,
    {},
  );
}

/**
 * Poll payment status after customer returns from Paymob checkout.
 * The return URL page calls this to determine CONFIRMED vs FAILED.
 */
export function getPaymentStatus(bookingId: string): Promise<PaymentStatusResponse> {
  return get<PaymentStatusResponse>(
    `/api/v1/tours-operator/bookings/${bookingId}/payment-status`,
  );
}

/** Customer self-lookup by reference + phone — no authentication. */
export function lookupBooking(
  reference: string,
  phone: string,
): Promise<PublicBookingLookup> {
  // Both values are knowledge factors/PII. Keep them in the POST body so they
  // cannot leak through browser history, referrers, proxy URLs, or access logs.
  return post<PublicBookingLookup>("/api/v1/tours-operator/bookings/lookup", {
    reference,
    phone,
  });
}

const CONFIRMATION_STORAGE_PREFIX = "sts.booking-confirmation.";
export const LATEST_CONFIRMATION_STORAGE_KEY = "sts.booking-confirmation.latest";

function parseStoredBooking(raw: string | null): BookingConfirmation | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as BookingConfirmation;
  } catch {
    return null;
  }
}

/** Keep a just-created booking across the redirect without putting customer PII in the URL. */
export function storeBookingConfirmation(booking: BookingConfirmation): void {
  if (typeof sessionStorage === "undefined") return;
  const serialized = JSON.stringify(booking);
  sessionStorage.setItem(`${CONFIRMATION_STORAGE_PREFIX}${booking.reference}`, serialized);
  sessionStorage.setItem(LATEST_CONFIRMATION_STORAGE_KEY, serialized);
}

export function readStoredBookingConfirmation(reference: string): BookingConfirmation | null {
  if (typeof sessionStorage === "undefined") return null;
  const raw = sessionStorage.getItem(`${CONFIRMATION_STORAGE_PREFIX}${reference}`);
  if (!raw) return null;
  sessionStorage.removeItem(`${CONFIRMATION_STORAGE_PREFIX}${reference}`);
  return parseStoredBooking(raw);
}

/** Read the return-flow booking without consuming it before confirmation. */
export function peekLatestBookingConfirmation(): BookingConfirmation | null {
  if (typeof sessionStorage === "undefined") return null;
  return parseStoredBooking(sessionStorage.getItem(LATEST_CONFIRMATION_STORAGE_KEY));
}

/** Consume return-flow data once the confirmation page has rendered it. */
export function takeLatestBookingConfirmation(): BookingConfirmation | null {
  if (typeof sessionStorage === "undefined") return null;
  const booking = parseStoredBooking(sessionStorage.getItem(LATEST_CONFIRMATION_STORAGE_KEY));
  sessionStorage.removeItem(LATEST_CONFIRMATION_STORAGE_KEY);
  if (booking) {
    sessionStorage.removeItem(`${CONFIRMATION_STORAGE_PREFIX}${booking.reference}`);
  }
  return booking;
}

// ── Helpers ────────────────────────────────────────────────────────────────

/** Product-facing alias kept for readable templates. */
export const formatPrice = formatMoney;

/** "MORNING" → "Morning" */
export function formatTimeSlot(slot: TimeSlot): string {
  return slot.charAt(0) + slot.slice(1).toLowerCase();
}

/** ISO date → "Friday, 3 October 2026" */
export function formatDate(iso: string, locale: string = "en"): string {
  return new Date(iso).toLocaleDateString(locale, {
    weekday: "long",
    day: "numeric",
    month: "long",
    year: "numeric",
  });
}
