import { describe, expect, it } from "vitest";

import {
  addMoney,
  calculateBookingTotal,
  unitsNeeded,
  divideMoney,
  formatMoney,
  minorUnitsToMoney,
  moneyToMinorUnits,
  multiplyMoney,
} from "../src";

describe("OpenAPI money helpers", () => {
  it("keeps decimal money exact without JavaScript floating-point arithmetic", () => {
    expect(moneyToMinorUnits({ amount: "99999999.99", currencyCode: "EUR" })).toBe(9999999999n);
    expect(minorUnitsToMoney(3505n)).toEqual({ amount: "35.05", currencyCode: "EUR" });
  });

  it("adds and multiplies money in minor units", () => {
    const adult = { amount: "35.00", currencyCode: "EUR" };
    const child = { amount: "17.50", currencyCode: "EUR" };
    expect(addMoney([multiplyMoney(adult, 2), multiplyMoney(child, 1)])).toEqual({
      amount: "87.50",
      currencyCode: "EUR",
    });
  });

  it("rejects malformed or mixed-currency money", () => {
    expect(() => moneyToMinorUnits({ amount: "35.5", currencyCode: "EUR" })).toThrow();
    expect(() => moneyToMinorUnits({ amount: "35.50", currencyCode: "eur" })).toThrow();
    expect(() => addMoney([
      { amount: "1.00", currencyCode: "EUR" },
      { amount: "1.00", currencyCode: "USD" },
    ])).toThrow();
    expect(() => minorUnitsToMoney(10000000000000000000n)).toThrow();
  });

  it("formats EUR without coercing the amount to a number", () => {
    expect(formatMoney({ amount: "35.00", currencyCode: "EUR" })).toBe("€35");
    expect(formatMoney({ amount: "35.50", currencyCode: "EUR" })).toBe("€35.50");
  });

  it("calculates a booking total and rejects children without a child price", () => {
    const adult = { amount: "35.00", currencyCode: "EUR" };
    const child = { amount: "17.50", currencyCode: "EUR" };
    expect(calculateBookingTotal(adult, 2, child, 1)).toEqual({
      amount: "87.50",
      currencyCode: "EUR",
    });
    expect(() => calculateBookingTotal(adult, 1, null, 1)).toThrow();
  });

  it("rounds an average to the nearest minor unit", () => {
    expect(divideMoney({ amount: "10.01", currencyCode: "EUR" }, 2)).toEqual({
      amount: "5.01",
      currencyCode: "EUR",
    });
  });
});

describe("unitsNeeded", () => {
  it("rounds guests up to whole units", () => {
    expect(unitsNeeded(1, 2)).toBe(1);
    expect(unitsNeeded(3, 2)).toBe(2);
    expect(unitsNeeded(8, 8)).toBe(1);
    expect(unitsNeeded(9, 8)).toBe(2);
  });
  it("rejects impossible inputs", () => {
    expect(() => unitsNeeded(0, 2)).toThrow();
    expect(() => unitsNeeded(2, 0)).toThrow();
  });
});
