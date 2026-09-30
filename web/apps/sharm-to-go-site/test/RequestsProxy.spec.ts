import { afterEach, describe, expect, it, vi } from "vitest";

let createHandler: typeof import("../server/api/requests/index.post").default;
let lookupHandler: typeof import("../server/api/requests/[reference].get").default;

async function loadHandlers() {
  vi.resetModules();
  createHandler = (await import("../server/api/requests/index.post")).default;
  lookupHandler = (await import("../server/api/requests/[reference].get")).default;
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("POST /api/requests (proxy)", () => {
  it("forwards the Idempotency-Key header and body, and passes the backend's status/body through", async () => {
    await loadHandlers();
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      new Response(JSON.stringify({ reference: "STG-ABCDEFGH", status: "NEW" }), { status: 201 }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await createHandler({
      headers: { "idempotency-key": "abc-123" },
      body: { serviceId: "s1" },
    } as never);

    expect(result).toEqual({ reference: "STG-ABCDEFGH", status: "NEW" });
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(String(url)).toContain("/api/v1/travel-marketplace/public/requests");
    expect(init?.method).toBe("POST");
    expect(init?.headers).toMatchObject({ "Idempotency-Key": "abc-123" });
  });

  it("rejects with a clean 400 when the Idempotency-Key header is missing, before ever calling the backend", async () => {
    await loadHandlers();
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    await expect(createHandler({ headers: {}, body: {} } as never)).rejects.toMatchObject({ statusCode: 400 });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("passes a 409 (party size exceeds capacity) straight through, not a generic 500", async () => {
    await loadHandlers();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify({ error: "party_size_exceeds_capacity" }), { status: 409 })),
    );
    let capturedStatus: number | undefined;
    vi.stubGlobal("setResponseStatus", (_event: unknown, status: number) => {
      capturedStatus = status;
    });

    const result = await createHandler({ headers: { "idempotency-key": "k" }, body: {} } as never);

    expect(capturedStatus).toBe(409);
    expect(result).toEqual({ error: "party_size_exceeds_capacity" });
  });
});

describe("GET /api/requests/[reference] (proxy)", () => {
  it("forwards a found request unchanged", async () => {
    await loadHandlers();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify({ reference: "STG-ABCDEFGH", status: "CONFIRMED" }), { status: 200 })),
    );

    const result = await lookupHandler({ params: { reference: "STG-ABCDEFGH" } } as never);
    expect(result).toEqual({ reference: "STG-ABCDEFGH", status: "CONFIRMED" });
  });

  it("returns null with a 404, not a crash, for an unknown reference", async () => {
    await loadHandlers();
    vi.stubGlobal("fetch", vi.fn(async () => new Response("not found", { status: 404 })));

    const result = await lookupHandler({ params: { reference: "STG-ZZZZZZZZ" } } as never);
    expect(result).toBeNull();
  });

  it("throws a clean 502, not a raw crash, when the real backend is unreachable", async () => {
    await loadHandlers();
    vi.stubGlobal("fetch", vi.fn(async () => new Response("boom", { status: 500 })));

    await expect(lookupHandler({ params: { reference: "STG-ABCDEFGH" } } as never)).rejects.toMatchObject({ statusCode: 502 });
  });
});
