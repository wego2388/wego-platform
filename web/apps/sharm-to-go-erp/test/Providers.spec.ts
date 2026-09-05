import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import ProvidersPage from "../app/pages/providers.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions });
}

const sampleProvider = {
  id: "33333333-3333-3333-3333-333333333333",
  name: "Blue Horizon Diving",
  contactEmail: "ops@example.com",
  status: "ACTIVE",
  createdAt: "2026-09-02T00:00:00Z",
};

describe("providers page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(ProvidersPage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("lists providers for an authenticated user with provider:view", async () => {
    seedSession(["provider:view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([sampleProvider]), { status: 200 })));

    const wrapper = mount(ProvidersPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Blue Horizon Diving");
    expect(wrapper.text()).toContain("ops@example.com");
  });

  it("hides the provider form and archive button for a user without provider:manage", async () => {
    seedSession(["provider:view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([sampleProvider]), { status: 200 })));

    const wrapper = mount(ProvidersPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("New provider");
    expect(wrapper.findAll("button").some((button) => button.text() === "Archive")).toBe(false);
  });

  it("denies listing entirely for a user with no provider permissions", async () => {
    seedSession([]);
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(ProvidersPage);
    await flushPromises();

    expect(wrapper.text()).toContain("doesn't have permission to list providers");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("submits a new provider and prepends it to the list", async () => {
    seedSession(["provider:view", "provider:manage"]);
    const created = { ...sampleProvider, id: "new-provider-id", name: "New Provider" };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "POST" && url.pathname === "/api/v1/travel-marketplace/providers") {
        return new Response(JSON.stringify(created), { status: 201 });
      }
      return new Response(JSON.stringify([]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(ProvidersPage);
    await flushPromises();

    await wrapper.get("#name").setValue("New Provider");
    await wrapper.get("#contactEmail").setValue("new@example.com");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.text()).toContain("New Provider");
  });

  it("archives a provider after confirmation and removes it from the active list", async () => {
    seedSession(["provider:view", "provider:manage"]);
    vi.stubGlobal("confirm", vi.fn(() => true));
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === "DELETE") {
        return new Response(JSON.stringify({ ...sampleProvider, status: "ARCHIVED", archivedAt: "2026-09-02T00:00:00Z" }), {
          status: 200,
        });
      }
      return new Response(JSON.stringify([sampleProvider]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(ProvidersPage);
    await flushPromises();

    const archiveButton = wrapper.findAll("button").find((button) => button.text() === "Archive");
    await archiveButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).not.toContain("Blue Horizon Diving");
  });

  it("shows a real error, not a raw crash, when the server rejects an already-archived provider", async () => {
    seedSession(["provider:view", "provider:manage"]);
    vi.stubGlobal("confirm", vi.fn(() => true));
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === "DELETE") {
        return new Response(JSON.stringify({ error: "already_archived" }), { status: 409 });
      }
      return new Response(JSON.stringify([sampleProvider]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(ProvidersPage);
    await flushPromises();

    const archiveButton = wrapper.findAll("button").find((button) => button.text() === "Archive");
    await archiveButton?.trigger("click");
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toBe("That provider is already archived.");
  });
});
