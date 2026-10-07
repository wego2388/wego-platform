import { mount, flushPromises } from "@vue/test-utils";
import { ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { encode } from "uqr";
import type {
  CancellationFormDocument, DocumentStamp, PickupManifestDocument, ReceiptDocument, RunSheetDocument, VoucherDocument,
} from "@wego/api-contract";
import VoucherDoc from "../app/components/documents/VoucherDocument.vue";
import ReceiptDoc from "../app/components/documents/ReceiptDocument.vue";
import RunSheetDoc from "../app/components/documents/RunSheetDocument.vue";
import PickupManifestDoc from "../app/components/documents/PickupManifestDocument.vue";
import CancellationFormDoc from "../app/components/documents/CancellationFormDocument.vue";
import QrCode from "../app/components/documents/QrCode.vue";
import DocumentPage from "../app/pages/documents/[kind]/[id].vue";
import { writeAuthSession, type AuthSession } from "../app/composables/useAuthSession";
import * as api from "../app/composables/useToursApi";
import { COMPANY } from "../app/utils/companyProfile";
import { DOCUMENT_MESSAGE_KEYS, docMessage, documentMessages } from "../app/utils/documentMessages";
import { docInstant, documentPath, isValidSubject, staffInitials } from "../app/utils/documentFormat";
import { isLocalizedErpRoute } from "../app/utils/erpLocale";

vi.mock("../app/composables/useToursApi", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  printVoucher: vi.fn(), printReceipt: vi.fn(), printCancellationForm: vi.fn(), printRunSheet: vi.fn(), printPickupManifest: vi.fn(),
}));

