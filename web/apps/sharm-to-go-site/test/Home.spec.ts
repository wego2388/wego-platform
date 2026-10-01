import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import HomePage from "../app/pages/index.vue";

const sampleCategory = {
  id: "44444444-4444-4444-4444-444444444444",
  code: "sea-adventures",
  name: { en: "Sea adventures", ar: "مغامرات بحرية" },
  description: null,
};

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("Sharm To Go public foundation", () => {
  it("presents the approved marketing promise without inventing ratings or availability", () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([]), { status: 200 })));
    const wrapper = mount(HomePage, { global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } } });

    expect(wrapper.text()).toContain("Sharm To Go");
    expect(wrapper.text()).toContain("operates and coordinates every experience directly");
    expect(wrapper.text()).toContain("25+ years of local tourism experience");
    expect(wrapper.text()).toContain("3M+");
    expect(wrapper.text()).toContain("5M+");
    expect(wrapper.text()).not.toMatch(/\b[0-9]+ reviews?\b/i);
    // "24/7 continuous support" contradicted the contact page's own honest
    // "no published support-hours commitment yet" — fixed to a claim this
    // product actually keeps (one point of contact, not a staffing promise).
    expect(wrapper.text()).not.toContain("24/7");
    expect(wrapper.text()).toContain("point of contact, from request to return");
  });

  it("switches the public foundation between English LTR and Arabic RTL", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([]), { status: 200 })));
    let headFactory: (() => { htmlAttrs: { dir: string; lang: string } }) | undefined;
    vi.stubGlobal("useHead", (input: typeof headFactory) => {
      headFactory = input;
    });
    const wrapper = mount(HomePage, { global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } } });
    expect(wrapper.get("main").attributes("dir")).toBe("ltr");
    expect(headFactory).toBeDefined();
    expect(headFactory!().htmlAttrs).toEqual({ dir: "ltr", lang: "en" });

    await wrapper.get("button").trigger("click");
    await flushPromises();

    expect(wrapper.get("main").attributes("dir")).toBe("rtl");
    expect(headFactory!().htmlAttrs).toEqual({ dir: "rtl", lang: "ar" });
    expect(wrapper.text()).toContain("تشغّل وتنسّق كل تجربة مباشرةً");
  });

  it("has a real search box, not a static preview, that routes the chosen category/date/party into the catalog page", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([sampleCategory]), { status: 200 })));
    const push = vi.fn();
    vi.stubGlobal("useRouter", () => ({ push, replace: () => {} }));

    const wrapper = mount(HomePage, { global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } } });
    await flushPromises();

    await wrapper.get("#search-category").setValue(sampleCategory.id);
    await wrapper.get("#search-date").setValue("2099-06-15");
    const increaseAdults = wrapper.get('button[aria-label="Increase Adults"]');
    await increaseAdults.trigger("click");
    await wrapper.get("form").trigger("submit");

    expect(push).toHaveBeenCalledWith({
      path: "/experiences",
      query: { category: sampleCategory.id, date: "2099-06-15", adults: "3" },
    });
  });
});
