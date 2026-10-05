import type { ContentLocale, MediaUploadResponse, TourMediaInput } from "@wego/api-contract";
import { ToursApiError } from "./useToursApi";

export const MAX_PHOTO_BYTES = 10 * 1024 * 1024;
export type PhotoAlt = Partial<Record<ContentLocale, string>>;
const base = (id: string) => `/api/v1/tours-operator/staff/tours/${encodeURIComponent(id)}/media`;
async function check(response: Response): Promise<void> {
  if (response.ok) return;
  const body = await response.json().catch(() => null);
  throw new ToursApiError(response.status, body && typeof body === "object" && "error" in body ? String(body.error) : `http_${response.status}`);
}

/** Never retry a file POST automatically: an uncertain response may already own a new asset. */
export async function uploadTourPhoto(token: string, tourId: string, file: File, alt: PhotoAlt, requestId: string, replacement?: { mediaId: string; revision: string }): Promise<MediaUploadResponse> {
  const form = new FormData();
  form.set("file", file);
  form.set("requestId", requestId);
  for (const language of ["en", "ar", "ru", "it"] as const) {
    if (alt[language]?.trim()) form.set(`alt${language[0]!.toUpperCase()}${language[1]}`, alt[language]!.trim());
  }
  if (replacement) { form.set("mediaId", replacement.mediaId); form.set("revision", replacement.revision); }
  const response = await fetch(`${base(tourId)}/upload`, { method: "POST", headers: { Authorization: `Bearer ${token}` }, body: form, cache: "no-store" });
  await check(response);
  return response.json();
}
export async function replaceTourMedia(token: string, tourId: string, media: TourMediaInput[], revision: string): Promise<void> {
  const response = await fetch(`${base(tourId)}?${new URLSearchParams({ revision })}`, { method: "PUT", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }, body: JSON.stringify(media), cache: "no-store" });
  await check(response);
}
export async function approveTourPhoto(token: string, tourId: string, mediaId: string, revision: string): Promise<void> {
  const response = await fetch(`${base(tourId)}/${encodeURIComponent(mediaId)}/approve`, { method: "POST", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }, body: JSON.stringify({ revision }), cache: "no-store" });
  await check(response);
}
export function managedPhotoAssetId(path: string): string | null {
  return /^\/media\/tours\/[a-z0-9-]+\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\.(jpg|png)$/.exec(path)?.[1] ?? null;
}
export async function fetchPrivatePhoto(token: string, assetId: string, variant: "base" | "w768" = "base"): Promise<Blob> {
  const response = await fetch(`/api/v1/tours-operator/staff/media/preview/${encodeURIComponent(assetId)}?${new URLSearchParams({ variant })}`, { headers: { Authorization: `Bearer ${token}` }, cache: "no-store" });
  await check(response);
  const blob = await response.blob();
  if (blob.type !== "image/jpeg" && blob.type !== "image/png") throw new ToursApiError(502, "invalid_image_response");
  return blob;
}
