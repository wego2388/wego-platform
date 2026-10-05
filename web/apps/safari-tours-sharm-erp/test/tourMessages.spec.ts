import { describe, expect, it } from "vitest";
import { ToursApiError } from "../app/composables/useToursApi";
import { tourErrorMessage } from "../app/utils/tourMessages";
import { erpMessage } from "../app/utils/erpLocale";

describe("tour/slots errors keep their resource and follow the staff locale", () => {
  it.each([[401, "common.sessionExpired"], [403, "common.forbidden"], [404, "tours.notFound"]] as const)("maps status %s to %s", (status, key) => {
    const error = new ToursApiError(status, "fixture_code");
    expect(tourErrorMessage(error)).toEqual({ key });
    expect(error.status).toBe(status);
  });
  it("preserves unknown codes as escaped display parameters and hides private connection diagnostics", () => {
    const message = tourErrorMessage(new ToursApiError(409, "original_code"));
    expect(message).toEqual({ key: "common.requestFailed", params: { code: "original_code" } });
    expect(erpMessage("ar", message.key, message.params)).toContain("original_code");
    expect(tourErrorMessage(new Error("private diagnostic"))).toEqual({ key: "common.connectionFailed" });
  });
});
