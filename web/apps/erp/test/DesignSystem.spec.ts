import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import DesignSystemPage from "../app/pages/design-system.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("design system page", () => {
  it("shows a sign-in prompt for an anonymous visitor", async () => {
    const wrapper = mount(DesignSystemPage);
    await flushPromises();
    expect(wrapper.text()).toContain("You need to sign in");
  });

  it("renders every component section for a signed-in staff member", async () => {
    writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions: [] });
    const wrapper = mount(DesignSystemPage);
    await flushPromises();

    for (const section of ["Theme", "Buttons", "Alerts", "Badges", "Form controls", "Pagination", "Empty state", "Dialog"]) {
      expect(wrapper.text()).toContain(section);
    }
  });

  it("switching the theme preference actually calls the shared mechanism, not a local copy", async () => {
    writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions: [] });
    const wrapper = mount(DesignSystemPage);
    await flushPromises();

    const darkButton = wrapper.findAll("button").find((button) => button.text() === "dark");
    await darkButton?.trigger("click");

    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
    expect(window.localStorage.getItem("wego-erp-theme")).toBe("dark");
  });

  it("the dialog opens and its actions close it with the right result", async () => {
    writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions: [] });
    const wrapper = mount(DesignSystemPage, { attachTo: document.body });
    await flushPromises();

    const openButton = wrapper.findAll("button").find((button) => button.text() === "Close offering…");
    await openButton?.trigger("click");
    await wrapper.vm.$nextTick();
    expect((wrapper.get("dialog").element as HTMLDialogElement).open).toBe(true);

    const confirmButton = wrapper.findAll("dialog button").find((button) => button.text() === "Close offering");
    await confirmButton?.trigger("click");
    await wrapper.vm.$nextTick();

    expect(wrapper.text()).toContain("Last result: Confirmed.");
    wrapper.unmount();
  });
});
