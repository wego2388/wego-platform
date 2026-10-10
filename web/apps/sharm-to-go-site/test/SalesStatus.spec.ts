import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import SalesStatusBanner from "../app/components/SalesStatusBanner.vue";
import RequestPage from "../app/pages/experiences/[id]/request.vue";

afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });
const service = { id: "fixture-service", categoryId: "c", name: { en: "Sea experience", ar: "تجربة بحرية" }, description: { en: "Sea", ar: "بحر" }, confirmationType: "INSTANT", cancellationPolicy: { en: "Policy", ar: "السياسة" }, options: [{ id: "option", label: { en: "Trip", ar: "رحلة" }, durationMinutes: 60, maxParticipants: 5, priceAmount: "500.00", priceCurrency: "EGP", priceBasis: "PER_PERSON" }], media: [] };
async function review(wrapper: ReturnType<typeof mount>) {
  await wrapper.get("#date").setValue("2099-01-01"); await wrapper.get("form").trigger("submit"); await flushPromises();
  await wrapper.get("#name").setValue("Fixture Guest"); await wrapper.get("#phone").setValue("+201000000001");
  await wrapper.get("#consent").setValue(true); await wrapper.get("form").trigger("submit"); await flushPromises();
}
function page() {
  vi.stubGlobal("useRoute", () => ({ params: { id: service.id }, query: {} }));
  return mount(RequestPage, { global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } } });
}
describe("public request intake", () => {
  it("blocks a fresh submit during pause while keeping the request form and support usable", async () => {
    const fetch = vi.fn(async (url: RequestInfo | URL) => new Response(JSON.stringify(String(url) === "/api/sales-status" ? { requestsOpen: false } : service)));
    vi.stubGlobal("fetch", fetch);
    const wrapper = page(); await flushPromises(); await review(wrapper);
    expect(wrapper.text()).toContain("temporarily paused");
    expect(wrapper.get('button.flex-1').attributes("disabled")).toBeDefined();
    expect(fetch.mock.calls.some(call => String(call[0]) === "/api/requests")).toBe(false);
    wrapper.unmount();
  });
  it("blocks a new request on unknown availability and allows it after a successful refresh", async () => {
    let reachable = false;
    vi.stubGlobal("fetch", vi.fn(async (url: RequestInfo | URL) => {
      if (String(url) === "/api/sales-status") {
        if (!reachable) throw new TypeError("offline");
        return new Response(JSON.stringify({ requestsOpen: true }));
      }
      return new Response(JSON.stringify(service));
    }));
    const wrapper = page(); await flushPromises(); await review(wrapper);
    expect(wrapper.get('button.flex-1').attributes("disabled")).toBeDefined();
    reachable = true;
    await wrapper.findAll("button").find(button => button.text() === "Check availability again")!.trigger("click"); await flushPromises();
    expect(wrapper.get('button.flex-1').attributes("disabled")).toBeUndefined(); wrapper.unmount();
  });
  it("handles a server pause after a form was loaded open without losing the chosen details", async () => {
    vi.stubGlobal("fetch", vi.fn(async (url: RequestInfo | URL) => new Response(JSON.stringify(
      String(url) === "/api/sales-status" ? { requestsOpen: true } : String(url) === "/api/requests" ? { error: "requests_paused" } : service,
    ), { status: String(url) === "/api/requests" ? 503 : 200 })));
    const wrapper = page(); await flushPromises(); await review(wrapper);
    await wrapper.get('button.flex-1').trigger("click"); await flushPromises();
    expect(wrapper.text()).toContain("temporarily paused");
    expect(wrapper.text()).toContain("2099-01-01");
    expect(wrapper.get('button.flex-1').attributes("disabled")).toBeDefined(); wrapper.unmount();
  });
  it("can recover a lost successful response with the same key even after intake is paused", async () => {
    let posts = 0; const keys: string[] = [];
    vi.stubGlobal("fetch", vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
      if (String(url) === "/api/sales-status") return new Response(JSON.stringify({ requestsOpen: posts === 0 }));
      if (String(url) === "/api/requests") {
        keys.push((init!.headers as Record<string, string>)["Idempotency-Key"]!);
        if (++posts === 1) throw new TypeError("response lost");
        return new Response(JSON.stringify({ reference: "STG-ABCDEFGH", status: "CONFIRMED", serviceName: service.name, optionLabel: service.options[0]!.label, adults: 2, children: 0 }));
      }
      return new Response(JSON.stringify(service));
    }));
    const wrapper = page(); await flushPromises(); await review(wrapper);
    await wrapper.get('button.flex-1').trigger("click"); await flushPromises();
    // The shared state is what the global banner's next poll updates.
    useState<boolean | null>("stg-sales-open", () => null).value = false;
    await flushPromises();
    expect(wrapper.get('button.flex-1').attributes("disabled")).toBeUndefined();
    await wrapper.get('button.flex-1').trigger("click"); await flushPromises();
    expect(keys).toHaveLength(2); expect(keys[1]).toBe(keys[0]); expect(wrapper.text()).toContain("STG-ABCDEFGH"); wrapper.unmount();
  });
  it("keeps a server pause when an older availability read finishes afterwards", async () => {
    let reads = 0;
    let finishOlderRead!: (response: Response) => void;
    vi.stubGlobal("fetch", vi.fn(async (url: RequestInfo | URL) => {
      if (String(url) === "/api/sales-status") {
        if (++reads === 2) return new Promise<Response>(resolve => { finishOlderRead = resolve; });
        return new Response(JSON.stringify({ requestsOpen: true }));
      }
      if (String(url) === "/api/requests") return new Response(JSON.stringify({ error: "requests_paused" }), { status: 503 });
      return new Response(JSON.stringify(service));
    }));
    const wrapper = page(); await flushPromises(); await review(wrapper);
    const banner = mount(SalesStatusBanner); await flushPromises();
    expect(reads).toBe(2);
    await wrapper.get('button.flex-1').trigger("click"); await flushPromises();
    finishOlderRead(new Response(JSON.stringify({ requestsOpen: true }))); await flushPromises();
    expect(banner.text()).toContain("temporarily paused");
    expect(wrapper.get('button.flex-1').attributes("disabled")).toBeDefined();
    banner.unmount(); wrapper.unmount();
  });
  it("shows the global pause notice in Arabic and polls again on window focus", async () => {
    localStorage.setItem("sharm-to-go-locale", "ar");
    let open = false;
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ requestsOpen: open }))));
    const wrapper = mount(SalesStatusBanner); await flushPromises();
    expect(wrapper.text()).toContain("متوقف مؤقتًا");
    open = true; window.dispatchEvent(new Event("focus")); await flushPromises();
    expect(wrapper.find("aside").exists()).toBe(false); wrapper.unmount();
  });
});

describe("status proxy", () => {
  it("returns only the public flag and disables caching", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ requestsOpen: false, reason: "PRIVATE" }))));
    const headers = vi.fn(); vi.stubGlobal("setResponseHeader", headers);
    vi.stubGlobal("defineEventHandler", <T>(handler: T) => handler);
    const handler = (await import("../server/api/sales-status.get")).default;
    expect(await handler({} as never)).toEqual({ requestsOpen: false });
    expect(headers).toHaveBeenCalledWith({}, "cache-control", "no-store");
  });
  it("refuses malformed status instead of interpreting a missing flag as open", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({}))));
    vi.stubGlobal("defineEventHandler", <T>(handler: T) => handler);
    const handler = (await import("../server/api/sales-status.get")).default;
    await expect(handler({} as never)).rejects.toMatchObject({ statusCode: 502 });
  });
});
