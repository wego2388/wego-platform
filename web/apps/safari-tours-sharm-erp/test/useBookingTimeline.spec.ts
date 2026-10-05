import { describe, expect, it } from "vitest";
import { mergeTimeline, paymentActor } from "../app/composables/useBookingTimeline";
import type { BookingHistoryEntry, PaymentHistoryEntry } from "@wego/api-contract";

describe("booking timeline", () => {
  it("interleaves booking and payment steps chronologically", () => {
    const items = mergeTimeline(
      [
        { eventType: "BOOKING_CREATED", occurredAt: "2026-10-01T08:00:00Z" },
        {
          eventType: "BOOKING_CANCELLED",
          fromStatus: "NEW",
          toStatus: "CANCELLED",
          reason: "Customer called",
          actorEmail: "staff@example.com",
          occurredAt: "2026-10-01T09:00:00Z",
        },
      ],
      [
        { paymentId: "p1", toStatus: "PENDING", occurredAt: "2026-10-01T08:01:00Z", recorded: true },
        { paymentId: "p1", fromStatus: "PENDING", toStatus: "FAILED", providerStatus: "DECLINED", occurredAt: "2026-10-01T08:05:00Z", recorded: true },
      ],
    );
    expect(items.map((item) => item.title)).toEqual([
      "Booking created",
      "Payment started",
      "Payment failed",
      "Booking cancelled",
    ]);
    expect(items[0]?.actor).toBe("Customer");
    expect(items[2]?.detail).toBe("Provider status: DECLINED");
    expect(items[3]?.actor).toBe("staff@example.com");
    expect(items[3]?.detail).toBe("Customer called");
  });

  it("labels system actions and keeps reconstructed payment steps visible", () => {
    const items = mergeTimeline(
      [{ eventType: "BOOKING_EXPIRED", occurredAt: "2026-10-01T09:00:00Z" }],
      [{ paymentId: "p1", toStatus: "PAID", occurredAt: "2026-10-01T08:00:00Z", recorded: false }],
    );
    expect(items[0]?.recorded).toBe(false);
    expect(items[1]?.actor).toBe("System");
  });

  it("returns an empty timeline when nothing happened", () => {
    expect(mergeTimeline([], [])).toEqual([]);
  });

  it("credits each payment step to whoever actually caused it", () => {
    const base = { paymentId: "p1", occurredAt: "2026-10-01T08:00:00Z", recorded: true };
    expect(paymentActor({ ...base, toStatus: "PENDING" })).toBe("Customer");
    expect(paymentActor({ ...base, toStatus: "FAILED", providerStatus: "EXPIRED" })).toBe("System");
    expect(paymentActor({ ...base, toStatus: "FAILED", providerStatus: "DECLINED" })).toBe("Paymob");
    expect(paymentActor({ ...base, toStatus: "RECONCILIATION_REQUIRED", providerStatus: "BOOKING_EXPIRED" })).toBe("System");
    expect(paymentActor({ ...base, toStatus: "REFUNDED", providerStatus: "REFUNDED" })).toBe("Paymob");
  });

  it("translates every lifecycle label but preserves amounts of history, order and source facts", () => {
    const events: BookingHistoryEntry["eventType"][] = ["BOOKING_CREATED", "BOOKING_CONFIRMED", "BOOKING_CANCELLED", "BOOKING_COMPLETED", "BOOKING_EXPIRED"];
    const statuses: PaymentHistoryEntry["toStatus"][] = ["PENDING", "PAID", "FAILED", "REFUNDED", "REVIEW_REQUIRED", "RECONCILIATION_REQUIRED"];
    const bookings = events.map((eventType) => ({ eventType, occurredAt: "2026-10-01T08:00:00Z" }));
    const payments = statuses.map((toStatus) => ({ paymentId: "p1", toStatus, occurredAt: "2026-10-01T09:00:00Z", recorded: false }));
    const english = mergeTimeline(bookings, payments);
    const arabic = mergeTimeline(bookings, payments, "ar");
    expect(arabic).toHaveLength(11);
    expect(arabic.map(({ key, occurredAt, source, recorded }) => ({ key, occurredAt, source, recorded })))
      .toEqual(english.map(({ key, occurredAt, source, recorded }) => ({ key, occurredAt, source, recorded })));
    expect(arabic.every((item, index) => item.title !== english[index]?.title && /[\u0600-\u06ff]/.test(item.title))).toBe(true);
  });

  it("preserves staff identities and free-text reasons rather than translating user data", () => {
    const bookings: BookingHistoryEntry[] = [{ eventType: "BOOKING_CANCELLED", actorEmail: "staff@example.com", reason: "Original reason <script>", occurredAt: "2026-10-01T09:00:00Z" }];
    const payments: PaymentHistoryEntry[] = [{ paymentId: "p1", toStatus: "REFUNDED", providerStatus: "PROVIDER_UNRECOGNISED_CODE", occurredAt: "2026-10-01T09:01:00Z", recorded: true }];
    const original = JSON.stringify([bookings, payments]);
    const items = mergeTimeline(bookings, payments, "ar");
    expect(items[0]?.actor).toBe("staff@example.com");
    expect(items[0]?.detail).toBe("Original reason <script>");
    expect(items[1]?.actor).toBe("Paymob");
    expect(items[1]?.detail).toBe("حالة المزوّد: PROVIDER_UNRECOGNISED_CODE");
    expect(JSON.stringify([bookings, payments])).toBe(original);
  });

  it("translates customer and system actors without pretending all callbacks were staff actions", () => {
    const base = { paymentId: "p1", occurredAt: "2026-10-01T08:00:00Z", recorded: true };
    expect(paymentActor({ ...base, toStatus: "PENDING" }, "ar")).toBe("العميل");
    expect(paymentActor({ ...base, toStatus: "FAILED", providerStatus: "EXPIRED" }, "ar")).toBe("النظام");
    expect(paymentActor({ ...base, toStatus: "RECONCILIATION_REQUIRED" }, "ar")).toBe("النظام");
  });
});
