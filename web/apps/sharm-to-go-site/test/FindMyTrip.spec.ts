import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import FindMyTripPage from "../app/pages/find-my-trip.vue";

afterEach(() => {
  vi.unstubAllGlobals();
});

const seaCategory = {
  id: "11111111-1111-1111-1111-111111111111",
  code: "sea",
  name: { en: "Sea adventures", ar: "مغامرات البحر" },
  description: null,
};

// A code this page has no written vibe-copy for — proves the live-data
// fallback instead of crashing or silently dropping the category.
const unknownCategory = {
  id: "22222222-2222-2222-2222-222222222222",
  code: "brand-new-category",
  name: { en: "Something New", ar: "حاجة جديدة" },
  description: { en: "A fresh category description from the API.", ar: "وصف فئة جديد من الـ API." },
};

function stubFetch(routes: Record<string, () => Response>) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), "http://localhost");
      const key = url.pathname + url.search;
      return routes[key]?.() ?? routes[url.pathname]?.() ?? new Response("not found", { status: 404 });
    }),
  );
}

const NuxtLinkStub = {
  props: ["to"],
  computed: {
    href(): string {
      if (typeof this.to === "string") return this.to;
      const query = this.to?.query ?? {};
      const search = new URLSearchParams(query).toString();
      return search ? `${this.to.path}?${search}` : this.to.path;
    },
  },
  template: "<a :href=\"href\"><slot /></a>",
};

function mountPage() {
  return mount(FindMyTripPage, { global: { stubs: { NuxtLink: NuxtLinkStub } } });
}

describe("find-my-trip page", () => {
  it("renders a written vibe card per live category, linking into the filtered catalog", async () => {
    stubFetch({ "/api/catalog/categories": () => new Response(JSON.stringify([seaCategory]), { status: 200 }) });

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).toContain("Sea adventures");
    expect(wrapper.text()).toContain("Boat days, snorkelling");
    const link = wrapper.findAll("a").find((a) => a.text().includes("Sea adventures"));
    expect(link?.attributes("href")).toBe(`/experiences?category=${seaCategory.id}`);
  });

  it("falls back to the live category's own name and description for an unwritten code", async () => {
    stubFetch({ "/api/catalog/categories": () => new Response(JSON.stringify([unknownCategory]), { status: 200 }) });

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).toContain("Something New");
    expect(wrapper.text()).toContain("A fresh category description from the API.");
  });

  it("shows a clean error state when the live catalog can't be reached", async () => {
    stubFetch({ "/api/catalog/categories": () => new Response("boom", { status: 500 }) });

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).toContain("We could not reach the live catalog");
  });
});
