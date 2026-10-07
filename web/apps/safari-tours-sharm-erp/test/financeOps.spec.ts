import { flushPromises, mount } from "@vue/test-utils";
import { ref } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { Booking, CancellationFormDocument, CashDay, OfficeRefunds, PartyStatement, PartySummary, SettlementStatementDocument } from "@wego/api-contract";
import OfficeRefundPanel from "../app/components/OfficeRefundPanel.vue";
import SettlementStatementDoc from "../app/components/documents/SettlementStatementDocument.vue";
import CancellationFormDoc from "../app/components/documents/CancellationFormDocument.vue";
import CashBoxPage from "../app/pages/cash-box.vue";
import CostsPage from "../app/pages/costs.vue";
import SettlementsPage from "../app/pages/settlements.vue";
import ProfitabilityPage from "../app/pages/profitability.vue";
import { writeAuthSession, type AuthSession } from "../app/composables/useAuthSession";
import * as api from "../app/composables/useToursApi";
import { canPrintDocument, documentPath, isValidSubject } from "../app/utils/documentFormat";
import { docMessage } from "../app/utils/documentMessages";
import { erpMessage, formatErpSignedMoney, isLocalizedErpRoute } from "../app/utils/erpLocale";
import { financeOpsAr, financeOpsEn } from "../app/utils/financeOpsMessages";
import {
  financeErrorMessage, monthRange, needsApproval, needsApprovalToday, parseStatementSubject, statementSubject, toCents,
} from "../app/utils/financeOps";

vi.mock("../app/composables/useToursApi", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  listRefunds: vi.fn(), recordRefund: vi.fn(), reverseRefund: vi.fn(), getFxRateToday: vi.fn(),
  getCashDay: vi.fn(), listRecentCashDays: vi.fn(), countCash: vi.fn(), confirmCash: vi.fn(), reopenCash: vi.fn(),
  listPayables: vi.fn(), getStatement: vi.fn(), paySettlement: vi.fn(), approveSettlement: vi.fn(), adjustPayable: vi.fn(),
  getProfitability: vi.fn(), listAllStaffTours: vi.fn(), listCosts: vi.fn(), createCost: vi.fn(),
}));

const eur = (amount: string) => ({ amount, currencyCode: "EUR" as const });
const egp = (amount: string) => ({ amount, currencyCode: "EGP" as const });
const PARTY = "11111111-2222-3333-4444-555555555555";
const session = (permissions: string[]): AuthSession => ({ token: "tok", email: "m@x.y", roles: [], permissions });

function setup(locale: "en" | "ar", permissions: string[]) {
  sessionStorage.clear();
  writeAuthSession(session(permissions));
  vi.stubGlobal("useRouter", () => ({ replace: vi.fn(), push: vi.fn() }));
  vi.stubGlobal("useRoute", () => ({ query: {} }));
  vi.stubGlobal("useHead", vi.fn());
  vi.stubGlobal("useCookie", () => ref(locale));
  vi.stubGlobal("useState", (_k: string, f: () => unknown) => ref(f()));
}
afterEach(() => {
  vi.clearAllMocks();
  vi.unstubAllGlobals();
  sessionStorage.clear();
});
const link = { global: { stubs: { NuxtLink: { props: ["to"], template: "<a :href='to'><slot /></a>" } } } };

