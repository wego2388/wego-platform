import { flushPromises } from "@vue/test-utils";
import { effectScope, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const consent = { granted: ref(false) };
vi.mock("../app/composables/useConsent", () => ({ useConsent: () => consent }));

type Track = (event: string, params: Record<string, string | number>) => void;
type TrackingWindow = Window & { dataLayer?: unknown[]; gtag?: (...args: unknown[]) => void; fbq?: ((...args: unknown[]) => void) & { queue?: unknown[]; callMethod?: (...args: unknown[]) => void } } & Partial<Record<`ga-disable-${string}`, boolean>>;
const w = window as unknown as TrackingWindow;
let scope = effectScope();
let loadedScripts: string[] = [];
let routeRef: { value: { path: string } } = { value: { path: "/" } };
let navigationHook: ((to: { path: string }, from: { path: string }) => void) | null = null;

beforeEach(() => {
  consent.granted.value = false;
  scope = effectScope();
  loadedScripts = [];
  navigationHook = null;
  delete w.dataLayer; delete w.gtag; delete w.fbq;
  delete (w as Partial<TrackingWindow>)["ga-disable-G-TEST123"];
  // Capture insertions without asking happy-dom to fetch external JavaScript.
  vi.spyOn(document.head, "appendChild").mockImplementation((node) => {
    if (node instanceof HTMLScriptElement) loadedScripts.push(node.src);
    return node;
  });
  vi.stubGlobal("defineNuxtPlugin", (plugin: unknown) => plugin);
});

afterEach(() => { scope.stop(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

async function setup(ga4Id: string, metaPixelId: string, path = "/en/tour/sunset-quad-bike"): Promise<Track> {
  const config = { public: { ga4Id, metaPixelId } };
  const currentRoute = ref({ path });
  routeRef = currentRoute;
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
    consent.granted.value = false;
    // No nextTick gap in the provider kill switch.
    expect(w["ga-disable-G-TEST123"]).toBe(true);
    expect(Array.from(w.dataLayer!.at(-1) as ArrayLike<unknown>)).toEqual(["consent", "update", expect.objectContaining({ analytics_storage: "denied", ad_storage: "denied" })]);
    expect(w.fbq!.queue!.at(-1)).toEqual(["consent", "revoke"]);
    const gaBefore = w.dataLayer?.length;
    const metaBefore = w.fbq?.queue?.length;
    await flushPromises();
    track("request_submitted", { item_id: catalogId });
    navigationHook?.({ path: "/en/about" }, { path: "/en/tour/sunset-quad-bike" });
    expect(w.dataLayer).toHaveLength(gaBefore!);
    expect(w.fbq?.queue).toHaveLength(metaBefore!);
  });

  it("regrants loaded providers without reinitializing or loading duplicate tags", async () => {
    await setup("G-TEST123", "123456789");
    consent.granted.value = true;
    await flushPromises();
    consent.granted.value = false;
    consent.granted.value = true;
    await flushPromises();
    expect(w["ga-disable-G-TEST123"]).toBe(false);
    expect(loadedScripts).toHaveLength(2);
    const calls = (w.dataLayer ?? []).map(call => Array.from(call as ArrayLike<unknown>));
    expect(calls.filter(call => call[0] === "config")).toHaveLength(1);
    expect(calls).toContainEqual(["consent", "update", { analytics_storage: "granted", ad_storage: "denied", ad_user_data: "denied", ad_personalization: "denied" }]);
    expect(w.fbq!.queue).toContainEqual(["consent", "grant"]);
    expect(w.fbq!.queue!.filter(call => (call as unknown[])[0] === "init")).toHaveLength(1);
  });

  it("does not emit a queued startup pageview after immediate withdrawal", async () => {
    await setup("G-TEST123", "123456789");
    consent.granted.value = true;
    consent.granted.value = false;
    await flushPromises();
    const calls = (w.dataLayer ?? []).map(call => Array.from(call as ArrayLike<unknown>));
    expect(calls.filter(call => call[0] === "event")).toEqual([]);
    expect(w.fbq!.queue!.filter(call => (call as unknown[])[0] === "track")).toEqual([]);
    expect(w["ga-disable-G-TEST123"]).toBe(true);
  });

  it("delivers revocation to an already loaded Meta provider, not only its startup queue", async () => {
    const track = await setup("G-TEST123", "123456789");
    consent.granted.value = true;
    await flushPromises();
    const provider = vi.fn();
    w.fbq!.callMethod = provider;
    consent.granted.value = false;
    expect(provider).toHaveBeenCalledExactlyOnceWith("consent", "revoke");
    track("request_submitted", { item_id: catalogId });
    expect(provider).toHaveBeenCalledTimes(1);
    consent.granted.value = true;
    expect(provider).toHaveBeenCalledWith("consent", "grant");
  });

  it("never loads providers or sends events on payment-return paths, and starts on the next normal page", async () => {
    const { isPaymentSensitivePath } = await import("../app/plugins/analytics.client");
    for (const path of ["/booking/payment-result", "/en/booking/payment-result", "/ar/booking/confirmation"]) {
      expect(isPaymentSensitivePath(path)).toBe(true);
    }
    for (const path of ["/", "/en", "/en/tour/booking-tips", "/en/my-booking"]) expect(isPaymentSensitivePath(path)).toBe(false);
    const track = await setup("G-TEST123", "123456789", "/en/booking/payment-result");
    consent.granted.value = true;
    await flushPromises();
    track("purchase", { value: 10 });
    navigationHook?.({ path: "/en/booking/confirmation" }, { path: "/en/booking/payment-result" });
    expect(w.dataLayer).toBeUndefined();
    expect(w.fbq).toBeUndefined();
    expect(loadedScripts).toEqual([]);
    routeRef.value = { path: "/en" };
    navigationHook?.({ path: "/en" }, { path: "/en/booking/confirmation" });
    await flushPromises();
    expect(loadedScripts).toHaveLength(2);
  });
});
