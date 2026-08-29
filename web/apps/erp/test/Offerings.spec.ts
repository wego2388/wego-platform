import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import OfferingsPage from "../app/pages/offerings.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions });
}

const sampleOffering = {
  id: "11111111-1111-1111-1111-111111111111",
  offeringType: "DIVE_TRIP",
  title: "Reef Trip",
  startsOn: "2026-09-01",
  pricingBasis: "PER_PARTICIPANT",
  unitPrice: { amount: "45.00", currencyCode: "EUR" },
  status: "ACTIVE",
  createdAt: "2026-08-16T00:00:00Z",
};

describe("offerings page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(OfferingsPage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("lists offerings for an authenticated user, showing pricing basis", async () => {
    seedSession(["offering:view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([sampleOffering]), { status: 200 })));

    const wrapper = mount(OfferingsPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Reef Trip");
    expect(wrapper.text()).toContain("45.00 EUR");
    expect(wrapper.text()).toContain("per participant");
  });

  it("requests page=0&size=50 by default and pages forward with Next", async () => {
    seedSession(["offering:view"]);
    const fullPage = Array.from({ length: 50 }, (_, i) => ({ ...sampleOffering, id: `offering-${i}` }));
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), "http://localhost");
      expect(url.searchParams.get("size")).toBe("50");
      const page = url.searchParams.get("page");
      return new Response(JSON.stringify(page === "1" ? [] : fullPage), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(OfferingsPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Page 1");
    const nextButton = wrapper.findAll("button").find((button) => button.text() === "Next");
    expect(nextButton?.attributes("disabled")).toBeUndefined();
    await nextButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("Page 2");
    const previousButton = wrapper.findAll("button").find((button) => button.text() === "Previous");
    expect(previousButton?.attributes("disabled")).toBeUndefined();
  });

  it("hides the create form and close button for a user without offering:manage", async () => {
    seedSession(["offering:view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([sampleOffering]), { status: 200 })));

    const wrapper = mount(OfferingsPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("New offering");
    expect(wrapper.text()).not.toContain("Close offering");
  });

  it("does not call the list endpoint without offering:view", async () => {
    seedSession(["offering:manage"]);
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(OfferingsPage);
    await flushPromises();

    expect(wrapper.text()).toContain("doesn't have permission to list offerings");
    expect(wrapper.text()).toContain("New offering");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("creates an offering with a pricing basis and prepends it to the list when the user has offering:manage", async () => {
    seedSession(["offering:manage", "offering:view"]);
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/api/v1/divers/offerings") && (!init || init.method === undefined)) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      if (url.endsWith("/api/v1/divers/offerings") && init?.method === "POST") {
        return new Response(
          JSON.stringify({
            id: "22222222-2222-2222-2222-222222222222",
            offeringType: "COURSE",
            title: "Open Water Course",
            startsOn: "2026-09-05",
            pricingBasis: "FLAT",
            unitPrice: { amount: "350.00", currencyCode: "EUR" },
            status: "ACTIVE",
            createdAt: "2026-08-16T00:00:00Z",
          }),
          { status: 201 },
        );
      }
      throw new Error(`Unexpected fetch: ${url}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(OfferingsPage);
    await flushPromises();

    await wrapper.get("#offeringType").setValue("COURSE");
    await wrapper.get("#title").setValue("Open Water Course");
    await wrapper.get("#startsOn").setValue("2026-09-05");
    await wrapper.get("#pricingBasis").setValue("FLAT");
    await wrapper.get("#amount").setValue("350.00");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.text()).toContain("Open Water Course");
    const postCall = fetchMock.mock.calls.find((call) => call[1]?.method === "POST");
    expect(postCall).toBeDefined();
    expect((postCall?.[1]?.headers as Record<string, string>).Authorization).toBe("Bearer test-token");
    const body = JSON.parse(postCall?.[1]?.body as string);
    expect(body).toMatchObject({ offeringType: "COURSE", title: "Open Water Course", startsOn: "2026-09-05", pricingBasis: "FLAT" });
  });

  it("closes an active offering after confirmation when the user has offering:manage", async () => {
    seedSession(["offering:manage", "offering:view"]);
    vi.stubGlobal("confirm", vi.fn(() => true));
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/close") && init?.method === "POST") {
        return new Response(JSON.stringify({ ...sampleOffering, status: "CLOSED", closedAt: "2026-08-20T00:00:00Z" }), {
          status: 200,
        });
      }
      return new Response(JSON.stringify([sampleOffering]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(OfferingsPage);
    await flushPromises();

    const closeButton = wrapper.findAll("button").find((button) => button.text() === "Close offering");
    await closeButton?.trigger("click");
    await flushPromises();

    const closeCall = fetchMock.mock.calls.find((call) => String(call[0]).endsWith("/close"));
    expect(closeCall).toBeDefined();
    expect(wrapper.text()).toContain("CLOSED");
  });

  it("does not close an offering when the user cancels the confirmation dialog", async () => {
    seedSession(["offering:manage", "offering:view"]);
    vi.stubGlobal("confirm", vi.fn(() => false));
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) => new Response(JSON.stringify([sampleOffering]), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(OfferingsPage);
    await flushPromises();

    const closeButton = wrapper.findAll("button").find((button) => button.text() === "Close offering");
    await closeButton?.trigger("click");
    await flushPromises();

    const closeCall = fetchMock.mock.calls.find((call) => String(call[0]).endsWith("/close"));
    expect(closeCall).toBeUndefined();
  });

  it("falls back to the sign-in prompt when the list request returns 401", async () => {
    seedSession(["offering:view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ error: "unauthenticated" }), { status: 401 })));

    const wrapper = mount(OfferingsPage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
  });
});
