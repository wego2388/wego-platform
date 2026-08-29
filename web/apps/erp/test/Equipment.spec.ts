import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import EquipmentPage from "../app/pages/equipment.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions });
}

const sampleEquipment = {
  id: "33333333-3333-3333-3333-333333333333",
  equipmentType: "TANK",
  label: "Tank #1",
  qrCode: "QR-TANK-001",
  serialNumber: "SN-TANK-1",
  status: "ACTIVE",
  createdAt: "2026-08-29T00:00:00Z",
};

describe("equipment page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(EquipmentPage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("lists equipment for an authenticated user", async () => {
    seedSession(["equipment:view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([sampleEquipment]), { status: 200 })));

    const wrapper = mount(EquipmentPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Tank #1");
    expect(wrapper.text()).toContain("QR-TANK-001");
    expect(wrapper.text()).toContain("ACTIVE");
  });

  it("looks up equipment by an exact scanned QR code", async () => {
    seedSession(["equipment:view"]);
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), "http://localhost");
      if (url.searchParams.get("qrCode") === "QR-TANK-001") {
        return new Response(JSON.stringify([sampleEquipment]), { status: 200 });
      }
      return new Response(JSON.stringify([]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(EquipmentPage);
    await flushPromises();

    await wrapper.get("#qrLookup").setValue("QR-TANK-001");
    const lookupButton = wrapper.findAll("button").find((button) => button.text() === "Look up");
    await lookupButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("Tank #1");
  });

  it("hides the registration form and management actions for a user without equipment:manage", async () => {
    seedSession(["equipment:view"]);
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify([sampleEquipment]), { status: 200 })));

    const wrapper = mount(EquipmentPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("New equipment");
    expect(wrapper.findAll("button").some((button) => button.text() === "Retire")).toBe(false);
  });

  it("registers new equipment and prepends it to the list", async () => {
    seedSession(["equipment:view", "equipment:manage"]);
    const created = { ...sampleEquipment, id: "new-equipment-id", label: "New BCD", equipmentType: "BCD" };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "POST" && url.pathname === "/api/v1/divers/equipment") {
        return new Response(JSON.stringify(created), { status: 201 });
      }
      return new Response(JSON.stringify([]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(EquipmentPage);
    await flushPromises();

    await wrapper.get("#label").setValue("New BCD");
    await wrapper.get("#qrCode").setValue("QR-NEW-BCD");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.text()).toContain("New BCD");
  });

  it("starts maintenance on an active item", async () => {
    seedSession(["equipment:view", "equipment:manage"]);
    const inMaintenance = { ...sampleEquipment, status: "IN_MAINTENANCE" };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "POST" && url.pathname.endsWith("/start-maintenance")) {
        return new Response(JSON.stringify(inMaintenance), { status: 200 });
      }
      return new Response(JSON.stringify([sampleEquipment]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(EquipmentPage);
    await flushPromises();

    const startButton = wrapper.findAll("button").find((button) => button.text() === "Start maintenance");
    await startButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("IN_MAINTENANCE");
  });
});
