import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import AboutPage from "../app/pages/about.vue";
import ContactPage from "../app/pages/contact.vue";
import FaqPage from "../app/pages/faq.vue";

const global = { stubs: { NuxtLink: { template: "<a><slot /></a>" } } };

describe("marketing and trust pages", () => {
  it("renders the owner-approved experience story with careful historical wording", () => {
    const wrapper = mount(AboutPage, { global });

    expect(wrapper.text()).toContain("twenty-five years");
    expect(wrapper.text()).toContain("more than three million customers");
    expect(wrapper.text()).toContain("participated in organising more than five million successful trips");
    expect(wrapper.text()).toContain("Where you must go");
  });

  it("links the real owner-supplied contact channels", () => {
    const wrapper = mount(ContactPage, { global });
    const hrefs = wrapper.findAll("a").map(link => link.attributes("href"));

    expect(wrapper.text()).toContain("+20 10 0141 3469");
    expect(wrapper.text()).toContain("info@sharmtogo.com");
    expect(hrefs.some(href => href?.startsWith("https://wa.me/201001413469"))).toBe(true);
    expect(hrefs.some(href => href?.startsWith("mailto:info@sharmtogo.com"))).toBe(true);
  });

  it("does not represent an enquiry as an instant confirmed booking", () => {
    const wrapper = mount(FaqPage, { global });

    expect(wrapper.text()).toContain("Sending a request or WhatsApp message is not itself a confirmed booking");
    expect(wrapper.text()).not.toContain("request is confirmed instantly");
  });
});
