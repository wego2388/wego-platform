import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import DiscoverIndexPage from "../app/pages/discover/index.vue";

function mountPage() {
  return mount(DiscoverIndexPage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
  });
}

describe("Sharm Divers Club discover index page", () => {
  it("lists every real category and offering by default", () => {
    const wrapper = mountPage();

    expect(wrapper.text()).toContain("Boat diving");
    expect(wrapper.text()).toContain("PADI courses");
    expect(wrapper.get('a[href="https://wa.me/201066461010"]')).toBeTruthy();
  });

  it("filters offerings down to a single category on click", async () => {
    const wrapper = mountPage();
    const buttons = wrapper.findAll('button[aria-pressed]');
    const boatDivingButton = buttons.find(button => button.text() === "Boat diving");
    expect(boatDivingButton).toBeTruthy();

    await boatDivingButton!.trigger("click");

    expect(boatDivingButton!.attributes("aria-pressed")).toBe("true");
    expect(wrapper.text()).not.toContain("PADI Open Water Diver");
  });

  it("keeps the main landmark focusable for the skip link", () => {
    const wrapper = mountPage();
    expect(wrapper.get("main").attributes("id")).toBe("main-content");
    expect(wrapper.get("main").attributes("tabindex")).toBe("-1");
  });
});
