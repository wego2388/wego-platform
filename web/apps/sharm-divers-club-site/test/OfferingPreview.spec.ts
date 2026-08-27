import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import OfferingPreviewPage from "../app/pages/offering-preview.vue";

function mountPage() {
  return mount(OfferingPreviewPage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
  });
}

describe("Sharm Divers Club offering detail page", () => {
  it("shows the real, approved price for the exemplar offering", () => {
    const wrapper = mountPage();

    expect(wrapper.text()).toContain("PADI Open Water Diver");
    expect(wrapper.text()).toContain("350 EUR");
    expect(wrapper.text()).toContain("Approved 2026 price");
  });

  it("routes the inquiry action to the real WhatsApp channel with the offering pre-filled", () => {
    const wrapper = mountPage();
    const link = wrapper.get('a[href^="https://wa.me/201066461010"]');
    expect(decodeURIComponent(link.attributes("href") ?? "")).toContain("PADI Open Water Diver (PC04)");
  });

  it("switches to Arabic RTL", async () => {
    const wrapper = mountPage();
    await wrapper.get("button").trigger("click");
    await flushPromises();

    expect(wrapper.get("main").attributes("dir")).toBe("rtl");
    expect(wrapper.text()).toContain("350 EUR");
  });
});