describe("OPS2-F helpers", () => {
  it("previews the 5000 EGP manager limit like the server: exactly 5000 needs no approval, EUR at today's rate", () => {
    expect(needsApproval("5000", "EGP", null)).toBe(false);
    expect(needsApproval("5000.00", "EGP", null)).toBe(false);
    expect(needsApproval("5000.01", "EGP", null)).toBe(true);
    expect(needsApproval("100", "EUR", "50.0000")).toBe(false);
    expect(needsApproval("100.01", "EUR", "50.0000")).toBe(true);
    expect(needsApproval("1", "EUR", null)).toBe(true);
    expect(needsApproval("abc", "EGP", null)).toBe(false);
  });

  it("parses amounts exactly, including Arabic-Indic digits, and refuses zero or three decimals", () => {
    expect(toCents("12.5")).toBe(1250n);
    expect(toCents("٥٠٠٠٫٠١")).toBe(500001n);
    expect(toCents("0")).toBeNull();
    expect(toCents("1.001")).toBeNull();
    expect(toCents("-1")).toBeNull();
  });

  it("builds and validates statement links from ids and dates only", () => {
    const subject = statementSubject("suppliers", PARTY, "2026-10-01", "2026-10-31");
    expect(parseStatementSubject(subject)).toEqual({ party: "suppliers", id: PARTY, from: "2026-10-01", to: "2026-10-31" });
    expect(isValidSubject("settlement-statement", subject)).toBe(true);
    expect(isValidSubject("settlement-statement", `clients_${PARTY}_2026-10-01_2026-10-31`)).toBe(false);
    expect(isValidSubject("settlement-statement", `drivers_${PARTY}_2026-10-31_2026-10-01`)).toBe(false);
    expect(parseStatementSubject("drivers_x_2026-01-01_2026-01-02")).toBeNull();
    expect(documentPath("settlement-statement", subject)).toBe(`/documents/settlement-statement/${subject}`);
    expect(canPrintDocument("settlement-statement", (p) => p === "tours-operator.settlement:approve")).toBe(true);
    expect(canPrintDocument("settlement-statement", (p) => p === "tours-operator.document:print-ops")).toBe(false);
  });

  it("translates server refusals with their details and keeps unknown codes visible", () => {
    const refused = new api.ToursApiError(409, "amount_exceeds_balance", { error: "amount_exceeds_balance", details: { balance: "4999.99" } });
    const message = financeErrorMessage(refused);
    expect(erpMessage("en", message.key, message.params)).toBe("More than what is owed (4999.99).");
    expect(financeErrorMessage(new api.ToursApiError(403, "http_403")).key).toBe("fops.err.forbidden");
    expect(financeErrorMessage(new api.ToursApiError(403, "cannot_confirm_own_count", {})).key).toBe("fops.err.cannot_confirm_own_count");
    expect(financeErrorMessage(new api.ToursApiError(409, "something_new")).params).toEqual({ code: "something_new" });
    expect(financeErrorMessage(new Error("x")).key).toBe("common.connectionFailed");
  });

  it("has Arabic for every new label, the new pages are localized, and the month range is a calendar month", () => {
    expect(Object.keys(financeOpsAr).sort()).toEqual(Object.keys(financeOpsEn).sort());
    for (const key of Object.keys(financeOpsEn) as (keyof typeof financeOpsEn)[]) expect(financeOpsAr[key]).not.toBe(financeOpsEn[key]);
    for (const route of ["/costs", "/profitability", "/settlements", "/cash-box"]) expect(isLocalizedErpRoute(route)).toBe(true);
    expect(monthRange(new Date("2026-02-10T12:00:00Z"))).toEqual({ from: "2026-02-01", to: "2026-02-28" });
  });
});

