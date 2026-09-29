import type { components } from "./generated";

export type Money = components["schemas"]["Money"];
export type TourCategory = components["schemas"]["ToursOperatorCategory"];
export type TimeSlot = components["schemas"]["ToursOperatorTimeSlot"];
export type BookingStatus = components["schemas"]["ToursOperatorBookingStatus"];
export type Tour = components["schemas"]["ToursOperatorTourResponse"];
export type TourSlot = components["schemas"]["ToursOperatorSlotResponse"];
export type BookingCustomer = components["schemas"]["ToursOperatorCustomerResponse"];
export type CreateBookingPayload = components["schemas"]["CreateToursOperatorBookingRequest"];
export type Booking = components["schemas"]["ToursOperatorBookingResponse"];
export type PaymentLedgerEntry = components["schemas"]["ToursOperatorPaymentLedgerEntry"];
export type ToursOperatorPaymentStatus = components["schemas"]["ToursOperatorPaymentStatus"];

const DECIMAL_AMOUNT = /^(0|[1-9]\d{0,16})\.\d{2}$/;
const ISO_CURRENCY_CODE = /^[A-Z]{3}$/;

function assertMoney(money: Money): void {
  if (!DECIMAL_AMOUNT.test(money.amount)) {
    throw new Error(`Invalid contract money amount: ${money.amount}`);
  }
  if (!ISO_CURRENCY_CODE.test(money.currencyCode)) {
    throw new Error(`Invalid contract currency code: ${money.currencyCode}`);
  }
}

export function moneyToMinorUnits(money: Money): bigint {
  assertMoney(money);
  const [whole, fraction] = money.amount.split(".") as [string, string];
  return BigInt(whole) * 100n + BigInt(fraction);
}

export function minorUnitsToMoney(minorUnits: bigint, currencyCode = "EUR"): Money {
  if (minorUnits < 0n) throw new Error("Money must not be negative");
  const whole = minorUnits / 100n;
  const fraction = (minorUnits % 100n).toString().padStart(2, "0");
  const money = { amount: `${whole}.${fraction}`, currencyCode };
  assertMoney(money);
  return money;
}

export function addMoney(values: readonly Money[]): Money {
  if (values.length === 0) return minorUnitsToMoney(0n);
  const currencyCode = values[0]!.currencyCode;
  if (values.some((value) => value.currencyCode !== currencyCode)) {
    throw new Error("Cannot add money with different currencies");
  }
  return minorUnitsToMoney(
    values.reduce((total, value) => total + moneyToMinorUnits(value), 0n),
    currencyCode,
  );
}

export function multiplyMoney(money: Money, quantity: number): Money {
  if (!Number.isSafeInteger(quantity) || quantity < 0) {
    throw new Error("Money quantity must be a non-negative safe integer");
  }
  return minorUnitsToMoney(moneyToMinorUnits(money) * BigInt(quantity), money.currencyCode);
}

export function divideMoney(money: Money, divisor: number): Money {
  if (!Number.isSafeInteger(divisor) || divisor <= 0) {
    throw new Error("Money divisor must be a positive safe integer");
  }
  const units = moneyToMinorUnits(money);
  const divisorUnits = BigInt(divisor);
  return minorUnitsToMoney((units + divisorUnits / 2n) / divisorUnits, money.currencyCode);
}

export function calculateBookingTotal(
  adultPrice: Money,
  adultsCount: number,
  childPrice: Money | null,
  childrenCount: number,
): Money {
  if (childrenCount > 0 && childPrice === null) {
    throw new Error("A child price is required when children are included");
  }
  return addMoney([
    multiplyMoney(adultPrice, adultsCount),
    ...(childPrice ? [multiplyMoney(childPrice, childrenCount)] : []),
  ]);
}

export function formatMoney(money: Money): string {
  assertMoney(money);
  const symbol = money.currencyCode === "EUR" ? "€" : `${money.currencyCode} `;
  return `${symbol}${money.amount.replace(/\.00$/, "")}`;
}
