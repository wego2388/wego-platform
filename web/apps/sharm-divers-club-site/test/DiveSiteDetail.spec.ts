import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import DiveSiteDetailPage from "../app/pages/dive-sites/[slug].vue";

afterEach(() => {
  vi.unstubAllGlobals();
});

function mountPage(slug: string) {
  vi.stubGlobal("fetch", vi.fn(async () => new Response("", { status: 500 })));
  vi.stubGlobal("useRoute", () => ({ params: { slug } }));
  return mount(DiveSiteDetailPage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
  });
}

describe("Sharm Divers Club dive site detail page", () => {
  it("shows the real named site and the real, approved trips that visit it", async () => {
    const wrapper = mountPage("ras-mohammed");
    await flushPromises();

    expect(wrapper.text()).toContain("Ras Mohammed");
    expect(wrapper.text()).toContain("Ras Mohammed beginner dive — 30 minutes");
    expect(wrapper.text()).toContain("€60");
  });

  it("routes the inquiry action to the real WhatsApp channel with the site named", async () => {
    const wrapper = mountPage("tiran");
    await flushPromises();

    const link = wrapper.get('a[href*="text="]');
    expect(decodeURIComponent(link.attributes("href") ?? "")).toContain("Tiran");
  });

  it("throws a 404 for an unknown site slug", () => {
    expect(() => mountPage("does-not-exist")).toThrow();
  });
});
