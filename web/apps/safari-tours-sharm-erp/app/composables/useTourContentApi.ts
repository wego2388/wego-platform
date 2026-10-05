import type { ContentLocale, PublishRequest, StaffTourContent, TourContentDocument, TourFactsDocument } from "@wego/api-contract";
import { ToursApiError } from "./useToursApi";

export type { ContentLocale, StaffTourContent, TourContentDocument, TourFactsDocument } from "@wego/api-contract";

async function contentRequest<T>(path: string, token: string, method = "GET", body?: unknown): Promise<T> {
  const response = await fetch(path, {
    method,
    headers: { Authorization: `Bearer ${token}`, ...(body === undefined ? {} : { "Content-Type": "application/json" }) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    cache: "no-store",
  });
  if (!response.ok) {
    const result = await response.json().catch(() => null);
    throw new ToursApiError(response.status, result && typeof result === "object" && "error" in result
      ? String(result.error) : `http_${response.status}`);
  }
  return (response.status === 204 ? undefined : await response.json()) as T;
}

const base = (tourId: string) => `/api/v1/tours-operator/staff/tours/${encodeURIComponent(tourId)}`;
export const getStaffTourContent = (token: string, tourId: string) => contentRequest<StaffTourContent>(`${base(tourId)}/content`, token);
export const saveTourContentDraft = (token: string, tourId: string, locale: ContentLocale, document: TourContentDocument) =>
  contentRequest<undefined>(`${base(tourId)}/content/${locale}`, token, "PUT", document);
export const publishTourContent = (token: string, tourId: string, locale: ContentLocale, revision: string) =>
  contentRequest<undefined>(`${base(tourId)}/content/${locale}/publish`, token, "POST", { revision } satisfies PublishRequest);
export const unpublishTourContent = (token: string, tourId: string, locale: ContentLocale) =>
  contentRequest<undefined>(`${base(tourId)}/content/${locale}/unpublish`, token, "POST");
export const saveTourFactsDraft = (token: string, tourId: string, document: TourFactsDocument) =>
  contentRequest<undefined>(`${base(tourId)}/facts`, token, "PUT", document);
export const publishTourFacts = (token: string, tourId: string, revision: string) =>
  contentRequest<undefined>(`${base(tourId)}/facts/publish`, token, "POST", { revision } satisfies PublishRequest);
export const unpublishTourFacts = (token: string, tourId: string) => contentRequest<undefined>(`${base(tourId)}/facts/unpublish`, token, "POST");
