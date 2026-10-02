// Explicit imports/exports only — see useAuthSession.ts for why (no Nuxt
// auto-import runtime in the Vitest environment). Mirrors
// web/apps/erp/app/composables/useDiversApi.ts's request/error-shape
// conventions exactly, against this client's own separate backend (see
// WEGO-010-A Packet 0R) and its Packet 1A catalog contract
// (platform/contracts/openapi/v1/sharm-to-go-api.yaml).

export interface LocalizedText {
  en: string;
  ar: string;
}

export const PAGE_SIZE = 50;

/** Carries the API's own `error` code (e.g. "already_archived") so callers can show a specific message. */
export class TravelMarketplaceApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly errorCode: string,
    // Only set for "version_conflict" — the service's real current version,
    // so the caller can tell staff what to reload against.
    public readonly currentVersion?: number,
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
    const currentVersion = body && typeof body === "object" && "currentVersion" in body ? Number(body.currentVersion) : undefined;
    throw new TravelMarketplaceApiError(response.status, errorCode, currentVersion);
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export type ProviderStatus = "ACTIVE" | "ARCHIVED";

export interface Provider {
  id: string;
  name: string;
  contactEmail?: string;
  contactPhone?: string;
  status: ProviderStatus;
  createdAt: string;
  archivedAt?: string;
  version: number;
}

export interface UpsertProviderBody {
  name: string;
  contactEmail?: string;
  contactPhone?: string;
  // Required on an update (the version the editor loaded); omitted on
  // create. See useTravelMarketplaceApi.ts's UpsertServiceBody.expectedVersion.
  expectedVersion?: number;
}

export function listProviders(
  token: string,
  params: { status?: ProviderStatus; search?: string; page?: number; size?: number } = {},
): Promise<Provider[]> {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  if (params.search) query.set("search", params.search);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? PAGE_SIZE));
  return request<Provider[]>(`/api/v1/travel-marketplace/providers?${query.toString()}`, token);
}

