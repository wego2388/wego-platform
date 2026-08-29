import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import SiteFooter from "../app/components/SiteFooter.vue";
import { siteCopy } from "../app/content/locales";

function mountFooter() {
  return mount(SiteFooter, {
    props: { locale: "en" as const, copy: siteCopy.en.footer },
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
  });
}

describe("SiteFooter", () => {
  it("shows the real contact channels, never a fabricated one", () => {
    const wrapper = mountFooter();

    expect(wrapper.get('a[href="https://wa.me/201066461010"]')).toBeTruthy();
    expect(wrapper.get('a[href="mailto:Sales@sharmdiversclub.com"]')).toBeTruthy();
    expect(wrapper.text()).toContain("+20 10 6646 1010");
  });

  it("links to the legal pages", () => {
    const wrapper = mountFooter();
    const legalLinks = wrapper.findAll("a").map(a => a.text());
    expect(legalLinks).toContain(siteCopy.en.footer.legal.privacy);
    expect(legalLinks).toContain(siteCopy.en.footer.legal.terms);
  });
});