describe("refund panel on a cancelled office booking", () => {
  const booking = { id: "b1", channel: "OFFICE", status: "CANCELLED" } as Booking;
  const refunds = (refundable: string): OfficeRefunds => ({
    entries: [{ id: "r1", kind: "REFUND", method: "CASH", amount: eur("20.00"), amountPaid: eur("20.00"), fxRate: null, reference: null, reason: "Cancelled early", reversesRefundId: null, recordedByEmail: "a@b.c", recordedAt: "2026-10-07T09:00:00Z" }],
    position: { collected: eur("87.50"), refunded: eur("20.00"), refundable: eur(refundable) },
  });

  it("shows what is left to return and sends one request with a reason (manager)", async () => {
    setup("en", ["tours-operator.booking:refund-office"]);
    vi.mocked(api.listRefunds).mockResolvedValue(refunds("67.50"));
    vi.mocked(api.getFxRateToday).mockResolvedValue({ date: "2026-10-07", rate: null });
    vi.mocked(api.recordRefund).mockResolvedValue({} as never);
    const w = mount(OfficeRefundPanel, { props: { booking, session: session(["tours-operator.booking:refund-office"]) } });
    await flushPromises();
    expect(w.get("#refund-refundable").text()).toBe("€67.50");
    expect(w.text()).toContain("Cancelled early");
    await w.get("#refund-amount").setValue("67.50");
    await w.get("#refund-form").trigger("submit");
    expect(w.text()).toContain("Enter a reason.");
    expect(api.recordRefund).not.toHaveBeenCalled();
    await w.get("#refund-reason").setValue("Policy 100%");
    await w.get("#refund-form").trigger("submit");
    await flushPromises();
    const [, id, payload] = vi.mocked(api.recordRefund).mock.calls[0]!;
    expect(id).toBe("b1");
    expect(payload).toMatchObject({ method: "CASH", amount: 67.5, currency: "EUR", reason: "Policy 100%" });
    expect(payload).not.toHaveProperty("reference");
  });

  it("is read-only without the manager permission and shows the server's refusal in Arabic", async () => {
    setup("ar", ["tours-operator.booking:view"]);
    vi.mocked(api.listRefunds).mockResolvedValue(refunds("67.50"));
    let w = mount(OfficeRefundPanel, { props: { booking, session: session(["tours-operator.booking:view"]) } });
    await flushPromises();
    expect(w.find("#refund-form").exists()).toBe(false);

    setup("ar", ["tours-operator.booking:refund-office"]);
    vi.mocked(api.getFxRateToday).mockResolvedValue({ date: "2026-10-07", rate: null });
    vi.mocked(api.recordRefund).mockRejectedValue(new api.ToursApiError(409, "cash_day_closed", { error: "cash_day_closed", details: {} }));
    w = mount(OfficeRefundPanel, { props: { booking, session: session(["tours-operator.booking:refund-office"]) } });
    await flushPromises();
    await w.get("#refund-amount").setValue("10");
    await w.get("#refund-reason").setValue("سبب");
    await w.get("#refund-form").trigger("submit");
    await flushPromises();
    expect(w.text()).toContain(financeOpsAr["fops.err.cash_day_closed"]);
  });

  it("hides the form once everything was returned", async () => {
    setup("en", ["tours-operator.booking:refund-office"]);
    vi.mocked(api.listRefunds).mockResolvedValue(refunds("0.00"));
    vi.mocked(api.getFxRateToday).mockResolvedValue({ date: "2026-10-07", rate: null });
    const w = mount(OfficeRefundPanel, { props: { booking, session: session(["tours-operator.booking:refund-office"]) } });
    await flushPromises();
    expect(w.find("#refund-form").exists()).toBe(false);
  });
});

describe("cash box page", () => {
  const day = (state: CashDay["state"]): CashDay => ({
    date: "2026-10-07", currency: "EUR", state, cashCollected: eur("100.00"), collectionsReversed: eur("10.00"), cashRefunded: eur("20.00"),
    refundsReversed: eur("0.00"), cashSettlementsPaid: eur("5.00"), settlementsReversed: eur("0.00"), expected: eur("65.00"),
    events: state === "OPEN" ? [] : [{ id: "e1", date: "2026-10-07", currency: "EUR", sequence: 1, kind: "COUNT", expected: eur("65.00"), counted: eur("60.00"), difference: eur("-5.00"), note: null, actorEmail: "r@x.y", occurredAt: "2026-10-07T18:00:00Z" }],
  });

  it("shows the expected cash and lets reception count; the manager confirms a counted day", async () => {
    setup("en", ["tours-operator.cash-box:close"]);
    vi.mocked(api.getCashDay).mockResolvedValue(day("OPEN"));
    vi.mocked(api.listRecentCashDays).mockResolvedValue([]);
    vi.mocked(api.countCash).mockResolvedValue(day("COUNTED"));
    let w = mount(CashBoxPage);
    await flushPromises();
    expect(w.get("#cash-expected").text()).toBe("€65.00");
    expect(w.find("#cash-confirm").exists()).toBe(false);
    await w.get("#cash-count input").setValue("60");
    await w.get("#cash-count").trigger("submit");
    await flushPromises();
    expect(vi.mocked(api.countCash).mock.calls[0]!.slice(1, 4)).toEqual([expect.any(String), "EUR", 60]);
    expect(w.text()).toContain("-€5.00");

    setup("en", ["tours-operator.cash-box:confirm"]);
    vi.mocked(api.getCashDay).mockResolvedValue(day("COUNTED"));
    vi.mocked(api.confirmCash).mockRejectedValue(new api.ToursApiError(409, "cash_expected_changed", { error: "cash_expected_changed", details: { expected: "70.00" } }));
    w = mount(CashBoxPage);
    await flushPromises();
    await w.get("#cash-confirm").trigger("click");
    await flushPromises();
    expect(w.text()).toContain("Cash was recorded after the count: count again (now 70.00).");
  });

  it("a closed day offers only reopening with a reason, in Arabic", async () => {
    setup("ar", ["tours-operator.cash-box:close", "tours-operator.cash-box:confirm"]);
    vi.mocked(api.getCashDay).mockResolvedValue(day("CLOSED"));
    vi.mocked(api.listRecentCashDays).mockResolvedValue([]);
    const w = mount(CashBoxPage);
    await flushPromises();
    expect(w.get("#cash-state").text()).toBe(financeOpsAr["fops.cash.state.CLOSED"]);
    expect(w.find("#cash-count").exists()).toBe(false);
    expect(w.find("#cash-reopen").exists()).toBe(true);
  });
});