const eur = (amount: string) => ({ amount, currencyCode: "EUR" });
const stamp = (over: Partial<DocumentStamp> = {}): DocumentStamp => ({
  type: "VOUCHER", number: "STR-2026-0042", version: 1, language: "en", copy: false, revised: false,
  originalPrintedAt: "2026-10-06T10:00:00Z", printedAt: "2026-10-06T10:00:00Z", printedByEmail: "clerk@example.com", ...over,
});
const voucher = (data: Partial<VoucherDocument["data"]> = {}, s: Partial<DocumentStamp> = {}): VoucherDocument => ({
  document: stamp(s),
  data: {
    reference: "STR-2026-0042", status: "CONFIRMED", valid: true, channel: "OFFICE", tourNameEn: "Desert Safari", tourNameAr: "سفاري الصحراء",
    tourDate: "2027-06-15", timeSlot: "MORNING", adultsCount: 2, childrenCount: 1, unit: null, customerName: "Ahmed Hassan",
    hotelName: "Hilton Sharm Dreams", hotelRoom: "312", totalPrice: eur("87.50"),
    payment: { kind: "OFFICE", state: "PARTIALLY_PAID", collected: eur("30.00"), outstanding: eur("57.50") },
    contentLanguage: "en", includes: ["Hotel pickup"], excludes: ["Drinks"], knowBeforeYouGo: ["Bring water"], meetingPoint: "Hotel lobby",
    myBookingUrl: "https://safaritourssharm.example/en/my-booking", ...data,
  },
} as VoucherDocument);
const receipt = (data: Partial<ReceiptDocument["data"]> = {}, s: Partial<DocumentStamp> = {}): ReceiptDocument => ({
  document: stamp({ type: "RECEIPT", number: "RCT-2026-000007", ...s }),
  data: {
    bookingReference: "STR-2026-0042", tourNameEn: "Desert Safari", tourNameAr: null, tourDate: "2027-06-15", customerName: "Ahmed Hassan",
    method: "INSTAPAY", currencyPaid: "EGP", amountPaid: { amount: "1000.00", currencyCode: "EGP" }, settledEur: eur("20.00"), fxRate: "50.0000",
    referenceMasked: "****5432", recordedAt: "2026-10-06T09:30:00Z", collectedBy: "clerk@example.com",
    bookingTotal: eur("87.50"), collectedToDate: eur("20.00"), remainingAfter: eur("67.50"), reversed: false, reversal: null, ...data,
  },
} as ReceiptDocument);
const runSheet = (): RunSheetDocument => ({
  document: stamp({ type: "RUN_SHEET", number: "RUN-20270615" }),
  data: {
    date: "2027-06-15", totalGuests: 5,
    tours: [{
      tourId: "t1", tourNameEn: "Desert Safari", tourNameAr: "سفاري الصحراء", guests: 5,
      departures: [{
        timeSlot: "MORNING", guests: 5,
        hotels: [{ hotelName: "Alpha Resort", guests: 3 }, { hotelName: "Zeta Hotel", guests: 2 }],
        lines: [
          { reference: "STR-2026-0001", leadName: "Amy Alpha", hotelName: "Alpha Resort", hotelRoom: "12", guests: 3, paymentDue: true },
          { reference: "STR-2026-0002", leadName: "Zed Zebra", hotelName: "Zeta Hotel", hotelRoom: null, guests: 2, paymentDue: false },
        ],
        notes: [{ reference: "STR-2026-0002", text: "Wheelchair user" }],
      }],
    }],
  },
} as RunSheetDocument);
const manifest = (): PickupManifestDocument => ({
  document: stamp({ type: "PICKUP_MANIFEST", number: "PKM-2026-000003" }),
  data: {
    slotId: "s1", date: "2027-06-15", timeSlot: "MORNING", tourNameEn: "Desert Safari", tourNameAr: null, totalGuests: 5, driver: null, vehicle: null,
    lines: [
      { order: 1, reference: "STR-2026-0001", hotelName: "Alpha Resort", hotelRoom: "12", leadName: "Amy Alpha", phone: "+201000000012", guests: 3 },
      { order: 2, reference: "STR-2026-0002", hotelName: "Zeta Hotel", hotelRoom: "7", leadName: "Zed Zebra", phone: "+201000000011", guests: 2 },
    ],
  },
} as PickupManifestDocument);
const cancellation = (): CancellationFormDocument => ({
  document: stamp({ type: "CANCELLATION_FORM", number: "CXL-2026-000002" }),
  data: {
    reference: "STR-2026-0042", tourNameEn: "Desert Safari", tourNameAr: "سفاري الصحراء", tourDate: "2027-06-15", timeSlot: "MORNING", customerName: "Ahmed Hassan",
    cancelledAt: "2027-06-12T08:00:00Z", cancellationReason: "Customer called the office",
    collections: [
      { recordedAt: "2027-06-01T09:00:00Z", reversal: false, method: "CASH_AT_OFFICE", currencyPaid: "EUR", amountPaid: eur("40.00"), settledEur: eur("40.00") },
      { recordedAt: "2027-06-02T09:00:00Z", reversal: true, method: "CASH_AT_OFFICE", currencyPaid: "EUR", amountPaid: eur("10.00"), settledEur: eur("10.00") },
    ],
    collectedNet: eur("30.00"), policy: "STANDARD", hoursBeforeTour: 60, refundPercent: 100, expectedReturn: eur("30.00"),
    todayRate: "50.0000", expectedReturnEgp: { amount: "1500.00", currencyCode: "EGP" },
    refunds: [], refundedNet: { amount: "0.00", currencyCode: "EUR" }, remainingToReturn: { amount: "30.00", currencyCode: "EUR" }, returnState: "NOT_RETURNED",
  },
} as CancellationFormDocument);

describe("document messages", () => {
  it("has the same keys and placeholders in English and Arabic, none empty", () => {
    expect(Object.keys(documentMessages.ar).sort()).toEqual(Object.keys(documentMessages.en).sort());
    const holes = (text: string) => [...text.matchAll(/\{(\w+)\}/g)].map((x) => x[1]).sort();
    for (const key of DOCUMENT_MESSAGE_KEYS) {
      expect(documentMessages.en[key].length, key).toBeGreaterThan(0);
      expect(documentMessages.ar[key].length, key).toBeGreaterThan(0);
      expect(holes(documentMessages.ar[key]), key).toEqual(holes(documentMessages.en[key]));
    }
    expect(docMessage("ar", "doc.copy")).toBe("نسخة");
    expect(docMessage("en", "doc.version", { version: 3 })).toBe("Version 3");
  });

  it("prints only owner-confirmed company facts and never a tourism licence", () => {
    expect(COMPANY.taxId).toBe("779-150-155");
    expect(COMPANY.phone).toBe("+20 111 129 2690");
    expect(COMPANY.address.en).toContain("Office 238, Building 167, Delta Sharm");
    expect(COMPANY.supportHours.en).toBe("24 hours a day");
    expect(JSON.stringify(COMPANY).toLowerCase()).not.toMatch(/licen[cs]e|ترخيص/);
    expect(JSON.stringify(documentMessages).toLowerCase()).not.toMatch(/licen[cs]e/);
  });

  it("validates document links and shows Cairo time", () => {
    expect(isValidSubject("voucher", "3f2b8c3e-1a2b-4c5d-8e9f-0a1b2c3d4e5f")).toBe(true);
    expect(isValidSubject("voucher", "STR-2026-0042")).toBe(false);
    expect(isValidSubject("run-sheet", "2027-06-15")).toBe(true);
    expect(isValidSubject("run-sheet", "tomorrow")).toBe(false);
    expect(documentPath("receipt", "abc")).toBe("/documents/receipt/abc");
    expect(docInstant("2026-10-06T21:30:00Z", "en")).toContain("00:30");
    expect(isLocalizedErpRoute("/documents/voucher/3f2b8c3e-1a2b-4c5d-8e9f-0a1b2c3d4e5f")).toBe(true);
  });
});

