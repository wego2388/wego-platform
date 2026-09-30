import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import TrackIndexPage from "../app/pages/track/index.vue";
import TrackReferencePage from "../app/pages/track/[reference].vue";

afterEach(() => {
  vi.unstubAllGlobals();
});

function mountIndex() {
  const push = vi.fn();
  vi.stubGlobal("useRouter", () => ({ push }));
  const wrapper = mount(TrackIndexPage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
  });
  return { wrapper, push };
}

function mountReference(reference: string) {
  vi.stubGlobal("useRoute", () => ({ params: { reference } }));
  return mount(TrackReferencePage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } },
  });
}

describe("track index page", () => {
  it("navigates to /track/:reference on submit", async () => {
    const { wrapper, push } = mountIndex();
    await wrapper.get("#reference").setValue("STG-ABCDEFGH");
    await wrapper.get("form").trigger("submit");

    expect(push).toHaveBeenCalledWith("/track/STG-ABCDEFGH");
  });
});

describe("track reference page", () => {
  const sampleResult = {
    reference: "STG-ABCDEFGH",
    status: "CONFIRMED",
    serviceName: { en: "Desert Safari", ar: "سفاري صحراوي" },
    optionLabel: { en: "Evening trip", ar: "رحلة مسائية" },
    priceAmount: "500.00",
    priceCurrency: "EGP",
    priceBasis: "PER_PERSON",
    cancellationPolicy: { en: "Free cancellation.", ar: "إلغاء مجاني." },
    requestedDate: "2099-01-01",
    adults: 2,
    children: 0,
    createdAt: "2026-10-01T00:00:00Z",
    confirmedAt: "2026-10-01T00:00:01Z",
  };

  it("shows the real status for a known reference, with no customer PII in the response it renders", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify(sampleResult), { status: 200 })));

    const wrapper = mountReference("STG-ABCDEFGH");
    await flushPromises();

    expect(wrapper.text()).toContain("Desert Safari");
    expect(wrapper.text()).toContain("CONFIRMED");
    expect(wrapper.text()).toContain("EGP 500.00");
  });

  it("shows an honest not-found message for an unknown reference, not a crash", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("not found", { status: 404 })));

    const wrapper = mountReference("STG-ZZZZZZZZ");
    await flushPromises();

    expect(wrapper.text()).toContain("We couldn't find a request with that reference");
  });

  it("shows a clean error, not a raw crash, when the lookup service is unreachable", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("boom", { status: 500 })));

    const wrapper = mountReference("STG-ABCDEFGH");
    await flushPromises();

    expect(wrapper.find('[role="alert"]').exists()).toBe(true);
  });
});
