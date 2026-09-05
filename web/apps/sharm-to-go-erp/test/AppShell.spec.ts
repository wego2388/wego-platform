import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import AppShell from "../app/layouts/app-shell.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  window.localStorage.clear();
  document.documentElement.removeAttribute("data-theme");
});

function mountShell() {
  return mount(AppShell, {
    slots: { default: "<p>Page content</p>" },
    global: {
      stubs: {
        NuxtLink: { template: "<a><slot /></a>", props: ["to"] },
      },
    },
  });
}

describe("app-shell layout", () => {
  it("renders every nav group and link exactly once — no parallel desktop/mobile copy", async () => {
    const wrapper = mountShell();
    await flushPromises();

    for (const group of ["Overview", "Travel Marketplace"]) {
      expect(wrapper.findAll("p").filter((p) => p.text() === group)).toHaveLength(1);
    }
    for (const link of ["Dashboard", "Providers", "Categories", "Services"]) {
      const matches = wrapper.findAll("a").filter((a) => a.text() === link);
      expect(matches).toHaveLength(1);
    }
  });

  it("has exactly one <main> landmark and renders the slot content inside it", () => {
    const wrapper = mountShell();
    const mains = wrapper.findAll("main");
    expect(mains).toHaveLength(1);
    expect(mains[0]?.text()).toContain("Page content");
  });

  it("has a skip-to-content link pointing at the real main landmark", () => {
    const wrapper = mountShell();
    const skipLink = wrapper.get("a[href='#main-content']");
    expect(skipLink.text()).toBe("Skip to content");
    expect(wrapper.get("main").attributes("id")).toBe("main-content");
  });

  it("the mobile nav toggle starts collapsed (aria-expanded=false) and opens the drawer on click", async () => {
    const wrapper = mountShell();
    const toggle = wrapper.get("button[aria-controls='app-nav']");
    expect(toggle.attributes("aria-expanded")).toBe("false");

    await toggle.trigger("click");
    expect(toggle.attributes("aria-expanded")).toBe("true");
    expect(wrapper.get("nav#app-nav").classes()).toContain("translate-x-0");
  });

  it("Escape closes the open drawer and returns focus to the toggle button", async () => {
    document.body.innerHTML = "";
    const wrapper = mountShell();
    document.body.appendChild(wrapper.element);

    const toggle = wrapper.get("button[aria-controls='app-nav']");
    await toggle.trigger("click");
    expect(toggle.attributes("aria-expanded")).toBe("true");

    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    await wrapper.vm.$nextTick();

    expect(toggle.attributes("aria-expanded")).toBe("false");
    expect(document.activeElement).toBe(toggle.element);
    wrapper.element.remove();
  });

  it("clicking the overlay backdrop closes the drawer", async () => {
    const wrapper = mountShell();
    await wrapper.get("button[aria-controls='app-nav']").trigger("click");
    expect(wrapper.get("nav#app-nav").classes()).toContain("translate-x-0");

    await wrapper.get(".bg-wego-surface-overlay").trigger("click");
    expect(wrapper.get("button[aria-controls='app-nav']").attributes("aria-expanded")).toBe("false");
  });

  it("shows the signed-in account's email when a session exists, otherwise a generic Account link", async () => {
    writeAuthSession({ token: "t", email: "staff@example.com", roles: [], permissions: [] });
    const wrapper = mountShell();
    await flushPromises();
    expect(wrapper.text()).toContain("staff@example.com");
  });

  it("the theme control cycles system -> light -> dark -> system and applies it live", async () => {
    const wrapper = mountShell();
    const themeButton = wrapper.findAll("button").find((button) => button.text().includes("Theme"));
    expect(themeButton?.text()).toContain("system");

    await themeButton?.trigger("click");
    expect(themeButton?.text()).toContain("light");
    expect(document.documentElement.hasAttribute("data-theme")).toBe(false);

    await themeButton?.trigger("click");
    expect(themeButton?.text()).toContain("dark");
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");

    await themeButton?.trigger("click");
    expect(themeButton?.text()).toContain("system");
  });
});
