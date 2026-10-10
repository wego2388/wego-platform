import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import SalesPage from "../app/pages/sales.vue";
import SalesStatusNotice from "../app/components/SalesStatusNotice.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });
const initial = { requestsPaused: false, version: 3, reason: null, updatedAt: null, updatedByUserId: null };
function session(permissions = ["travel-sales:manage"]) { writeAuthSession({ token: "fixture", email: "staff@example.com", roles: [], permissions }); }
function page() { return mount(SalesPage, { global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } } }); }

describe("sales intake control", () => {
  it("does not fetch staff controls or offer writes without the dedicated permission", async () => {
    session(["service:manage"]);
    const fetch = vi.fn(); vi.stubGlobal("fetch", fetch);
    const wrapper = page(); await flushPromises();
    expect(wrapper.text()).toContain("تحتاج صلاحية");
    expect(fetch).not.toHaveBeenCalled();
    expect(wrapper.find("textarea").exists()).toBe(false);
    wrapper.unmount();
  });

  it("saves the loaded version and note once, renders saved state, and offers resume", async () => {
    session();
    let resolveWrite: ((value: Response) => void) | undefined;
    const fetch = vi.fn((_url: RequestInfo | URL, init?: RequestInit) => init?.method === "PUT"
      ? new Promise<Response>(resolve => { resolveWrite = resolve; })
      : Promise.resolve(new Response(JSON.stringify(initial))));
    vi.stubGlobal("fetch", fetch);
    const wrapper = page(); await flushPromises();
    await wrapper.get("textarea").setValue("Operator temporarily unavailable");
    const pause = wrapper.findAll("button").find(b => b.text() === "إيقاف استقبال طلبات جديدة")!;
    await pause.trigger("click");
    await pause.trigger("click");
    expect(fetch.mock.calls.filter(call => call[1]?.method === "PUT")).toHaveLength(1);
    const init = fetch.mock.calls.find(call => call[1]?.method === "PUT")![1]!;
    expect(JSON.parse(String(init.body))).toEqual({ requestsPaused: true, expectedVersion: 3, reason: "Operator temporarily unavailable" });
    resolveWrite!(new Response(JSON.stringify({ ...initial, requestsPaused: true, version: 4 })));
    await flushPromises();
    expect(wrapper.text()).toContain("تم حفظ الحالة");
    expect(wrapper.text()).toContain("استئناف استقبال الطلبات");
    await wrapper.findAll("button")[0]!.trigger("click");
    expect(wrapper.text()).toContain("New requests are paused");
    wrapper.unmount();
  });

  it("refreshes a stale manager version without silently repeating the write", async () => {
    session(); let gets = 0;
    const fetch = vi.fn((_url: RequestInfo | URL, init?: RequestInit) => Promise.resolve(init?.method === "PUT"
      ? new Response(JSON.stringify({ error: "version_conflict", currentVersion: 4 }), { status: 409 })
      : new Response(JSON.stringify(++gets === 1 ? initial : { ...initial, requestsPaused: true, version: 4 }))));
    vi.stubGlobal("fetch", fetch);
    const wrapper = page(); await flushPromises();
    await wrapper.get("textarea").setValue("Keep this draft reason");
    await wrapper.findAll("button").find(b => b.text() === "إيقاف استقبال طلبات جديدة")!.trigger("click");
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain("غيّر مدير آخر الحالة");
    expect(wrapper.text()).toContain("استئناف استقبال الطلبات");
    expect((wrapper.get("textarea").element as HTMLTextAreaElement).value).toBe("Keep this draft reason");
    expect(fetch.mock.calls.filter(call => call[1]?.method === "PUT")).toHaveLength(1);
    wrapper.unmount();
  });

  it("keeps the saved state when a write fails and reports the error", async () => {
    session();
    vi.stubGlobal("fetch", vi.fn((_url: RequestInfo | URL, init?: RequestInit) => Promise.resolve(init?.method === "PUT"
      ? new Response(JSON.stringify({ error: "unavailable" }), { status: 503 }) : new Response(JSON.stringify(initial)))));
    const wrapper = page(); await flushPromises();
    await wrapper.findAll("button").find(b => b.text() === "إيقاف استقبال طلبات جديدة")!.trigger("click"); await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain("تعذر حفظ");
    expect(wrapper.text()).toContain("استقبال الطلبات الجديدة متاح");
    expect(wrapper.text()).not.toContain("تم حفظ الحالة");
    wrapper.unmount();
  });

  it("clears an expired session rather than showing a successful save", async () => {
    session(); vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ error: "unauthorized" }), { status: 401 })));
    const wrapper = page(); await flushPromises();
    expect(sessionStorage.length).toBe(0);
    expect(wrapper.get('[role="alert"]').text()).toContain("انتهت جلسة الدخول");
    expect(wrapper.find("textarea").exists()).toBe(false); wrapper.unmount();
  });

  it("shows a public pause banner and updates immediately after a successful local save", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ requestsOpen: false }))));
    const wrapper = mount(SalesStatusNotice); await flushPromises();
    expect(wrapper.text()).toContain("New requests are paused");
    window.dispatchEvent(new CustomEvent("stg:sales-changed", { detail: { requestsOpen: true } }));
    await flushPromises(); expect(wrapper.find("aside").exists()).toBe(false); wrapper.unmount();
  });

  it("does not report an open status when availability could not be read", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => { throw new TypeError("offline"); }));
    const wrapper = mount(SalesStatusNotice); await flushPromises();
    expect(wrapper.text()).toContain("Could not verify request availability"); wrapper.unmount();
  });
});
