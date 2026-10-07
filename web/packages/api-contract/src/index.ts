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
export type BookingHistoryEntry = components["schemas"]["ToursOperatorBookingHistoryEntry"];
export type PaymentHistoryEntry = components["schemas"]["ToursOperatorPaymentHistoryEntry"];
export type CustomerNotification = components["schemas"]["ToursOperatorNotification"];
export type NotificationStatus = components["schemas"]["ToursOperatorNotificationStatus"];
export type SalesControl = components["schemas"]["ToursOperatorSalesControl"];
export type PublicSalesStatus = components["schemas"]["ToursOperatorPublicSalesStatus"];
export type ContentLocale = components["schemas"]["ToursOperatorContentLocale"];
export type PublicTourContent = components["schemas"]["ToursOperatorPublicTourContent"];
export type StaffTourContent = components["schemas"]["ToursOperatorStaffTourContent"];
export type TourContentDocument = components["schemas"]["ToursOperatorTourContentDocument"];
export type TourFactsDocument = components["schemas"]["ToursOperatorTourFactsDocument"];
export type TourMediaInput = components["schemas"]["ToursOperatorTourMediaInput"];
export type ContentErrorResponse = components["schemas"]["ToursOperatorContentErrorResponse"];
export type PublishRequest = components["schemas"]["ToursOperatorPublishRequest"];
export type MediaUploadResponse = components["schemas"]["ToursOperatorMediaUploadResponse"];
export type CategoryMedia = components["schemas"]["ToursOperatorCategoryMedia"];
export type PublicCategoryCover = components["schemas"]["ToursOperatorPublicCategoryCover"];
export type TourPriceOption = components["schemas"]["ToursOperatorPriceOption"];
export type BookingUnit = components["schemas"]["ToursOperatorBookingUnit"];
export type CreateOfficeBookingPayload = components["schemas"]["CreateToursOperatorOfficeBookingRequest"];
export type BookingChannel = components["schemas"]["ToursOperatorBookingChannel"];
export type OfficePayment = components["schemas"]["ToursOperatorOfficePayment"];
export type OfficePaymentState = components["schemas"]["ToursOperatorOfficePaymentState"];
export type OfficeCollection = components["schemas"]["ToursOperatorOfficeCollection"];
export type OfficeCollectionOutcome = components["schemas"]["ToursOperatorOfficeCollectionOutcome"];
export type OfficeCollectionQuote = components["schemas"]["ToursOperatorOfficeCollectionQuote"];
export type CollectionMethod = components["schemas"]["ToursOperatorCollectionMethod"];
export type PaidCurrency = components["schemas"]["ToursOperatorPaidCurrency"];
export type FxRate = components["schemas"]["ToursOperatorFxRate"];
export type FxRateToday = components["schemas"]["ToursOperatorFxRateToday"];
export type DocumentStamp = components["schemas"]["ToursOperatorDocumentStamp"];
export type VoucherDocument = components["schemas"]["ToursOperatorVoucherDocument"];
export type ReceiptDocument = components["schemas"]["ToursOperatorReceiptDocument"];
export type RunSheetDocument = components["schemas"]["ToursOperatorRunSheetDocument"];
export type Supplier = components["schemas"]["ToursOperatorSupplier"];
export type SupplierRequest = components["schemas"]["ToursOperatorSupplierRequest"];
export type Driver = components["schemas"]["ToursOperatorDriver"];
export type DriverRequest = components["schemas"]["ToursOperatorDriverRequest"];
export type Vehicle = components["schemas"]["ToursOperatorVehicle"];
export type VehicleRequest = components["schemas"]["ToursOperatorVehicleRequest"];
export type AssignmentView = components["schemas"]["ToursOperatorAssignmentView"];
export type AssignmentIssue = components["schemas"]["ToursOperatorAssignmentIssue"];
export type AssignmentOptions = components["schemas"]["ToursOperatorAssignmentOptions"];
export type AssignmentRequest = components["schemas"]["ToursOperatorAssignmentRequest"];
export type DriverSheetDocument = components["schemas"]["ToursOperatorDriverSheetDocument"];
export type SupplierOrderDocument = components["schemas"]["ToursOperatorSupplierOrderDocument"];
export type PickupManifestDocument = components["schemas"]["ToursOperatorPickupManifestDocument"];
export type CancellationFormDocument = components["schemas"]["ToursOperatorCancellationFormDocument"];
export type FinanceAmount = components["schemas"]["ToursOperatorAmount"];
export type FinanceError = components["schemas"]["ToursOperatorFinanceError"];
export type CostCategory = components["schemas"]["ToursOperatorCostCategory"];
export type CostBasis = components["schemas"]["ToursOperatorCostBasis"];
export type CostComponent = components["schemas"]["ToursOperatorCostComponent"];
export type CostComponentRequest = components["schemas"]["ToursOperatorCostComponentRequest"];
export type ProfitTotals = components["schemas"]["ToursOperatorProfitTotals"];
export type ProfitGroup = components["schemas"]["ToursOperatorProfitGroup"];
export type ProfitReport = components["schemas"]["ToursOperatorProfitReport"];
export type OfficeSummary = components["schemas"]["ToursOperatorOfficeSummary"];
export type RefundMethod = components["schemas"]["ToursOperatorRefundMethod"];
export type OfficeRefund = components["schemas"]["ToursOperatorOfficeRefund"];
export type OfficeRefunds = components["schemas"]["ToursOperatorOfficeRefunds"];
export type OfficeRefundOutcome = components["schemas"]["ToursOperatorOfficeRefundOutcome"];
export type RefundPosition = components["schemas"]["ToursOperatorRefundPosition"];
export type PartyType = components["schemas"]["ToursOperatorPartyType"];
export type CurrencyBalance = components["schemas"]["ToursOperatorCurrencyBalance"];
export type PartySummary = components["schemas"]["ToursOperatorPartySummary"];
export type PartyStatement = components["schemas"]["ToursOperatorPartyStatement"];
export type StatementMovement = components["schemas"]["ToursOperatorStatementMovement"];
export type StatementIssue = components["schemas"]["ToursOperatorStatementIssue"];
export type SettlementPayment = components["schemas"]["ToursOperatorSettlementPayment"];
export type SettlementApproval = components["schemas"]["ToursOperatorSettlementApproval"];
export type PayableAdjustment = components["schemas"]["ToursOperatorPayableAdjustment"];
export type SettlementMethod = components["schemas"]["ToursOperatorSettlementMethod"];
export type CashDay = components["schemas"]["ToursOperatorCashDay"];
export type CashBoxEvent = components["schemas"]["ToursOperatorCashBoxEvent"];
export type SettlementStatementDocument = components["schemas"]["ToursOperatorSettlementStatementDocument"];

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

/** Smallest number of units that seats every guest (e.g. 3 guests → 2 two-seat buggies). */
export function unitsNeeded(guests: number, seatsPerUnit: number): number {
  if (!Number.isSafeInteger(guests) || guests < 1) throw new Error("At least one guest is required");
  if (!Number.isSafeInteger(seatsPerUnit) || seatsPerUnit < 1) throw new Error("A unit seats at least one guest");
  return Math.ceil(guests / seatsPerUnit);
}

export function formatMoney(money: Money): string {
  assertMoney(money);
  const symbol = money.currencyCode === "EUR" ? "€" : `${money.currencyCode} `;
  return `${symbol}${money.amount.replace(/\.00$/, "")}`;
}
