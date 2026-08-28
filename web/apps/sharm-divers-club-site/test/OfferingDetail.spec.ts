import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";

import OfferingDetailPage from "../app/pages/discover/[code].vue";

function mountPage(code: string) {
  vi.stubGlobal("useRoute", () => ({ params: { code } }));
  return mount(OfferingDetailPage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
  });
}

describe("Sharm Divers Club offering detail page", () => {
  it("shows the real, approved price and meta for a diving offering", () => {
    const wrapper = mountPage("PC04");

    expect(wrapper.text()).toContain("PADI Open Water Diver");
    expect(wrapper.text()).toContain("€350");
    expect(wrapper.text()).toContain("4 dives");
  });

  it("shows the real, approved price for a water sports offering", () => {
    const wrapper = mountPage("WS01");

    expect(wrapper.text()).toContain("Parasailing");
    expect(wrapper.text()).toContain("€30");
  });

  it("routes the inquiry action to the real WhatsApp channel with the offering pre-filled", () => {
    const wrapper = mountPage("PC04");
    const link = wrapper.get('a[href*="text="]');
    expect(decodeURIComponent(link.attributes("href") ?? "")).toContain("PADI Open Water Diver (PC04)");
  });

  it("lists other offerings in the same category", () => {
    const wrapper = mountPage("SD02");
    expect(wrapper.text()).toContain("Two guided shore dives");
  });

  it("throws a 404 for an unknown offering code", () => {
    expect(() => mountPage("DOES-NOT-EXIST")).toThrow();
  });
});
