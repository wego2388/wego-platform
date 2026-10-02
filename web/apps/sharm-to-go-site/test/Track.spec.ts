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
  vi.stubGlobal("useRoute", () => ({ params: { reference }, query: {} }));
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

  it("carries the real shared footer, which this page previously had none of", () => {
    const { wrapper } = mountIndex();
    expect(wrapper.text()).toContain("All rights reserved");
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
    // Translated customer-facing text, not the raw backend status string —
    // this used to literally say "CONFIRMED" even on the Arabic page.
    expect(wrapper.text()).toContain("Confirmed");
    expect(wrapper.text()).not.toContain("CONFIRMED");
    expect(wrapper.text()).toContain("EGP 500.00");
    expect(wrapper.text()).toContain("per person");
    expect(wrapper.text()).toContain("All rights reserved");
  });

  it("shows the real requested calendar date regardless of the viewer's own timezone", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify(sampleResult), { status: 200 })));
    // America/Los_Angeles is west of UTC — the bug this guards against
    // parsed "2099-01-01" as UTC midnight, which is still "Dec 31" in that
    // timezone, showing the wrong calendar day to any visitor west of UTC.
    const originalTz = process.env.TZ;
    process.env.TZ = "America/Los_Angeles";
    try {
      const wrapper = mountReference("STG-ABCDEFGH");
      await flushPromises();
      expect(wrapper.text()).toContain("1 Jan 2099");
      expect(wrapper.text()).not.toContain("31 Dec 2098");
    } finally {
      process.env.TZ = originalTz;
    }
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

  it("tells search engines not to index this page — the URL itself carries a bearer-secret reference", async () => {
    let headFactory: (() => { meta?: Array<{ name: string; content: string }> }) | undefined;
    vi.stubGlobal("useHead", (input: typeof headFactory) => {
      headFactory = input;
    });
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify(sampleResult), { status: 200 })));

    mountReference("STG-ABCDEFGH");
    await flushPromises();

    expect(headFactory).toBeDefined();
    expect(headFactory!().meta).toContainEqual({ name: "robots", content: "noindex,nofollow" });
  });
});
