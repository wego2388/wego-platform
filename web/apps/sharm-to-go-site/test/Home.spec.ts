import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";

import HomePage from "../app/pages/index.vue";

describe("Sharm To Go public foundation", () => {
  it("states the marketplace responsibility boundary and never presents live inventory", () => {
    const wrapper = mount(HomePage);

    expect(wrapper.text()).toContain("Sharm To Go");
    expect(wrapper.text()).toContain("approved local partners");
    expect(wrapper.text()).toContain("not a claim that every service is already live");
    expect(wrapper.text()).not.toMatch(/\b[0-9]+ reviews?\b/i);
  });

  it("switches the public foundation between English LTR and Arabic RTL", async () => {
    let headFactory: (() => { htmlAttrs: { dir: string; lang: string } }) | undefined;
    vi.stubGlobal("useHead", (input: typeof headFactory) => {
      headFactory = input;
    });
    const wrapper = mount(HomePage);
    expect(wrapper.get("main").attributes("dir")).toBe("ltr");
    expect(headFactory).toBeDefined();
    expect(headFactory!().htmlAttrs).toEqual({ dir: "ltr", lang: "en" });

    await wrapper.get("button").trigger("click");
    await flushPromises();

    expect(wrapper.get("main").attributes("dir")).toBe("rtl");
    expect(headFactory!().htmlAttrs).toEqual({ dir: "rtl", lang: "ar" });
    expect(wrapper.text()).toContain("بعض التجارب سيقدمها شركاء محليون معتمدون");
  });
});