export function createProvider(token: string, body: UpsertProviderBody): Promise<Provider> {
  return request<Provider>("/api/v1/travel-marketplace/providers", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function updateProvider(token: string, id: string, body: UpsertProviderBody): Promise<Provider> {
  return request<Provider>(`/api/v1/travel-marketplace/providers/${id}`, token, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function archiveProvider(token: string, id: string): Promise<Provider> {
  return request<Provider>(`/api/v1/travel-marketplace/providers/${id}`, token, { method: "DELETE" });
}

export type CategoryStatus = "ACTIVE" | "ARCHIVED";

export interface Category {
  id: string;
  code: string;
  name: LocalizedText;
  description?: LocalizedText;
  displayOrder: number;
  status: CategoryStatus;
  createdAt: string;
  archivedAt?: string;
  version: number;
}

export interface UpsertCategoryBody {
  code: string;
  name: LocalizedText;
  description?: LocalizedText;
  displayOrder: number;
  // Required on an update (the version the editor loaded); omitted on
  // create. See useTravelMarketplaceApi.ts's UpsertServiceBody.expectedVersion.
  expectedVersion?: number;
}

export function listCategories(token: string, params: { status?: CategoryStatus } = {}): Promise<Category[]> {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  return request<Category[]>(`/api/v1/travel-marketplace/categories?${query.toString()}`, token);
}

export function createCategory(token: string, body: UpsertCategoryBody): Promise<Category> {
  return request<Category>("/api/v1/travel-marketplace/categories", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function updateCategory(token: string, id: string, body: UpsertCategoryBody): Promise<Category> {
  return request<Category>(`/api/v1/travel-marketplace/categories/${id}`, token, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function archiveCategory(token: string, id: string): Promise<Category> {
  return request<Category>(`/api/v1/travel-marketplace/categories/${id}`, token, { method: "DELETE" });
}

export type ServiceStatus = "DRAFT" | "REVIEW" | "APPROVED" | "PUBLISHED" | "SUSPENDED" | "ARCHIVED";
export type FulfilmentModel = "DIRECT" | "PARTNER";
export type ConfirmationType = "INSTANT" | "STAFF_REVIEW";
export type PriceBasis = "PER_PERSON" | "PER_GROUP" | "PER_VEHICLE" | "FLAT";

export interface ServiceOption {
  id?: string;
  label: LocalizedText;
  durationMinutes?: number;
  maxParticipants: number;
  priceAmount: string;
  priceCurrency: string;
  priceBasis: PriceBasis;
}

export interface ServiceMedia {
  id?: string;
  assetReference: string;
  rightsEvidence: string;
  locale: string;
}

export interface Service {
  id: string;
  categoryId: string;
  name: LocalizedText;
  description: LocalizedText;
  fulfilmentModel: FulfilmentModel;
  providerId?: string;
  confirmationType: ConfirmationType;
  cancellationPolicy: LocalizedText;
  pickupInfo?: LocalizedText;
  inclusions?: LocalizedText;
  exclusions?: LocalizedText;
  options: ServiceOption[];
  media: ServiceMedia[];
  status: ServiceStatus;
  createdAt: string;
  publishedAt?: string;
  archivedAt?: string;
  version: number;
}

export interface UpsertServiceBody {
  categoryId: string;
  name: LocalizedText;
  description: LocalizedText;
  fulfilmentModel: FulfilmentModel;
  providerId?: string;
  confirmationType: ConfirmationType;
  cancellationPolicy: LocalizedText;
  pickupInfo?: LocalizedText;
  inclusions?: LocalizedText;
  exclusions?: LocalizedText;
  options: ServiceOption[];
  media: ServiceMedia[];
  // Required on an update (the version the editor loaded — a mismatch
  // means someone else saved first); omitted on create, where there is
  // nothing to be stale against yet.
  expectedVersion?: number;
}

export function listServices(
  token: string,
  params: { status?: ServiceStatus; categoryId?: string; page?: number; size?: number } = {},
): Promise<Service[]> {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  if (params.categoryId) query.set("categoryId", params.categoryId);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? PAGE_SIZE));
  return request<Service[]>(`/api/v1/travel-marketplace/services?${query.toString()}`, token);
}

export function getService(token: string, id: string): Promise<Service> {
  return request<Service>(`/api/v1/travel-marketplace/services/${id}`, token);
}

export function createService(token: string, body: UpsertServiceBody): Promise<Service> {
  return request<Service>("/api/v1/travel-marketplace/services", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function updateService(token: string, id: string, body: UpsertServiceBody): Promise<Service> {
  return request<Service>(`/api/v1/travel-marketplace/services/${id}`, token, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function submitServiceForReview(token: string, id: string): Promise<Service> {
  return request<Service>(`/api/v1/travel-marketplace/services/${id}/submit-for-review`, token, { method: "POST" });
}

export function approveService(token: string, id: string): Promise<Service> {
  return request<Service>(`/api/v1/travel-marketplace/services/${id}/approve`, token, { method: "POST" });
}

export function publishService(token: string, id: string): Promise<Service> {
  return request<Service>(`/api/v1/travel-marketplace/services/${id}/publish`, token, { method: "POST" });
}

export function suspendService(token: string, id: string): Promise<Service> {
  return request<Service>(`/api/v1/travel-marketplace/services/${id}/suspend`, token, { method: "POST" });
}

export function archiveService(token: string, id: string): Promise<Service> {
  return request<Service>(`/api/v1/travel-marketplace/services/${id}/archive`, token, { method: "POST" });
}

// --- Travel requests (WEGO-010-A Phase 1/2) ---------------------------------

export type TravelRequestStatus = "NEW" | "IN_REVIEW" | "CONFIRMED" | "COMPLETED" | "CANCELLED" | "EXPIRED";
export type TravelRequestSourceChannel = "WEBSITE" | "MOBILE";
export type TravelRequestCancelReason = "CUSTOMER_REQUESTED" | "STAFF_REJECTED" | "SERVICE_UNAVAILABLE" | "DUPLICATE_REQUEST" | "OTHER";
export type TravelRequestActorType = "CUSTOMER" | "STAFF" | "SYSTEM";

export interface TravelRequestCustomer {
  name: string;
  phone?: string;
  email?: string;
}

/** The full staff-facing shape — `TravelRequestStaffResponse` on the backend. Includes customer contact; the public shape (never used here) does not. */
export interface TravelRequest {
  id: string;
  reference: string;
  status: TravelRequestStatus;
  serviceId: string;
  serviceOptionId: string;
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
  locale: string;
  notes?: string;
  sourceChannel: TravelRequestSourceChannel;
  customer: TravelRequestCustomer;
  createdAt: string;
  confirmedAt?: string;
  completedAt?: string;
  cancelledAt?: string;
  cancelReason?: TravelRequestCancelReason;
  cancelDetail?: string;
  expiredAt?: string;
}

export interface TravelRequestAuditEvent {
  id: string;
  occurredAt: string;
  fromStatus?: TravelRequestStatus;
  toStatus: TravelRequestStatus;
  actorType: TravelRequestActorType;
  actorUserId?: string;
  reason?: TravelRequestCancelReason;
  detail?: string;
}

export function listTravelRequests(
  token: string,
  params: { status?: TravelRequestStatus; page?: number; size?: number } = {},
): Promise<TravelRequest[]> {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? PAGE_SIZE));
  return request<TravelRequest[]>(`/api/v1/travel-marketplace/requests?${query.toString()}`, token);
}

export function getTravelRequest(token: string, id: string): Promise<TravelRequest> {
  return request<TravelRequest>(`/api/v1/travel-marketplace/requests/${id}`, token);
}

export function getTravelRequestAudit(token: string, id: string): Promise<TravelRequestAuditEvent[]> {
  return request<TravelRequestAuditEvent[]>(`/api/v1/travel-marketplace/requests/${id}/audit`, token);
}

export function startTravelRequestReview(token: string, id: string): Promise<TravelRequest> {
  return request<TravelRequest>(`/api/v1/travel-marketplace/requests/${id}/start-review`, token, { method: "POST" });
}

export function confirmTravelRequest(token: string, id: string): Promise<TravelRequest> {
  return request<TravelRequest>(`/api/v1/travel-marketplace/requests/${id}/confirm`, token, { method: "POST" });
}

export function cancelTravelRequest(
  token: string,
  id: string,
  body: { reason: TravelRequestCancelReason; detail?: string },
): Promise<TravelRequest> {
  return request<TravelRequest>(`/api/v1/travel-marketplace/requests/${id}/cancel`, token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function completeTravelRequest(token: string, id: string): Promise<TravelRequest> {
  return request<TravelRequest>(`/api/v1/travel-marketplace/requests/${id}/complete`, token, { method: "POST" });
}
