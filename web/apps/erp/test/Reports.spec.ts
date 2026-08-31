import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";

import ReportsPage from "../app/pages/reports.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

function seedSession(permissions: string[] = []) {
  writeAuthSession({ token: "test-token", email: "staff@example.com", roles: ["platform-admin"], permissions });
}

const trialBalance = {
  asOfDate: "2026-08-31",
  lines: [
    { accountId: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", code: "1000", name: "Cash", accountType: "ASSET", debitBalance: "800.00", creditBalance: "0.00" },
    { accountId: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", code: "2000", name: "Accounts Payable", accountType: "LIABILITY", debitBalance: "0.00", creditBalance: "800.00" },
  ],
  totalDebits: "800.00",
  totalCredits: "800.00",
};

const incomeStatement = {
  from: "2026-08-01",
  to: "2026-08-31",
  revenueLines: [{ accountId: "cccccccc-cccc-cccc-cccc-cccccccccccc", code: "4000", name: "Service Revenue", amount: "5000.00" }],
  expenseLines: [{ accountId: "dddddddd-dddd-dddd-dddd-dddddddddddd", code: "5100", name: "Rent Expense", amount: "1200.00" }],
  totalRevenue: "5000.00",
  totalExpenses: "1200.00",
  netIncome: "3800.00",
};

const balanceSheet = {
  asOfDate: "2026-08-31",
  assetLines: [{ accountId: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", code: "1000", name: "Cash", amount: "13800.00" }],
  liabilityLines: [],
  equityLines: [
    { accountId: "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee", code: "3000", name: "Owner's Equity", amount: "10000.00" },
    { name: "Retained Earnings (accumulated)", amount: "3800.00" },
  ],
  totalAssets: "13800.00",
  totalLiabilities: "0.00",
  totalEquity: "13800.00",
};

function fetchMockFor() {
  return vi.fn(async (input: RequestInfo | URL) => {
    const url = new URL(String(input), "http://localhost");
    if (url.pathname === "/api/v1/accounting/reports/trial-balance") return new Response(JSON.stringify(trialBalance), { status: 200 });
    if (url.pathname === "/api/v1/accounting/reports/income-statement") return new Response(JSON.stringify(incomeStatement), { status: 200 });
    if (url.pathname === "/api/v1/accounting/reports/balance-sheet") return new Response(JSON.stringify(balanceSheet), { status: 200 });
    return new Response(JSON.stringify({}), { status: 200 });
  });
}

describe("reports page", () => {
  it("shows a sign-in prompt and makes no request when there is no session", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    const wrapper = mount(ReportsPage);
    await flushPromises();

    expect(wrapper.text()).toContain("You need to sign in");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("hides all three reports for a user without accounting:journal-view", async () => {
    seedSession([]);
    vi.stubGlobal("fetch", fetchMockFor());

    const wrapper = mount(ReportsPage);
    await flushPromises();

    expect(wrapper.text()).not.toContain("Trial Balance");
  });

  it("runs the trial balance and shows debit/credit balances that actually total equal", async () => {
    seedSession(["accounting:journal-view"]);
    vi.stubGlobal("fetch", fetchMockFor());

    const wrapper = mount(ReportsPage);
    await flushPromises();

    await wrapper.get("#trialBalanceDate").setValue("2026-08-31");
    const runButtons = wrapper.findAll("button").filter((button) => button.text() === "Run");
    await runButtons[0]?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("800.00 DR");
    expect(wrapper.text()).toContain("800.00 CR");
    expect(wrapper.text()).toContain("Total debits 800.00 · Total credits 800.00");
  });

  it("runs the income statement and shows net income", async () => {
    seedSession(["accounting:journal-view"]);
    vi.stubGlobal("fetch", fetchMockFor());

    const wrapper = mount(ReportsPage);
    await flushPromises();

    await wrapper.get("#incomeFrom").setValue("2026-08-01");
    await wrapper.get("#incomeTo").setValue("2026-08-31");
    const runButtons = wrapper.findAll("button").filter((button) => button.text() === "Run");
    await runButtons[1]?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("Service Revenue");
    expect(wrapper.text()).toContain("Rent Expense");
    expect(wrapper.text()).toContain("Net income 3800.00");
  });

  it("runs the balance sheet and shows the synthesized retained earnings line", async () => {
    seedSession(["accounting:journal-view"]);
    vi.stubGlobal("fetch", fetchMockFor());

    const wrapper = mount(ReportsPage);
    await flushPromises();

    await wrapper.get("#balanceSheetDate").setValue("2026-08-31");
    const runButtons = wrapper.findAll("button").filter((button) => button.text() === "Run");
    await runButtons[2]?.trigger("click");
    await flushPromises();

    expect(wrapper.text()).toContain("Retained Earnings (accumulated)");
    expect(wrapper.text()).toContain("Total assets 13800.00");
    expect(wrapper.text()).toContain("Total equity 13800.00");
  });
});
