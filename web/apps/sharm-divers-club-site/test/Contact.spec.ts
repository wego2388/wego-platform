import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import ContactPage from "../app/pages/contact.vue";

function mountPage() {
  return mount(ContactPage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
  });
}

describe("Sharm Divers Club contact page", () => {
  it("shows the real WhatsApp number, email and location, never a fabricated one", () => {
    const wrapper = mountPage();

    expect(wrapper.get('a[href="https://wa.me/201066461010"]')).toBeTruthy();
    expect(wrapper.text()).toContain("+20 10 6646 1010");
    expect(wrapper.text()).toContain("Sales@sharmdiversclub.com");
    expect(wrapper.text()).toContain("Royal Grand Sharm Hotel");
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
    expect(wrapper.text()).toContain("+20 10 6646 1010");
  });
});
