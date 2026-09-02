import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import ExperienceDetailPage from "../app/pages/experiences/[id].vue";

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

function withRoute(id: string) {
  vi.stubGlobal("useRoute", () => ({ params: { id } }));
}

function mountPage() {
  return mount(ExperienceDetailPage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
  });
}

describe("experience detail page", () => {
  it("renders the full real detail for a published service", async () => {
    withRoute(serviceId);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify(sampleService), { status: 200 })));

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).toContain("Desert Safari");
    expect(wrapper.text()).toContain("Red Sea Adventures");
    expect(wrapper.text()).toContain("Evening trip");
    expect(wrapper.text()).toContain("EGP 500.00");
    expect(wrapper.text()).toContain("Free cancellation up to 24h before.");
    expect(wrapper.text()).toContain("Hotel pickup included.");
    expect(wrapper.text()).toContain("Dinner and water.");
    expect(wrapper.text()).toContain("Personal expenses.");
    expect(wrapper.text()).toContain("1 photo");
  });

  it("never shows a fake booking action, only an honest contact placeholder", async () => {
    withRoute(serviceId);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify(sampleService), { status: 200 })));

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).not.toMatch(/book now/i);
    expect(wrapper.text()).toContain("Online booking for this experience isn't live yet.");
  });

  it("shows an honest not-found state for an unknown or unpublished id, not a crash", async () => {
    withRoute("00000000-0000-0000-0000-000000000000");
    vi.stubGlobal("fetch", vi.fn(async () => new Response("not found", { status: 404 })));

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).toContain("This experience isn't available");
    expect(wrapper.findAll("a").some((link) => link.text() === "Back to experiences")).toBe(true);
  });

  it("shows a real error, not a raw crash, when the catalog cannot be reached", async () => {
    withRoute(serviceId);
    vi.stubGlobal("fetch", vi.fn(async () => new Response("boom", { status: 500 })));

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain("We could not reach the live catalog");
  });
});