describe("staff initials", () => {
  it("derives initials from the email local part", () => {
    expect(staffInitials("mona.ali@x.com")).toBe("MA");
    expect(staffInitials("clerk@x.com")).toBe("C");
    expect(staffInitials(null)).toBe("—");
  });
});

describe("QR code", () => {
  it("draws exactly the encoded URL as an accessible SVG", () => {
    const url = "https://safaritourssharm.example/en/my-booking";
    const w = mount(QrCode, { props: { value: url, label: "QR to my booking" } });
    const svg = w.find("svg");
    expect(svg.attributes("role")).toBe("img");
    expect(svg.attributes("aria-label")).toBe("QR to my booking");
    const size = encode(url, { ecc: "M", border: 3 }).size;
    expect(svg.attributes("viewBox")).toBe(`0 0 ${size} ${size}`);
    expect(w.find("path").attributes("d")).toContain("M");
  });
});

describe.each([["en", "ltr"], ["ar", "rtl"]] as const)("documents in %s", (lang, dir) => {
  const root = (w: ReturnType<typeof mount>) => w.get("article.doc-sheet");

  it("voucher: booking facts, payment, content, QR to the public page only", () => {
    const w = mount(VoucherDoc, { props: { doc: voucher(), lang } });
    expect(root(w).attributes("lang")).toBe(lang);
    expect(root(w).attributes("dir")).toBe(dir);
    const text = w.text();
    expect(text).toContain(docMessage(lang, "doc.voucher.title"));
    for (const fact of ["STR-2026-0042", "Ahmed Hassan", "Hilton Sharm Dreams", "312", "Hotel pickup", "Drinks", "Bring water", "Hotel lobby"]) expect(text).toContain(fact);
    expect(text).toContain(lang === "ar" ? "سفاري الصحراء" : "Desert Safari");
    expect(text).toContain(docMessage(lang, "doc.pay.PARTIALLY_PAID"));
    expect(text).toContain(lang === "ar" ? "٣٠٫٠٠" : "30.00");
    expect(text).toContain(COMPANY.taxId);
    expect(text).toContain(COMPANY.phone);
    expect(text).toContain(COMPANY.address[lang]);
    expect(text).not.toContain("+20123");
    const qr = w.findComponent(QrCode);
    expect(qr.exists()).toBe(true);
    expect(qr.props("value")).toBe("https://safaritourssharm.example/en/my-booking");
    expect(qr.props("value")).not.toMatch(/STR-|\?|\+20/);
    expect(w.find("img.doc-logo").attributes("src")).toBe("/logo-full.webp");
    expect(w.find(".doc-banner").exists()).toBe(false);
    expect(w.find(".doc-copy").exists()).toBe(false);
  });

  it("voucher: a cancelled booking shows a banner and watermark, no QR and no tour notes", () => {
    const w = mount(VoucherDoc, { props: { doc: voucher({ valid: false, status: "CANCELLED", myBookingUrl: null }), lang } });
    expect(w.get(".doc-banner").attributes("role")).toBe("alert");
    expect(w.get(".doc-banner").text()).toBe(docMessage(lang, "doc.banner.cancelled"));
    expect(w.get(".doc-watermark").text()).toBe(docMessage(lang, "doc.watermark.cancelled"));
    expect(w.findComponent(QrCode).exists()).toBe(false);
    expect(w.text()).not.toContain("Bring water");
  });

  it("voucher: a reprint is marked COPY with its version and the original date", () => {
    const w = mount(VoucherDoc, { props: { doc: voucher({}, { version: 2, copy: true, originalPrintedAt: "2026-10-01T08:00:00Z" }), lang } });
    const copy = w.get(".doc-copy").text();
    expect(copy).toContain(docMessage(lang, "doc.copy"));
    expect(copy).toContain(lang === "ar" ? "٢" : "2");
    expect(w.text()).toContain(docMessage(lang, "doc.versionLabel"));
  });

  it("voucher: online payment state and per-unit tours", () => {
    const w = mount(VoucherDoc, { props: { doc: voucher({ channel: "ONLINE", payment: { kind: "ONLINE", state: "PAID", collected: null, outstanding: null }, unit: { optionLabel: "Two-seat buggy", unitCount: 2 } }), lang } });
    expect(w.text()).toContain(docMessage(lang, "doc.pay.online.PAID"));
    expect(w.text()).toContain("Two-seat buggy");
    expect(w.text()).not.toContain(docMessage(lang, "doc.voucher.balance"));
  });

  it("receipt: masked reference, rate, settled EUR, no card data", () => {
    const w = mount(ReceiptDoc, { props: { doc: receipt(), lang } });
    const text = w.text();
    expect(text).toContain("****5432");
    expect(text).toContain("RCT-2026-000007");
    expect(text).toContain(docMessage(lang, "doc.receipt.rateValue", { rate: "50.0000" }));
    expect(text).toContain(lang === "ar" ? "٢٠٫٠٠" : "20.00");
    expect(text).toContain(docMessage(lang, "doc.receipt.noCard"));
    expect(text).not.toMatch(/\b\d{12,}\b|CVV|PAN/i);
    expect(w.find(".doc-watermark").exists()).toBe(false);
    expect(w.find(".doc-banner").exists()).toBe(false);
  });

  it("receipt: a reversed payment is stamped VOID and links the reversal", () => {
    const w = mount(ReceiptDoc, { props: { doc: receipt({ reversed: true, reversal: { entryRef: "REV-1A2B3C4D", recordedAt: "2026-10-06T12:00:00Z" } }), lang } });
    expect(w.get(".doc-watermark").text()).toBe(docMessage(lang, "doc.watermark.void"));
    expect(w.get(".doc-banner").text()).toContain("REV-1A2B3C4D");
  });

  it("run sheet: guests, hotels and notes, and no phone numbers", () => {
    const w = mount(RunSheetDoc, { props: { doc: runSheet(), lang } });
    const text = w.text();
    expect(text).toContain("Alpha Resort (" + (lang === "ar" ? "٣" : "3") + ")");
    expect(text).toContain("Wheelchair user");
    expect(text).toContain(docMessage(lang, "doc.run.phones"));
    expect(text).not.toMatch(/\+20\d/);
    expect(w.findAll("tbody tr")).toHaveLength(2);
    expect(w.findAll("th[scope='col']").length).toBeGreaterThan(0);
    expect(w.text()).toContain(docMessage(lang, "doc.run.dueYes"));
  });

  it("pickup manifest: ordered rows with phones and blank driver and vehicle", () => {
    const w = mount(PickupManifestDoc, { props: { doc: manifest(), lang } });
    const rows = w.findAll("tbody tr").map((r) => r.text());
    expect(rows[0]).toContain("Alpha Resort");
    expect(rows[0]).toContain("+201000000012");
    expect(rows[1]).toContain("Zeta Hotel");
    expect(w.findAll(".doc-blank")).toHaveLength(2);
    expect(w.text()).toContain(docMessage(lang, "doc.pickup.privacy"));
  });

  it("customer paper shows staff initials, never an email; internal paper keeps the email", () => {
    for (const w of [
      mount(VoucherDoc, { props: { doc: voucher(), lang } }),
      mount(ReceiptDoc, { props: { doc: receipt(), lang } }),
      mount(CancellationFormDoc, { props: { doc: cancellation(), lang } }),
    ]) {
      expect(w.text()).not.toContain("clerk@example.com");
      expect(w.text()).toContain(docMessage(lang, "doc.staff", { initials: "C" }));
    }
    expect(mount(RunSheetDoc, { props: { doc: runSheet(), lang } }).text()).toContain("clerk@example.com");
    expect(mount(PickupManifestDoc, { props: { doc: manifest(), lang } }).text()).toContain("clerk@example.com");
  });

  it("a changed run sheet / manifest reprint says REVISED with the time, an unchanged one says COPY", () => {
    const revised = { ...runSheet(), document: { ...runSheet().document, version: 3, copy: false, revised: true } };
    let w = mount(RunSheetDoc, { props: { doc: revised, lang } });
    expect(w.get(".doc-copy").text()).toContain(docMessage(lang, "doc.revised"));
    expect(w.get(".doc-copy").text().startsWith(docMessage(lang, "doc.revised"))).toBe(true);
    const same = { ...manifest(), document: { ...manifest().document, version: 2, copy: true, revised: false } };
    w = mount(PickupManifestDoc, { props: { doc: same, lang } });
    expect(w.get(".doc-copy").text()).toContain(docMessage(lang, "doc.copy"));
    expect(w.get(".doc-copy").text().startsWith(docMessage(lang, "doc.copy"))).toBe(true);
    expect(w.get(".doc-copy").text()).not.toContain(lang === "ar" ? "معدّلة" : "REVISED");
  });

  it("cancellation form: EGP equivalent at today's rate, or a clear no-rate note, and the basis", () => {
    let w = mount(CancellationFormDoc, { props: { doc: cancellation(), lang } });
    expect(w.get("[data-testid=egp-equivalent]").text()).toContain("50.0000");
    expect(w.text()).toContain(docMessage(lang, "doc.cancel.basis"));
    const noRate = cancellation();
    noRate.data.todayRate = null; noRate.data.expectedReturnEgp = null;
    w = mount(CancellationFormDoc, { props: { doc: noRate, lang } });
    expect(w.find("[data-testid=egp-equivalent]").exists()).toBe(false);
    expect(w.get("[data-testid=no-rate]").text()).toBe(docMessage(lang, "doc.cancel.noRate"));
  });

  it("cancellation form: collected net, policy, expected return and three signature lines", () => {
    const w = mount(CancellationFormDoc, { props: { doc: cancellation(), lang } });
    const text = w.text();
    expect(text).toContain(docMessage(lang, "doc.policy.STANDARD"));
    expect(text).toContain(docMessage(lang, "doc.cancel.internal"));
    expect(text).toContain(lang === "ar" ? "٣٠٫٠٠" : "30.00");
    expect(text).toContain(docMessage(lang, "doc.cancel.reversal"));
    expect(w.findAll(".doc-sign").map((s) => s.text())).toEqual([
      docMessage(lang, "doc.cancel.customerSign"), docMessage(lang, "doc.cancel.staffSign"), docMessage(lang, "doc.cancel.managerSign"),
    ]);
    expect(text).toContain(docMessage(lang, "doc.cancel.timing", { hours: lang === "ar" ? "٦٠" : "60", percent: lang === "ar" ? "١٠٠" : "100" }));
    expect(text).not.toMatch(/\+20\d/);
  });
});

