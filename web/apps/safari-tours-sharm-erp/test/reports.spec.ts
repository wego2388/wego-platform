import { mount, flushPromises } from "@vue/test-utils";
import { ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Reports from "../app/pages/reports.vue";
import { writeAuthSession } from "../app/composables/useAuthSession";
import * as api from "../app/composables/useToursApi";
import { en, ar, type ErpMessageKey } from "../app/utils/erpLocale";
import { reportsAr, reportsEn } from "../app/utils/reportsMessages";

vi.mock("../app/composables/useToursApi", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  getReversalReport: vi.fn(), getCashVarianceReport: vi.fn(), getRequestConversionReport: vi.fn(), getDepartureMoneyReport: vi.fn(), downloadReportCsv: vi.fn(),
}));

const amt = (amount: string, currencyCode = "EUR") => ({ amount, currencyCode });
const reversalReport = {
  from: "2027-01-01", to: "2027-01-31", basis: "RECORDED_DAY",
  lines: [{
    kind: "COLLECTION", id: "r1", reversedAt: "2027-01-10T09:00:00Z", reversedOn: "2027-01-10", reversedBy: "mgr@example.com", reason: "Wrong booking",
    originalAt: "2027-01-08T09:00:00Z", originalOn: "2027-01-08", originalBy: "rec@example.com", lagDays: 2, amount: amt("500.00", "EGP"), amountEur: amt("10.00"),
    cash: true, crossedClosedDay: true, bookingReference: "STR-1", partyType: null, partyName: null,
  }],
  totals: [{ kind: "COLLECTION", currency: "EGP", count: 1, amount: amt("500.00", "EGP") }],
};
const cashReport = {
  from: "2027-01-01", to: "2027-01-31", basis: "BUSINESS_DAY",
  days: [{ date: "2027-01-08", currency: "EGP", state: "CLOSED", expected: amt("500.00", "EGP"), counted: amt("450.00", "EGP"), difference: amt("-50.00", "EGP"), countedBy: "rec@example.com", countedAt: "2027-01-08T20:00:00Z", confirmedBy: "mgr@example.com", confirmedAt: "2027-01-08T21:00:00Z", countEvents: 1, reopenCount: 0 }],
  totals: [{ currency: "EGP", closedDays: 1, daysWithVariance: 1, difference: amt("-50.00", "EGP") }],
};
const requestReport = { from: "2027-01-01", to: "2027-01-31", basis: "CREATED_DAY", total: 4, new: 1, inProgress: 1, closed: 1, converted: 1, conversionPercent: "25.0" };
const departureReport = {
  from: "2027-01-01", to: "2027-01-31", basis: "TOUR_DAY",
  days: [{
    date: "2027-01-15", liveBookings: 2, cancelledBookings: 0, booked: amt("200.00"), collected: amt("150.00"), outstanding: amt("50.00"), retainedOnCancelled: amt("0.00"),
    received: [{ currency: "EUR", amount: amt("100.00"), settledEur: amt("100.00") }, { currency: "EGP", amount: amt("2500.00", "EGP"), settledEur: amt("50.00") }],
    paymentsUnderReview: 0, refundsPending: 0,
  }],
  totals: null,
};
const empty = {
  rev: { ...reversalReport, lines: [], totals: [] }, cash: { ...cashReport, days: [], totals: [] },
  req: { ...requestReport, total: 0, new: 0, inProgress: 0, closed: 0, converted: 0, conversionPercent: null },
  dep: { ...departureReport, days: [] },
};

function setup(permissions: string[], locale: "en" | "ar" = "en") {
  sessionStorage.clear();
  writeAuthSession({ token: "tok", email: "a@b.c", roles: [], permissions });
  vi.stubGlobal("useRouter", () => ({ replace: vi.fn() })); vi.stubGlobal("useRoute", () => ({ query: {} }));
  vi.stubGlobal("useHead", vi.fn()); vi.stubGlobal("useCookie", () => ref(locale)); vi.stubGlobal("useState", (_k: string, f: () => unknown) => ref(f()));
}
function full() {
  vi.mocked(api.getReversalReport).mockResolvedValue(reversalReport as never);
  vi.mocked(api.getCashVarianceReport).mockResolvedValue(cashReport as never);
  vi.mocked(api.getRequestConversionReport).mockResolvedValue(requestReport as never);
  vi.mocked(api.getDepartureMoneyReport).mockResolvedValue(departureReport as never);
}
afterEach(() => { vi.clearAllMocks(); vi.unstubAllGlobals(); sessionStorage.clear(); });
const FINANCE = ["tours-operator.payment:view", "tours-operator.booking:view"];

