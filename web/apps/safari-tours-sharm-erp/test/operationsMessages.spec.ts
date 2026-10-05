import { describe, expect, it } from "vitest";
import { ToursApiError } from "../app/composables/useToursApi";
import { erpMessage } from "../app/utils/erpLocale";
import { notificationReasonMessage, notificationResendErrorMessage, operationsErrorMessage, salesSaveErrorMessage } from "../app/utils/operationsMessages";

describe("operations display contracts", () => {
  it.each([401, 403, 404, 500])("keeps HTTP %s as a locale-reactive, resource-neutral error", (status) => {
    const message = operationsErrorMessage(new ToursApiError(status, "original_failure"));
    expect(erpMessage("en", message.key, message.params)).not.toBe(erpMessage("ar", message.key, message.params));
    expect(message.key).not.toBe("tours.notFound");
    if (status >= 404) expect(message.params).toEqual({ code: "original_failure" });
  });
  it.each(["no_customer_email", "review_link_not_configured", "booking_missing", "booking_state_changed", "tour_already_past"])("translates dispatcher reason %s", (code) => {
    const message = notificationReasonMessage(code)!;
    expect(message.key).not.toBe("messages.deliveryError");
    expect(erpMessage("ar", message.key)).not.toContain(code);
  });
  it("passes unknown reasons as text, not executable markup or inherited keys", () => {
    expect(notificationReasonMessage(null)).toBeNull();
    expect(notificationReasonMessage(undefined)).toBeNull();
    for (const code of ["__proto__", "constructor", "<img src=x onerror=alert(1)>"]) {
      expect(notificationReasonMessage(code)).toEqual({ key: "messages.deliveryError", params: { code } });
    }
  });
  it.each([new Error("offline"), new ToursApiError(500, "failure"), new ToursApiError(409, "unknown_conflict")])("does not infer that an ambiguous write made no change", (error) => {
    expect(salesSaveErrorMessage(error)).toEqual({ key: "sales.saveUnknown" });
    expect(notificationResendErrorMessage(error)).toEqual({ key: "messages.queueUnknown" });
  });
  it("keeps definitive forbidden and booking-state-change responses distinct", () => {
    expect(salesSaveErrorMessage(new ToursApiError(403, "forbidden"))).toEqual({ key: "sales.forbidden" });
    expect(notificationResendErrorMessage(new ToursApiError(409, "booking_state_changed"))).toEqual({ key: "messages.queueStateChanged" });
    expect(notificationResendErrorMessage(new ToursApiError(401, "unauthorized"))).toEqual({ key: "common.sessionExpired" });
    expect(notificationResendErrorMessage(new ToursApiError(403, "forbidden"))).toEqual({ key: "common.forbidden" });
    expect(salesSaveErrorMessage(new ToursApiError(401, "unauthorized"))).toEqual({ key: "common.sessionExpired" });
  });
});
