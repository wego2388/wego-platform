import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import AccountsPage from "../app/pages/accounts.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "admin@example.com", roles: ["platform-admin"], permissions });
}

const sampleUser = {
  id: "11111111-1111-1111-1111-111111111111",
  email: "front-desk@example.com",
  status: "ACTIVE",
  roles: ["front-desk"],
  createdAt: "2026-08-29T00:00:00Z",
};

const sampleRoles = [
  { code: "front-desk", description: "Front desk", permissions: ["booking:create"] },
  { code: "accountant", description: "Accountant", permissions: ["booking:payment-update"] },
];

function stubFetchFor(users: unknown[], roles: unknown[] = sampleRoles) {
  return vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), "http://localhost");
    if (url.pathname === "/api/v1/identity/users" && (!init || init.method === undefined)) {
      return new Response(JSON.stringify(users), { status: 200 });
    }
    if (url.pathname === "/api/v1/identity/roles" && (!init || init.method === undefined)) {
      return new Response(JSON.stringify(roles), { status: 200 });
    }
    return new Response(JSON.stringify(null), { status: 200 });
  });
}

describe("accounts page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(AccountsPage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("lists staff accounts for a user with identity:user-view", async () => {
    seedSession(["identity:user-view"]);
    vi.stubGlobal("fetch", stubFetchFor([sampleUser]));

    const wrapper = mount(AccountsPage);
    await flushPromises();

    expect(wrapper.text()).toContain("front-desk@example.com");
    expect(wrapper.text()).toContain("ACTIVE");
    expect(wrapper.text()).toContain("front-desk");
  });

  it("hides the create-account form and management buttons without identity:user-manage", async () => {
    seedSession(["identity:user-view"]);
    vi.stubGlobal("fetch", stubFetchFor([sampleUser]));

    const wrapper = mount(AccountsPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("New staff account");
    expect(wrapper.findAll("button").some((button) => button.text() === "Disable")).toBe(false);
  });

  it("creates a new staff account and prepends it to the list", async () => {
    seedSession(["identity:user-view", "identity:user-manage"]);
    const created = { ...sampleUser, id: "new-id", email: "new-staff@example.com" };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "POST" && url.pathname === "/api/v1/identity/users") {
        return new Response(JSON.stringify(created), { status: 201 });
      }
      if (url.pathname === "/api/v1/identity/users") return new Response(JSON.stringify([]), { status: 200 });
      if (url.pathname === "/api/v1/identity/roles") return new Response(JSON.stringify(sampleRoles), { status: 200 });
      return new Response(JSON.stringify(null), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(AccountsPage);
    await flushPromises();

    await wrapper.get("#newUserEmail").setValue("new-staff@example.com");
    await wrapper.get("#newUserPassword").setValue("a-real-password-123456");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.text()).toContain("new-staff@example.com");
  });

  it("disables an active account", async () => {
    seedSession(["identity:user-view", "identity:user-manage"]);
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "POST" && url.pathname === `/api/v1/identity/users/${sampleUser.id}/disable`) {
        return new Response(JSON.stringify({ ...sampleUser, status: "DISABLED" }), { status: 200 });
      }
      if (url.pathname === "/api/v1/identity/users") return new Response(JSON.stringify([sampleUser]), { status: 200 });
      if (url.pathname === "/api/v1/identity/roles") return new Response(JSON.stringify(sampleRoles), { status: 200 });
      return new Response(JSON.stringify(null), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(AccountsPage);
    await flushPromises();

    const disableButton = wrapper.findAll("button").find((button) => button.text() === "Disable");
    await disableButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("DISABLED");
  });

  it("resets a password via the prompt flow and never sends a blank password when the admin cancels", async () => {
    seedSession(["identity:user-view", "identity:user-manage"]);
    const fetchMock = stubFetchFor([sampleUser]);
    vi.stubGlobal("fetch", fetchMock);
    vi.stubGlobal("prompt", vi.fn(() => null));
    vi.stubGlobal("alert", vi.fn());

    const wrapper = mount(AccountsPage);
    await flushPromises();

    const resetButton = wrapper.findAll("button").find((button) => button.text() === "Reset password");
    await resetButton?.trigger("click");
    await flushPromises();

    expect(fetchMock).not.toHaveBeenCalledWith(expect.stringContaining("reset-password"), expect.anything());
  });

  it("reassigns roles for a staff account", async () => {
    seedSession(["identity:user-view", "identity:user-manage"]);
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "PUT" && url.pathname === `/api/v1/identity/users/${sampleUser.id}/roles`) {
        return new Response(JSON.stringify({ ...sampleUser, roles: ["accountant"] }), { status: 200 });
      }
      if (url.pathname === "/api/v1/identity/users") return new Response(JSON.stringify([sampleUser]), { status: 200 });
      if (url.pathname === "/api/v1/identity/roles") return new Response(JSON.stringify(sampleRoles), { status: 200 });
      return new Response(JSON.stringify(null), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(AccountsPage);
    await flushPromises();

    const changeRolesButton = wrapper.findAll("button").find((button) => button.text() === "Change roles");
    await changeRolesButton?.trigger("click");
    await flushPromises();

    const accountantCheckbox = wrapper.findAll('input[type="checkbox"]').find((input) => (input.element as HTMLInputElement).value === "accountant");
    await accountantCheckbox?.setValue(true);
    const frontDeskCheckbox = wrapper.findAll('input[type="checkbox"]').find((input) => (input.element as HTMLInputElement).value === "front-desk");
    await frontDeskCheckbox?.setValue(false);

    const saveButton = wrapper.findAll("button").find((button) => button.text() === "Save roles");
    await saveButton?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("accountant");
  });
});
