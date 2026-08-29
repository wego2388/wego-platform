import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import DiversPage from "../app/pages/divers.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions });
}

const sampleDiver = {
  id: "22222222-2222-2222-2222-222222222222",
  fullName: "Ada Lovelace",
  nationality: "British",
  primaryLanguage: "English",
  email: "ada@example.com",
  totalLoggedDives: 12,
  maxDepthMeters: "18.5",
  certifications: [{ id: "cert-1", agency: "PADI", level: "Advanced Open Water" }],
  status: "ACTIVE",
  createdAt: "2026-08-29T00:00:00Z",
};

describe("divers page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(DiversPage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("lists diver profiles with certifications for an authenticated user", async () => {
    seedSession(["diver:view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([sampleDiver]), { status: 200 })));

    const wrapper = mount(DiversPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Ada Lovelace");
    expect(wrapper.text()).toContain("PADI");
    expect(wrapper.text()).toContain("Advanced Open Water");
    expect(wrapper.text()).toContain("12 logged dives");
  });

  it("filters by status ACTIVE by default when listing", async () => {
    seedSession(["diver:view"]);
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), "http://localhost");
      expect(url.searchParams.get("status")).toBe("ACTIVE");
      return new Response(JSON.stringify([sampleDiver]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    mount(DiversPage);
    await flushPromises();

    expect(fetchMock).toHaveBeenCalled();
  });

  it("hides the profile form and archive button for a user without diver:manage", async () => {
    seedSession(["diver:view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([sampleDiver]), { status: 200 })));

    const wrapper = mount(DiversPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("New diver profile");
    expect(wrapper.findAll("button").some((button) => button.text() === "Archive")).toBe(false);
  });

  it("submits a new diver profile and prepends it to the list", async () => {
    seedSession(["diver:view", "diver:manage"]);
    const created = { ...sampleDiver, id: "new-diver-id", fullName: "New Diver" };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "POST" && url.pathname === "/api/v1/divers/divers") {
        return new Response(JSON.stringify(created), { status: 201 });
      }
      return new Response(JSON.stringify([]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(DiversPage);
    await flushPromises();

    await wrapper.get("#fullName").setValue("New Diver");
    await wrapper.get("#email").setValue("new@example.com");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.text()).toContain("New Diver");
  });

  it("fetches the full diver record (including medical notes) when editing, since the list only carries the roster projection", async () => {
    seedSession(["diver:view", "diver:manage"]);
    const fullDiver = {
      ...sampleDiver,
      email: "ada@example.com",
      phone: "+201000000000",
      emergencyContactName: "Grace Hopper",
      emergencyContactPhone: "+201000000001",
      medicalNotes: "No known conditions.",
      certifications: [{ id: "cert-1", agency: "PADI", level: "Advanced Open Water", certificationNumber: "PADI-123" }],
    };
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), "http://localhost");
      if (url.pathname === `/api/v1/divers/divers/${sampleDiver.id}`) {
        return new Response(JSON.stringify(fullDiver), { status: 200 });
      }
      return new Response(JSON.stringify([sampleDiver]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(DiversPage);
    await flushPromises();

    const editButton = wrapper.findAll("button").find((button) => button.text() === "Edit");
    await editButton?.trigger("click");
    await flushPromises();

    const medicalNotesField = wrapper.get("#medicalNotes").element as HTMLTextAreaElement;
    expect(medicalNotesField.value).toBe("No known conditions.");
  });

  it("archives a diver profile after confirmation and removes it from the active list", async () => {
    seedSession(["diver:view", "diver:manage"]);
    vi.stubGlobal("confirm", vi.fn(() => true));
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === "DELETE") {
        return new Response(JSON.stringify({ ...sampleDiver, status: "ARCHIVED", archivedAt: "2026-08-29T00:00:00Z" }), {
          status: 200,
        });
      }
      return new Response(JSON.stringify([sampleDiver]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(DiversPage);
    await flushPromises();

    const archiveButton = wrapper.findAll("button").find((button) => button.text() === "Archive");
    await archiveButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).not.toContain("Ada Lovelace");
  });
});
