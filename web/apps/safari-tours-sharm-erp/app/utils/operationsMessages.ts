import { ToursApiError, type CustomerNotification } from "../composables/useToursApi";
import type { ErpMessageDescriptor } from "./bookingMessages";

/** Keep errors as descriptors so changing language neither refetches nor retries. */
export function operationsErrorMessage(error: unknown): ErpMessageDescriptor {
  if (!(error instanceof ToursApiError)) return { key: "common.connectionFailed" };
  if (error.status === 401) return { key: "common.sessionExpired" };
  if (error.status === 403) return { key: "common.forbidden" };
  return { key: "common.requestFailed", params: { code: error.errorCode } };
}

export function notificationReasonMessage(code: CustomerNotification["lastError"]): ErpMessageDescriptor | null {
  switch (code) {
    case null: case undefined: return null;
    case "no_customer_email": return { key: "messages.noEmail" };
    case "review_link_not_configured": return { key: "messages.noReviewLink" };
    case "booking_missing": return { key: "messages.bookingMissing" };
    case "booking_state_changed": return { key: "messages.stateChanged" };
    case "tour_already_past": return { key: "messages.tourPast" };
    default: return { key: "messages.deliveryError", params: { code } };
  }
}

export function notificationResendErrorMessage(error: unknown): ErpMessageDescriptor {
  if (error instanceof ToursApiError && (error.status === 401 || error.status === 403)) return operationsErrorMessage(error);
  if (error instanceof ToursApiError && error.errorCode === "booking_state_changed") return { key: "messages.queueStateChanged" };
  return { key: "messages.queueUnknown" };
}

export function salesSaveErrorMessage(error: unknown): ErpMessageDescriptor {
  if (error instanceof ToursApiError && error.status === 401) return { key: "common.sessionExpired" };
  if (error instanceof ToursApiError && error.status === 403) return { key: "sales.forbidden" };
  // A missing response cannot prove a write was not committed. Never auto-retry.
  return { key: "sales.saveUnknown" };
}
