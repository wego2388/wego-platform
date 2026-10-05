import { mount, flushPromises } from "@vue/test-utils";
import { ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Booking, Tour, TourSlot } from "@wego/api-contract";
import NewOfficeBooking from "../app/pages/bookings/new.vue";
import OfficePaymentsPanel from "../app/components/OfficePaymentsPanel.vue";
import OfficePaymentBadge from "../app/components/OfficePaymentBadge.vue";
import { writeAuthSession, type AuthSession } from "../app/composables/useAuthSession";
import * as api from "../app/composables/useToursApi";

vi.mock("../app/composables/useToursApi", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  listTours: vi.fn(), listSlotsByDate: vi.fn(), createOfficeBooking: vi.fn(), listOfficeCollections: vi.fn(),
  recordOfficeCollection: vi.fn(), reverseOfficeCollection: vi.fn(), quoteOfficeCollection: vi.fn(), getFxRateToday: vi.fn(), setFxRate: vi.fn(),
}));
const eur = (amount: string) => ({ amount, currencyCode: "EUR" });
const tour = { id: "t1", slug: "quad", nameEn: "Quad", isActive: true, priceBasis: "PER_PERSON", priceAdult: eur("35.00"), priceChild: eur("17.50"), priceOptions: [] } as unknown as Tour;
const slot = { id: "s1", tourId: "t1", date: "2027-06-15", timeSlot: "MORNING", capacity: 10, bookedCount: 7, available: 3, isBlocked: false } as TourSlot;
const perms = ["tours-operator.booking:create-office", "tours-operator.booking:collect-cash", "tours-operator.booking:reverse-collection", "tours-operator.fx-rate:manage"];
const session = (permissions = perms): AuthSession => ({ token: "tok", email: "a@b.c", roles: [], permissions });

function setup(locale: "en" | "ar" = "en") {
  sessionStorage.clear(); writeAuthSession(session());
  vi.stubGlobal("useRouter", () => ({ replace: vi.fn() })); vi.stubGlobal("useRoute", () => ({ query: {} }));
  vi.stubGlobal("useHead", vi.fn()); vi.stubGlobal("useCookie", () => ref(locale)); vi.stubGlobal("useState", (_k: string, f: () => unknown) => ref(f()));
}
beforeEach(() => setup());
afterEach(() => { vi.clearAllMocks(); vi.unstubAllGlobals(); sessionStorage.clear(); });
const link = { global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } } };

