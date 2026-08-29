import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import BoatChartersPage from "../app/pages/boat-charters.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions });
}

const sampleCharter = {
  id: "44444444-4444-4444-4444-444444444444",
  boatName: "Barbarossa",
  charterType: "STANDING",
  licensedCapacity: 50,
  startsOn: "2026-01-01",
  status: "ACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
};

describe("boat charters page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(BoatChartersPage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("lists boat charters with their licensed capacity", async () => {
    seedSession(["boat-charter:view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([sampleCharter]), { status: 200 })));

    const wrapper = mount(BoatChartersPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Barbarossa");
    expect(wrapper.text()).toContain("licensed for 50 passengers");
  });

  it("hides the registration form and end action for a user without boat-charter:manage", async () => {
    seedSession(["boat-charter:view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([sampleCharter]), { status: 200 })));

    const wrapper = mount(BoatChartersPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("New charter");
    expect(wrapper.findAll("button").some((button) => button.text() === "End charter")).toBe(false);
  });

  it("registers a new charter and prepends it to the list", async () => {
    seedSession(["boat-charter:view", "boat-charter:manage"]);
    const created = { ...sampleCharter, id: "new-charter-id", boatName: "Al-Horeya", licensedCapacity: 40 };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "POST" && url.pathname === "/api/v1/divers/boat-charters") {
        return new Response(JSON.stringify(created), { status: 201 });
      }
      return new Response(JSON.stringify([]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(BoatChartersPage);
    await flushPromises();

    await wrapper.get("#boatName").setValue("Al-Horeya");
    await wrapper.get("#licensedCapacity").setValue("40");
    await wrapper.get("#startsOn").setValue("2026-01-01");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.text()).toContain("Al-Horeya");
  });

  it("ends a charter after confirmation", async () => {
    seedSession(["boat-charter:view", "boat-charter:manage"]);
    vi.stubGlobal("confirm", vi.fn(() => true));
    const ended = { ...sampleCharter, status: "ENDED", endedAt: "2026-08-29T00:00:00Z" };
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      if (init?.method === "POST") return new Response(JSON.stringify(ended), { status: 200 });
      return new Response(JSON.stringify([sampleCharter]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(BoatChartersPage);
    await flushPromises();

    const endButton = wrapper.findAll("button").find((button) => button.text() === "End charter");
    await endButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("ENDED");
  });
});
