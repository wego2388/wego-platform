import { describe, expect, it } from "vitest";
import type { Tour } from "@wego/api-contract";
import { enquiryCopy } from "../app/content/enquiry";
import { onlineSalesAvailable, readSalesCapability, tripEnquiryUrl } from "../app/utils/enquiry";

const tour = { slug: "desert-buggy", priceBasis: "PER_UNIT", availableTimeSlots: ["MORNING", "SUNSET"], priceOptions: [{ code: "buggy" }] } as Tour;

describe("enquiry mode — factual selection only", () => {
  it("rejects malformed or inconsistent status without echoing unrelated fields", () => {
    for (const value of [null, {}, { bookingMode: "legacy", bookingsOpen: true, paymentsOpen: true },
      { bookingMode: ["ONLINE_PAYMENT"], bookingsOpen: true, paymentsOpen: true },
      { bookingMode: ["ENQUIRY_ONLY"], bookingsOpen: false, paymentsOpen: false },
      { bookingMode: "ONLINE_PAYMENT", bookingsOpen: "true", paymentsOpen: true },
      { bookingMode: "ENQUIRY_ONLY", bookingsOpen: true, paymentsOpen: true }]) expect(readSalesCapability(value)).toBeNull();
    expect(readSalesCapability({ bookingMode: "ENQUIRY_ONLY", bookingsOpen: false, paymentsOpen: false, reason: "private note" }))
      .toEqual({ bookingMode: "ENQUIRY_ONLY", bookingsOpen: false, paymentsOpen: false });
  });
  it("requires explicit online capability AND both effective sales flags", () => {
    expect(onlineSalesAvailable(null)).toBe(false);
    for (const bookingMode of ["ENQUIRY_ONLY", "ONLINE_PAYMENT"] as const) {
      for (const bookingsOpen of [false, true]) for (const paymentsOpen of [false, true]) {
        expect(onlineSalesAvailable({ bookingMode, bookingsOpen, paymentsOpen })).toBe(bookingMode === "ONLINE_PAYMENT" && bookingsOpen && paymentsOpen);
      }
    }
    expect(onlineSalesAvailable({ bookingsOpen: true, paymentsOpen: true } as never)).toBe(false);
  });

  for (const locale of ["en", "ar", "ru", "it"] as const) {
    it(`${locale}: only approved selection enters WhatsApp, never price payment or customer facts`, () => {
      const selected = {
        date: "2026-10-20", timeSlot: "MORNING" as const, adults: 2, children: 1, optionCode: "buggy", units: 2,
        // Extra caller fields are ignored even at runtime, not just in TypeScript.
        fullName: "Sensitive Guest", email: "sensitive@example.com", phone: "+201999999999", token: "sensitive-token", hotel: "Sensitive hotel",
      };
      const url = new URL(tripEnquiryUrl(locale, tour, selected));
      expect(url.origin + url.pathname).toBe("https://wa.me/201111292690");
      expect([...url.searchParams.keys()]).toEqual(["text"]);
      const text = url.searchParams.get("text")!;
      expect(text).toContain(enquiryCopy[locale].message);
      expect(text).toContain("desert-buggy");
      expect(text).toContain("2026-10-20");
      expect(text).toContain("MORNING");
      expect(text).toContain("buggy");
      expect(text).not.toMatch(/Sensitive|sensitive|1999999999|EUR|PAID|STR-/);
      expect(enquiryCopy[locale].notice).toBeTruthy();
    });
  }

  it("does not echo arbitrary query text, unknown options or invalid quantities", () => {
    const text = new URL(tripEnquiryUrl("en", tour, {
      date: "sensitive@example.com", timeSlot: "sensitive-name" as never, adults: Infinity, children: -1,
      optionCode: "sensitive-phone", units: 1000,
    })).searchParams.get("text")!;
    expect(text).not.toMatch(/sensitive|Infinity|1000/);
    expect(text).not.toContain("Preferred date:");
    expect(text).not.toContain("Preferred departure:");
    expect(text).not.toContain("Option:");
  });

  it("missing catalog data produces a generic request, not visitor-supplied tour data", () => {
    const url = new URL(tripEnquiryUrl("en", null, { date: "2026-10-20", adults: 2, children: 0 }));
    expect(url.searchParams.get("text")).toBe(enquiryCopy.en.message);
  });
});