describe("new office booking form", () => {
  async function fill(wrapper: ReturnType<typeof mount>) {
    await wrapper.find("#office-tour").setValue("t1"); await flushPromises();
    await wrapper.find("#office-slot").setValue("s1");
    await wrapper.find("#office-adults").setValue("2"); await wrapper.find("#office-children").setValue("1");
    await wrapper.find("#office-name").setValue("Ahmed Hassan"); await wrapper.find("#office-phone").setValue("+201234567890");
    await wrapper.find("#office-hotel").setValue("Hilton");
  }
  beforeEach(() => {
    vi.mocked(api.listTours).mockResolvedValue([tour]); vi.mocked(api.listSlotsByDate).mockResolvedValue([slot]);
  });

  it("shows the live price and the places left from the backend data", async () => {
    const w = mount(NewOfficeBooking, link); await flushPromises(); await fill(w);
    expect(w.text()).toContain("€87.50");
    expect(w.text()).toContain("3 of 10 places left");
    await w.find("#office-adults").setValue("4"); await w.find("#office-children").setValue("0");
    expect(w.text()).toContain("Only 3 places left; this party needs 4.");
  });

  it("validates required fields next to each field and sends nothing", async () => {
    const w = mount(NewOfficeBooking, link); await flushPromises();
    await w.find("form").trigger("submit"); await flushPromises();
    expect(w.text()).toContain("Choose a tour."); expect(w.text()).toContain("Enter the customer's name.");
    expect(w.find("#office-name").attributes("aria-invalid")).toBe("true");
    expect(api.createOfficeBooking).not.toHaveBeenCalled();
  });

  it("sends one idempotency key, prices come from the server, then offers the booking", async () => {
    vi.mocked(api.createOfficeBooking).mockResolvedValue({ id: "b1", reference: "STR-2027-1", totalPrice: eur("87.50"), officePayment: { state: "UNPAID" } } as unknown as Booking);
    const w = mount(NewOfficeBooking, link); await flushPromises(); await fill(w);
    await w.find("form").trigger("submit"); await flushPromises();
    const payload = vi.mocked(api.createOfficeBooking).mock.calls[0]![1];
    expect(payload).toMatchObject({ slotId: "s1", adultsCount: 2, childrenCount: 1, hotelName: "Hilton", locale: "en" });
    expect(payload.clientRequestId).toMatch(/^[0-9a-f-]{36}$/);
    expect(payload).not.toHaveProperty("totalPrice");
    expect(w.text()).toContain("Office booking STR-2027-1 created.");
    expect(w.text()).toContain("Saved total: €87.50"); expect(w.text()).not.toContain("Warning: the saved total");
  });

  it("warns visibly when the saved total differs from the preview", async () => {
    vi.mocked(api.createOfficeBooking).mockResolvedValue({ id: "b1", reference: "STR-2027-2", totalPrice: eur("100.00"), officePayment: { state: "UNPAID" } } as unknown as Booking);
    const w = mount(NewOfficeBooking, link); await flushPromises(); await fill(w);
    await w.find("form").trigger("submit"); await flushPromises();
    expect(w.text()).toContain("Warning: the saved total €100.00 differs");
    expect(w.text()).toContain("Payment: Unpaid — awaiting collection");
  });

  it("explains a refused slot in plain language and keeps the form", async () => {
    vi.mocked(api.createOfficeBooking).mockRejectedValue(new api.ToursApiError(409, "slot_in_past"));
    const w = mount(NewOfficeBooking, link); await flushPromises(); await fill(w);
    await w.find("form").trigger("submit"); await flushPromises();
    expect(w.find('[role="alert"]').text()).toContain("That departure date has passed.");
    expect((w.find("#office-name").element as HTMLInputElement).value).toBe("Ahmed Hassan");
  });

  it("is Arabic and right-to-left aware", async () => {
    setup("ar");
    const w = mount(NewOfficeBooking, link); await flushPromises();
    expect(w.text()).toContain("حجز جديد من المكتب");
    expect(w.find("#office-phone").attributes("dir")).toBe("ltr");
  });

  it("blocks a user without the create permission", async () => {
    sessionStorage.clear(); writeAuthSession(session([]));
    const w = mount(NewOfficeBooking, link); await flushPromises();
    expect(w.text()).toContain("cannot create office bookings"); expect(w.find("form").exists()).toBe(false);
  });
});

const booking = (state: string, extra: Record<string, unknown> = {}) => ({
  id: "b1", status: "CONFIRMED", channel: "OFFICE", totalPrice: eur("87.50"),
  officePayment: { state, collected: eur(state === "UNPAID" ? "0.00" : "20.00"), outstanding: eur(state === "UNPAID" ? "87.50" : "67.50"), cashToReturn: null }, ...extra,
}) as unknown as Booking;

