import { ToursApiError } from "../composables/useToursApi";
import type { ErpMessageKey } from "./erpLocale";

/** Store a message descriptor, not rendered text, so an existing error follows locale changes. */
export interface ErpMessageDescriptor {
  key: ErpMessageKey;
  params?: Record<string, string | number>;
}

export function bookingErrorMessage(error: unknown): ErpMessageDescriptor {
  if (!(error instanceof ToursApiError)) return { key: "common.connectionFailed" };
  if (error.status === 401) return { key: "booking.sessionExpired" };
  if (error.status === 403) return { key: "booking.forbidden" };
  if (error.status === 404) return { key: "booking.notFound" };
  const codes: Record<string, ErpMessageKey> = {
    slot_fully_booked: "booking.slotFull",
    already_confirmed: "booking.alreadyConfirmed",
    already_cancelled: "booking.alreadyCancelled",
    invalid_transition: "booking.invalidTransition",
  };
  const key = Object.hasOwn(codes, error.errorCode) ? codes[error.errorCode] : undefined;
  return key ? { key } : { key: "common.requestFailed", params: { code: error.errorCode } };
}
