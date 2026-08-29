import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import AboutPage from "../app/pages/about.vue";

function mountPage() {
  return mount(AboutPage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
  });
}

describe("Sharm Divers Club about page", () => {
  it("shows the real office photo and the team facts", () => {
    const wrapper = mountPage();

    const img = wrapper.get('img[src="/images/offerings/about-office.webp"]');
    expect(img.attributes("alt")).toContain("Sharm Divers Club");
    expect(wrapper.text()).toContain("PADI 5 Star Dive Center");
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
    const img = wrapper.get('img[src="/images/offerings/about-office.webp"]');
    expect(img.attributes("alt")).toContain("شرم الشيخ");
  });
});
