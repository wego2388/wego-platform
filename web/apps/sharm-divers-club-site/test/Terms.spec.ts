import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import TermsPage from "../app/pages/terms.vue";

function mountPage() {
  return mount(TermsPage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
  });
}

describe("Sharm Divers Club terms of use page", () => {
  it("renders the terms sections", () => {
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
