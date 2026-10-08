import { describe, expect, it } from "vitest";
import { discoveryCopy } from "../app/content/discovery";
import { infoCopy } from "../app/content/info";
import { discoveryForSales, infoForSales, officeCopy } from "../app/content/salesAwareCopy";

describe("marketing promises match effective sales capability", () => {
  for (const locale of ["en", "ar", "ru", "it"] as const) {
    it(`${locale}: request/unknown/paused modes never advertise active Paymob or reserved dates`, () => {
      const original = JSON.stringify({ discovery: discoveryCopy[locale], info: infoCopy[locale] });
      for (const status of [null, undefined,
        { bookingMode: "ENQUIRY_ONLY", bookingsOpen: false, paymentsOpen: false } as const,
        { bookingMode: "ONLINE_PAYMENT", bookingsOpen: false, paymentsOpen: true } as const,
        { bookingMode: "ONLINE_PAYMENT", bookingsOpen: true, paymentsOpen: false } as const,
      ]) {
        const discovery = discoveryForSales(locale, status), info = infoForSales(locale, status);
        expect(discovery.hero.body).toBe(officeCopy[locale].hero);
        expect(JSON.stringify(discovery.why)).not.toContain("Paymob");
        expect(discovery.faq.items[1]!.body).toBe(officeCopy[locale].confirmation);
        expect(info.faq.items[1]!.body).toBe(officeCopy[locale].booking);
        expect(info.faq.items[2]!.body).toBe(officeCopy[locale].availability);
        expect(info.faq.items[4]!.body).toBe(officeCopy[locale].confirmation);
        expect(JSON.stringify(info.terms)).not.toContain("Paymob");
        expect(info.privacy.sections[2]!.body).toEqual([officeCopy[locale].privacy]);
        expect(info.terms.sections[3]).toEqual(infoCopy[locale].terms.sections[3]);
      }
      expect(JSON.stringify({ discovery: discoveryCopy[locale], info: infoCopy[locale] })).toBe(original);
    });
    it(`${locale}: explicitly enabled online sales preserve existing checkout information`, () => {
      const online = { bookingMode: "ONLINE_PAYMENT", bookingsOpen: true, paymentsOpen: true } as const;
      expect(discoveryForSales(locale, online)).toBe(discoveryCopy[locale]);
      expect(infoForSales(locale, online)).toBe(infoCopy[locale]);
    });
  }
});
