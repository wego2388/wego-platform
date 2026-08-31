import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import IndexPage from "../app/pages/index.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions });
}

describe("index page", () => {
  it("shows no live business summary and makes no dashboard request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(IndexPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("Live business summary");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("shows real bookings-today and paid-revenue numbers for a user with booking:view", async () => {
    seedSession(["booking:view"]);
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => {
        const url = new URL(String(input), "http://localhost");
        if (url.pathname === "/api/v1/divers/dashboard/bookings") {
          return new Response(JSON.stringify({ bookingsToday: 4, paidRevenueThisMonth: [{ amount: "270.00", currencyCode: "EUR" }] }), {
            status: 200,
          });
        }
        return new Response(null, { status: 403 });
      }),
    );

    const wrapper = mount(IndexPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Live business summary");
    expect(wrapper.text()).toContain("4");
    expect(wrapper.text()).toContain("270.00 EUR");
  });

  it("shows a real upcoming trip for a user with offering:view", async () => {
    seedSession(["offering:view"]);
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => {
        const url = new URL(String(input), "http://localhost");
        if (url.pathname === "/api/v1/divers/dashboard/offerings") {
          return new Response(
            JSON.stringify({
              upcomingTrips: [{ id: "1", offeringType: "DIVE_TRIP", title: "Ras Mohammed Trip", startsOn: "2026-09-05" }],
            }),
            { status: 200 },
          );
        }
        return new Response(null, { status: 403 });
      }),
    );

    const wrapper = mount(IndexPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Ras Mohammed Trip");
    expect(wrapper.text()).toContain("2026-09-05");
  });

  it("only requests the widgets the account actually has permission for", async () => {
    seedSession(["diver:view"]);
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ activeDivers: 7 }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(IndexPage);
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [firstCallUrl] = fetchMock.mock.calls[0] ?? [];
    expect(String(firstCallUrl)).toContain("/dashboard/divers");
    expect(wrapper.text()).toContain("7");
    expect(wrapper.text()).not.toContain("Bookings today");
  });

  it("shows a real equipment status breakdown for a user with equipment:view", async () => {
    seedSession(["equipment:view"]);
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify({ active: 12, inMaintenance: 2, retired: 1 }), { status: 200 })),
    );

    const wrapper = mount(IndexPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Equipment status");
    expect(wrapper.text()).toContain("12");
    expect(wrapper.text()).toContain("2");
    expect(wrapper.text()).toContain("1");
  });

  it("shows the no-permission notice for a signed-in account with none of the dashboard permissions", async () => {
    seedSession([]);
    vi.stubGlobal("fetch", vi.fn());

    const wrapper = mount(IndexPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Your account doesn't hold permission to view any business summary widget yet.");
  });
});
