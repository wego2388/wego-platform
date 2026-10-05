import { ToursApiError } from "../composables/useToursApi";
import type { ErpMessageDescriptor } from "./bookingMessages";

export function tourErrorMessage(error: unknown): ErpMessageDescriptor {
  if (!(error instanceof ToursApiError)) return { key: "common.connectionFailed" };
  if (error.status === 401) return { key: "common.sessionExpired" };
  if (error.status === 403) return { key: "common.forbidden" };
  if (error.status === 404) return { key: "tours.notFound" };
  return { key: "common.requestFailed", params: { code: error.errorCode } };
}
