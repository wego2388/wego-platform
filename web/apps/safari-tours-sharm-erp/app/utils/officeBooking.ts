import { minorUnitsToMoney, moneyToMinorUnits, type Money } from "@wego/api-contract";
import type { Booking, CollectionMethod, OfficePayment, Tour } from "../composables/useToursApi";
import { ToursApiError } from "../composables/useToursApi";
import type { ErpMessageDescriptor } from "./bookingMessages";
import type { ErpMessageKey } from "./erpLocale";

/** Every method is entered by hand by staff; there is no online integration. */
export const COLLECTION_METHODS: readonly CollectionMethod[] = [
  "CASH_AT_OFFICE", "CASH_ON_PICKUP", "MOBILE_WALLET", "CARD_TERMINAL", "INSTAPAY", "FAWRY_OFFICE",
];

const CASH_METHODS: readonly CollectionMethod[] = ["CASH_AT_OFFICE", "CASH_ON_PICKUP"];
export const REFERENCE_MAX = 64;

/** Non-cash payments carry the receipt number from the terminal, wallet, InstaPay or Fawry. */
export function methodNeedsReference(method: CollectionMethod): boolean {
  return !CASH_METHODS.includes(method);
}

/** Same rules the server enforces: trimmed, 1–64 characters, no control characters. */
export function normalizeReference(raw: string): { ok: true; value: string } | { ok: false; key: ErpMessageKey } {
  const value = raw.trim();
  if (!value) return { ok: false, key: "office.collect.referenceRequired" };
  if (value.length > REFERENCE_MAX) return { ok: false, key: "office.collect.referenceTooLong" };
  // eslint-disable-next-line no-control-regex
  if (/[\u0000-\u001f\u007f-\u009f]/.test(value)) return { ok: false, key: "office.collect.referenceInvalid" };
  return { ok: true, value };
}

/** Accepts "12", "12.5" and "12.50" (also Arabic-Indic digits typed on an Arabic keyboard). Greater than zero, two decimals at most. */
export function parseAmount(raw: string): { amount: number; text: string } | null {
  const ascii = raw
    .trim()
    .replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660))
    .replace(/٫/g, ".");
  if (!/^\d{1,10}(\.\d{1,2})?$/.test(ascii)) return null;
  const amount = Number(ascii);
  if (!(amount > 0)) return null;
  return { amount, text: ascii };
}

export interface OfficeBadge {
  tone: "warning" | "info" | "success" | "danger";
  labelKey: ErpMessageKey;
  /** Shown beside the badge while money is still due. */
  outstanding: Money | null;
  collected: Money | null;
  /** A cancelled booking that still holds collected money staff must return. */
  cashToReturn: Money | null;
}

/** Null for online bookings: their badge is the ordinary booking status. */
export function officeBadge(booking: Pick<Booking, "channel" | "status" | "officePayment"> & { completedWithUnpaidBalance?: boolean }): OfficeBadge | null {
  if (booking.channel !== "OFFICE") return null;
  const pay: OfficePayment | null = booking.officePayment;
  if (!pay) return null;
  if (booking.status === "CANCELLED") {
    return {
      tone: pay.cashToReturn ? "danger" : "info",
      labelKey: pay.cashToReturn ? "office.badge.cashToReturn" : "office.badge.cancelled",
      outstanding: null, collected: pay.collected, cashToReturn: pay.cashToReturn,
    };
  }
  if (booking.status === "COMPLETED" && pay.state !== "PAID") {
    return { tone: "danger", labelKey: "office.badge.completedUnpaid", outstanding: pay.outstanding, collected: pay.collected, cashToReturn: null };
  }
  if (pay.state === "PAID") {
    return { tone: "success", labelKey: "office.badge.PAID", outstanding: null, collected: pay.collected, cashToReturn: null };
  }
  return {
    tone: pay.state === "PARTIALLY_PAID" ? "info" : "warning",
    labelKey: pay.state === "PARTIALLY_PAID" ? "office.badge.PARTIALLY_PAID" : "office.badge.UNPAID",
    outstanding: pay.outstanding, collected: pay.collected, cashToReturn: null,
  };
}

export interface PartyInput {
  adults: number;
  children: number;
  optionCode: string;
  unitCount: number;
}

export type Estimate =
  | { ok: true; total: Money; seats: number }
  | { ok: false; key: ErpMessageKey };

/**
 * A preview only: it mirrors the server rules so staff see the price while typing.
 * The server re-prices from its own catalogue and its answer is the one that is saved.
 */
