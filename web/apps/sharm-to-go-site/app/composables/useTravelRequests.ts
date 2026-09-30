// Same-origin-proxy convention as usePublicCatalog.ts — the browser never
// talks to the real backend directly (CORS), it goes through this app's own
// server/api/requests/* routes. Mirrors
// platform/contracts/openapi/v1/sharm-to-go-api.yaml's
// TravelRequestPublicResponse shape exactly — no customer name/phone/email,
// by design (see PublicTravelRequestController's own doc comment).

import type { LocalizedText, PriceBasis } from "./usePublicCatalog";

export type TravelRequestStatus = "NEW" | "IN_REVIEW" | "CONFIRMED" | "COMPLETED" | "CANCELLED" | "EXPIRED";
export type TravelRequestSourceChannel = "WEBSITE" | "MOBILE";

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

function idempotencyKey(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) return crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export async function createTravelRequest(body: CreateTravelRequestBody): Promise<TravelRequestPublicResponse> {
  const response = await fetch("/api/requests", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Idempotency-Key": idempotencyKey() },
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
