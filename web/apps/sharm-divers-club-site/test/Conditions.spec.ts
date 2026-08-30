import { beforeEach, describe, expect, it, vi } from "vitest";

// The route module caches its response for 10 minutes in a module-level
// variable, so each test resets the module registry and re-imports fresh —
// otherwise the second test would just see the first test's cached result.
// Deliberately no `afterEach(() => vi.unstubAllGlobals())` here: that would
// also remove test/setup.ts's own `defineEventHandler` stub (stubGlobal
// doesn't track which file registered a stub), breaking every import after
// the first test. Each test overwrites the `fetch` stub it needs directly.
let handler: typeof import("../server/api/conditions.get").default;

beforeEach(async () => {
  vi.resetModules();
  handler = (await import("../server/api/conditions.get")).default;
});

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status });
}

describe("GET /api/conditions", () => {
  it("returns real values when both upstream calls succeed", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) =>
        url.includes("marine-api")
          ? jsonResponse({ current: { wave_height: 0.56, sea_surface_temperature: 29.0 } })
          : jsonResponse({ current: { temperature_2m: 37.4, wind_speed_10m: 16.7, weather_code: 0 } }),
      ),
    );

    const result = await handler({} as never);

    expect(result.air).toEqual({ tempC: 37.4, windKph: 16.7, weatherCode: 0 });
    expect(result.sea).toEqual({ tempC: 29.0, waveHeightM: 0.56 });
  });

  it("never fabricates a value when the upstream returns null fields — the exact gap independent Tier 1 review found", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) =>
        url.includes("marine-api")
          ? jsonResponse({ current: { wave_height: null, sea_surface_temperature: 29.0 } })
          : jsonResponse({ current: { temperature_2m: null, wind_speed_10m: 16.7, weather_code: 0 } }),
      ),
    );

    const result = await handler({} as never);

    expect(result.air).toBeNull();
    expect(result.sea).toBeNull();
  });

  it("falls back to null instead of throwing when the upstream request genuinely times out", async () => {
    // Deliberately does NOT fake an immediate AbortError — a mock that rejects on its own would pass
    // even if fetchWithTimeout's real AbortController/setTimeout logic were deleted entirely. Instead
    // the mock hangs forever and only rejects when the real `signal` it was given actually fires its
    // "abort" event, so this test only passes if the real 8s timer genuinely aborts the real signal.
    vi.useFakeTimers();
    try {
      vi.stubGlobal(
        "fetch",
        vi.fn((_url: string, init?: { signal?: AbortSignal }) => {
          return new Promise<Response>((_resolve, reject) => {
            init?.signal?.addEventListener("abort", () => {
              reject(new DOMException("The operation was aborted", "AbortError"));
            });
          });
        }),
      );

      const resultPromise = handler({} as never);
      await vi.advanceTimersByTimeAsync(8000);
      const result = await resultPromise;

      expect(result.air).toBeNull();
      expect(result.sea).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });
});