export function estimateTotal(tour: Tour, party: PartyInput): Estimate {
  const guests = party.adults + party.children;
  if (!Number.isInteger(party.adults) || !Number.isInteger(party.children) || party.adults < 1 || party.children < 0) {
    return { ok: false, key: "office.new.errAdults" };
  }
  if (tour.priceBasis === "PER_UNIT") {
    const option = tour.priceOptions.find((o) => o.code === party.optionCode);
    if (!option) return { ok: false, key: "office.new.errOption" };
    if (!Number.isInteger(party.unitCount) || party.unitCount < 1) return { ok: false, key: "office.new.errUnits" };
    if (guests > party.unitCount * option.seatsPerUnit) return { ok: false, key: "office.new.errGuestsExceedUnits" };
    if (party.unitCount > guests) return { ok: false, key: "office.new.errUnitsExceedGuests" };
    const cents = moneyToMinorUnits(option.price) * BigInt(party.unitCount);
    return { ok: true, total: minorUnitsToMoney(cents, option.price.currencyCode), seats: party.unitCount * option.seatsPerUnit };
  }
  if (party.children > 0 && !tour.priceChild) return { ok: false, key: "office.new.errNoChildPrice" };
  const cents = moneyToMinorUnits(tour.priceAdult) * BigInt(party.adults)
    + (tour.priceChild ? moneyToMinorUnits(tour.priceChild) * BigInt(party.children) : 0n);
  return { ok: true, total: minorUnitsToMoney(cents, tour.priceAdult.currencyCode), seats: guests };
}

export interface OfficeFormValues {
  tourId: string;
  slotId: string;
  fullName: string;
  phone: string;
  nationality: string;
  email: string;
  hotelName: string;
}

/** Field-level validation; each message is shown next to its field. */
export function validateOfficeForm(form: OfficeFormValues): Partial<Record<keyof OfficeFormValues, ErpMessageKey>> {
  const errors: Partial<Record<keyof OfficeFormValues, ErpMessageKey>> = {};
  if (!form.tourId) errors.tourId = "office.new.errTour";
  if (!form.slotId) errors.slotId = "office.new.errSlot";
  if (!form.fullName.trim()) errors.fullName = "office.new.errName";
  if (!form.phone.trim()) errors.phone = "office.new.errPhone";
  if (!/^[A-Za-z]{2}$/.test(form.nationality.trim())) errors.nationality = "office.new.errNationality";
  if (form.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) errors.email = "office.new.errEmail";
  if (!form.hotelName.trim()) errors.hotelName = "office.new.errHotel";
  return errors;
}

/** Plain-language messages for the server's office-booking and office-payment error codes. */
export function officeErrorMessage(error: unknown): ErpMessageDescriptor {
  if (!(error instanceof ToursApiError)) return { key: "common.connectionFailed" };
  if (error.status === 401) return { key: "booking.sessionExpired" };
  if (error.status === 403) return { key: "booking.forbidden" };
  if (error.status === 404) return { key: "booking.notFound" };
  const codes: Record<string, ErpMessageKey> = {
    slot_blocked: "office.err.slot_blocked",
    slot_in_past: "office.err.slot_in_past",
    slot_fully_booked: "booking.slotFull",
    tour_not_active: "office.err.tour_not_active",
    price_option_required: "office.new.errOption",
    unit_count_required: "office.new.errUnits",
    guests_exceed_units: "office.new.errGuestsExceedUnits",
    units_exceed_guests: "office.new.errUnitsExceedGuests",
    price_option_not_applicable: "office.err.price_option_not_applicable",
    child_price_not_available: "office.new.errNoChildPrice",
    idempotency_key_reused: "office.err.idempotency_key_reused",
    not_an_office_booking: "office.err.not_an_office_booking",
    amount_exceeds_outstanding: "office.err.amount_exceeds_outstanding",
    reference_required: "office.collect.referenceRequired",
    reference_not_allowed: "office.err.reference_not_allowed",
    reference_already_used: "office.err.reference_already_used",
    fx_rate_not_set: "office.err.fx_rate_not_set",
    amount_below_minimum: "office.err.amount_below_minimum",
    fx_rate_changed: "office.err.fx_rate_changed",
    fx_rate_id_required: "office.err.fx_rate_changed",
    invalid_correction: "office.err.invalid_correction",
    cannot_reverse_own_collection: "office.err.cannot_reverse_own_collection",
    collection_already_reversed: "office.err.collection_already_reversed",
    collection_not_reversible: "office.err.collection_not_reversible",
    refunds_exceed_collected: "office.err.refunds_exceed_collected",
    cash_day_closed: "fops.err.cash_day_closed",
  };
  if (Object.hasOwn(codes, error.errorCode)) return { key: codes[error.errorCode]! };
  if (error.errorCode.startsWith("booking_not_open_status_")) return { key: "office.err.booking_not_open" };
  if (error.status === 400) return { key: "office.err.validation" };
  return { key: "common.requestFailed", params: { code: error.errorCode } };
}

/** Success screen check: the server's price is the truth; a difference from the preview must be visible. */
export function totalsDiffer(serverTotal: Money, estimate: Estimate | null): boolean {
  if (!estimate || !estimate.ok) return false;
  return moneyToMinorUnits(serverTotal) !== moneyToMinorUnits(estimate.total);
}