describe("settlements page", () => {
  const party: PartySummary = {
    partyType: "SUPPLIER", partyId: PARTY, name: "Hamed", code: "S01", active: true, balances: [{ currency: "EGP", opening: egp("0.00"), owed: egp("15000.00"), paid: egp("0.00"), closing: egp("15000.00") }],
    openIssues: 1, pendingApprovals: 1,
  };
  const statement: PartyStatement = {
    party, from: "2026-10-01", to: "2026-10-31", balances: party.balances, managerLimitEgp: "5000.00", paidTodayEgp: "0.00",
    movements: [{ date: "2026-10-07", kind: "DEPARTURE", amount: egp("15000.00"), slotId: "s1", tourId: "t1", timeSlot: "MORNING", guests: 3, labels: ["Supplier price"], adjustment: null, payment: null }],
    issues: [{ date: "2026-10-07", slotId: "s2", tourId: "t1", timeSlot: "MORNING", code: "MANUAL_AMOUNT_NEEDED" }],
    approvals: [{ id: "ap1", amount: egp("6000.00"), note: null, approvedByEmail: "owner@x.y", approvedAt: "2026-10-07T10:00:00Z", usedByPaymentId: null }],
  };

  it("asks for the owner's approval above 5000 EGP and sends it with the payment", async () => {
    setup("en", ["tours-operator.settlement:pay"]);
    vi.mocked(api.listPayables).mockResolvedValue([party]);
    vi.mocked(api.getStatement).mockResolvedValue(statement);
    vi.mocked(api.getFxRateToday).mockResolvedValue({ date: "2026-10-07", rate: null });
    vi.mocked(api.paySettlement).mockResolvedValue({} as never);
    const w = mount(SettlementsPage, link);
    await flushPromises();
    await w.get("tbody button").trigger("click");
    await flushPromises();
    expect(w.text()).toContain("Driver has no trip rate");
    expect(w.find("#approve-form").exists()).toBe(false);
    await w.get("#pay-amount").setValue("5000");
    expect(w.find("#pay-needs-approval").exists()).toBe(false);
    await w.get("#pay-amount").setValue("6000");
    expect(w.find("#pay-needs-approval").exists()).toBe(true);
    const selects = w.get("#pay-form").findAll("select");
    await selects[selects.length - 1]!.setValue("ap1");
    await w.get("#pay-form").trigger("submit");
    await flushPromises();
    expect(vi.mocked(api.paySettlement).mock.calls[0]![3]).toMatchObject({ method: "CASH", currency: "EGP", amount: 6000, approvalId: "ap1" });
    expect(w.html()).toContain(`/documents/settlement-statement/suppliers_${PARTY}_`);
  });
});

