import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import RolesPage from "../app/pages/roles.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "admin@example.com", roles: ["platform-admin"], permissions });
}

const sampleRole = { code: "front-desk", description: "Front desk staff", permissions: ["booking:create", "booking:view"] };
const samplePermissions = [
  { code: "booking:create", description: "Create bookings." },
  { code: "booking:view", description: "View bookings." },
  { code: "diver:view", description: "View diver profiles." },
];

function stubFetchFor(roles: unknown[], permissions: unknown[] = samplePermissions) {
  return vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), "http://localhost");
    if (url.pathname === "/api/v1/identity/roles" && (!init || init.method === undefined)) {
      return new Response(JSON.stringify(roles), { status: 200 });
    }
    if (url.pathname === "/api/v1/identity/permissions") {
      return new Response(JSON.stringify(permissions), { status: 200 });
    }
    return new Response(JSON.stringify(null), { status: 200 });
  });
}

describe("roles page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(RolesPage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("lists roles with their permissions for a user with identity:role-view", async () => {
    seedSession(["identity:role-view"]);
    vi.stubGlobal("fetch", stubFetchFor([sampleRole]));

    const wrapper = mount(RolesPage);
    await flushPromises();

    expect(wrapper.text()).toContain("front-desk");
    expect(wrapper.text()).toContain("Front desk staff");
    expect(wrapper.text()).toContain("booking:create");
  });

  it("hides the create-role form and edit buttons without identity:role-manage", async () => {
    seedSession(["identity:role-view"]);
    vi.stubGlobal("fetch", stubFetchFor([sampleRole]));

    const wrapper = mount(RolesPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("New role");
    expect(wrapper.findAll("button").some((button) => button.text() === "Edit permissions")).toBe(false);
  });

  it("creates a new role with the selected permissions", async () => {
    seedSession(["identity:role-view", "identity:role-manage"]);
    const created = { code: "http-test-role", description: "A test role", permissions: ["diver:view"] };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "POST" && url.pathname === "/api/v1/identity/roles") {
        return new Response(JSON.stringify(created), { status: 201 });
      }
      if (url.pathname === "/api/v1/identity/roles") return new Response(JSON.stringify([]), { status: 200 });
      if (url.pathname === "/api/v1/identity/permissions") return new Response(JSON.stringify(samplePermissions), { status: 200 });
      return new Response(JSON.stringify(null), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(RolesPage);
    await flushPromises();

    await wrapper.get("#newRoleCode").setValue("http-test-role");
    await wrapper.get("#newRoleDescription").setValue("A test role");
    const diverViewCheckbox = wrapper
      .findAll('input[type="checkbox"]')
      .find((input) => (input.element as HTMLInputElement).value === "diver:view");
    await diverViewCheckbox?.setValue(true);
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.text()).toContain("http-test-role");
  });

  it("updates a role's permission set", async () => {
    seedSession(["identity:role-view", "identity:role-manage"]);
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "PUT" && url.pathname === "/api/v1/identity/roles/front-desk/permissions") {
        return new Response(JSON.stringify({ ...sampleRole, permissions: ["booking:create", "booking:view", "diver:view"] }), {
          status: 200,
        });
      }
      if (url.pathname === "/api/v1/identity/roles") return new Response(JSON.stringify([sampleRole]), { status: 200 });
      if (url.pathname === "/api/v1/identity/permissions") return new Response(JSON.stringify(samplePermissions), { status: 200 });
      return new Response(JSON.stringify(null), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(RolesPage);
    await flushPromises();

    const editButton = wrapper.findAll("button").find((button) => button.text() === "Edit permissions");
    await editButton?.trigger("click");
    await flushPromises();

    const diverViewCheckbox = wrapper
      .findAll('input[type="checkbox"]')
      .find((input) => (input.element as HTMLInputElement).value === "diver:view");
    await diverViewCheckbox?.setValue(true);

    const saveButton = wrapper.findAll("button").find((button) => button.text() === "Save permissions");
    await saveButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("diver:view");
  });
});
