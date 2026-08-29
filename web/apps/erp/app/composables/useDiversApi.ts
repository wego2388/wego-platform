// Explicit imports/exports only — see useAuthSession.ts for why (no Nuxt
// auto-import runtime in the Vitest environment).

export interface Money {
  amount: string;
  currencyCode: string;
}

export type OfferingType = "DIVE_TRIP" | "COURSE" | "EQUIPMENT_RENTAL" | "PACKAGE";
export type OfferingStatus = "ACTIVE" | "CLOSED";
export type BookingStatus = "CONFIRMED" | "CANCELLED";
export type PaymentStatus = "UNPAID" | "PAID" | "REFUNDED";
export type PricingBasis = "PER_PARTICIPANT" | "FLAT";

export const PAGE_SIZE = 50;

export interface Offering {
  id: string;
  offeringType: OfferingType;
  title: string;
  description?: string;
  startsOn: string;
  endsOn?: string;
  capacity?: number;
  pricingBasis: PricingBasis;
  unitPrice: Money;
  status: OfferingStatus;
  createdAt: string;
  closedAt?: string;
}

export interface Booking {
  id: string;
  offeringId: string;
  partySize: number;
  customerName: string;
  customerEmail?: string;
  customerPhone?: string;
  status: BookingStatus;
  paymentStatus: PaymentStatus;
  pricingBasis: PricingBasis;
  unitPrice: Money;
  billableQuantity: number;
  totalPrice: Money;
  createdAt: string;
  cancelledAt?: string;
  cancellationReason?: string;
}

/** Carries the API's own `error` code (e.g. "capacity_exceeded") so callers can show a specific message. */
export class DiversApiError extends Error {
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
    const errorCode = (body && typeof body === "object" && "error" in body ? String(body.error) : null) ?? `http_${response.status}`;
    throw new DiversApiError(response.status, errorCode);
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export function listOfferings(
  token: string,
  params: { status?: OfferingStatus; page?: number; size?: number } = {},
): Promise<Offering[]> {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? PAGE_SIZE));
  return request<Offering[]>(`/api/v1/divers/offerings?${query.toString()}`, token);
}

export function getOffering(token: string, id: string): Promise<Offering> {
  return request<Offering>(`/api/v1/divers/offerings/${id}`, token);
}

export function createOffering(
  token: string,
  body: {
    offeringType: OfferingType;
    title: string;
    description?: string;
    startsOn: string;
    endsOn?: string;
    capacity?: number;
    pricingBasis: PricingBasis;
    unitPrice: Money;
  },
): Promise<Offering> {
  return request<Offering>("/api/v1/divers/offerings", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function closeOffering(token: string, id: string, reason: string): Promise<Offering> {
  return request<Offering>(`/api/v1/divers/offerings/${id}/close`, token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason: reason || undefined }),
  });
}

export function listBookings(
  token: string,
  params: { offeringId?: string; status?: BookingStatus; page?: number; size?: number } = {},
): Promise<Booking[]> {
  const query = new URLSearchParams();
  if (params.offeringId) query.set("offeringId", params.offeringId);
  if (params.status) query.set("status", params.status);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? PAGE_SIZE));
  return request<Booking[]>(`/api/v1/divers/bookings?${query.toString()}`, token);
}

export function createBooking(
  token: string,
  idempotencyKey: string,
  body: {
    offeringId: string;
    partySize: number;
    customerName: string;
    customerEmail?: string;
    customerPhone?: string;
  },
): Promise<Booking> {
  return request<Booking>("/api/v1/divers/bookings", token, {
    method: "POST",
    headers: { "Content-Type": "application/json", "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(body),
  });
}

export function cancelBooking(token: string, id: string, reason: string): Promise<Booking> {
  return request<Booking>(`/api/v1/divers/bookings/${id}/cancel`, token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason }),
  });
}

export function markBookingPaid(token: string, id: string): Promise<Booking> {
  return request<Booking>(`/api/v1/divers/bookings/${id}/mark-paid`, token, { method: "PATCH" });
}

export function refundBooking(token: string, id: string, reason: string): Promise<Booking> {
  return request<Booking>(`/api/v1/divers/bookings/${id}/refund`, token, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason }),
  });
}

export type DiverStatus = "ACTIVE" | "ARCHIVED";

export interface DiverCertification {
  id?: string;
  agency: string;
  level: string;
  certificationNumber?: string;
  issuedOn?: string;
}

export interface Diver {
  id: string;
  fullName: string;
  nationality?: string;
  primaryLanguage?: string;
  email?: string;
  phone?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  medicalNotes?: string;
  totalLoggedDives: number;
  maxDepthMeters?: string;
  lastDiveOn?: string;
  bcdSize?: string;
  finSize?: string;
  wetsuitSize?: string;
  certifications: DiverCertification[];
  status: DiverStatus;
  createdAt: string;
  archivedAt?: string;
}

export interface UpsertDiverBody {
  fullName: string;
  nationality?: string;
  primaryLanguage?: string;
  email?: string;
  phone?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  medicalNotes?: string;
  totalLoggedDives: number;
  maxDepthMeters?: number;
  lastDiveOn?: string;
  bcdSize?: string;
  finSize?: string;
  wetsuitSize?: string;
  certifications: DiverCertification[];
}

export function listDivers(
  token: string,
  params: { status?: DiverStatus; search?: string; page?: number; size?: number } = {},
): Promise<Diver[]> {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  if (params.search) query.set("search", params.search);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? PAGE_SIZE));
  return request<Diver[]>(`/api/v1/divers/divers?${query.toString()}`, token);
}

export function getDiver(token: string, id: string): Promise<Diver> {
  return request<Diver>(`/api/v1/divers/divers/${id}`, token);
}

export function createDiver(token: string, body: UpsertDiverBody): Promise<Diver> {
  return request<Diver>("/api/v1/divers/divers", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function updateDiver(token: string, id: string, body: UpsertDiverBody): Promise<Diver> {
  return request<Diver>(`/api/v1/divers/divers/${id}`, token, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function archiveDiver(token: string, id: string): Promise<Diver> {
  return request<Diver>(`/api/v1/divers/divers/${id}`, token, { method: "DELETE" });
}
