import { afterEach, describe, expect, it, vi } from "vitest";

let categoriesHandler: typeof import("../server/api/catalog/categories.get").default;
let servicesHandler: typeof import("../server/api/catalog/services.get").default;
let serviceDetailHandler: typeof import("../server/api/catalog/services/[id].get").default;

async function loadHandlers() {
  vi.resetModules();
  categoriesHandler = (await import("../server/api/catalog/categories.get")).default;
  servicesHandler = (await import("../server/api/catalog/services.get")).default;
  serviceDetailHandler = (await import("../server/api/catalog/services/[id].get")).default;
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("GET /api/catalog/categories (proxy)", () => {
  it("forwards the real backend's categories unchanged", async () => {
    await loadHandlers();
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([{ id: "c1" }]), { status: 200 })));

    const result = await categoriesHandler({} as never);
    expect(result).toEqual([{ id: "c1" }]);
  });

  it("throws a clean 502, not a raw crash, when the real backend is unreachable", async () => {
    await loadHandlers();
    vi.stubGlobal("fetch", vi.fn(async () => new Response("boom", { status: 500 })));

    await expect(categoriesHandler({} as never)).rejects.toMatchObject({ statusCode: 502 });
  });
});

describe("GET /api/catalog/services (proxy)", () => {
  it("forwards the categoryId filter to the real backend", async () => {
    await loadHandlers();
    const fetchMock = vi.fn(async () => new Response(JSON.stringify([]), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await servicesHandler({ query: { categoryId: "cat-1" } } as never);

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining("categoryId=cat-1"));
  });

  it("omits the filter entirely when no category was selected", async () => {
    await loadHandlers();
    const fetchMock = vi.fn(async () => new Response(JSON.stringify([]), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await servicesHandler({ query: {} } as never);

    expect(fetchMock).toHaveBeenCalledWith(expect.not.stringContaining("categoryId"));
  });
});

describe("GET /api/catalog/services/:id (proxy)", () => {
  it("passes through a real 404 instead of treating it as an upstream failure", async () => {
    await loadHandlers();
    vi.stubGlobal("fetch", vi.fn(async () => new Response("not found", { status: 404 })));

    const result = await serviceDetailHandler({ params: { id: "unknown" } } as never);
    expect(result).toBeNull();
  });

  it("returns the real service body for a published id", async () => {
    await loadHandlers();
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ id: "s1" }), { status: 200 })));

    const result = await serviceDetailHandler({ params: { id: "s1" } } as never);
    expect(result).toEqual({ id: "s1" });
  });
});
