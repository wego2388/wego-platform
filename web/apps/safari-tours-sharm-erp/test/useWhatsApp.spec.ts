import { describe, expect, it } from "vitest";
import type { Booking } from "@wego/api-contract";
import { whatsappLink, whatsappNumber } from "../app/composables/useWhatsApp";

const booking = (phone: string, locale = "en") =>
  ({
    reference: "STR-2027-0042",
    tourDate: "2027-07-01",
    locale,
    customer: { fullName: "Anna", phone, nationality: "IT", email: null },
  }) as unknown as Booking;

describe("whatsapp click-to-chat", () => {
  it("normalises international numbers to digits", () => {
    expect(whatsappNumber("+20 100 000 0777")).toBe("201000000777");
    expect(whatsappNumber("0039 333 1234567")).toBe("393331234567");
    expect(whatsappNumber("123")).toBeNull();
    // Local format has no country code: no link rather than a wrong chat.
    expect(whatsappNumber("010 1234 5678")).toBeNull();
  });

  it("prefills the message in the booking language", () => {
    const link = whatsappLink(booking("+393331234567", "it"))!;
    expect(link.startsWith("https://wa.me/393331234567?text=")).toBe(true);
    expect(decodeURIComponent(link.split("text=")[1]!)).toContain("prenotazione STR-2027-0042");
  });

  it("falls back to English and refuses unusable numbers", () => {
    expect(decodeURIComponent(whatsappLink(booking("+201000000777", "de"))!)).toContain("your booking STR-2027-0042");
    expect(whatsappLink(booking("n/a"))).toBeNull();
  });
});
