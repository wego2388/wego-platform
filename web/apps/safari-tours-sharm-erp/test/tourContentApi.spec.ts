import { afterEach, describe, expect, it, vi } from "vitest";
import { getStaffTourContent, publishTourContent, publishTourFacts, saveTourContentDraft, saveTourFactsDraft, unpublishTourContent, unpublishTourFacts } from "../app/composables/useTourContentApi";
import { normalizeContent, normalizeFacts } from "../app/utils/tourContentEditor";

afterEach(() => vi.unstubAllGlobals());
describe("existing tour content API contract", () => {
  it("puts the bearer token only in the header and reads no-store", async () => {
    const fetcher = vi.fn(async () => new Response(JSON.stringify({ content: {}, facts: [], media: [] }), { status: 200 }));
    vi.stubGlobal("fetch", fetcher);
    await getStaffTourContent("private-token", "tour/id");
    expect(fetcher).toHaveBeenCalledWith("/api/v1/tours-operator/staff/tours/tour%2Fid/content", expect.objectContaining({ cache: "no-store", headers: { Authorization: "Bearer private-token" } }));
  });
  it("uses exact save/publish/unpublish routes and treats 204 as success", async () => {
    const fetcher = vi.fn(async () => new Response(null, { status: 204 })); vi.stubGlobal("fetch", fetcher);
    await saveTourContentDraft("token", "id", "ar", normalizeContent());
    await publishTourContent("token", "id", "ar", "reviewed-revision");
    await unpublishTourContent("token", "id", "ar");
    await saveTourFactsDraft("token", "id", normalizeFacts());
    await publishTourFacts("token", "id", "facts-revision");
    await unpublishTourFacts("token", "id");
    expect(fetcher.mock.calls.map(call => (call as unknown[])[0])).toEqual([
      "/api/v1/tours-operator/staff/tours/id/content/ar", "/api/v1/tours-operator/staff/tours/id/content/ar/publish", "/api/v1/tours-operator/staff/tours/id/content/ar/unpublish", "/api/v1/tours-operator/staff/tours/id/facts", "/api/v1/tours-operator/staff/tours/id/facts/publish", "/api/v1/tours-operator/staff/tours/id/facts/unpublish",
    ]);
    expect((fetcher.mock.calls[1] as unknown[])[1]).toMatchObject({ method: "POST", body: '{"revision":"reviewed-revision"}' });
  });
  it("preserves draft_changed and hides unparseable upstream diagnostics", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response('{"error":"draft_changed"}', { status: 409 })));
    await expect(publishTourContent("token", "id", "en", "revision")).rejects.toMatchObject({ status: 409, errorCode: "draft_changed" });
    vi.stubGlobal("fetch", vi.fn(async () => new Response("private proxy diagnostic", { status: 502 })));
    await expect(getStaffTourContent("token", "id")).rejects.toMatchObject({ status: 502, errorCode: "http_502" });
  });
});
