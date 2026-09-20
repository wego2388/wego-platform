import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import ExperiencesPage from "../app/pages/experiences/index.vue";

afterEach(() => {
  vi.unstubAllGlobals();
});

const sampleCategory = {
  id: "44444444-4444-4444-4444-444444444444",
  code: "sea-adventures",
  name: { en: "Sea adventures", ar: "مغامرات بحرية" },
  description: null,
};

const sampleService = {
  id: "55555555-5555-5555-5555-555555555555",
  categoryId: sampleCategory.id,
  name: { en: "Desert Safari", ar: "سفاري صحراوي" },
  description: { en: "An evening safari.", ar: "رحلة مسائية." },
  confirmationType: "INSTANT",
  cancellationPolicy: { en: "Free cancellation.", ar: "إلغاء مجاني." },
  pickupInfo: null,
  inclusions: null,
  exclusions: null,
  operatedBy: "Red Sea Adventures",
  options: [
    { label: { en: "Evening trip", ar: "رحلة مسائية" }, durationMinutes: 180, maxParticipants: 10, priceAmount: "500.00", priceCurrency: "EGP", priceBasis: "PER_PERSON" },
  ],
  media: [{ assetReference: "asset-1", locale: "en" }],
};

function stubFetch(routes: Record<string, () => Response>) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), "http://localhost");
      const key = url.pathname + url.search;
      const exact = routes[key];
      if (exact) return exact();
      const withoutQuery = routes[url.pathname];
      if (withoutQuery) return withoutQuery();
      return new Response("not found", { status: 404 });
    }),
  );
}

function mountPage() {
  return mount(ExperiencesPage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
  });
}

describe("experiences page", () => {
  it("shows an honest empty state when the live catalog has nothing published", async () => {
    stubFetch({
      "/api/catalog/categories": () => new Response(JSON.stringify([]), { status: 200 }),
      "/api/catalog/services": () => new Response(JSON.stringify([]), { status: 200 }),
    });

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).toContain("No live experiences yet");
    expect(wrapper.text()).not.toContain("Desert Safari");
  });

  it("renders real published services with category, price and operator", async () => {
    stubFetch({
      "/api/catalog/categories": () => new Response(JSON.stringify([sampleCategory]), { status: 200 }),
      "/api/catalog/services": () => new Response(JSON.stringify([sampleService]), { status: 200 }),
    });

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).toContain("Desert Safari");
    expect(wrapper.text()).toContain("Sea adventures");
    expect(wrapper.text()).toContain("EGP 500.00");
    expect(wrapper.text()).toContain("Red Sea Adventures");
    expect(wrapper.text()).toContain("1 photo");
    expect(wrapper.findAll("a").some((link) => link.text() === "View details")).toBe(true);
  });

  it("re-fetches services scoped to the selected category", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), "http://localhost");
      if (url.pathname === "/api/catalog/categories") {
        return new Response(JSON.stringify([sampleCategory]), { status: 200 });
      }
      if (url.pathname === "/api/catalog/services") {
        return new Response(JSON.stringify(url.searchParams.get("categoryId") ? [sampleService] : []), { status: 200 });
      }
      return new Response("not found", { status: 404 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mountPage();
    await flushPromises();
    expect(wrapper.text()).toContain("No live experiences yet");

    const categoryButton = wrapper.findAll("button").find((button) => button.text() === "Sea adventures");
    await categoryButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("Desert Safari");
  });

  it("shows a real error, not a raw crash, when the catalog cannot be reached", async () => {
    stubFetch({
      "/api/catalog/categories": () => new Response("boom", { status: 500 }),
      "/api/catalog/services": () => new Response("boom", { status: 500 }),
    });

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("We could not reach the live catalog");
  });
});