describe("print preview page", () => {
  const ID = "3f2b8c3e-1a2b-4c5d-8e9f-0a1b2c3d4e5f";
  const session = (permissions: string[]): AuthSession => ({ token: "tok", email: "clerk@example.com", roles: [], permissions });
  const push = vi.fn(); const replace = vi.fn();
  let print: ReturnType<typeof vi.fn>;

  function setup(params: { kind: string; id: string }, permissions: string[], query: Record<string, string> = {}, locale: "en" | "ar" = "en") {
    sessionStorage.clear(); writeAuthSession(session(permissions));
    vi.stubGlobal("useRoute", () => ({ params, query }));
    vi.stubGlobal("useRouter", () => ({ push, replace, back: vi.fn() }));
    vi.stubGlobal("useHead", vi.fn());
    vi.stubGlobal("definePageMeta", vi.fn());
    vi.stubGlobal("useCookie", () => ref(locale));
    vi.stubGlobal("useState", (_k: string, f: () => unknown) => ref(f()));
    print = vi.fn(); vi.stubGlobal("print", print);
    window.print = print as unknown as typeof window.print;
  }
  afterEach(() => { vi.clearAllMocks(); vi.unstubAllGlobals(); sessionStorage.clear(); });
  beforeEach(() => { vi.mocked(api.printVoucher).mockResolvedValue(voucher()); });

  it("records nothing until Generate is pressed, then records once and prints", async () => {
    setup({ kind: "voucher", id: ID }, ["tours-operator.document:print"]);
    const w = mount(DocumentPage, { global: { stubs: { ErpLanguageSwitch: true } } });
    await flushPromises();
    expect(api.printVoucher).not.toHaveBeenCalled();
    expect(w.find("article").exists()).toBe(false);
    await w.get("button.bg-sts-ocean").trigger("click"); await flushPromises();
    expect(api.printVoucher).toHaveBeenCalledTimes(1);
    expect(api.printVoucher).toHaveBeenCalledWith("tok", ID, "en");
    expect(print).toHaveBeenCalledTimes(1);
    expect(w.find("article.doc-sheet").exists()).toBe(true);
    expect(w.get("[role='status']").text()).toContain("STR-2026-0042");
  });

  it("prints in the chosen language and a language change clears the preview", async () => {
    setup({ kind: "voucher", id: ID }, ["tours-operator.document:print"], { lang: "ar" });
    const w = mount(DocumentPage, { global: { stubs: { ErpLanguageSwitch: true } } });
    await flushPromises();
    await w.get("button.bg-sts-ocean").trigger("click"); await flushPromises();
    expect(api.printVoucher).toHaveBeenCalledWith("tok", ID, "ar");
    expect(w.get("article").attributes("dir")).toBe("rtl");
    await w.get("input[value='en']").setValue(); await flushPromises();
    expect(w.find("article").exists()).toBe(false);
  });

  it("uses the matching endpoint for every document kind", async () => {
    const cases: [string, string, keyof typeof api, unknown][] = [
      ["receipt", ID, "printReceipt", receipt()], ["cancellation", ID, "printCancellationForm", cancellation()],
      ["pickup", ID, "printPickupManifest", manifest()], ["run-sheet", "2027-06-15", "printRunSheet", runSheet()],
    ];
    for (const [kind, id, fn, result] of cases) {
      const perm = kind === "pickup" || kind === "run-sheet" ? "tours-operator.document:print-ops" : "tours-operator.document:print";
      setup({ kind, id }, [perm]);
      (api[fn] as ReturnType<typeof vi.fn>).mockResolvedValue(result);
      const w = mount(DocumentPage, { global: { stubs: { ErpLanguageSwitch: true } } });
      await flushPromises();
      await w.get("button.bg-sts-ocean").trigger("click"); await flushPromises();
      expect(api[fn], kind).toHaveBeenCalledWith("tok", id, "en");
      expect(w.find("article.doc-sheet").exists(), kind).toBe(true);
    }
  });

  it("refuses without the permission or with a malformed link, without calling the API", async () => {
    setup({ kind: "run-sheet", id: "2027-06-15" }, ["tours-operator.document:print"]);
    let w = mount(DocumentPage, { global: { stubs: { ErpLanguageSwitch: true } } });
    await flushPromises();
    expect(w.get("[role='alert']").text()).toContain(docMessage("en", "doc.ui.errorForbidden"));
    expect(w.find("button.bg-sts-ocean").exists()).toBe(false);
    setup({ kind: "voucher", id: "STR-2026-0042" }, ["tours-operator.document:print"]);
    w = mount(DocumentPage, { global: { stubs: { ErpLanguageSwitch: true } } });
    await flushPromises();
    expect(w.get("[role='alert']").text()).toContain(docMessage("en", "doc.ui.errorBadLink"));
    expect(api.printVoucher).not.toHaveBeenCalled();
  });

  it("explains a refused voucher and does not print", async () => {
    setup({ kind: "voucher", id: ID }, ["tours-operator.document:print"], {}, "ar");
    vi.mocked(api.printVoucher).mockRejectedValue(new api.ToursApiError(409, "booking_not_confirmed"));
    const w = mount(DocumentPage, { global: { stubs: { ErpLanguageSwitch: true } } });
    await flushPromises();
    await w.get("button.bg-sts-ocean").trigger("click"); await flushPromises();
    expect(w.get("[role='alert']").text()).toContain(docMessage("ar", "doc.ui.errorNotConfirmed"));
    expect(print).not.toHaveBeenCalled();
    expect(w.find("article").exists()).toBe(false);
  });

  it("is keyboard- and screen-reader-friendly: labelled language group, one h1, focusable preview", async () => {
    setup({ kind: "voucher", id: ID }, ["tours-operator.document:print"]);
    const w = mount(DocumentPage, { attachTo: document.body, global: { stubs: { ErpLanguageSwitch: true } } });
    await flushPromises();
    expect(w.get("fieldset legend").text()).toBe(docMessage("en", "doc.ui.language"));
    expect(w.findAll("input[type='radio']").every((r) => r.element.closest("label"))).toBe(true);
    await w.get("button.bg-sts-ocean").trigger("click"); await flushPromises();
    expect(w.findAll("h1").length).toBe(2); // toolbar title plus the paper's own title
    expect(w.get("section[aria-label]").attributes("tabindex")).toBe("-1");
    expect(w.get("article").attributes("aria-labelledby")).toBeTruthy();
    expect(document.getElementById(w.get("article").attributes("aria-labelledby")!)).not.toBeNull();
    w.unmount();
  });
});
