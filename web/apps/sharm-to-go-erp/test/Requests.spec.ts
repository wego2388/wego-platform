import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import RequestsPage from "../app/pages/requests.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions });
}

function sampleRequest(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: "11111111-1111-1111-1111-111111111111",
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

function mountRequests() {
  return mount(RequestsPage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>", props: ["to"] } } },
  });
}

describe("requests queue page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mountRequests();
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("hides the queue for an account without travel-request:view", async () => {
    seedSession([]);
    vi.stubGlobal("fetch", vi.fn());

    const wrapper = mountRequests();
    await flushPromises();

    expect(wrapper.text()).toContain("doesn't have permission to view the requests queue");
  });

  it("lists requests and visually marks an unclaimed NEW request", async () => {
    seedSession(["travel-request:view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([sampleRequest()]), { status: 200 })));

    const wrapper = mountRequests();
    await flushPromises();

    expect(wrapper.text()).toContain("STG-ABCDEFGH");
    expect(wrapper.text()).toContain("Nour");
    expect(wrapper.text()).toContain("unclaimed");
    expect(wrapper.text()).toContain("NEW");
  });

  it("filters the request list by status via the select", async () => {
    seedSession(["travel-request:view"]);
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), "http://localhost");
      if (url.searchParams.get("status") === "CONFIRMED") {
        return new Response(JSON.stringify([sampleRequest({ status: "CONFIRMED", reference: "STG-ZZZZZZZZ" })]), { status: 200 });
      }
      return new Response(JSON.stringify([sampleRequest()]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mountRequests();
    await flushPromises();
    expect(wrapper.text()).toContain("STG-ABCDEFGH");

    await wrapper.get("#statusFilter").setValue("CONFIRMED");
    await flushPromises();

    expect(wrapper.text()).toContain("STG-ZZZZZZZZ");
    expect(fetchMock.mock.calls.some((call) => String(call[0]).includes("status=CONFIRMED"))).toBe(true);
  });

  it("searches the loaded page by reference or customer name, client-side", async () => {
    seedSession(["travel-request:view"]);
    vi.stubGlobal(
      "fetch",
      vi.fn(
        async () =>
          new Response(
            JSON.stringify([sampleRequest(), sampleRequest({ id: "other-id", reference: "STG-OTHERONE", customer: { name: "Sara" } })]),
            { status: 200 },
          ),
      ),
    );

    const wrapper = mountRequests();
    await flushPromises();
    expect(wrapper.text()).toContain("Nour");
    expect(wrapper.text()).toContain("Sara");

    await wrapper.get("#search").setValue("Sara");
    await flushPromises();

    expect(wrapper.text()).not.toContain("Nour");
    expect(wrapper.text()).toContain("Sara");
  });

  it("shows a clean error message on a 403, not a raw stack trace", async () => {
    seedSession(["travel-request:view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ error: "forbidden" }), { status: 403 })));

    const wrapper = mountRequests();
    await flushPromises();

    expect(wrapper.text()).toContain("don't have permission to view the requests queue");
  });
});
