import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import RequestDetailPage from "../app/pages/requests/[id].vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions });
}

const requestId = "11111111-1111-1111-1111-111111111111";

function sampleRequest(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: requestId,
    reference: "STG-ABCDEFGH",
    status: "NEW",
    serviceId: "22222222-2222-2222-2222-222222222222",
    serviceOptionId: "33333333-3333-3333-3333-333333333333",
    serviceName: { en: "Airport Transfer", ar: "انتقال من المطار" },
    optionLabel: { en: "Arrival", ar: "استلام" },
    priceAmount: "700.00",
    priceCurrency: "EGP",
    priceBasis: "PER_VEHICLE",
    cancellationPolicy: { en: "Free up to 24h before.", ar: "إلغاء مجاني." },
    requestedDate: "2026-12-01",
    adults: 2,
    children: 0,
    locale: "en",
    sourceChannel: "WEBSITE",
    customer: { name: "Nour", phone: "+201001413469" },
    createdAt: "2026-09-30T10:00:00Z",
    ...overrides,
  };
}

function fetchRoutedBy(routes: Record<string, () => Response>, fallback: () => Response) {
  return vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), "http://localhost");
    const key = `${init?.method ?? "GET"} ${url.pathname}`;
    return (routes[key] ?? fallback)();
  });
}

function mountDetail() {
  vi.stubGlobal("useRoute", () => ({ params: { id: requestId } }));
  return mount(RequestDetailPage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>", props: ["to"] } } },
  });
}