describe("profitability page", () => {
  it("shows online and office revenue apart and hides profit when a cost is unknown", async () => {
    setup("en", ["tours-operator.payment:view"]);
    const totals = (complete: boolean) => ({
      bookings: 1, guests: 3, onlineRevenue: eur("0.00"), officeRevenue: eur("87.50"), revenue: eur("87.50"),
      cost: complete ? eur("78.50") : null, profit: complete ? eur("9.00") : null, marginPercent: complete ? "10.3" : null,
      incompleteBookings: complete ? 0 : 1, gaps: complete ? [] : ["DRIVER_COST_MISSING" as const], rateSources: ["LATEST" as const],
    });
    vi.mocked(api.getProfitability).mockResolvedValue({
      from: "2026-10-01", to: "2026-10-31", groupBy: "DEPARTURE", totals: totals(false),
      groups: [
        { key: "s1", tourId: "t1", slotId: "s1", date: "2026-10-07", timeSlot: "MORNING", month: null, bookingId: null, reference: null, status: null, channel: null, egpPerEur: "50.0000", rateSource: "LATEST", totals: totals(true) },
        { key: "s2", tourId: "t1", slotId: "s2", date: "2026-10-08", timeSlot: "MORNING", month: null, bookingId: null, reference: null, status: null, channel: null, egpPerEur: "50.0000", rateSource: "DEPARTURE_DATE", totals: totals(false) },
      ],
    });
    const w = mount(ProfitabilityPage);
    await flushPromises();
    expect(w.text()).toContain("€9.00");
    expect(w.text()).toContain("10.3%");
    expect(w.text()).toContain("Driver amount missing");
    expect(w.text()).toContain("Rate 50.0000 (latest rate)");
    expect(w.text()).toContain("1 booking(s) with an unknown cost: profit hidden.");
    expect(w.findAll("tfoot td")[5]!.text()).toBe("—");
  });
});

describe("OPS2-F documents", () => {
  const statementDoc = (): SettlementStatementDocument => ({
    document: { type: "SETTLEMENT_STATEMENT", number: "STL-2026-000001", version: 1, language: "en", copy: false, revised: false, originalPrintedAt: "2026-10-07T10:00:00Z", printedAt: "2026-10-07T10:00:00Z", printedByEmail: "m@x.y" },
    data: {
      partyType: "SUPPLIER", partyName: "Hamed", partyCode: "S01", from: "2026-10-01", to: "2026-10-31",
      balances: [{ currency: "EGP", opening: egp("0.00"), owed: egp("3300.00"), paid: egp("1000.00"), closing: egp("2300.00") }],
      lines: [
        { date: "2026-10-07", kind: "DEPARTURE", amount: egp("3300.00"), tourNameEn: "Desert Safari", tourNameAr: "سفاري الصحراء", timeSlot: "MORNING", guests: 3, method: null, reference: null, text: null },
        { date: "2026-10-07", kind: "PAYMENT", amount: egp("-1000.00"), tourNameEn: null, tourNameAr: null, timeSlot: null, guests: null, method: "INSTAPAY", reference: "****1234", text: null },
      ],
      openIssues: 1,
    },
  });

  for (const lang of ["en", "ar"] as const) {
    it(`settlement statement in ${lang}: balances, signed lines, masked reference, issues note`, () => {
      setup(lang, []);
      const w = mount(SettlementStatementDoc, { props: { doc: statementDoc(), lang } });
      expect(w.text()).toContain(docMessage(lang, "doc.stl.title"));
      expect(w.text()).toContain("****1234");
      expect(w.text()).toContain(lang === "en" ? "Desert Safari" : "سفاري الصحراء");
      expect(w.text()).toContain(formatErpSignedMoney(egp("-1000.00"), lang));
      expect(w.get("[data-testid=statement-issues]").text()).toContain(lang === "en" ? "1" : "١");
    });

    it(`cancellation form in ${lang} lists money returned and says it was returned`, () => {
      setup(lang, []);
      const doc = {
        document: { type: "CANCELLATION_FORM", number: "CXL-2026-000002", version: 2, language: lang, copy: false, revised: true, originalPrintedAt: "2026-10-06T10:00:00Z", printedAt: "2026-10-07T10:00:00Z", printedByEmail: "c@x.y" },
        data: {
          reference: "STR-2026-1", tourNameEn: "Desert Safari", tourNameAr: null, tourDate: "2027-06-15", timeSlot: "MORNING", customerName: "Ahmed",
          cancelledAt: "2027-06-12T08:00:00Z", cancellationReason: "Called", collections: [], collectedNet: eur("30.00"), policy: "STANDARD",
          hoursBeforeTour: 60, refundPercent: 100, expectedReturn: eur("30.00"), todayRate: null, expectedReturnEgp: null,
          refunds: [{ recordedAt: "2027-06-12T09:00:00Z", reversal: false, method: "CASH", amountPaid: eur("30.00"), returnedEur: eur("30.00") }],
          refundedNet: eur("30.00"), remainingToReturn: eur("0.00"), returnState: "RETURNED",
        },
      } as CancellationFormDocument;
      const w = mount(CancellationFormDoc, { props: { doc, lang } });
      expect(w.find("[data-testid=refunds]").exists()).toBe(true);
      expect(w.get("[data-testid=return-state]").text()).toBe(docMessage(lang, "doc.cancel.state.RETURNED"));
    });
  }
});

