import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import DiveSitesIndexPage from "../app/pages/dive-sites/index.vue";

afterEach(() => {
  vi.unstubAllGlobals();
});

function mountPage() {
  vi.stubGlobal("fetch", vi.fn(async () => new Response("", { status: 500 })));
  return mount(DiveSitesIndexPage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
  });
}

describe("Sharm Divers Club dive sites index page", () => {
  it("lists every real named site with a real trip attached", async () => {
    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).toContain("Ras Mohammed");
    expect(wrapper.text()).toContain("Tiran");
    expect(wrapper.text()).toContain("SS Thistlegorm");
    expect(wrapper.text()).toContain("Dahab Blue Hole & Canyon");
  });

  it("never invents dive stats — the blurb text stays to real public geography/history", async () => {
    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).toContain("Sinai Peninsula");
    expect(wrapper.text()).not.toMatch(/\d+\s*m(eters)?\s*(deep|depth|visibility)/i);
  });

  it("keeps the main landmark focusable for the skip link", () => {
    const wrapper = mountPage();
    expect(wrapper.get("main").attributes("id")).toBe("main-content");
    expect(wrapper.get("main").attributes("tabindex")).toBe("-1");
  });
});
