import { describe, expect, it } from "vitest";
import {
  ar, en, erpMessage, formatErpCount, formatErpDate, formatErpMoney, formatErpInstant, formatErpSignedMoney,
  isLocalizedErpRoute, resolveErpLocale, type ErpMessageKey,
} from "../app/utils/erpLocale";

describe("ERP language contract", () => {
  it("has non-empty Arabic equivalents with matching interpolation fields for every English key", () => {
    expect(Object.keys(ar).sort()).toEqual(Object.keys(en).sort());
    for (const key of Object.keys(en) as ErpMessageKey[]) {
      expect(ar[key].trim(), key).not.toBe("");
      expect(ar[key].match(/\{\w+\}/g)?.sort() ?? [], key).toEqual(en[key].match(/\{\w+\}/g)?.sort() ?? []);
    }
  });

  it("falls back to English for an unsupported, malformed or missing preference", () => {
    for (const value of [null, undefined, "ru", "AR", {}, "<script>"]) expect(resolveErpLocale(value)).toBe("en");
    expect(resolveErpLocale("ar")).toBe("ar");
  });

  it("marks only translated routes ready rather than implying the full ERP is translated", () => {
    for (const route of ["/", "/login", "/today", "/today/", "/bookings", "/bookings/fixture-id", "/bookings/fixture-id/", "/tours", "/categories", "/categories/", "/tours/id/slots", "/tours/id/slots/", "/tours/id/content", "/tours/id/content/", "/notifications", "/reviews", "/sales", "/settings", "/customers", "/finance", "/staff"]) expect(isLocalizedErpRoute(route)).toBe(true);
    for (const route of ["/today-other", "/bookings-other", "/bookings/fixture-id/other", "/tours/id", "/tours/id/slots/other", "/settings/other", "/reviews-other", "/staff/other", "/finance/other"]) expect(isLocalizedErpRoute(route)).toBe(false);
  });

  it("interpolates supplied values as text and cannot use inherited properties as values", () => {
    expect(erpMessage("ar", "common.party", { adults: "٢", children: "١" })).toBe("البالغون: ٢ · الأطفال: ١");
    expect(erpMessage("en", "common.room", { room: "<b>12</b>" })).toBe("Room <b>12</b>");
    expect(erpMessage("en", "common.room", Object.create({ room: "inherited" }))).toBe("Room {room}");
  });
});

describe("localized display preserves commercial facts", () => {
  it("preserves signed finance amounts and cents in both languages", () => {
    const money = { amount: "-90071992547409.91", currencyCode: "EUR" };
    expect(formatErpSignedMoney(money, "en")).toBe("-€90,071,992,547,409.91");
    expect(formatErpSignedMoney(money, "ar")).toContain("-‏٩٠٬٠٧١٬٩٩٢٬٥٤٧٬٤٠٩٫٩١");
    expect(money.amount).toBe("-90071992547409.91");
    expect(() => formatErpSignedMoney({ ...money, amount: "--1.00" }, "en")).toThrow();
  });
  it("formats the same exact amount and currency in each locale", () => {
    const money = { amount: "1234.05", currencyCode: "EUR" };
    expect(formatErpMoney(money, "en")).toBe("€1,234.05");
    expect(formatErpMoney(money, "ar")).toContain("١٬٢٣٤٫٠٥");
    expect(formatErpMoney(money, "ar")).toContain("€");
    expect(money.amount).toBe("1234.05");
    expect(money.currencyCode).toBe("EUR");
  });

  it("preserves cents even beyond the floating-point integer precision boundary", () => {
    expect(formatErpMoney({ amount: "90071992547409.91", currencyCode: "EUR" }, "en")).toBe("€90,071,992,547,409.91");
    expect(formatErpMoney({ amount: "0.01", currencyCode: "EUR" }, "ar")).toContain("٠٫٠١");
  });

  it("rejects invalid money rather than inventing a zero value", () => {
    expect(() => formatErpMoney({ amount: "bad", currencyCode: "EUR" }, "en")).toThrow();
  });

  it("uses the tour's date-only day without a timezone shift", () => {
    expect(formatErpDate("2026-10-03", "en")).toBe("3 Oct 2026");
    expect(formatErpDate("2026-10-03", "ar")).toContain("٣");
    expect(formatErpDate("2026-10-03", "ar", true)).toContain("السبت");
  });

  it("does not roll impossible calendar days into a different tour date", () => {
    for (const day of ["", "2026-02-31", "2026-13-01", "not-a-date", "2026-10-03T00:00:00Z"]) {
      expect(formatErpDate(day, "ar")).toBe("—");
    }
    expect(formatErpCount(1234, "ar")).toBe("١٬٢٣٤");
  });

  it("formats equivalent timezone-aware timestamps as the same instant without changing the input", () => {
    const value = "2026-10-03T10:00:00Z";
    expect(formatErpInstant(value, "en")).toBe(formatErpInstant("2026-10-03T12:00:00+02:00", "en"));
    expect(formatErpInstant(value, "ar")).toMatch(/[٠-٩]/);
    expect(formatErpInstant(value, "en")).not.toBe(formatErpInstant(value, "ar"));
    expect(value).toBe("2026-10-03T10:00:00Z");
  });

  it("never treats a date-only, ambiguous local timestamp or impossible day as a durable instant", () => {
    for (const value of ["", "not-an-instant", "2026-10-03", "2026-10-03T10:00:00", "2026-02-31T10:00:00Z", "2026-10-03T26:00:00Z"]) {
      expect(formatErpInstant(value, "en")).toBe("—");
    }
  });
});
