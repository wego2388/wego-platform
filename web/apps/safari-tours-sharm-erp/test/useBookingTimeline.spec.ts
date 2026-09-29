import { describe, expect, it } from "vitest";
import { mergeTimeline, paymentActor } from "../app/composables/useBookingTimeline";

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
});
