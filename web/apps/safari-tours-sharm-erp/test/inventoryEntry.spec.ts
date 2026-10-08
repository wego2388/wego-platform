import { mount, flushPromises } from "@vue/test-utils";
import { ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import CalendarDateField from "../app/components/CalendarDateField.vue";
import DepartureCreator from "../app/components/DepartureCreator.vue";
import TourEntryPage from "../app/pages/tours/new.vue";
import { dateFromParts, isCalendarDate, operatorCalendarDay } from "../app/utils/calendarDate";
import { entryPrice, tourEntryPayload, type TourEntry } from "../app/utils/tourEntry";
import { writeAuthSession } from "../app/composables/useAuthSession";
import * as api from "../app/composables/useToursApi";

vi.mock("../app/composables/useToursApi", async (original) => ({ ...(await original<object>()), createSlot: vi.fn(), createTour: vi.fn() }));
const tour = { id: "fixture", availableTimeSlots: ["MORNING"], capacity: 20 } as api.Tour;
const slot = { id: "slot", tourId: "fixture", date: "2028-02-29", timeSlot: "MORNING", capacity: 7, available: 7, bookedCount: 0, isBlocked: false } as api.TourSlot;
function setup(locale = "en") {
  vi.stubGlobal("useHead", vi.fn()); vi.stubGlobal("useCookie", () => ref(locale)); vi.stubGlobal("useState", (_k: string, f: () => unknown) => ref(f()));
  vi.stubGlobal("useRouter", () => ({ replace: vi.fn() }));
}
beforeEach(() => { setup(); sessionStorage.clear(); vi.mocked(api.createSlot).mockResolvedValue(slot); });
afterEach(() => { vi.clearAllMocks(); vi.unstubAllGlobals(); sessionStorage.clear(); });
const link = { global: { stubs: { NuxtLink: { template: "<a><slot /></a>" } } } };

describe("Gregorian date control", () => {
  it("validates real calendar days, leap years and Arabic digits without date rollover", () => {
    expect(dateFromParts("29", "2", "٢٠٢٨")).toBe("2028-02-29");
    expect(dateFromParts("1", "10", "۲۰۲۷")).toBe("2027-10-01");
    for (const day of ["2027-02-29", "2028-02-30", "2028-13-01", "2028-00-01", "2028-01-00", "2028-01-01T00:00:00Z", "0000-01-01"]) expect(isCalendarDate(day)).toBe(false);
    expect(operatorCalendarDay(new Date("2026-10-08T22:00:00Z"))).toBe("2026-10-09");
  });
  for (const locale of ["en", "ar"]) it(`${locale}: separates day/month/year, stores ISO and retains invalid parts`, async () => {
    setup(locale);
    const w = mount(CalendarDateField, { props: { id: "day", modelValue: "2028-01-31", label: "Date", required: true } });
    expect(w.get("#day-day").element.tagName).toBe("SELECT");
    expect(w.get("#day-year").attributes("dir")).toBe("ltr");
    await w.get("#day-month").setValue("2");
    expect(w.emitted("update:modelValue")!.at(-1)).toEqual([""]);
    await w.setProps({ modelValue: "" });
    expect((w.get("#day-day").element as HTMLSelectElement).value).toBe("31");
    expect(w.get("#day-day").attributes("aria-invalid")).toBe("true");
    await w.get("#day-day").setValue("29");
    expect(w.emitted("update:modelValue")!.at(-1)).toEqual(["2028-02-29"]);
  });
  it("enforces min/max, supports dates beyond 60 days and explicit clearing", async () => {
    const w = mount(CalendarDateField, { props: { id: "day", modelValue: "2028-05-01", label: "Date", min: "2028-04-01", max: "2028-12-31" } });
    await w.get("#day-month").setValue("3"); expect(w.emitted("update:modelValue")!.at(-1)).toEqual([""]);
    await w.get("#day-month").setValue("12"); expect(w.emitted("update:modelValue")!.at(-1)).toEqual(["2028-12-01"]);
    await w.get("button").trigger("click"); expect(w.emitted("update:modelValue")!.at(-1)).toEqual([""]);
  });
});

describe("confirmed departure entry", () => {
  async function fill(w: ReturnType<typeof mount>) {
    await w.get("#departure-time").setValue("MORNING"); await w.get("#departure-capacity").setValue("7");
    await w.get('input[type="checkbox"]').setValue(true);
  }
  it("requires real capacity and confirmation and only offers catalog-supported windows", async () => {
    const w = mount(DepartureCreator, { props: { token: "tok", tour, date: "2028-02-29" } });
    expect(w.findAll("#departure-time option")).toHaveLength(2);
    await w.get("button").trigger("click"); expect(api.createSlot).not.toHaveBeenCalled();
    expect(w.text()).toContain("capacity from 1 to 1000");
    await fill(w); await w.get("button").trigger("click"); await flushPromises();
    expect(api.createSlot).toHaveBeenCalledWith("tok", "fixture", { date: "2028-02-29", timeSlot: "MORNING", capacity: 7 });
    expect(w.emitted("saved")).toEqual([[slot]]);
  });
  it("cannot retain approval after date or capacity changes", async () => {
    const w = mount(DepartureCreator, { props: { token: "tok", tour, date: "2028-02-29" } });
    await fill(w); await w.setProps({ date: "2028-03-01" }); await w.get("button").trigger("click");
    expect(api.createSlot).not.toHaveBeenCalled();
  });
  it("rejects past dates, unsupported periods, fractions and excessive capacity", async () => {
    const w = mount(DepartureCreator, { props: { token: "tok", tour, date: "2020-02-29" } });
    await fill(w); await w.get("button").trigger("click"); expect(api.createSlot).not.toHaveBeenCalled();
    await w.setProps({ date: "2028-02-29" });
    for (const capacity of ["0", "1001", "1.5", "-1"]) {
      await w.get("#departure-capacity").setValue(capacity); await w.get('input[type="checkbox"]').setValue(true); await w.get("button").trigger("click");
    }
    expect(api.createSlot).not.toHaveBeenCalled();
  });
  it("handles a duplicate or lost-response retry without pretending it created another slot", async () => {
    vi.mocked(api.createSlot).mockRejectedValue(new api.ToursApiError(409, "slot_already_exists"));
    const w = mount(DepartureCreator, { props: { token: "tok", tour, date: "2028-02-29" } });
    await fill(w); await w.get("button").trigger("click"); await flushPromises();
    expect(w.emitted("saved")).toBeUndefined(); expect(w.emitted("conflict")).toHaveLength(1);
    expect(w.text()).toContain("already exists");
  });
  it("respects disabled state and surfaces forbidden/session-expired failures", async () => {
    const w = mount(DepartureCreator, { props: { token: "tok", tour, date: "2028-02-29", disabled: true } });
    await w.get("button").trigger("click"); expect(api.createSlot).not.toHaveBeenCalled();
    await w.setProps({ disabled: false }); await fill(w);
    vi.mocked(api.createSlot).mockRejectedValue(new api.ToursApiError(403, "forbidden"));
    await w.get("button").trigger("click"); await flushPromises(); expect(w.text()).toContain("permission");
    vi.mocked(api.createSlot).mockRejectedValue(new api.ToursApiError(401, "unauthenticated"));
    await w.get("button").trigger("click"); await flushPromises(); expect(w.emitted("expired")).toHaveLength(1);
  });
});

const facts = (): TourEntry => ({ name: "Fixture only", slug: "fixture-only", category: "DESERT", duration: "2 hours", adult: "12.05", child: "", capacity: "20", times: ["MORNING"], order: "0", type: "TOUR", pricingNote: "", confirmed: true });
describe("inactive tour entry contract", () => {
  it("uses exact minor units, Arabic decimals, zero versus missing child price", () => {
    expect(entryPrice("12.05")).toBe(1205); expect(entryPrice("١٢٫٠٥")).toBe(1205);
    for (const x of ["1.005", "-1", "1e3", "Infinity", "", "90071992547410"]) expect(entryPrice(x)).toBeNull();
    expect(tourEntryPayload(facts())).toMatchObject({ priceAdultCents: 1205, priceChildCents: null, cancellationPolicy: "STANDARD" });
    expect(tourEntryPayload({ ...facts(), child: "0" })!.priceChildCents).toBe(0);
    expect(tourEntryPayload(facts())).not.toHaveProperty("isActive");
  });
  it("rejects missing commercial facts, invalid slugs, unsupported times and missing approval", () => {
    for (const patch of [{ confirmed: false }, { adult: "" }, { name: "" }, { duration: "" }, { slug: "Ab C" }, { capacity: "0" }, { capacity: "1001" }, { times: [] }, { child: "bad" }, { order: "-1" }]) expect(tourEntryPayload({ ...facts(), ...patch })).toBeNull();
  });
  it("does not expose a create form without the manage permission", async () => {
    writeAuthSession({ token: "tok", email: "fixture@example.com", roles: [], permissions: ["tours-operator.tour:view"] });
    const w = mount(TourEntryPage, link); await flushPromises();
    expect(w.find("form").exists()).toBe(false); expect(api.createTour).not.toHaveBeenCalled();
  });
});
