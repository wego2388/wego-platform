import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import BookingsPage from "../app/pages/bookings.vue";
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

const sampleBooking = {
  id: "22222222-2222-2222-2222-222222222222",
  offeringId: sampleOffering.id,
  partySize: 2,
  customerName: "Ada Lovelace",
  customerEmail: "ada@example.com",
  status: "CONFIRMED",
  paymentStatus: "UNPAID",
  pricingBasis: "PER_PARTICIPANT",
  unitPrice: { amount: "45.00", currencyCode: "EUR" },
  billableQuantity: 2,
  totalPrice: { amount: "90.00", currencyCode: "EUR" },
  createdAt: "2026-08-16T00:00:00Z",
};

function respondTo(url: string) {
  if (url.includes("/api/v1/divers/bookings")) return new Response(JSON.stringify([sampleBooking]), { status: 200 });
  if (url.includes("/api/v1/divers/offerings")) return new Response(JSON.stringify([sampleOffering]), { status: 200 });
  throw new Error(`Unexpected fetch: ${url}`);
}

describe("bookings page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(BookingsPage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("lists bookings for an authenticated user, showing unit and total price and the offering name", async () => {
    seedSession(["booking:view", "offering:view"]);
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => respondTo(String(input))));

    const wrapper = mount(BookingsPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Ada Lovelace");
    expect(wrapper.text()).toContain("CONFIRMED");
    expect(wrapper.text()).toContain("Reef Trip — 2026-09-01");
    expect(wrapper.text()).toContain("unit 45.00 EUR");
    expect(wrapper.text()).toContain("total 90.00 EUR");
    expect(wrapper.text()).toContain("ada@example.com");
  });

  it("requests page=0&size=50 for bookings and pages forward with Next", async () => {
    seedSession(["booking:view"]);
    const fullPage = Array.from({ length: 50 }, (_, i) => ({ ...sampleBooking, id: `booking-${i}` }));
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), "http://localhost");
      if (url.pathname.endsWith("/bookings")) {
        expect(url.searchParams.get("size")).toBe("50");
        const page = url.searchParams.get("page");
        return new Response(JSON.stringify(page === "1" ? [] : fullPage), { status: 200 });
      }
      return new Response(JSON.stringify([sampleOffering]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(BookingsPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Page 1");
    const nextButton = wrapper.findAll("button").find((button) => button.text() === "Next");
    expect(nextButton?.attributes("disabled")).toBeUndefined();
    await nextButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("Page 2");
  });

  it("hides the create form for a user without booking:create", async () => {
    seedSession(["booking:view"]);
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => respondTo(String(input))));

    const wrapper = mount(BookingsPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("New booking");
  });

  it("hides mark-paid and refund controls for a user with only booking:create", async () => {
    seedSession(["booking:create", "booking:view"]);
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => respondTo(String(input))));

    const wrapper = mount(BookingsPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("Mark paid");
    expect(wrapper.text()).not.toContain("Refund");
  });

  it("shows mark-paid only for a user with booking:payment-update", async () => {
    seedSession(["booking:view", "booking:payment-update"]);
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => respondTo(String(input))));

    const wrapper = mount(BookingsPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Mark paid");
    expect(wrapper.text()).not.toContain("Refund reason");
  });

  it("creates a booking with a fresh Idempotency-Key and prepends it to the list", async () => {
    seedSession(["booking:create", "booking:view", "offering:view"]);
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (init?.method === "POST") {
        return new Response(JSON.stringify({ ...sampleBooking, id: "33333333-3333-3333-3333-333333333333" }), {
          status: 201,
        });
      }
      return respondTo(url);
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(BookingsPage);
    await flushPromises();

    await wrapper.get("#offeringId").setValue(sampleOffering.id);
    await wrapper.get("#partySize").setValue("2");
    await wrapper.get("#customerName").setValue("Grace Hopper");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    const postCall = fetchMock.mock.calls.find((call) => call[1]?.method === "POST");
    expect(postCall).toBeDefined();
    const headers = postCall?.[1]?.headers as Record<string, string>;
    expect(headers.Authorization).toBe("Bearer test-token");
    expect(headers["Idempotency-Key"]).toBeTruthy();
    const body = JSON.parse(postCall?.[1]?.body as string);
    expect(body).toMatchObject({ offeringId: sampleOffering.id, partySize: 2, customerName: "Grace Hopper" });
  });

  it("reuses the same Idempotency-Key on a retry after a failed attempt, not a fresh one", async () => {
    seedSession(["booking:create", "booking:view", "offering:view"]);
    let postAttempts = 0;
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (init?.method === "POST") {
        postAttempts += 1;
        if (postAttempts === 1) {
          throw new TypeError("Failed to fetch");
        }
        return new Response(JSON.stringify(sampleBooking), { status: 201 });
      }
      return respondTo(url);
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(BookingsPage);
    await flushPromises();

    await wrapper.get("#offeringId").setValue(sampleOffering.id);
    await wrapper.get("#partySize").setValue("2");
    await wrapper.get("#customerName").setValue("Grace Hopper");

    // First attempt: the network request itself throws (e.g. the response
    // never arrived, though the server may have already processed it).
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();
    const firstKey = fetchMock.mock.calls.find((call) => call[1]?.method === "POST")?.[1]
      ?.headers as Record<string, string> | undefined;

    // Retry: same form still filled in, same logical attempt.
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();
    const postCalls = fetchMock.mock.calls.filter((call) => call[1]?.method === "POST");
    const secondKey = postCalls[1]?.[1]?.headers as Record<string, string> | undefined;

    expect(postCalls).toHaveLength(2);
    expect(firstKey?.["Idempotency-Key"]).toBeTruthy();
    expect(secondKey?.["Idempotency-Key"]).toBe(firstKey?.["Idempotency-Key"]);
  });

  it("shows a specific message when capacity is exceeded", async () => {
    seedSession(["booking:create", "booking:view", "offering:view"]);
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (init?.method === "POST") {
          return new Response(JSON.stringify({ error: "capacity_exceeded" }), { status: 409 });
        }
        return respondTo(url);
      }),
    );

    const wrapper = mount(BookingsPage);
    await flushPromises();

    await wrapper.get("#offeringId").setValue(sampleOffering.id);
    await wrapper.get("#partySize").setValue("2");
    await wrapper.get("#customerName").setValue("Grace Hopper");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.text()).toContain("fully booked");
  });

  it("requires a reason before cancelling and does not call the API without one", async () => {
    seedSession(["booking:cancel", "booking:view"]);
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => respondTo(String(input)));
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(BookingsPage);
    await flushPromises();

    const cancelButton = wrapper.findAll("button").find((button) => button.text() === "Cancel");
    await cancelButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("reason is required");
    expect(fetchMock.mock.calls.find((call) => String(call[0]).endsWith("/cancel"))).toBeUndefined();
  });

  it("cancels a confirmed booking with a reason after confirmation, when the user has booking:cancel", async () => {
    seedSession(["booking:cancel", "booking:view"]);
    vi.stubGlobal("confirm", vi.fn(() => true));
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/cancel") && init?.method === "POST") {
        const body = JSON.parse(init.body as string);
        expect(body.reason).toBe("Customer requested cancellation");
        return new Response(JSON.stringify({ ...sampleBooking, status: "CANCELLED", cancellationReason: body.reason }), {
          status: 200,
        });
      }
      return respondTo(url);
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(BookingsPage);
    await flushPromises();

    await wrapper.get(`#cancel-reason-${sampleBooking.id}`).setValue("Customer requested cancellation");
    const cancelButton = wrapper.findAll("button").find((button) => button.text() === "Cancel");
    await cancelButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("CANCELLED");
    const cancelCall = fetchMock.mock.calls.find((call) => String(call[0]).endsWith("/cancel"));
    expect(cancelCall).toBeDefined();
  });

  it("does not cancel when the user declines the confirmation dialog", async () => {
    seedSession(["booking:cancel", "booking:view"]);
    vi.stubGlobal("confirm", vi.fn(() => false));
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => respondTo(String(input)));
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(BookingsPage);
    await flushPromises();

    await wrapper.get(`#cancel-reason-${sampleBooking.id}`).setValue("Customer requested cancellation");
    const cancelButton = wrapper.findAll("button").find((button) => button.text() === "Cancel");
    await cancelButton?.trigger("click");
    await flushPromises();

    expect(fetchMock.mock.calls.find((call) => String(call[0]).endsWith("/cancel"))).toBeUndefined();
  });

  it("marks a booking paid via a distinct endpoint requiring booking:payment-update, not booking:create", async () => {
    seedSession(["booking:view", "booking:payment-update"]);
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith("/mark-paid") && init?.method === "PATCH") {
        expect(init.body).toBeUndefined();
        return new Response(JSON.stringify({ ...sampleBooking, paymentStatus: "PAID" }), { status: 200 });
      }
      return respondTo(url);
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(BookingsPage);
    await flushPromises();

    const markPaidButton = wrapper.findAll("button").find((button) => button.text() === "Mark paid");
    await markPaidButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("payment PAID");
    expect(fetchMock.mock.calls.find((call) => String(call[0]).endsWith("/mark-paid"))).toBeDefined();
  });

  it("refunds a paid booking with a reason after confirmation, when the user has booking:refund", async () => {
    seedSession(["booking:view", "booking:refund"]);
    vi.stubGlobal("confirm", vi.fn(() => true));
    const paidBooking = { ...sampleBooking, paymentStatus: "PAID" };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.includes("/api/v1/divers/bookings") && (!init || init.method === undefined)) {
        return new Response(JSON.stringify([paidBooking]), { status: 200 });
      }
      if (url.endsWith("/refund") && init?.method === "PATCH") {
        const body = JSON.parse(init.body as string);
        expect(body.reason).toBe("Customer requested a refund");
        return new Response(JSON.stringify({ ...paidBooking, paymentStatus: "REFUNDED" }), { status: 200 });
      }
      return respondTo(url);
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(BookingsPage);
    await flushPromises();

    await wrapper.get(`#refund-reason-${sampleBooking.id}`).setValue("Customer requested a refund");
    const refundButton = wrapper.findAll("button").find((button) => button.text() === "Refund");
    await refundButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("payment REFUNDED");
    expect(fetchMock.mock.calls.find((call) => String(call[0]).endsWith("/refund"))).toBeDefined();
  });
});
