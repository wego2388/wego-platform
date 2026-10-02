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

  it("marks its response no-store — the body carries a fresh bearer-secret reference", async () => {
    await loadHandlers();
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ reference: "STG-ABCDEFGH", status: "NEW" }), { status: 201 })));
    const capturedHeaders: Array<[string, string]> = [];
    vi.stubGlobal("setResponseHeader", (_event: unknown, name: string, value: string) => {
      capturedHeaders.push([name, value]);
    });

    await createHandler({ headers: { "idempotency-key": "k" }, body: {} } as never);

    expect(capturedHeaders).toContainEqual(["cache-control", "no-store"]);
  });

  it("generates a correlation id and forwards it to the backend when the caller sent none", async () => {
    await loadHandlers();
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      new Response(JSON.stringify({ reference: "STG-ABCDEFGH" }), { status: 201 }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await createHandler({ headers: { "idempotency-key": "k" }, body: {} } as never);

    const [, init] = fetchMock.mock.calls[0]!;
    const forwarded = (init?.headers as Record<string, string>)["X-Correlation-Id"];
    expect(forwarded).toMatch(/^[0-9a-f-]{36}$/i);
  });

  it("reuses the caller's own correlation id instead of generating a new one, and echoes it back", async () => {
    await loadHandlers();
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      new Response(JSON.stringify({ reference: "STG-ABCDEFGH" }), { status: 201 }),
    );
    vi.stubGlobal("fetch", fetchMock);
    const capturedHeaders: Array<[string, string]> = [];
    vi.stubGlobal("setResponseHeader", (_event: unknown, name: string, value: string) => {
      capturedHeaders.push([name, value]);
    });

    await createHandler({
      headers: { "idempotency-key": "k", "x-correlation-id": "incoming-id" },
      body: {},
    } as never);

    const [, init] = fetchMock.mock.calls[0]!;
    expect((init?.headers as Record<string, string>)["X-Correlation-Id"]).toBe("incoming-id");
    expect(capturedHeaders).toContainEqual(["x-correlation-id", "incoming-id"]);
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

  it("throws a clean 502, not a raw unhandled crash, when fetch itself fails (a true connection failure, not an HTTP error status)", async () => {
    await loadHandlers();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new TypeError("fetch failed");
      }),
    );

    await expect(createHandler({ headers: { "idempotency-key": "k" }, body: {} } as never)).rejects.toMatchObject({ statusCode: 502 });
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

  it("throws a clean 502, not a raw crash, when the backend responds with a real HTTP error status", async () => {
    await loadHandlers();
    vi.stubGlobal("fetch", vi.fn(async () => new Response("boom", { status: 500 })));

    await expect(lookupHandler({ params: { reference: "STG-ABCDEFGH" } } as never)).rejects.toMatchObject({ statusCode: 502 });
  });

  it("throws a clean 502, not a raw unhandled crash, when fetch itself fails (a true connection failure, not an HTTP error status)", async () => {
    await loadHandlers();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new TypeError("fetch failed");
      }),
    );

    await expect(lookupHandler({ params: { reference: "STG-ABCDEFGH" } } as never)).rejects.toMatchObject({ statusCode: 502 });
  });

  it("marks its response no-store — a reference is a bearer secret, see the nginx config's own logging hardening", async () => {
    await loadHandlers();
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ reference: "STG-ABCDEFGH" }), { status: 200 })));
    const capturedHeaders: Array<[string, string]> = [];
    vi.stubGlobal("setResponseHeader", (_event: unknown, name: string, value: string) => {
      capturedHeaders.push([name, value]);
    });

    await lookupHandler({ params: { reference: "STG-ABCDEFGH" } } as never);

    expect(capturedHeaders).toContainEqual(["cache-control", "no-store"]);
  });

  it("generates a correlation id and forwards it to the backend when the caller sent none", async () => {
    await loadHandlers();
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      new Response(JSON.stringify({ reference: "STG-ABCDEFGH" }), { status: 200 }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await lookupHandler({ params: { reference: "STG-ABCDEFGH" }, headers: {} } as never);

    const [, init] = fetchMock.mock.calls[0]!;
    const forwarded = (init?.headers as Record<string, string>)["X-Correlation-Id"];
    expect(forwarded).toMatch(/^[0-9a-f-]{36}$/i);
  });

  it("reuses the caller's own correlation id instead of generating a new one, and echoes it back", async () => {
    await loadHandlers();
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      new Response(JSON.stringify({ reference: "STG-ABCDEFGH" }), { status: 200 }),
    );
    vi.stubGlobal("fetch", fetchMock);
    const capturedHeaders: Array<[string, string]> = [];
    vi.stubGlobal("setResponseHeader", (_event: unknown, name: string, value: string) => {
      capturedHeaders.push([name, value]);
    });

    await lookupHandler({
      params: { reference: "STG-ABCDEFGH" },
      headers: { "x-correlation-id": "incoming-id" },
    } as never);

    const [, init] = fetchMock.mock.calls[0]!;
    expect((init?.headers as Record<string, string>)["X-Correlation-Id"]).toBe("incoming-id");
    expect(capturedHeaders).toContainEqual(["x-correlation-id", "incoming-id"]);
  });
});
