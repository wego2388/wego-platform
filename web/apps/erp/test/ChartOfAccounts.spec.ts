import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import ChartOfAccountsPage from "../app/pages/chart-of-accounts.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions });
}

const sampleAccount = {
  id: "88888888-8888-8888-8888-888888888888",
  code: "1000",
  name: "Cash on Hand",
  accountType: "ASSET",
  normalBalance: "DEBIT",
  active: true,
  createdAt: "2026-08-31T00:00:00Z",
  updatedAt: "2026-08-31T00:00:00Z",
};

function fetchMockFor(accounts: unknown[] = [sampleAccount]) {
  return vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), "http://localhost");
    if (init?.method === "POST" && url.pathname === "/api/v1/accounting/accounts") {
      return new Response(JSON.stringify(sampleAccount), { status: 201 });
    }
    if (url.pathname === "/api/v1/accounting/accounts") {
      return new Response(JSON.stringify(accounts), { status: 200 });
    }
    return new Response(JSON.stringify([]), { status: 200 });
  });
}

describe("chart of accounts page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(ChartOfAccountsPage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("lists accounts with their normal balance", async () => {
    seedSession(["accounting:coa-view"]);
    vi.stubGlobal("fetch", fetchMockFor());

    const wrapper = mount(ChartOfAccountsPage);
    await flushPromises();

    expect(wrapper.text()).toContain("1000 · Cash on Hand");
    expect(wrapper.text()).toContain("ASSET");
    expect(wrapper.text()).toContain("normal balance DEBIT");
  });

  it("hides the account form for a user without accounting:coa-manage", async () => {
    seedSession(["accounting:coa-view"]);
    vi.stubGlobal("fetch", fetchMockFor());

    const wrapper = mount(ChartOfAccountsPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("New account");
    expect(wrapper.findAll("button").some((button) => button.text() === "Deactivate")).toBe(false);
  });

  it("creates a new account and the list refreshes to include it", async () => {
    seedSession(["accounting:coa-view", "accounting:coa-manage"]);
    const created = { ...sampleAccount, id: "new-account-id", code: "2000", name: "Accounts Payable" };
    // The page refetches the list after create (matching employees.vue's
    // own pattern) rather than appending the POST response directly, so
    // the GET mock must reflect the post-creation state, not stay empty.
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "POST" && url.pathname === "/api/v1/accounting/accounts") {
        return new Response(JSON.stringify(created), { status: 201 });
      }
      if (url.pathname === "/api/v1/accounting/accounts") {
        return new Response(JSON.stringify([created]), { status: 200 });
      }
      return new Response(JSON.stringify([]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(ChartOfAccountsPage);
    await flushPromises();

    await wrapper.get("#code").setValue("2000");
    await wrapper.get("#name").setValue("Accounts Payable");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.text()).toContain("Accounts Payable");
  });
});
