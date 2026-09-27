import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";

import HomePage from "../app/pages/index.vue";

describe("Sharm To Go public foundation", () => {
  it("presents the approved marketing promise without inventing ratings or availability", () => {
    const wrapper = mount(HomePage, { global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } } });

    expect(wrapper.text()).toContain("Sharm To Go");
    expect(wrapper.text()).toContain("operates and coordinates every experience directly");
    expect(wrapper.text()).toContain("25+ years of local tourism experience");
    expect(wrapper.text()).toContain("3M+");
    expect(wrapper.text()).toContain("5M+");
    expect(wrapper.text()).not.toMatch(/\b[0-9]+ reviews?\b/i);
  });

  it("switches the public foundation between English LTR and Arabic RTL", async () => {
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
});
