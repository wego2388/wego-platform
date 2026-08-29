import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";

import SiteHeader from "../app/components/SiteHeader.vue";

const baseProps = {
  locale: "en" as const,
  direction: "ltr" as const,
  homeLabel: "Sharm Divers Club home",
  discoverLabel: "Discover",
  aboutLabel: "About",
  faqLabel: "FAQ",
  contactLabel: "Contact",
  languageName: "العربية",
  whatsappLabel: "Message us on WhatsApp",
  menuLabel: "Menu",
};

function mountHeader(props: Partial<typeof baseProps> = {}) {
  return mount(SiteHeader, {
    props: { ...baseProps, ...props },
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
  });
}

describe("SiteHeader", () => {
  it("starts with the mobile menu closed and toggles it open on click", async () => {
    const wrapper = mountHeader();
    const menuButton = wrapper.get('button[aria-controls="sdc-mobile-menu"]');

    expect(menuButton.attributes("aria-expanded")).toBe("false");
    expect(wrapper.find("#sdc-mobile-menu").exists()).toBe(false);

    await menuButton.trigger("click");

    expect(menuButton.attributes("aria-expanded")).toBe("true");
    expect(wrapper.get("#sdc-mobile-menu").attributes("aria-label")).toBe("Mobile navigation");
  });

  it("closes the mobile menu when a nav link is clicked", async () => {
    const wrapper = mountHeader();
    await wrapper.get('button[aria-controls="sdc-mobile-menu"]').trigger("click");
    expect(wrapper.find("#sdc-mobile-menu").exists()).toBe(true);

    await wrapper.get("#sdc-mobile-menu a").trigger("click");

    expect(wrapper.find("#sdc-mobile-menu").exists()).toBe(false);
  });

  it("emits toggle-locale when the language button is clicked", async () => {
    const wrapper = mountHeader();
    const languageButtons = wrapper.findAll("button").filter(button => button.text() === "العربية");
    await languageButtons[0]!.trigger("click");

    expect(wrapper.emitted("toggle-locale")).toHaveLength(1);
  });

  it("links the real WhatsApp channel in both the desktop and mobile CTAs", () => {
    const wrapper = mountHeader();
    const whatsappLinks = wrapper.findAll('a[href="https://wa.me/201066461010"]');
    expect(whatsappLinks.length).toBeGreaterThan(0);
  });
});