describe("request detail page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mountDetail();
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("shows the full customer contact and snapshot, plus a payment-independence note", async () => {
    seedSession(["travel-request:view"]);
    vi.stubGlobal(
      "fetch",
      fetchRoutedBy(
        {
          [`GET /api/v1/travel-marketplace/requests/${requestId}`]: () => new Response(JSON.stringify(sampleRequest()), { status: 200 }),
          [`GET /api/v1/travel-marketplace/requests/${requestId}/audit`]: () => new Response(JSON.stringify([]), { status: 200 }),
        },
        () => new Response(JSON.stringify(null), { status: 404 }),
      ),
    );

    const wrapper = mountDetail();
    await flushPromises();

    expect(wrapper.text()).toContain("STG-ABCDEFGH");
    expect(wrapper.text()).toContain("Nour");
    expect(wrapper.text()).toContain("+201001413469");
    expect(wrapper.text()).toContain("No payment has been collected");
  });

  it("does not render a broken WhatsApp link for a pre-validation row whose phone has no real digits", async () => {
    seedSession(["travel-request:view"]);
    vi.stubGlobal(
      "fetch",
      fetchRoutedBy(
        {
          [`GET /api/v1/travel-marketplace/requests/${requestId}`]: () =>
            new Response(JSON.stringify(sampleRequest({ customer: { name: "Nour", phone: "abc" } })), { status: 200 }),
          [`GET /api/v1/travel-marketplace/requests/${requestId}/audit`]: () => new Response(JSON.stringify([]), { status: 200 }),
        },
        () => new Response(JSON.stringify(null), { status: 404 }),
      ),
    );

    const wrapper = mountDetail();
    await flushPromises();

    expect(wrapper.text()).not.toContain("Message on WhatsApp");
  });

  it("a 404 from the backend shows a clean not-found state, not a stack trace", async () => {
    seedSession(["travel-request:view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ error: "not_found" }), { status: 404 })));

    const wrapper = mountDetail();
    await flushPromises();

    expect(wrapper.text()).toContain("No request with this id");
  });

  it("only shows actions the account actually has permission for", async () => {
    seedSession(["travel-request:view", "travel-request:confirm"]);
    vi.stubGlobal(
      "fetch",
      fetchRoutedBy(
        {
          [`GET /api/v1/travel-marketplace/requests/${requestId}`]: () => new Response(JSON.stringify(sampleRequest()), { status: 200 }),
          [`GET /api/v1/travel-marketplace/requests/${requestId}/audit`]: () => new Response(JSON.stringify([]), { status: 200 }),
        },
        () => new Response(JSON.stringify(null), { status: 404 }),
      ),
    );

    const wrapper = mountDetail();
    await flushPromises();

    const buttonTexts = wrapper.findAll("button").map((button) => button.text());
    expect(buttonTexts).toContain("Confirm");
    expect(buttonTexts).not.toContain("Claim for review");
    expect(buttonTexts).not.toContain("Cancel request");
  });

  it("confirming a request calls the confirm endpoint and updates the visible status", async () => {
    seedSession(["travel-request:view", "travel-request:confirm"]);
    vi.stubGlobal(
      "fetch",
      fetchRoutedBy(
        {
          [`GET /api/v1/travel-marketplace/requests/${requestId}`]: () => new Response(JSON.stringify(sampleRequest()), { status: 200 }),
          [`GET /api/v1/travel-marketplace/requests/${requestId}/audit`]: () => new Response(JSON.stringify([]), { status: 200 }),
          [`POST /api/v1/travel-marketplace/requests/${requestId}/confirm`]: () =>
            new Response(JSON.stringify(sampleRequest({ status: "CONFIRMED" })), { status: 200 }),
        },
        () => new Response(JSON.stringify(null), { status: 404 }),
      ),
    );

    const wrapper = mountDetail();
    await flushPromises();

    const confirmButton = wrapper.findAll("button").find((button) => button.text() === "Confirm");
    await confirmButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("CONFIRMED");
  });

  it("shows an honest status in the customer-safe summary for cancelled and expired requests, not 'Awaiting confirmation'", async () => {
    seedSession(["travel-request:view"]);
    vi.stubGlobal(
      "fetch",
      fetchRoutedBy(
        {
          [`GET /api/v1/travel-marketplace/requests/${requestId}`]: () =>
            new Response(JSON.stringify(sampleRequest({ status: "CANCELLED" })), { status: 200 }),
          [`GET /api/v1/travel-marketplace/requests/${requestId}/audit`]: () => new Response(JSON.stringify([]), { status: 200 }),
        },
        () => new Response(JSON.stringify(null), { status: 404 }),
      ),
    );

    const wrapper = mountDetail();
    await flushPromises();

    expect(wrapper.text()).toContain("Status: Cancelled");
    expect(wrapper.text()).not.toContain("Awaiting confirmation");
  });

  it("writes the customer-safe summary in the customer's own recorded locale, not always English", async () => {
    seedSession(["travel-request:view"]);
    vi.stubGlobal(
      "fetch",
      fetchRoutedBy(
        {
          [`GET /api/v1/travel-marketplace/requests/${requestId}`]: () =>
            new Response(JSON.stringify(sampleRequest({ status: "EXPIRED", locale: "ar" })), { status: 200 }),
          [`GET /api/v1/travel-marketplace/requests/${requestId}/audit`]: () => new Response(JSON.stringify([]), { status: 200 }),
        },
        () => new Response(JSON.stringify(null), { status: 404 }),
      ),
    );

    const wrapper = mountDetail();
    await flushPromises();

    expect(wrapper.text()).toContain("منتهي الصلاحية");
    expect(wrapper.text()).toContain("استلام");
    expect(wrapper.text()).not.toContain("Status: Expired");
  });

  it("a confirm attempt the backend rejects shows the mapped error, not a raw one", async () => {
    seedSession(["travel-request:view", "travel-request:confirm"]);
    vi.stubGlobal(
      "fetch",
      fetchRoutedBy(
        {
          [`GET /api/v1/travel-marketplace/requests/${requestId}`]: () => new Response(JSON.stringify(sampleRequest()), { status: 200 }),
          [`GET /api/v1/travel-marketplace/requests/${requestId}/audit`]: () => new Response(JSON.stringify([]), { status: 200 }),
          [`POST /api/v1/travel-marketplace/requests/${requestId}/confirm`]: () =>
            new Response(JSON.stringify({ error: "invalid_transition" }), { status: 409 }),
        },
        () => new Response(JSON.stringify(null), { status: 404 }),
      ),
    );

    const wrapper = mountDetail();
    await flushPromises();

    const confirmButton = wrapper.findAll("button").find((button) => button.text() === "Confirm");
    await confirmButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("no longer in a state that allows that action");
  });

  it("does not report an action as failed when it actually succeeded but only the audit-timeline refresh failed", async () => {
    seedSession(["travel-request:view", "travel-request:confirm"]);
    let auditCallCount = 0;
    vi.stubGlobal(
      "fetch",
      fetchRoutedBy(
        {
          [`GET /api/v1/travel-marketplace/requests/${requestId}`]: () => new Response(JSON.stringify(sampleRequest()), { status: 200 }),
          [`GET /api/v1/travel-marketplace/requests/${requestId}/audit`]: () => {
            auditCallCount += 1;
            // The initial page-load fetch (call 1) succeeds; only the
            // post-confirm refresh (call 2) fails.
            if (auditCallCount === 1) return new Response(JSON.stringify([]), { status: 200 });
            return new Response("boom", { status: 500 });
          },
          [`POST /api/v1/travel-marketplace/requests/${requestId}/confirm`]: () =>
            new Response(JSON.stringify(sampleRequest({ status: "CONFIRMED" })), { status: 200 }),
        },
        () => new Response(JSON.stringify(null), { status: 404 }),
      ),
    );

    const wrapper = mountDetail();
    await flushPromises();

    const confirmButton = wrapper.findAll("button").find((button) => button.text() === "Confirm");
    await confirmButton?.trigger("click");
    await flushPromises();

    // The real outcome — confirmed — is shown; the failure is reported as a
    // narrower warning about the timeline, not as the action itself failing.
    expect(wrapper.text()).toContain("CONFIRMED");
    expect(wrapper.text()).not.toContain("no longer in a state that allows that action");
    expect(wrapper.text()).toContain("timeline below could not be refreshed");
  });

  it("reloads the real current state after a rejected action, instead of leaving the stale pre-action record displayed", async () => {
    seedSession(["travel-request:view", "travel-request:confirm"]);
    let getCallCount = 0;
    vi.stubGlobal(
      "fetch",
      fetchRoutedBy(
        {
          [`GET /api/v1/travel-marketplace/requests/${requestId}`]: () => {
            getCallCount += 1;
            // Simulates another staff member having already cancelled it first.
            const status = getCallCount === 1 ? "NEW" : "CANCELLED";
            return new Response(JSON.stringify(sampleRequest({ status })), { status: 200 });
          },
          [`GET /api/v1/travel-marketplace/requests/${requestId}/audit`]: () => new Response(JSON.stringify([]), { status: 200 }),
          [`POST /api/v1/travel-marketplace/requests/${requestId}/confirm`]: () =>
            new Response(JSON.stringify({ error: "invalid_transition" }), { status: 409 }),
        },
        () => new Response(JSON.stringify(null), { status: 404 }),
      ),
    );

    const wrapper = mountDetail();
    await flushPromises();

    const confirmButton = wrapper.findAll("button").find((button) => button.text() === "Confirm");
    await confirmButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("no longer in a state that allows that action");
    expect(wrapper.text()).toContain("CANCELLED");
  });
});
