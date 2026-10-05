import type { CategoryMedia, MediaUploadResponse, TourCategory } from "@wego/api-contract";
import { ToursApiError } from "./useToursApi";
import type { PhotoAlt } from "./useTourMediaApi";

async function checked(response: Response): Promise<void> {
  if (response.ok) return;
  const body = await response.json().catch(() => null);
  throw new ToursApiError(response.status, body && typeof body === "object" && "error" in body ? String(body.error) : `http_${response.status}`);
}
const base = (category: TourCategory) => `/api/v1/tours-operator/staff/categories/${category}/media`;
const altFields = (alt: PhotoAlt) => ({ altEn: alt.en?.trim() || null, altAr: alt.ar?.trim() || null, altRu: alt.ru?.trim() || null, altIt: alt.it?.trim() || null });
export async function listCategoryMedia(token: string): Promise<CategoryMedia[]> {
  const response = await fetch("/api/v1/tours-operator/staff/categories/media", { headers: { Authorization: `Bearer ${token}` }, cache: "no-store" });
  await checked(response); return response.json();
}
export async function getCategoryMedia(token: string, category: TourCategory): Promise<CategoryMedia> {
  const response = await fetch(base(category), { headers: { Authorization: `Bearer ${token}` }, cache: "no-store" });
  await checked(response); return response.json();
}
export async function uploadCategoryCover(token: string, category: TourCategory, file: File, revision: string, requestId: string, alt: PhotoAlt): Promise<MediaUploadResponse> {
  const form = new FormData(); form.set("file", file); form.set("revision", revision); form.set("requestId", requestId);
  for (const [key, value] of Object.entries(altFields(alt))) if (value !== null) form.set(key, value);
  const response = await fetch(`${base(category)}/upload`, { method: "POST", headers: { Authorization: `Bearer ${token}` }, body: form, cache: "no-store" });
  await checked(response); return response.json();
}
export async function saveCategoryAlt(token: string, category: TourCategory, revision: string, alt: PhotoAlt): Promise<void> {
  const response = await fetch(`${base(category)}/alt`, { method: "PUT", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }, body: JSON.stringify({ revision, ...altFields(alt) }), cache: "no-store" });
  await checked(response);
}
export async function approveCategoryCover(token: string, category: TourCategory, revision: string): Promise<void> {
  const response = await fetch(`${base(category)}/approve`, { method: "POST", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }, body: JSON.stringify({ revision }), cache: "no-store" });
  await checked(response);
}
