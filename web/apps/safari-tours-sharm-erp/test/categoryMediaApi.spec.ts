import { afterEach, describe, expect, it, vi } from "vitest";
import { approveCategoryCover, getCategoryMedia, listCategoryMedia, saveCategoryAlt, uploadCategoryCover } from "../app/composables/useCategoryMediaApi";
afterEach(() => vi.unstubAllGlobals());
describe("category media API uses existing typed backend contract", () => {
  it("reads category list and single record with bearer header/no-store, never credential query params", async () => {
    const fetcher = vi.fn(async () => new Response("{}", { status: 200 })); vi.stubGlobal("fetch", fetcher);
    await listCategoryMedia("secret"); await getCategoryMedia("secret", "SEA");
    expect(fetcher.mock.calls.map(call => (call as unknown[])[0])).toEqual(["/api/v1/tours-operator/staff/categories/media", "/api/v1/tours-operator/staff/categories/SEA/media"]);
    expect((fetcher.mock.calls[0] as unknown[])[1]).toEqual({ headers: { Authorization: "Bearer secret" }, cache: "no-store" });
  });
  it("uploads once with immutable request UUID plus reviewed category revision and browser multipart boundary", async () => {
    const fetcher = vi.fn(async () => new Response('{"assetId":"asset-id","mediaId":null}', { status: 200 })); vi.stubGlobal("fetch", fetcher);
    const file = new File(["synthetic JPEG"], "photo.jpg", { type: "image/jpeg" });
    await uploadCategoryCover("secret", "SEA", file, "category-revision", "request-uuid", { en: " Real sea ", ar: "بحر", ru: " " });
    const [url, request] = fetcher.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/v1/tours-operator/staff/categories/SEA/media/upload");
    expect(request.headers).toEqual({ Authorization: "Bearer secret" });
    const form = request.body as FormData; expect(form.get("file")).toBeInstanceOf(File);
    expect(form.get("revision")).toBe("category-revision"); expect(form.get("requestId")).toBe("request-uuid"); expect(form.get("altEn")).toBe("Real sea"); expect(form.get("altAr")).toBe("بحر"); expect(form.has("altRu")).toBe(false);
    expect(fetcher).toHaveBeenCalledTimes(1);
  });
  it("saves/clears localized alt using exact nullable fields and requires reviewed revision for approval", async () => {
    const fetcher = vi.fn(async () => new Response(null, { status: 204 })); vi.stubGlobal("fetch", fetcher);
    await saveCategoryAlt("secret", "DESERT", "reviewed", { en: "Desert", ar: " " }); await approveCategoryCover("secret", "DESERT", "reviewed-exact");
    expect((fetcher.mock.calls[0] as unknown[])[1]).toMatchObject({ method: "PUT", body: '{"revision":"reviewed","altEn":"Desert","altAr":null,"altRu":null,"altIt":null}' });
    expect((fetcher.mock.calls[1] as unknown[])[1]).toMatchObject({ method: "POST", body: '{"revision":"reviewed-exact"}' });
  });
  it("preserves draft_changed and does not retry rejected/uncertain writes", async () => {
    const fetcher = vi.fn(async () => new Response('{"error":"draft_changed"}', { status: 409 })); vi.stubGlobal("fetch", fetcher);
    await expect(saveCategoryAlt("secret", "SEA", "stale", { en: "Local" })).rejects.toMatchObject({ status: 409, errorCode: "draft_changed" }); expect(fetcher).toHaveBeenCalledTimes(1);
  });
});
