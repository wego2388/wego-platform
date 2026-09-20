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

const sampleService = {
  id: "s1",
  categoryId: "c1",
  name: { en: "Desert Safari", ar: "سفاري صحراوي" },
  description: { en: "An evening safari.", ar: "رحلة مسائية." },
  fulfilmentModel: "DIRECT",
  confirmationType: "INSTANT",
  cancellationPolicy: { en: "Free cancellation.", ar: "إلغاء مجاني." },
  options: [],
  media: [],
  status: "DRAFT",
  createdAt: "2026-09-05T00:00:00Z",
};

function fetchRoutedBy(routes: Record<string, () => Response>) {
  return vi.fn(async (input: RequestInfo | URL) => {
    const url = new URL(String(input), "http://localhost");
    return (routes[url.pathname] ?? (() => new Response(JSON.stringify([]), { status: 200 })))();
  });
}

describe("index page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(IndexPage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("shows real service status counts for a user with service:view", async () => {
    seedSession(["service:view"]);
    vi.stubGlobal(
      "fetch",
      fetchRoutedBy({
        "/api/v1/travel-marketplace/services": () => new Response(JSON.stringify([sampleService]), { status: 200 }),
        "/api/v1/travel-marketplace/categories": () => new Response(JSON.stringify([{ id: "c1" }]), { status: 200 }),
      }),
    );

    const wrapper = mount(IndexPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Live catalog summary");
    expect(wrapper.text()).toContain("1 Draft");
    expect(wrapper.text()).toContain("No real service is live yet");
  });

  it("shows real active provider count for a user with provider:view", async () => {
    seedSession(["provider:view"]);
    vi.stubGlobal(
      "fetch",
      fetchRoutedBy({
        "/api/v1/travel-marketplace/providers": () =>
          new Response(JSON.stringify([{ id: "p1", name: "Red Sea Adventures", status: "ACTIVE" }]), { status: 200 }),
      }),
    );

    const wrapper = mount(IndexPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Providers");
    expect(wrapper.text()).toContain("1");
  });

  it("only requests the widgets the account actually has permission for", async () => {
    seedSession(["provider:view"]);
    const fetchMock = fetchRoutedBy({
      "/api/v1/travel-marketplace/providers": () => new Response(JSON.stringify([{ id: "p1", name: "X", status: "ACTIVE" }]), { status: 200 }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(IndexPage);
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).not.toContain("Services by status");
  });

  it("shows the no-permission notice for a signed-in account with none of the dashboard permissions", async () => {
    seedSession([]);
    vi.stubGlobal("fetch", vi.fn());

    const wrapper = mount(IndexPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Your account doesn't hold permission to view any catalog summary widget yet.");
  });
});
