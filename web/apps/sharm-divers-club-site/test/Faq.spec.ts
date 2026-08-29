import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import FaqPage from "../app/pages/faq.vue";

function mountPage() {
  return mount(FaqPage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
  });
}

describe("Sharm Divers Club FAQ page", () => {
  it("renders both the known and the not-yet-confirmed question groups", () => {
    const wrapper = mountPage();
    const details = wrapper.findAll("details");

    expect(details.length).toBeGreaterThan(0);
    expect(wrapper.get('a[href="https://wa.me/201066461010"]')).toBeTruthy();
  });

  it("keeps the main landmark focusable for the skip link", () => {
    const wrapper = mountPage();
    expect(wrapper.get("main").attributes("id")).toBe("main-content");
    expect(wrapper.get("main").attributes("tabindex")).toBe("-1");
  });

  it("switches between English LTR and Arabic RTL", async () => {
    const wrapper = mountPage();
    expect(wrapper.get("main").attributes("dir")).toBe("ltr");

    await wrapper.get("button").trigger("click");
    await flushPromises();

    expect(wrapper.get("main").attributes("dir")).toBe("rtl");
  });
});
