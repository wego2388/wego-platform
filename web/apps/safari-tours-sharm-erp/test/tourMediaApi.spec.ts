import { afterEach, describe, expect, it, vi } from "vitest";
import { approveTourPhoto, fetchPrivatePhoto, managedPhotoAssetId, replaceTourMedia, uploadTourPhoto } from "../app/composables/useTourMediaApi";

const assetId = "16f34404-0bc9-4cf0-ae8b-3c60f7d5f7d0";
afterEach(() => vi.unstubAllGlobals());
describe("managed media API boundary", () => {
  it("sends bounded file/alt/UUID input with bearer header, browser-owned multipart boundary, never token in URLs", async () => {
    const fetcher = vi.fn(async () => new Response(JSON.stringify({ assetId, mediaId: "media-id" }), { status: 200 })); vi.stubGlobal("fetch", fetcher);
    const file = new File(["jpeg bytes"], "owner-photo.jpg", { type: "image/jpeg" });
    await uploadTourPhoto("private-token", "tour-id", file, { en: " Actual beach ", ar: "شاطئ", it: " " }, "request-id", { mediaId: "media-id", revision: "reviewed" });
    const [path, init] = fetcher.mock.calls[0] as unknown as [string, RequestInit];
    expect(path).toBe("/api/v1/tours-operator/staff/tours/tour-id/media/upload");
    expect(path).not.toContain("private-token"); expect(init.headers).toEqual({ Authorization: "Bearer private-token" });
    const form = init.body as FormData;
    expect(form.get("file")).toBeInstanceOf(File); expect(form.get("requestId")).toBe("request-id");
    expect(form.get("altEn")).toBe("Actual beach"); expect(form.get("altAr")).toBe("شاطئ"); expect(form.has("altIt")).toBe(false);
    expect(form.get("mediaId")).toBe("media-id"); expect(form.get("revision")).toBe("reviewed");
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it("does not automatically retry failed or uncertain uploads", async () => {
    const fetcher = vi.fn(async () => new Response('{"error":"upload_busy"}', { status: 503 })); vi.stubGlobal("fetch", fetcher);
    await expect(uploadTourPhoto("token", "id", new File(["bytes"], "photo.jpg", { type: "image/jpeg" }), {}, "request-id")).rejects.toMatchObject({ status: 503, errorCode: "upload_busy" });
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it("preserves actual server-owned paths/dimensions and sends reviewed rights revision only", async () => {
    const fetcher = vi.fn(async () => new Response(null, { status: 204 })); vi.stubGlobal("fetch", fetcher);
    const input = [{ id: "media-id", path: `/media/tours/real-tour/${assetId}.jpg`, width: 1200, height: 800, isCover: true, alt: { en: "Real photo" } }];
    await replaceTourMedia("token", "id", input, "whole-list-reviewed-revision"); await approveTourPhoto("token", "id", "media-id", "exact-reviewed-revision");
    expect((fetcher.mock.calls[0] as unknown[])[0]).toBe("/api/v1/tours-operator/staff/tours/id/media?revision=whole-list-reviewed-revision");
    expect((fetcher.mock.calls[0] as unknown[])[1]).toMatchObject({ method: "PUT", body: JSON.stringify(input) });
    expect((fetcher.mock.calls[1] as unknown[])[1]).toMatchObject({ method: "POST", body: '{"revision":"exact-reviewed-revision"}' });
  });
  it("only extracts generated immutable asset IDs from verified managed-tour path shapes", () => {
    expect(managedPhotoAssetId(`/media/tours/real-tour/${assetId}.jpg`)).toBe(assetId);
    expect(managedPhotoAssetId(`/media/tours/real-tour/${assetId}.png`)).toBe(assetId);
    for (const path of ["https://remote.example/photo.jpg", `/media/tours/../${assetId}.jpg`, "/media/tours/tour/legacy.jpg", `/media/categories/SEA/${assetId}.jpg`, `/media/tours/tour/${assetId}.svg`, `/media/tours/tour/${assetId}.jpg?token=secret`]) expect(managedPhotoAssetId(path)).toBeNull();
  });
  it("fetches private preview with no-store auth header and rejects non-image server responses", async () => {
    const fetcher = vi.fn(async () => new Response(new Blob(["image"], { type: "image/jpeg" }), { status: 200, headers: { "Content-Type": "image/jpeg" } })); vi.stubGlobal("fetch", fetcher);
    expect((await fetchPrivatePhoto("secret", assetId, "w768")).type).toBe("image/jpeg");
    expect(fetcher).toHaveBeenCalledWith(`/api/v1/tours-operator/staff/media/preview/${assetId}?variant=w768`, { headers: { Authorization: "Bearer secret" }, cache: "no-store" });
    vi.stubGlobal("fetch", vi.fn(async () => new Response("<html>private diagnostic</html>", { status: 200, headers: { "Content-Type": "text/html" } })));
    await expect(fetchPrivatePhoto("secret", assetId)).rejects.toMatchObject({ errorCode: "invalid_image_response" });
  });
});