describe("office payments panel and badge", () => {
  beforeEach(() => {
    vi.mocked(api.listOfficeCollections).mockResolvedValue([]);
    vi.mocked(api.getFxRateToday).mockResolvedValue({ date: "2026-10-05", rate: null });
  });

  it("badge says unpaid awaiting collection with the balance, in Arabic too", () => {
    const en = mount(OfficePaymentBadge, { props: { booking: booking("UNPAID") } });
    expect(en.text()).toContain("Unpaid — awaiting collection"); expect(en.text()).toContain("Due: €87.50");
    setup("ar");
    const ar = mount(OfficePaymentBadge, { props: { booking: booking("PARTIALLY_PAID") } });
    expect(ar.text()).toContain("عربون مدفوع"); expect(ar.text()).toContain("المتبقي");
  });

  it("requires a reference for non-cash and none for cash", async () => {
    const w = mount(OfficePaymentsPanel, { props: { booking: booking("UNPAID"), session: session() } }); await flushPromises();
    expect(w.find("#collect-reference").exists()).toBe(false);
    await w.find("#collect-method").setValue("INSTAPAY");
    expect(w.find("#collect-reference").exists()).toBe(true);
    await w.find("#collect-amount").setValue("10");
    await w.find("form").trigger("submit"); await flushPromises();
    expect(w.text()).toContain("Enter the receipt number for this method.");
    expect(api.recordOfficeCollection).not.toHaveBeenCalled();
  });

  it("records a non-cash EGP payment with a trimmed reference and shows the EUR it settles before saving", async () => {
    vi.mocked(api.getFxRateToday).mockResolvedValue({ date: "2026-10-05", rate: { id: "r", rateDate: "2026-10-05", egpPerEur: "50.0000", setByUserId: null, setAt: "2026-10-05T08:00:00Z" } });
    vi.mocked(api.quoteOfficeCollection).mockResolvedValue({ status: "OK", settledEur: eur("20.00"), fxRate: "50.0000", fxRateId: "rate-1", outstanding: eur("87.50") });
    vi.mocked(api.recordOfficeCollection).mockResolvedValue({} as never);
    const w = mount(OfficePaymentsPanel, { props: { booking: booking("UNPAID"), session: session() } }); await flushPromises();
    expect(w.text()).toContain("50.0000 EGP per 1 EUR");
    await w.find("#collect-method").setValue("FAWRY_OFFICE"); await w.find("#collect-currency").setValue("EGP");
    await w.find("#collect-amount").setValue("1000"); await w.find("#collect-reference").setValue("  F-123 "); await flushPromises();
    expect(api.quoteOfficeCollection).toHaveBeenLastCalledWith("tok", "b1", "EGP", "1000");
    expect(w.text()).toContain("Settles €20.00 of the balance");
    await w.find("form").trigger("submit"); await flushPromises();
    expect(vi.mocked(api.recordOfficeCollection).mock.calls[0]![2]).toMatchObject({ method: "FAWRY_OFFICE", currency: "EGP", amount: 1000, reference: "F-123", fxRateId: "rate-1" });
    expect(w.emitted("changed")).toBeTruthy();
  });

  it("says when EGP cannot be taken because no rate is set", async () => {
    vi.mocked(api.quoteOfficeCollection).mockResolvedValue({ status: "RATE_MISSING", settledEur: null, fxRate: null, fxRateId: null, outstanding: eur("87.50") });
    const w = mount(OfficePaymentsPanel, { props: { booking: booking("UNPAID"), session: session() } }); await flushPromises();
    expect(w.text()).toContain("Not set for today");
    await w.find("#collect-currency").setValue("EGP"); await w.find("#collect-amount").setValue("100"); await flushPromises();
    expect(w.text()).toContain("No rate set for today");
  });

  it("reversal needs a reason and cash-to-return is explained", async () => {
    vi.mocked(api.listOfficeCollections).mockResolvedValue([{ id: "c1", kind: "COLLECTION", method: "CASH_AT_OFFICE", amount: eur("20.00"), amountPaid: eur("20.00"), fxRate: null, reference: null, reversesCollectionId: null, reason: null, recordedByUserId: null, recordedAt: "2026-10-05T08:00:00Z" }] as never);
    vi.mocked(api.reverseOfficeCollection).mockResolvedValue({} as never);
    const w = mount(OfficePaymentsPanel, { props: { booking: booking("PARTIALLY_PAID"), session: session() } }); await flushPromises();
    await w.findAll("button").find((b) => b.text() === "Reverse")!.trigger("click");
    await w.findAll("button").find((b) => b.text() === "Confirm reversal")!.trigger("click"); await flushPromises();
    expect(w.text()).toContain("A reason is required."); expect(api.reverseOfficeCollection).not.toHaveBeenCalled();
    await w.find("#reverse-c1").setValue("Counted wrong"); await w.findAll("button").find((b) => b.text() === "Confirm reversal")!.trigger("click"); await flushPromises();
    expect(vi.mocked(api.reverseOfficeCollection).mock.calls[0]![3]).toMatchObject({ reason: "Counted wrong" });
    const cancelled = mount(OfficePaymentsPanel, { props: { booking: booking("PARTIALLY_PAID", { status: "CANCELLED", officePayment: { state: "PARTIALLY_PAID", collected: eur("20.00"), outstanding: eur("67.50"), cashToReturn: eur("20.00") } }), session: session() } }); await flushPromises();
    expect(cancelled.text()).toContain("Return it to the customer"); expect(cancelled.find("#collect-method").exists()).toBe(false);
  });

  it("re-quotes and shows the new EUR equivalent when the rate changed before saving", async () => {
    vi.mocked(api.getFxRateToday).mockResolvedValue({ date: "2026-10-05", rate: { id: "r2", rateDate: "2026-10-05", egpPerEur: "60.0000", setByUserId: null, setAt: "2026-10-05T09:00:00Z" } });
    vi.mocked(api.quoteOfficeCollection)
      .mockResolvedValueOnce({ status: "OK", settledEur: eur("20.00"), fxRate: "50.0000", fxRateId: "r1", outstanding: eur("87.50") })
      .mockResolvedValue({ status: "OK", settledEur: eur("16.67"), fxRate: "60.0000", fxRateId: "r2", outstanding: eur("87.50") });
    vi.mocked(api.recordOfficeCollection).mockRejectedValue(new api.ToursApiError(409, "fx_rate_changed"));
    const w = mount(OfficePaymentsPanel, { props: { booking: booking("UNPAID"), session: session() } }); await flushPromises();
    await w.find("#collect-currency").setValue("EGP"); await w.find("#collect-amount").setValue("1000"); await flushPromises();
    expect(w.text()).toContain("€20.00");
    await w.find("form").trigger("submit"); await flushPromises();
    expect(w.text()).toContain("The rate changed while you were typing");
    expect(w.text()).toContain("€16.67");
  });

  it("shows who reversed an entry, when and why, and a manager cannot reverse without the permission", async () => {
    const base = { amountPaid: eur("20.00"), fxRate: null, recordedAt: "2026-10-05T08:00:00Z", reversesCollectionId: null, reason: null, recordedByUserId: "u1", recordedByEmail: "cashier@x.y", correctsCollectionId: null };
    vi.mocked(api.listOfficeCollections).mockResolvedValue([
      { ...base, id: "c1", kind: "COLLECTION", method: "INSTAPAY", amount: eur("20.00"), reference: "R-9" },
      { ...base, id: "c2", kind: "REVERSAL", method: "INSTAPAY", amount: eur("20.00"), reference: null, reversesCollectionId: "c1", reason: "Wrong receipt", recordedByEmail: "manager@x.y" },
    ] as never);
    const w = mount(OfficePaymentsPanel, { props: { booking: booking("UNPAID"), session: session() } }); await flushPromises();
    expect(w.text()).toContain("Reversed by manager@x.y"); expect(w.text()).toContain("Wrong receipt");
    expect(w.findAll("button").some((b) => b.text() === "Record corrected payment")).toBe(true);
    const noPerm = mount(OfficePaymentsPanel, { props: { booking: booking("PARTIALLY_PAID"), session: session(["tours-operator.booking:collect-cash"]) } }); await flushPromises();
    expect(noPerm.findAll("button").some((b) => b.text() === "Reverse")).toBe(false);
  });

  it("hides recording and rate setting from users without those permissions", async () => {
    const w = mount(OfficePaymentsPanel, { props: { booking: booking("UNPAID"), session: session([]) } }); await flushPromises();
    expect(w.find("form").exists()).toBe(false);
  });
});
