import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import PrivacyPage from "../app/pages/privacy.vue";

function mountPage() {
  return mount(PrivacyPage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
  });
}

describe("Sharm Divers Club privacy policy page", () => {
  it("renders the policy sections", () => {
    const wrapper = mountPage();
    expect(wrapper.findAll("h2").length).toBeGreaterThan(0);
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
