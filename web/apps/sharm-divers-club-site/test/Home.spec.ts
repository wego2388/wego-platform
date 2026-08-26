import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";

import HomePage from "../app/pages/index.vue";

describe("Sharm Divers Club public foundation", () => {
  it("states the WhatsApp booking boundary and never presents a live price", () => {
    const wrapper = mount(HomePage, {
      global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
    });

    expect(wrapper.text()).toContain("Sharm Divers Club");
    expect(wrapper.text()).toContain("PADI 5 Star Dive Center");
    expect(wrapper.text()).toContain("There is no self-service checkout");
    expect(wrapper.text()).not.toMatch(/€\s?\d/);
    expect(wrapper.get('a[href="https://wa.me/201066461010"]')).toBeTruthy();
  });

  it("switches the public foundation between English LTR and Arabic RTL", async () => {
    let headFactory: (() => { htmlAttrs: { dir: string; lang: string } }) | undefined;
    vi.stubGlobal("useHead", (input: typeof headFactory) => {
      headFactory = input;
    });
    const wrapper = mount(HomePage, {
      global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
    });
    expect(wrapper.get("main").attributes("dir")).toBe("ltr");
    expect(headFactory).toBeDefined();
    expect(headFactory!().htmlAttrs).toEqual({ dir: "ltr", lang: "en" });

    await wrapper.get("button").trigger("click");
    await flushPromises();

    expect(wrapper.get("main").attributes("dir")).toBe("rtl");
    expect(headFactory!().htmlAttrs).toEqual({ dir: "rtl", lang: "ar" });
    expect(wrapper.text()).toContain("مركز PADI 5 نجوم");
  });
});
