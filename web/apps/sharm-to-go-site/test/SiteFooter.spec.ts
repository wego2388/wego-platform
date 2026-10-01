import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import SiteFooter from "../app/components/SiteFooter.vue";

const global = { stubs: { NuxtLink: { template: "<a :href=\"to\"><slot /></a>", props: ["to"] } } };

describe("SiteFooter", () => {
  it("links every real trust/legal page and the real contact channels, in English", () => {
    const wrapper = mount(SiteFooter, { props: { locale: "en" }, global });
    const hrefs = wrapper.findAll("a").map((link) => link.attributes("href"));

    expect(hrefs).toContain("/privacy");
    expect(hrefs).toContain("/terms");
    expect(hrefs).toContain("/about");
    expect(hrefs).toContain("/faq");
    expect(hrefs).toContain("/contact");
    expect(hrefs).toContain("/track");
    expect(hrefs.some((href) => href?.startsWith("https://wa.me/201001413469"))).toBe(true);
    expect(hrefs.some((href) => href?.startsWith("mailto:info@sharmtogo.com"))).toBe(true);
    expect(wrapper.text()).toContain("© 2026 Sharm To Go. All rights reserved.");
  });

  it("renders the Arabic copy, not a mix of languages, when locale is ar", () => {
    const wrapper = mount(SiteFooter, { props: { locale: "ar" }, global });

    expect(wrapper.text()).toContain("جميع الحقوق محفوظة");
    expect(wrapper.text()).toContain("التجارب");
    expect(wrapper.text()).toContain("سياسة الخصوصية");
    expect(wrapper.text()).not.toContain("All rights reserved");
  });
});