describe("reports page", () => {
  beforeEach(() => full());

  it("documents every indicator and renders all four reports in English", async () => {
    setup(FINANCE);
    const w = mount(Reports); await flushPromises();
    for (const id of ["report-reversals", "report-cash", "report-requests", "report-departures"]) expect(w.find(`#${id}`).exists()).toBe(true);
    const text = w.text();
    for (const k of ["rep.rev", "rep.cash", "rep.req", "rep.dep"]) for (const f of ["definition", "period", "currency", "source"]) expect(text).toContain(en[`${k}.${f}` as ErpMessageKey]);
    expect(text).toContain("Wrong booking");
    expect(text).toContain("STR-1");
    expect(text).toContain("25.0%");
    expect(w.findAll("section dl dt").filter((n) => n.text() === "Definition")).toHaveLength(4);
    expect(w.text()).not.toMatch(/\+20\d/);
  });

  it("renders in Arabic with the Arabic documentation", async () => {
    setup(FINANCE, "ar");
    const w = mount(Reports); await flushPromises();
    expect(w.text()).toContain(ar["rep.title"]);
    expect(w.text()).toContain(ar["rep.dep.currency"]);
    expect(w.text()).toContain(ar["rep.rev.definition"]);
  });

  it("shows only the sections the account may read", async () => {
    setup(["tours-operator.booking:view"]);
    const w = mount(Reports); await flushPromises();
    expect(w.find("#report-requests").exists()).toBe(true);
    expect(w.find("#report-reversals").exists()).toBe(false);
    expect(w.find("#report-cash").exists()).toBe(false);
    expect(w.find("#report-departures").exists()).toBe(false);
    expect(api.getReversalReport).not.toHaveBeenCalled();
    expect(api.getDepartureMoneyReport).not.toHaveBeenCalled();
    expect(api.getRequestConversionReport).toHaveBeenCalled();
  });

  it("cash-box confirm alone sees only the cash variances", async () => {
    setup(["tours-operator.cash-box:confirm"]);
    const w = mount(Reports); await flushPromises();
    expect(w.find("#report-cash").exists()).toBe(true);
    expect(w.find("#report-reversals").exists()).toBe(false);
    expect(w.find("#report-requests").exists()).toBe(false);
  });

  it("tells an account with no report permission so, and fetches nothing", async () => {
    setup(["tours-operator.tour:view"]);
    const w = mount(Reports); await flushPromises();
    expect(w.text()).toContain(en["rep.noAccess"]);
    expect(api.getReversalReport).not.toHaveBeenCalled();
  });

  for (const locale of ["en", "ar"] as const) it(`${locale}: shows an empty state per report`, async () => {
    setup(FINANCE, locale);
    vi.mocked(api.getReversalReport).mockResolvedValue(empty.rev as never);
    vi.mocked(api.getCashVarianceReport).mockResolvedValue(empty.cash as never);
    vi.mocked(api.getRequestConversionReport).mockResolvedValue(empty.req as never);
    vi.mocked(api.getDepartureMoneyReport).mockResolvedValue(empty.dep as never);
    const w = mount(Reports); await flushPromises();
    const message = (locale === "ar" ? ar : en)["rep.empty"];
    expect(w.text().split(message).length - 1).toBe(4);
  });

  it("shows a load failure instead of an empty table", async () => {
    setup(FINANCE);
    vi.mocked(api.getReversalReport).mockRejectedValue(new Error("boom"));
    const w = mount(Reports); await flushPromises();
    expect(w.find("#report-reversals").text()).toContain(en["rep.failed"]);
  });

  it("downloads the CSV with the page language", async () => {
    setup(FINANCE, "ar");
    vi.mocked(api.downloadReportCsv).mockResolvedValue(new Blob(["x"]));
    vi.stubGlobal("URL", { createObjectURL: () => "blob:x", revokeObjectURL: vi.fn() });
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
    const w = mount(Reports); await flushPromises();
    await w.find("#report-reversals button").trigger("click"); await flushPromises();
    expect(api.downloadReportCsv).toHaveBeenCalledWith("tok", "reversals", expect.any(String), expect.any(String), "ar");
    expect(click).toHaveBeenCalled();
  });
});

describe("report messages", () => {
  it("has the same keys in English and Arabic, none empty", () => {
    expect(Object.keys(reportsAr).sort()).toEqual(Object.keys(reportsEn).sort());
    for (const v of [...Object.values(reportsEn), ...Object.values(reportsAr)]) expect(v.trim()).not.toBe("");
  });
});
