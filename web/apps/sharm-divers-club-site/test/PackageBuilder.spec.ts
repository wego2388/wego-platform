import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import PackageBuilderPage from "../app/pages/package-builder.vue";

function mountPage() {
  return mount(PackageBuilderPage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
  });
}

describe("Sharm Divers Club package builder page", () => {
  it("starts empty with an honest empty-state message", () => {
    const wrapper = mountPage();
    expect(wrapper.text()).toContain("Nothing added yet");
  });

  it("adds a real offering and shows a real running total", async () => {
    const wrapper = mountPage();
    const addButtons = wrapper.findAll("button[aria-pressed]");
    const openWaterButton = addButtons.find(button => button.element.closest("div")?.textContent?.includes("PADI Open Water Diver"));
    expect(openWaterButton).toBeTruthy();

    await openWaterButton!.trigger("click");

    expect(wrapper.text()).toContain("€350");
    expect(openWaterButton!.attributes("aria-pressed")).toBe("true");
  });

  it("removes an offering and updates the total back down", async () => {
    const wrapper = mountPage();
    const addButtons = wrapper.findAll("button[aria-pressed]");
    const parasailingButton = addButtons.find(button => button.element.closest("div")?.textContent?.includes("Parasailing"));
    await parasailingButton!.trigger("click");
    expect(wrapper.text()).toContain("€30");

    const removeButton = wrapper.findAll("button").find(button => button.text() === "Remove");
    await removeButton!.trigger("click");

    expect(wrapper.text()).toContain("Nothing added yet");
  });

  it("prefills the WhatsApp inquiry with the selected offerings and the real total", async () => {
    const wrapper = mountPage();
    const addButtons = wrapper.findAll("button[aria-pressed]");
    const parasailingButton = addButtons.find(button => button.element.closest("div")?.textContent?.includes("Parasailing"));
    await parasailingButton!.trigger("click");

    const link = wrapper.get('a[href*="text="]');
    const decoded = decodeURIComponent(link.attributes("href") ?? "");
    expect(decoded).toContain("Parasailing");
    expect(decoded).toContain("€30");
  });
});
