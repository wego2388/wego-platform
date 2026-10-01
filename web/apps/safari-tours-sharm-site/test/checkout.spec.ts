import { describe, expect, it } from "vitest";
import { bookingResultCopy, buildCalendarFile } from "../app/content/bookingResult";
import { myBookingCopy } from "../app/content/myBooking";
import { ALL_NATIONALITIES, COMMON_NATIONALITIES, checkoutCopy, isPlausibleEmail, isPlausiblePhone, normalizePhone } from "../app/content/checkout";

describe("checkout validation", () => {
  it("accepts international phone numbers with common separators", () => {
    expect(isPlausiblePhone("+44 7700 900123")).toBe(true);
    expect(isPlausiblePhone("+20 (100) 123-4567")).toBe(true);
    expect(isPlausiblePhone("01001234567")).toBe(true);
  });

  it("rejects text, too short and too long numbers", () => {
    expect(isPlausiblePhone("call me")).toBe(false);
    expect(isPlausiblePhone("+20 123")).toBe(false);
    expect(isPlausiblePhone("+1234567890123456")).toBe(false);
  });

  it("checks email shape only loosely", () => {
    expect(isPlausibleEmail("guest@example.co.uk")).toBe(true);
    expect(isPlausibleEmail("guest@example")).toBe(false);
    expect(isPlausibleEmail("guest example.com")).toBe(false);
  });

  it("lists every common nationality among all codes, without duplicates", () => {
    expect(new Set(ALL_NATIONALITIES).size).toBe(ALL_NATIONALITIES.length);
    for (const code of COMMON_NATIONALITIES) expect(ALL_NATIONALITIES).toContain(code);
  });
});

describe("checkout copy", () => {
  it("has the same shape in every language", () => {
    const shape = (value: unknown): unknown =>
      Array.isArray(value) ? value.length : value && typeof value === "object"
        ? Object.fromEntries(Object.entries(value).map(([k, v]) => [k, shape(v)]))
        : typeof value;
    for (const locale of ["ar", "ru", "it"] as const) expect(shape(checkoutCopy[locale])).toEqual(shape(checkoutCopy.en));
  });

  it("keeps the English labels the end-to-end checkout test clicks", () => {
    expect(checkoutCopy.en.details.continue).toMatch(/Continue/);
    expect(checkoutCopy.en.review.pay).toBe("Confirm & Pay");
  });
});


describe("booking result and my-booking copy", () => {
  const shape = (value: unknown): unknown =>
    typeof value === "function" ? "fn" : Array.isArray(value) ? value.length : value && typeof value === "object"
      ? Object.fromEntries(Object.entries(value).map(([k, v]) => [k, shape(v)]))
      : typeof value;

  it("has the same shape in every language", () => {
    for (const locale of ["ar", "ru", "it"] as const) {
      expect(shape(bookingResultCopy[locale])).toEqual(shape(bookingResultCopy.en));
      expect(shape(myBookingCopy[locale])).toEqual(shape(myBookingCopy.en));
    }
  });

  it("keeps the English strings the end-to-end test looks for", () => {
    expect(bookingResultCopy.en.confirmed.title).toContain("Booking Confirmed");
    expect(bookingResultCopy.en.unconfirmed.title).toBe("Payment is not confirmed");
  });
});

describe("calendar file", () => {
  it("is an all-day RFC 5545 event on the tour date with escaped text", () => {
    const ics = buildCalendarFile({ uid: "STR-2026-1", date: "2026-12-31", title: "Quad, sunset; fun", description: "Ref: STR-2026-1", now: new Date("2026-10-01T10:00:00Z") });
    expect(ics).toContain("DTSTART;VALUE=DATE:20261231\r\n");
    expect(ics).toContain("DTEND;VALUE=DATE:20270101\r\n");
    expect(ics).toContain(String.raw`SUMMARY:Quad\, sunset\; fun` + "\r\n");
    expect(ics).toContain("DTSTAMP:20261001T100000Z\r\n");
    expect(ics.startsWith("BEGIN:VCALENDAR\r\n")).toBe(true);
  });
});


describe("phone normalisation", () => {
  it("stores and looks up one form however the guest types it", () => {
    expect(normalizePhone(" +20 (100) 123-4567 ")).toBe("+201001234567");
    expect(normalizePhone("0020 100 123 4567")).toBe("+201001234567");
    expect(normalizePhone("01001234567")).toBe("01001234567");
  });
});

describe("calendar line folding", () => {
  it("folds lines longer than 75 octets without splitting characters", () => {
    const ics = buildCalendarFile({ uid: "STR-2026-2", date: "2026-11-01", title: "رحلة سفاري سوبر في صحراء سيناء مع عشاء بدوي وعرض تنورة", description: "x" });
    for (const line of ics.split("\r\n")) expect(new TextEncoder().encode(line).length).toBeLessThanOrEqual(75);
    expect(ics).toContain("\r\n ");
  });
});
