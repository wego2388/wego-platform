import { describe, expect, it } from "vitest";
import type { Booking, Tour } from "@wego/api-contract";
import { ar, en, type ErpMessageKey } from "../app/utils/erpLocale";
import {
  COLLECTION_METHODS, estimateTotal, methodNeedsReference, normalizeReference, officeBadge, officeErrorMessage, parseAmount, validateOfficeForm,
} from "../app/utils/officeBooking";
import { isLive, isUnpaid } from "../app/utils/runSheet";
import { ToursApiError } from "../app/composables/useToursApi";

const eur = (amount: string) => ({ amount, currencyCode: "EUR" });
const perPerson = { priceBasis: "PER_PERSON", priceAdult: eur("35.00"), priceChild: eur("17.50"), priceOptions: [] } as unknown as Tour;
const perUnit = { priceBasis: "PER_UNIT", priceAdult: eur("30.00"), priceChild: null, priceOptions: [{ code: "buggy", label: "Buggy", seatsPerUnit: 2, price: eur("30.00") }] } as unknown as Tour;
const office = (status: string, state: string, extra: Record<string, unknown> = {}) => ({
  channel: "OFFICE", status,
  officePayment: { state, collected: eur(state === "UNPAID" ? "0.00" : "20.00"), outstanding: eur(state === "PAID" ? "0.00" : "67.50"), cashToReturn: null, ...extra },
}) as unknown as Booking;

describe("office price preview mirrors the server rules", () => {
  it("prices per person with children", () => {
    expect(estimateTotal(perPerson, { adults: 2, children: 1, optionCode: "", unitCount: 1 })).toEqual({ ok: true, total: eur("87.50"), seats: 3 });
  });
  it("refuses children without a child price and zero adults", () => {
    expect(estimateTotal({ ...perPerson, priceChild: null } as Tour, { adults: 1, children: 1, optionCode: "", unitCount: 1 })).toEqual({ ok: false, key: "office.new.errNoChildPrice" });
    expect(estimateTotal(perPerson, { adults: 0, children: 0, optionCode: "", unitCount: 1 }).ok).toBe(false);
  });
  it("prices per unit and takes every seat of every unit", () => {
    expect(estimateTotal(perUnit, { adults: 2, children: 1, optionCode: "buggy", unitCount: 2 })).toEqual({ ok: true, total: eur("60.00"), seats: 4 });
    expect(estimateTotal(perUnit, { adults: 1, children: 0, optionCode: "buggy", unitCount: 1 })).toEqual({ ok: true, total: eur("30.00"), seats: 2 });
    expect(estimateTotal(perUnit, { adults: 3, children: 0, optionCode: "buggy", unitCount: 1 })).toEqual({ ok: false, key: "office.new.errGuestsExceedUnits" });
    expect(estimateTotal(perUnit, { adults: 1, children: 0, optionCode: "buggy", unitCount: 2 })).toEqual({ ok: false, key: "office.new.errUnitsExceedGuests" });
    expect(estimateTotal(perUnit, { adults: 1, children: 0, optionCode: "", unitCount: 1 })).toEqual({ ok: false, key: "office.new.errOption" });
  });
});

describe("office form validation", () => {
  const ok = { tourId: "t", slotId: "s", fullName: "A B", phone: "+20100", nationality: "eg", email: "", hotelName: "Hilton" };
  it("accepts a complete form with optional email empty", () => expect(validateOfficeForm(ok)).toEqual({}));
  it("flags each missing or malformed field", () => {
    expect(Object.keys(validateOfficeForm({ tourId: "", slotId: "", fullName: " ", phone: "", nationality: "egypt", email: "x", hotelName: "" })).sort())
      .toEqual(["email", "fullName", "hotelName", "nationality", "phone", "slotId", "tourId"]);
  });
});