describe("review follow-ups (M2, M3, L1, L2)", () => {
  it("previews the daily limit: today's payments count, unknown means approval", () => {
    expect(needsApprovalToday("2000", "EGP", null, "3000.00")).toBe(false);
    expect(needsApprovalToday("2000.01", "EGP", null, "3000.00")).toBe(true);
    expect(needsApprovalToday("0.01", "EGP", null, "5000.00")).toBe(true);
    expect(needsApprovalToday("10", "EGP", null, null)).toBe(true);
    expect(needsApprovalToday("40", "EUR", "50.0000", "3000.00")).toBe(false);
    expect(needsApprovalToday("40.01", "EUR", "50.0000", "3000.00")).toBe(true);
  });

  it("explains the new refusals", () => {
    const own = financeErrorMessage(new api.ToursApiError(403, "approver_cannot_pay", { error: "approver_cannot_pay", details: {} }));
    expect(erpMessage("en", own.key, own.params)).toBe("You approved this payment: someone else must record it.");
    const charge = financeErrorMessage(new api.ToursApiError(403, "charge_needs_approval", { error: "charge_needs_approval", details: {} }));
    expect(erpMessage("ar", charge.key, charge.params)).toContain("5000");
    expect(financeErrorMessage(new api.ToursApiError(409, "cost_component_duplicate", {})).key).toBe("fops.err.cost_component_duplicate");
  });

  it("warns that a past start restates history and sends one idempotency key per save", async () => {
    setup("en", ["tours-operator.cost:manage"]);
    vi.mocked(api.listCosts).mockResolvedValue([]);
    vi.mocked(api.createCost).mockRejectedValue(new api.ToursApiError(409, "cost_component_duplicate", { error: "cost_component_duplicate", details: {} }));
    const w = mount(CostsPage);
    await flushPromises();
    await w.get("button.bg-sts-ocean").trigger("click");
    await w.get("#cost-category").setValue("FIXED");
    await w.get("#cost-label").setValue("Guide");
    await w.get("#cost-amount").setValue("300");
    expect(w.find("#cost-past-warning").exists()).toBe(false);
    await w.get("#cost-from").setValue("2020-01-01");
    expect(w.get("#cost-past-warning").text()).toBe("This start date is in the past: it restates past profit and payables.");
    await w.get("#cost-form").trigger("submit");
    await flushPromises();
    await w.get("#cost-form").trigger("submit");
    await flushPromises();
    const keys = vi.mocked(api.createCost).mock.calls.map((c) => c[1].clientRequestId);
    expect(keys).toHaveLength(2);
    expect(keys[0]).toBe(keys[1]);
    expect(w.text()).toContain("The same cost is already in force for these days");
  });

  it("tells staff when an EGP refund used today's rate", async () => {
    setup("en", ["tours-operator.booking:refund-office"]);
    const booking = { id: "b1", channel: "OFFICE", status: "CANCELLED" } as Booking;
    vi.mocked(api.listRefunds).mockResolvedValue({ entries: [], position: { collected: eur("30.00"), refunded: eur("0.00"), refundable: eur("30.00") } });
    vi.mocked(api.getFxRateToday).mockResolvedValue({ date: "2026-10-07", rate: { id: "r1", rateDate: "2026-10-07", egpPerEur: "60.0000", setByUserId: null, setAt: "2026-10-07T08:00:00Z" } });
    vi.mocked(api.recordRefund).mockResolvedValue({ warning: "TODAY_RATE_USED" } as never);
    const w = mount(OfficeRefundPanel, { props: { booking, session: session(["tours-operator.booking:refund-office"]) } });
    await flushPromises();
    await w.get("#refund-currency").setValue("EGP");
    await w.get("#refund-amount").setValue("600");
    await w.get("#refund-reason").setValue("Policy");
    await w.get("#refund-form").trigger("submit");
    await flushPromises();
    expect(vi.mocked(api.recordRefund).mock.calls[0]![2]).toMatchObject({ currency: "EGP", fxRateId: "r1" });
    expect(w.get("#refund-today-rate").exists()).toBe(true);
  });
});
