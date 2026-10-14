import { flushPromises } from "@vue/test-utils";
import { effectScope, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const consent = { granted: ref(false) };
vi.mock("../app/composables/useConsent", () => ({ useConsent: () => consent }));

type Track = (event: string, params: Record<string, string | number>) => void;
type TrackingWindow = Window & { dataLayer?: unknown[]; gtag?: (...args: unknown[]) => void; fbq?: ((...args: unknown[]) => void) & { queue?: unknown[] } };
const w = window as TrackingWindow;
let scope = effectScope();
let loadedScripts: string[] = [];
let navigationHook: ((to: { path: string }, from: { path: string }) => void) | null = null;

beforeEach(() => {
  consent.granted.value = false;
  scope = effectScope();
  loadedScripts = [];
  navigationHook = null;
  delete w.dataLayer; delete w.gtag; delete w.fbq;
  // Capture insertions without asking happy-dom to fetch external JavaScript.
  vi.spyOn(document.head, "appendChild").mockImplementation((node) => {
    if (node instanceof HTMLScriptElement) loadedScripts.push(node.src);
    return node;
  });
  vi.stubGlobal("defineNuxtPlugin", (plugin: unknown) => plugin);
});

afterEach(() => { scope.stop(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

async function setup(ga4Id: string, metaPixelId: string): Promise<Track> {
  const config = { public: { ga4Id, metaPixelId } };
  const currentRoute = ref({ path: "/en/tour/sunset-quad-bike" });
  vi.stubGlobal("useRuntimeConfig", () => config);
  vi.stubGlobal("useRouter", () => ({ currentRoute, isReady: () => Promise.resolve(), afterEach: (hook: typeof navigationHook) => { navigationHook = hook; } }));
  const plugin = (await import("../app/plugins/analytics.client")).default as unknown as (app: { provide: (key: string, value: Track) => void }) => void;
  let track!: Track;
  scope.run(() => plugin({ provide: (key, value) => { expect(key).toBe("stsTrack"); track = value; } }));
  return track;
}

describe("request lead provider adapters", () => {
  const catalogId = "00000000-0000-4000-8000-000000000001";

  it("does not load tags or send an event before consent, even with IDs configured", async () => {
    const track = await setup("G-TEST123", "123456789");
    track("request_submitted", { item_id: catalogId });
    await flushPromises();
    expect(w.dataLayer).toBeUndefined();
    expect(w.fbq).toBeUndefined();
    expect(loadedScripts).toEqual([]);
  });

  it("sends a consented saved request as GA custom event and Meta Lead, never Purchase or PII", async () => {
    const track = await setup("G-TEST123", "123456789");
    consent.granted.value = true;
    await flushPromises();
    track("request_submitted", { item_id: catalogId });
    const gaCalls = (w.dataLayer ?? []).map(call => Array.from(call as ArrayLike<unknown>));
    const metaCalls = w.fbq?.queue ?? [];
    expect(gaCalls).toContainEqual(["event", "request_submitted", expect.objectContaining({ item_id: catalogId, page_path: "/en/tour/sunset-quad-bike" })]);
    expect(metaCalls).toContainEqual(["track", "Lead", expect.objectContaining({ content_ids: [catalogId] })]);
    expect(loadedScripts).toContain("https://www.googletagmanager.com/gtag/js?id=G-TEST123");
    expect(loadedScripts).toContain("https://connect.facebook.net/en_US/fbevents.js");
    expect(JSON.stringify({ gaCalls, metaCalls })).not.toMatch(/Purchase|guest@example.com|Test Guest|Test Hotel|STQ-|201000000001/);
  });

  it("keeps all providers absent when no IDs are configured, despite consent", async () => {
    const track = await setup("", "");
    consent.granted.value = true;
    await flushPromises();
    track("request_submitted", { item_id: catalogId });
    expect(w.dataLayer).toBeUndefined();
    expect(w.fbq).toBeUndefined();
    expect(loadedScripts).toEqual([]);
  });

  it("stops app events and SPA page views immediately after consent is withdrawn", async () => {
    const track = await setup("G-TEST123", "123456789");
    consent.granted.value = true;
    await flushPromises();
    track("request_submitted", { item_id: catalogId });
    const gaBefore = w.dataLayer?.length;
    const metaBefore = w.fbq?.queue?.length;
    expect(gaBefore).toBeGreaterThan(0);
    expect(metaBefore).toBeGreaterThan(0);
    consent.granted.value = false;
    await flushPromises();
    track("request_submitted", { item_id: catalogId });
    navigationHook?.({ path: "/en/about" }, { path: "/en/tour/sunset-quad-bike" });
    expect(w.dataLayer).toHaveLength(gaBefore!);
    expect(w.fbq?.queue).toHaveLength(metaBefore!);
  });
});