describe("payment methods, references and amounts", () => {
  it("offers exactly the six approved methods; only cash needs no reference", () => {
    expect([...COLLECTION_METHODS]).toEqual(["CASH_AT_OFFICE", "CASH_ON_PICKUP", "MOBILE_WALLET", "CARD_TERMINAL", "INSTAPAY", "FAWRY_OFFICE"]);
    expect(COLLECTION_METHODS.filter((m) => !methodNeedsReference(m))).toEqual(["CASH_AT_OFFICE", "CASH_ON_PICKUP"]);
  });
  it("trims the reference and rejects empty, long and control-character values", () => {
    expect(normalizeReference("  AB-1 ")).toEqual({ ok: true, value: "AB-1" });
    expect(normalizeReference("  ")).toEqual({ ok: false, key: "office.collect.referenceRequired" });
    expect(normalizeReference("x".repeat(65))).toEqual({ ok: false, key: "office.collect.referenceTooLong" });
    expect(normalizeReference("a\nb")).toEqual({ ok: false, key: "office.collect.referenceInvalid" });
  });
  it("parses amounts above zero with at most two decimals, including Arabic digits", () => {
    expect(parseAmount("12.5")).toEqual({ amount: 12.5, text: "12.5" });
    expect(parseAmount("١٢٫٥٠")?.text).toBe("12.50");
    for (const bad of ["", "0", "0.00", "-1", "1.005", "abc", "1,5"]) expect(parseAmount(bad), bad).toBeNull();
  });
});

describe("unpaid / balance badges", () => {
  it("is absent for online bookings", () => expect(officeBadge({ channel: "ONLINE", status: "NEW", officePayment: null })).toBeNull());
  it("shows unpaid awaiting collection with the balance", () => {
    const b = officeBadge(office("NEW", "UNPAID"))!;
    expect(b.labelKey).toBe("office.badge.UNPAID");
    expect(b.outstanding).toEqual(eur("67.50"));
  });
  it("shows deposit with the balance, and paid in full without one", () => {
    expect(officeBadge(office("NEW", "PARTIALLY_PAID"))!.labelKey).toBe("office.badge.PARTIALLY_PAID");
    const paid = officeBadge(office("NEW", "PAID"))!;
    expect(paid.labelKey).toBe("office.badge.PAID");
    expect(paid.outstanding).toBeNull();
  });
  it("flags money to return on a cancelled booking that holds cash", () => {
    const b = officeBadge(office("CANCELLED", "PARTIALLY_PAID", { cashToReturn: eur("20.00") }))!;
    expect(b.labelKey).toBe("office.badge.cashToReturn");
    expect(b.cashToReturn).toEqual(eur("20.00"));
    expect(officeBadge(office("CANCELLED", "UNPAID"))!.labelKey).toBe("office.badge.cancelled");
  });
  it("run sheet: part- or fully-paid office bookings are live, unpaid ones need the toggle", () => {
    expect(isLive(office("NEW", "UNPAID"), false)).toBe(false);
    expect(isLive(office("NEW", "UNPAID"), true)).toBe(true);
    expect(isLive(office("NEW", "PARTIALLY_PAID"), false)).toBe(true);
    expect(isUnpaid(office("NEW", "PARTIALLY_PAID"))).toBe(true);
    expect(isUnpaid(office("NEW", "PAID"))).toBe(false);
  });
});

describe("office messages exist in both languages", () => {
  it("maps every server code to a bilingual message", () => {
    for (const code of ["slot_in_past", "fx_rate_not_set", "reference_already_used", "amount_exceeds_outstanding", "booking_not_open_status_cancelled", "idempotency_key_reused"]) {
      const d = officeErrorMessage(new ToursApiError(409, code));
      expect(en[d.key as ErpMessageKey], code).toBeTruthy();
      expect(ar[d.key as ErpMessageKey], code).toBeTruthy();
    }
    expect(officeErrorMessage(new ToursApiError(403, "x")).key).toBe("booking.forbidden");
  });
});
