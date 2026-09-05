import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import JournalEntriesPage from "../app/pages/journal-entries.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions });
}

const cashAccount = {
  id: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  code: "1000",
  name: "Cash on Hand",
  accountType: "ASSET",
  normalBalance: "DEBIT",
  active: true,
  createdAt: "2026-08-31T00:00:00Z",
  updatedAt: "2026-08-31T00:00:00Z",
};

const revenueAccount = {
  id: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  code: "4000",
  name: "Service Revenue",
  accountType: "REVENUE",
  normalBalance: "CREDIT",
  active: true,
  createdAt: "2026-08-31T00:00:00Z",
  updatedAt: "2026-08-31T00:00:00Z",
};

const sampleEntry = {
  id: "cccccccc-cccc-cccc-cccc-cccccccccccc",
  entryDate: "2026-08-31",
  description: "Booking revenue",
  currencyCode: "EGP",
  debitTotal: "100.00",
  creditTotal: "100.00",
  postedAt: "2026-08-31T00:00:00Z",
  lines: [
    { id: "line-1", accountId: cashAccount.id, direction: "DEBIT", amount: "100.00" },
    { id: "line-2", accountId: revenueAccount.id, direction: "CREDIT", amount: "100.00" },
  ],
};

function fetchMockFor(entries: unknown[] = [sampleEntry]) {
  return vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), "http://localhost");
    if (init?.method === "POST" && url.pathname === "/api/v1/accounting/journal-entries") {
      return new Response(JSON.stringify(sampleEntry), { status: 201 });
    }
    if (url.pathname === "/api/v1/accounting/accounts") {
      return new Response(JSON.stringify([cashAccount, revenueAccount]), { status: 200 });
    }
    if (url.pathname === "/api/v1/accounting/journal-entries") {
      return new Response(JSON.stringify(entries), { status: 200 });
    }
    return new Response(JSON.stringify([]), { status: 200 });
  });
}

describe("journal entries page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(JournalEntriesPage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("lists journal entries with resolved account labels for each line", async () => {
    seedSession(["accounting:journal-view"]);
    vi.stubGlobal("fetch", fetchMockFor());

    const wrapper = mount(JournalEntriesPage);
    await flushPromises();

    expect(wrapper.text()).toContain("Booking revenue");
    expect(wrapper.text()).toContain("100.00 EGP debit");
    expect(wrapper.text()).toContain("1000 · Cash on Hand");
    expect(wrapper.text()).toContain("4000 · Service Revenue");
  });

  it("hides the posting form and reverse action for a user without accounting:journal-manage", async () => {
    seedSession(["accounting:journal-view"]);
    vi.stubGlobal("fetch", fetchMockFor());

    const wrapper = mount(JournalEntriesPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("Post a journal entry");
    expect(wrapper.findAll("button").some((button) => button.text() === "Reverse")).toBe(false);
  });

  it("posts a new balanced journal entry", async () => {
    seedSession(["accounting:journal-view", "accounting:journal-manage"]);
    const posted = { ...sampleEntry, id: "new-entry-id", description: "New posting test" };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "POST" && url.pathname === "/api/v1/accounting/journal-entries") {
        return new Response(JSON.stringify(posted), { status: 201 });
      }
      if (url.pathname === "/api/v1/accounting/accounts") {
        return new Response(JSON.stringify([cashAccount, revenueAccount]), { status: 200 });
      }
      if (url.pathname === "/api/v1/accounting/journal-entries") {
        return new Response(JSON.stringify([posted]), { status: 200 });
      }
      return new Response(JSON.stringify([]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(JournalEntriesPage);
    await flushPromises();

    await wrapper.get("#entryDate").setValue("2026-08-31");
    await wrapper.get("#description").setValue("New posting test");
    await wrapper.get("#line-account-0").setValue(cashAccount.id);
    await wrapper.get("#line-amount-0").setValue("100.00");
    await wrapper.get("#line-account-1").setValue(revenueAccount.id);
    const directionSelects = wrapper.findAll("select[id^='line-direction-']");
    await directionSelects[1]!.setValue("CREDIT");
    await wrapper.get("#line-amount-1").setValue("100.00");
    await wrapper.get("form").trigger("submit.prevent");
    await flushPromises();

    expect(wrapper.text()).toContain("New posting test");
  });

  it("reverses a posted entry after providing a reason", async () => {
    seedSession(["accounting:journal-view", "accounting:journal-manage"]);
    const reversalEntry = { ...sampleEntry, id: "reversal-id", reversalOfEntryId: sampleEntry.id };
    // The reversal only shows up in a GET response once it has actually
    // been posted — the reverse button/reason input must be visible
    // *before* that happens, or the test would never reach the click.
    let reversed = false;
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = new URL(String(input), "http://localhost");
      if (init?.method === "POST" && url.pathname === `/api/v1/accounting/journal-entries/${sampleEntry.id}/reverse`) {
        reversed = true;
        return new Response(JSON.stringify(reversalEntry), { status: 201 });
      }
      if (url.pathname === "/api/v1/accounting/accounts") {
        return new Response(JSON.stringify([cashAccount, revenueAccount]), { status: 200 });
      }
      if (url.pathname === "/api/v1/accounting/journal-entries") {
        return new Response(JSON.stringify(reversed ? [sampleEntry, reversalEntry] : [sampleEntry]), { status: 200 });
      }
      return new Response(JSON.stringify([]), { status: 200 });
    });
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(JournalEntriesPage);
    await flushPromises();

    await wrapper.get(`#reverse-reason-${sampleEntry.id}`).setValue("Booking cancelled");
    const reverseButton = wrapper.findAll("button").find((button) => button.text() === "Reverse");
    await reverseButton?.trigger("click");
    await flushPromises();

    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining(`/api/v1/accounting/journal-entries/${sampleEntry.id}/reverse`),
      expect.objectContaining({ method: "POST" }),
    );
  });
});
