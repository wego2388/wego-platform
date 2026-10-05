import { describe, expect, it } from "vitest";
import type { Booking } from "@wego/api-contract";
import { summarizeBookingCustomers } from "../app/utils/customerSummaries";
const booking = (id: string, amount: string, currencyCode = "EUR", status: Booking["status"] = "NEW") => ({
  id, customer: { fullName: "Original guest", phone: "+200000000000", email: null, nationality: "GB" },
  tourDate: "2026-10-05", totalPrice: { amount, currencyCode }, status,
}) as Booking;
describe("bounded booking-derived customer summaries", () => {
  it("uses exact booked value including unpaid/cancelled records, never a paid-spend metric", () => {
    const input = [booking("a", "90071992547409.91"), booking("b", "0.01", "EUR", "CANCELLED")];
    const before = JSON.stringify(input);
    const [contact] = summarizeBookingCustomers(input);
    expect(contact?.bookedValues).toEqual([{ amount: "90071992547409.92", currencyCode: "EUR" }]);
    expect(contact?.bookingCount).toBe(2);
    expect(contact).not.toHaveProperty("totalSpent");
    expect(JSON.stringify(input)).toBe(before);
  });
  it("does not sum or convert currencies", () => {
    expect(summarizeBookingCustomers([booking("a", "35.05"), booking("b", "200.05", "EGP")])[0]?.bookedValues).toEqual([
      { amount: "35.05", currencyCode: "EUR" }, { amount: "200.05", currencyCode: "EGP" },
    ]);
  });
  it("keeps the latest tour day, not a claimed creation/payment date", () => {
    const input = [booking("a", "35.05"), { ...booking("b", "35.05"), tourDate: "2026-11-01" }];
    expect(summarizeBookingCustomers(input)[0]?.latestTourDate).toBe("2026-11-01");
    expect(summarizeBookingCustomers([])).toEqual([]);
  });
  it("does not merge unrelated records with missing phone numbers", () => {
    const input = [booking("a", "35.05"), booking("b", "35.05")].map((b) => ({ ...b, customer: { ...b.customer, phone: "" } }));
    expect(summarizeBookingCustomers(input)).toHaveLength(2);
  });
});
