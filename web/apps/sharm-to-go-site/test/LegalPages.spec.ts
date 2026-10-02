import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import PrivacyPage from "../app/pages/privacy.vue";
import TermsPage from "../app/pages/terms.vue";

const global = { stubs: { NuxtLink: { template: "<a><slot /></a>" } } };

describe("legal pages describe the real current product, not an earlier one", () => {
  it("privacy policy says the real request form collects contact details, not that the site has no accounts or contact form", () => {
    const wrapper = mount(PrivacyPage, { global });

    expect(wrapper.text()).toContain("your name and at least one way to reach you");
    expect(wrapper.text()).not.toContain("has no accounts or contact form");
    expect(wrapper.text()).not.toContain("When online booking goes live");
    expect(wrapper.text()).toContain("never show your name, phone or email");
  });

  it("terms say most experiences confirm instantly, not that every request needs manual verification first", () => {
    const wrapper = mount(TermsPage, { global });

    expect(wrapper.text()).toContain("Most experiences confirm instantly");
    expect(wrapper.text()).not.toMatch(/becomes confirmed only after/i);
  });
});
