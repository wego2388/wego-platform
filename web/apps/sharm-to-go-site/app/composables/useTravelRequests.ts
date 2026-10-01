// Same-origin-proxy convention as usePublicCatalog.ts — the browser never
// talks to the real backend directly (CORS), it goes through this app's own
// server/api/requests/* routes. Mirrors
// platform/contracts/openapi/v1/sharm-to-go-api.yaml's
// TravelRequestPublicResponse shape exactly — no customer name/phone/email,
// by design (see PublicTravelRequestController's own doc comment).

import type { LocalizedText, PriceBasis } from "./usePublicCatalog";

export type TravelRequestStatus = "NEW" | "IN_REVIEW" | "CONFIRMED" | "COMPLETED" | "CANCELLED" | "EXPIRED";
export type TravelRequestSourceChannel = "WEBSITE" | "MOBILE";

/**
 * Translated, customer-facing status text — never the raw status string.
 * Used by both the tracking page and the request flow's own shareable
 * summary, which previously embedded `result.status` directly, showing
 * "IN_REVIEW" etc. even on the Arabic page.
 */
const STATUS_TEXT: Record<TravelRequestStatus, { en: string; ar: string }> = {
  NEW: { en: "Received, awaiting review", ar: "تم الاستلام، في انتظار المراجعة" },
  IN_REVIEW: { en: "Under review", ar: "قيد المراجعة" },
  CONFIRMED: { en: "Confirmed", ar: "مؤكد" },
  COMPLETED: { en: "Completed", ar: "تم الإنجاز" },
  CANCELLED: { en: "Cancelled", ar: "ملغى" },
  EXPIRED: { en: "Expired", ar: "منتهي الصلاحية" },
};

export function travelRequestStatusText(status: TravelRequestStatus, locale: "en" | "ar"): string {
  return STATUS_TEXT[status][locale];
}

export interface TravelRequestCustomer {
  name: string;
  phone?: string;
  email?: string;
}

export interface CreateTravelRequestBody {
  serviceId: string;
  serviceOptionId: string;
  requestedDate: string;
  requestedTime?: string;
  adults: number;
  children: number;
  hotelOrPickup?: string;
  locale: "en" | "ar";
  notes?: string;
  sourceChannel: TravelRequestSourceChannel;
  customer: TravelRequestCustomer;
  /** The price the review screen actually showed — see the backend's own doc comment on this field for why. */
  expectedPriceAmount: string;
  expectedPriceCurrency: string;
}

export interface TravelRequestPublicResponse {
  reference: string;
  status: TravelRequestStatus;
  serviceName: LocalizedText;
  optionLabel: LocalizedText;
  priceAmount: string;
  priceCurrency: string;
  priceBasis: PriceBasis;
  cancellationPolicy: LocalizedText;
  requestedDate: string;
  requestedTime?: string;
  adults: number;
  children: number;
  hotelOrPickup?: string;
  createdAt: string;
  confirmedAt?: string;
  completedAt?: string;
  cancelledAt?: string;
  expiredAt?: string;
}

export class TravelRequestError extends Error {
  constructor(
    public readonly status: number,
    public readonly errorCode: string,
  ) {
    super(errorCode);
  }
}

/**
 * One of these must be generated per *logical submission attempt* — by the
 * caller, once, held across retries of that same attempt — never freshly
 * inside `createTravelRequest` itself. A key minted fresh on every call
 * defeats the backend's idempotency protection on exactly the case it
 * exists for: the response to a successful write gets lost (a network
 * blip, a timeout) and the visitor's retry would otherwise create a
 * second, possibly instantly-confirmed, request.
 */
export function newIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) return crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export async function createTravelRequest(body: CreateTravelRequestBody, idempotencyKey: string): Promise<TravelRequestPublicResponse> {
  const response = await fetch("/api/requests", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(body),
  });
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    const errorCode = payload && typeof payload === "object" && "error" in payload ? String(payload.error) : `http_${response.status}`;
    throw new TravelRequestError(response.status, errorCode);
  }
  return payload as TravelRequestPublicResponse;
}

/** Returns null for a 404 — an unknown or mistyped reference. */
export async function getTravelRequestByReference(reference: string): Promise<TravelRequestPublicResponse | null> {
  const response = await fetch(`/api/requests/${encodeURIComponent(reference)}`);
  if (response.status === 404) return null;
  if (!response.ok) throw new TravelRequestError(response.status, `http_${response.status}`);
  return response.json();
}
