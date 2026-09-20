// Explicit imports/exports only — no Nuxt auto-import runtime in the
// Vitest environment (see useAuthSession.ts in the ERP app for the same
// convention). Calls this app's own same-origin server/api/catalog/*
// routes, never the real backend directly — a browser fetch straight to
// the backend's own origin is blocked by CORS (caught live in a real
// headless browser). Those routes mirror
// platform/contracts/openapi/v1/sharm-to-go-api.yaml's PublicCategoryResponse
// / PublicServiceResponse shapes exactly.

export interface LocalizedText {
  en: string;
  ar: string;
}

export interface PublicCategory {
  id: string;
  code: string;
  name: LocalizedText;
  description: LocalizedText | null;
}

export type ConfirmationType = "INSTANT" | "REQUEST";
export type PriceBasis = "PER_PERSON" | "PER_GROUP" | "PER_VEHICLE";

export interface PublicServiceOption {
  label: LocalizedText;
  durationMinutes: number | null;
  maxParticipants: number;
  priceAmount: string;
  priceCurrency: string;
  priceBasis: PriceBasis;
}

export interface PublicServiceMedia {
  assetReference: string;
  locale: string;
}

export interface PublicService {
  id: string;
  categoryId: string;
  name: LocalizedText;
  description: LocalizedText;
  confirmationType: ConfirmationType;
  cancellationPolicy: LocalizedText;
  pickupInfo: LocalizedText | null;
  inclusions: LocalizedText | null;
  exclusions: LocalizedText | null;
  operatedBy: string | null;
  options: PublicServiceOption[];
  media: PublicServiceMedia[];
}

export class PublicCatalogError extends Error {
  constructor(public readonly status: number) {
    super(`public catalog request failed (${status})`);
  }
}

async function get<T>(path: string): Promise<T> {
  const response = await fetch(path);
  if (!response.ok) throw new PublicCatalogError(response.status);
  return (await response.json()) as T;
}

export function listPublicCategories(): Promise<PublicCategory[]> {
  return get<PublicCategory[]>("/api/catalog/categories");
}

export function listPublicServices(categoryId?: string): Promise<PublicService[]> {
  const query = categoryId ? `?categoryId=${encodeURIComponent(categoryId)}` : "";
  return get<PublicService[]>(`/api/catalog/services${query}`);
}

/** Returns null for a 404 — an unknown id and a real, not-currently-published service are indistinguishable to a public caller. */
export async function getPublicService(id: string): Promise<PublicService | null> {
  try {
    return await get<PublicService>(`/api/catalog/services/${encodeURIComponent(id)}`);
  } catch (error) {
    if (error instanceof PublicCatalogError && error.status === 404) return null;
    throw error;
  }
}

/** The lowest option price, for a card's "from EGP X" line. Null when a published service somehow has no options (shouldn't happen — publish() requires one — but a card must not crash on it). */
export function startingPrice(service: PublicService): PublicServiceOption | null {
  if (service.options.length === 0) return null;
  return service.options.reduce((lowest, option) => (Number(option.priceAmount) < Number(lowest.priceAmount) ? option : lowest));
}
