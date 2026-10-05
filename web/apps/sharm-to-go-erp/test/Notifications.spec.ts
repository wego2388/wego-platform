import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import NotificationsPage from "../app/pages/notifications.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions });
}

function sampleNotification(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: "aaaaaaaa-1111-1111-1111-111111111111",
    requestId: "11111111-1111-1111-1111-111111111111",
    requestReference: "STG-ABCDEFGH",
    kind: "CUSTOMER_REQUEST_RECEIVED",
    status: "SENT",
    attemptCount: 1,
    lastError: null,
    availableAt: "2026-10-05T10:00:00Z",
    createdAt: "2026-10-05T10:00:00Z",
    sentAt: "2026-10-05T10:00:30Z",
    resendCount: 0,
    lastResentAt: null,
    ...overrides,
  };
}

function mountPage() {
  return mount(NotificationsPage, {
    global: { stubs: { NuxtLink: { template: "<a><slot /></a>", props: ["to"] } } },
  });
}

describe("notifications page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("hides the list and makes no request without travel-notification:manage", async () => {
    seedSession(["travel-request:view"]);
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).toContain("doesn't have permission to manage notifications");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("lists the booking reference, kind and status, and never an address or body", async () => {
    seedSession(["travel-notification:manage"]);
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify([sampleNotification(), sampleNotification({ id: "b", kind: "STAFF_NEW_REQUEST" })]), { status: 200 })),
    );

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).toContain("STG-ABCDEFGH");
    expect(wrapper.text()).toContain("Customer: request received");
    expect(wrapper.text()).toContain("Staff alert: new request");
    expect(wrapper.text()).toContain("SENT");
    expect(wrapper.text()).not.toContain("@");
  });

  it("explains a skipped row in plain words", async () => {
    seedSession(["travel-notification:manage"]);
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify([sampleNotification({ status: "SKIPPED", lastError: "no_staff_address", sentAt: null })]), { status: 200 })),
    );

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).toContain("No staff alert address is configured");
  });

  it("filters by status through the query string", async () => {
    seedSession(["travel-notification:manage"]);
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), "http://localhost");
      const rows = url.searchParams.get("status") === "FAILED" ? [sampleNotification({ id: "f", status: "FAILED", requestReference: "STG-ZZZZZZZZ", sentAt: null })] : [sampleNotification()];
      return new Response(JSON.stringify(rows), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mountPage();
    await flushPromises();
    await wrapper.get("#statusFilter").setValue("FAILED");
    await flushPromises();

    expect(fetchMock.mock.calls.some((call) => String(call[0]).includes("status=FAILED"))).toBe(true);
    expect(wrapper.text()).toContain("STG-ZZZZZZZZ");
  });

  it("resends a notification with a POST and reloads the list", async () => {
    seedSession(["travel-notification:manage"]);
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input).endsWith("/resend") && init?.method === "POST") return new Response(null, { status: 202 });
      return new Response(JSON.stringify([sampleNotification({ status: "FAILED", sentAt: null })]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mountPage();
    await flushPromises();
    await wrapper.get("button.resend").trigger("click");
    await flushPromises();

    const resendCall = fetchMock.mock.calls.find((call) => String(call[0]).endsWith("/resend"));
    expect(String(resendCall?.[0])).toBe("/api/v1/travel-marketplace/notifications/aaaaaaaa-1111-1111-1111-111111111111/resend");
    expect(wrapper.text()).toContain("Queued again for STG-ABCDEFGH");
    expect(fetchMock.mock.calls.filter((call) => !String(call[0]).endsWith("/resend")).length).toBe(2);
  });

  it("shows a clear message when the resend is refused because the request changed state", async () => {
    seedSession(["travel-notification:manage"]);
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        if (String(input).endsWith("/resend") && init?.method === "POST") {
          return new Response(JSON.stringify({ error: "request_state_changed" }), { status: 409 });
        }
        return new Response(JSON.stringify([sampleNotification()]), { status: 200 });
      }),
    );

    const wrapper = mountPage();
    await flushPromises();
    await wrapper.get("button.resend").trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("no longer true for the request's current state");
  });

  it("shows a clean message on a 403, not a raw error", async () => {
    seedSession(["travel-notification:manage"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify({ error: "forbidden" }), { status: 403 })));

    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.text()).toContain("don't have permission to manage notifications");
  });
});
