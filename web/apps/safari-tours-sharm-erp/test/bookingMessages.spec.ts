import { describe, expect, it } from "vitest";
import { ToursApiError } from "../app/composables/useToursApi";
import { bookingErrorMessage } from "../app/utils/bookingMessages";
import { erpMessage } from "../app/utils/erpLocale";

describe("booking presentation errors", () => {
  it.each([
    [401, "anything", "booking.sessionExpired"],
    [403, "anything", "booking.forbidden"],
    [404, "anything", "booking.notFound"],
    [409, "slot_fully_booked", "booking.slotFull"],
    [409, "already_confirmed", "booking.alreadyConfirmed"],
    [409, "already_cancelled", "booking.alreadyCancelled"],
    [409, "invalid_transition", "booking.invalidTransition"],
  ] as const)("maps HTTP %s / %s without changing the API error", (status, code, key) => {
    const error = new ToursApiError(status, code);
    expect(bookingErrorMessage(error)).toEqual({ key });
    expect(error.status).toBe(status);
    expect(error.errorCode).toBe(code);
  });

  it("keeps unknown error codes as plain display parameters, not inherited dictionary properties", () => {
    for (const code of ["future_code", "constructor", "__proto__", "<script>"]) {
      const message = bookingErrorMessage(new ToursApiError(400, code));
      expect(message).toEqual({ key: "common.requestFailed", params: { code } });
      expect(erpMessage("en", message.key, message.params)).toBe(`Request failed (${code}).`);
      expect(erpMessage("ar", message.key, message.params)).toContain(code);
    }
  });

  it("lets an existing error follow language changes without another request", () => {
    const message = bookingErrorMessage(new ToursApiError(403, "forbidden"));
    expect(erpMessage("en", message.key, message.params)).toBe("You don't have permission for this action.");
    expect(erpMessage("ar", message.key, message.params)).toBe("ليس لديك صلاحية لتنفيذ هذا الإجراء.");
    expect(bookingErrorMessage(new Error("private diagnostic"))).toEqual({ key: "common.connectionFailed" });
  });
});
