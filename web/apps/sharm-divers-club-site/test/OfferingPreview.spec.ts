import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import OfferingPreviewPage from "../app/pages/offering-preview.vue";

function mountPage() {
  return mount(OfferingPreviewPage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
  });
}

function buttonWithText(wrapper: ReturnType<typeof mountPage>, label: string) {
  const button = wrapper.findAll("button").find(candidate => candidate.text().trim() === label);
  if (!button) throw new Error(`Button not found: ${label}`);
  return button;
}

describe("Sharm Divers Club offering detail preview", () => {
  it("hides the price by default and labels it illustrative once shown", async () => {
    const wrapper = mountPage();

    expect(wrapper.text()).toContain("design evidence for one real catalog entry");
    expect(wrapper.text()).not.toMatch(/350\s?EUR/);

    await buttonWithText(wrapper, "Show illustrative price").trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("350 EUR");
    expect(wrapper.text()).toContain("not yet approved for publication");
  });

  it("always routes the booking action to the real WhatsApp channel", () => {
    const wrapper = mountPage();
    expect(wrapper.get('a[href="https://wa.me/201066461010"]')).toBeTruthy();
  });
});
