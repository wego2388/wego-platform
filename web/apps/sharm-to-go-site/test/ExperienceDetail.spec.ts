import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import ExperienceDetailPage from "../app/pages/experiences/[id]/index.vue";

afterEach(() => {
  vi.unstubAllGlobals();
});

const serviceId = "55555555-5555-5555-5555-555555555555";

const sampleService = {
  id: serviceId,
  categoryId: "44444444-4444-4444-4444-444444444444",
  name: { en: "Desert Safari", ar: "سفاري صحراوي" },
  description: { en: "An evening safari.", ar: "رحلة مسائية." },
  confirmationType: "INSTANT",
  cancellationPolicy: { en: "Free cancellation up to 24h before.", ar: "إلغاء مجاني قبل ٢٤ ساعة." },
  pickupInfo: { en: "Hotel pickup included.", ar: "شامل الانتقال من الفندق." },
  inclusions: { en: "Dinner and water.", ar: "عشاء ومياه." },
  exclusions: { en: "Personal expenses.", ar: "المصاريف الشخصية." },
  operatedBy: "Red Sea Adventures",
  options: [
    { label: { en: "Evening trip", ar: "رحلة مسائية" }, durationMinutes: 180, maxParticipants: 10, priceAmount: "500.00", priceCurrency: "EGP", priceBasis: "PER_PERSON" },
  ],
  media: [{ assetReference: "asset-1", locale: "en" }],
};

const sampleCategory = {
  id: sampleService.categoryId,
  code: "desert-adventures",
  name: { en: "Desert & stargazing", ar: "الصحراء والنجوم" },
  description: null,
};

function withRoute(id: string, query: Record<string, string> = {}) {
  vi.stubGlobal("useRoute", () => ({ params: { id }, query }));
}

function stubFetch(serviceResponse: () => Response) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), "http://localhost");
      if (url.pathname === "/api/catalog/categories") {
        return new Response(JSON.stringify([sampleCategory]), { status: 200 });
      }
      return serviceResponse();
    }),
  );
}

// `to` can be a plain string or a `{ path, query }` route object (used by
// the request/back links so a forwarded search date/party survives
// navigation) — resolve both the same way Nuxt's real NuxtLink would.
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
  return mount(ExperienceDetailPage, {
    global: { stubs: { NuxtLink: NuxtLinkStub } },
  });
}

describe("experience detail page", () => {
  it("renders the full real detail for a published service", async () => {
    withRoute(serviceId);
    stubFetch(() => new Response(JSON.stringify(sampleService), { status: 200 }));

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).toContain("Desert Safari");
    expect(wrapper.text()).toContain("Red Sea Adventures");
    expect(wrapper.text()).toContain("Evening trip");
    expect(wrapper.text()).toContain("EGP 500.00");
    expect(wrapper.text()).toContain("(approx. $9)"); // display-only approximation; the stored price stays EGP
    expect(wrapper.text()).toContain("Free cancellation up to 24h before.");
    expect(wrapper.text()).toContain("Hotel pickup included.");
    expect(wrapper.text()).toContain("Dinner and water.");
    expect(wrapper.text()).toContain("Personal expenses.");
    expect(wrapper.text()).toContain("1 photo");
    // This page previously had no footer at all — proves the real shared one is wired in.
    expect(wrapper.text()).toContain("All rights reserved");
  });

  it("shows a real request action that links to the real request flow, not a fake instant-book button", async () => {
    withRoute(serviceId);
    stubFetch(() => new Response(JSON.stringify(sampleService), { status: 200 }));

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).not.toMatch(/book now/i);
    const requestLink = wrapper.findAll("a").find((link) => link.text() === "Request this experience");
    expect(requestLink?.attributes("href")).toBe(`/experiences/${serviceId}/request`);
    // The contact block is now explicitly secondary ("ask first"), not the only option.
    expect(wrapper.text()).toContain("Prefer to ask first?");
  });

  it("forwards a date/party carried over from the homepage search box into the request CTA and the back link", async () => {
    withRoute(serviceId, { date: "2099-06-15", adults: "2" });
    stubFetch(() => new Response(JSON.stringify(sampleService), { status: 200 }));

    const wrapper = mountPage();
    await flushPromises();

    const requestLink = wrapper.findAll("a").find((link) => link.text() === "Request this experience");
    expect(requestLink?.attributes("href")).toBe(`/experiences/${serviceId}/request?date=2099-06-15&adults=2`);
    const backLink = wrapper.findAll("a").find((link) => link.text() === "Back to experiences");
    expect(backLink?.attributes("href")).toBe("/experiences?date=2099-06-15&adults=2");
  });

  it("shows an honest not-found state for an unknown or unpublished id, not a crash", async () => {
    withRoute("00000000-0000-0000-0000-000000000000");
    stubFetch(() => new Response("not found", { status: 404 }));

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).toContain("This experience isn't available");
    expect(wrapper.findAll("a").some((link) => link.text() === "Back to experiences")).toBe(true);
  });

  it("shows a real error, not a raw crash, when the catalog cannot be reached", async () => {
    withRoute(serviceId);
    stubFetch(() => new Response("boom", { status: 500 }));

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("We could not reach the live catalog");
  });

  it("sets a real per-service meta description and og:title, not the generic site-wide default", async () => {
    withRoute(serviceId);
    stubFetch(() => new Response(JSON.stringify(sampleService), { status: 200 }));
    let headFactory: (() => { meta: Array<{ name?: string; property?: string; content: string }> }) | undefined;
    vi.stubGlobal("useHead", (input: typeof headFactory) => {
      headFactory = input;
    });

    mountPage();
    await flushPromises();

    const meta = headFactory!().meta;
    const description = meta.find((tag) => tag.name === "description");
    const ogTitle = meta.find((tag) => tag.property === "og:title");
    const ogDescription = meta.find((tag) => tag.property === "og:description");
    expect(description?.content).toBe("An evening safari.");
    expect(ogTitle?.content).toBe("Desert Safari · Sharm To Go");
    expect(ogDescription?.content).toBe("An evening safari.");
  });

  it("truncates a long service description at a word boundary for the meta description, not mid-word", async () => {
    withRoute(serviceId);
    const longDescription =
      "A full-day desert safari with dune bashing, a Bedouin camp dinner, stargazing through a real telescope, camel riding, and a guided tour of the surrounding mountains with a certified local guide who shares real history.";
    stubFetch(() => new Response(JSON.stringify({ ...sampleService, description: { en: longDescription, ar: longDescription } }), { status: 200 }));
    let headFactory: (() => { meta: Array<{ name?: string; content: string }> }) | undefined;
    vi.stubGlobal("useHead", (input: typeof headFactory) => {
      headFactory = input;
    });

    mountPage();
    await flushPromises();

    const description = headFactory!().meta.find((tag) => tag.name === "description")?.content ?? "";
    expect(description.length).toBeLessThanOrEqual(156);
    expect(description.endsWith("…")).toBe(true);
    // The truncated text (minus the ellipsis) must be a real prefix of the
    // original, and the original character right after it must be a space
    // — proving the cut landed exactly on a word boundary, not mid-word.
    const truncated = description.slice(0, -1);
    expect(longDescription.startsWith(truncated)).toBe(true);
    expect(longDescription[truncated.length]).toBe(" ");
  });
});
