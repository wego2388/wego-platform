import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import TodayPage from "../app/pages/today.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions });
}

const todayIso = new Date().toISOString().slice(0, 10);

function sampleRequest(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: "11111111-1111-1111-1111-111111111111",
    reference: "STG-TODAY001",
    status: "CONFIRMED",
    serviceId: "22222222-2222-2222-2222-222222222222",
    serviceOptionId: "33333333-3333-3333-3333-333333333333",
    serviceName: { en: "Airport Transfer", ar: "انتقال من المطار" },
    optionLabel: { en: "Sedan car", ar: "سيارة سيدان" },
    priceAmount: "900.00",
    priceCurrency: "EGP",
    priceBasis: "PER_VEHICLE",
    cancellationPolicy: { en: "Free up to 24h before.", ar: "إلغاء مجاني." },
    requestedDate: todayIso,
    requestedTime: "14:00",
    adults: 2,
    children: 0,
    hotelOrPickup: "Naama Bay hotel zone",
    locale: "en",
    sourceChannel: "WEBSITE",
    customer: { name: "Nour", phone: "+201001413469" },
    createdAt: "2026-09-30T10:00:00Z",
    ...overrides,
  };
}

function mountToday() {
  return mount(TodayPage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>", props: ["to"] } } },
  });
}

function stubFetchByStatus(byStatus: Record<string, unknown[]>) {
  return vi.fn(async (input: RequestInfo | URL) => {
    const url = new URL(String(input), "http://localhost");
    const status = url.searchParams.get("status") ?? "";
    return new Response(JSON.stringify(byStatus[status] ?? []), { status: 200 });
  });
}

describe("today run-sheet page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mountToday();
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("hides the run sheet for an account without travel-request:view", async () => {
    seedSession([]);
    vi.stubGlobal("fetch", vi.fn());

    const wrapper = mountToday();
    await flushPromises();

    expect(wrapper.text()).toContain("doesn't have permission to view today's run sheet");
  });

  it("lists only today's confirmed requests, sorted by pickup time", async () => {
    seedSession(["travel-request:view"]);
    vi.stubGlobal(
      "fetch",
      stubFetchByStatus({
        CONFIRMED: [
          sampleRequest({ id: "a", reference: "STG-LATER", requestedTime: "16:00" }),
          sampleRequest({ id: "b", reference: "STG-EARLIER", requestedTime: "07:30" }),
          sampleRequest({ id: "c", reference: "STG-OTHERDAY", requestedDate: "2099-01-01" }),
        ],
        NEW: [],
      }),
    );

    const wrapper = mountToday();
    await flushPromises();

    const text = wrapper.text();
    expect(text).toContain("STG-EARLIER");
    expect(text).toContain("STG-LATER");
    expect(text).not.toContain("STG-OTHERDAY");
    // earlier pickup must render before the later one
    expect(text.indexOf("STG-EARLIER")).toBeLessThan(text.indexOf("STG-LATER"));
  });

  it("shows an empty state when nothing is confirmed for today", async () => {
    seedSession(["travel-request:view"]);
    vi.stubGlobal("fetch", stubFetchByStatus({ CONFIRMED: [], NEW: [] }));

    const wrapper = mountToday();
    await flushPromises();

    expect(wrapper.text()).toContain("Nothing confirmed for today yet.");
  });

  it("surfaces a count of unclaimed NEW requests with a link to the queue", async () => {
    seedSession(["travel-request:view"]);
    vi.stubGlobal(
      "fetch",
      stubFetchByStatus({
        CONFIRMED: [],
        NEW: [sampleRequest({ id: "n1", status: "NEW" }), sampleRequest({ id: "n2", status: "NEW" })],
      }),
    );

    const wrapper = mountToday();
    await flushPromises();

    expect(wrapper.text()).toContain("2 new request(s)");
    expect(wrapper.text()).toContain("Requests queue");
  });

  it("shows a clean error message on a 403, not a raw stack trace", async () => {
    seedSession(["travel-request:view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ error: "forbidden" }), { status: 403 })));

    const wrapper = mountToday();
    await flushPromises();

    expect(wrapper.text()).toContain("don't have permission to view today's run sheet");
  });
});
