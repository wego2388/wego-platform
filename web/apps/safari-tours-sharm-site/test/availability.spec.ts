import { describe, expect, it } from "vitest";
import type { TourSlot } from "@wego/api-contract";
import { addDays, addMonths, bookableByDay, firstDayOfWeek, monthGrid, operatorToday } from "../app/utils/availability";
import { tourPageCopy } from "../app/content/tourPage";

function slot(date: string, timeSlot: TourSlot["timeSlot"], available = 5, isBlocked = false): TourSlot {
  return { id: `${date}-${timeSlot}`, tourId: "t", date, timeSlot, capacity: 10, bookedCount: 10 - available, available, isBlocked };
}

describe("availability calendar helpers", () => {
  it("uses the operator's day, not the visitor's", () => {
    // 23:30 UTC on 30 Sep is already 1 Oct in Cairo (UTC+3 in summer time).
    expect(operatorToday(new Date("2026-09-30T23:30:00Z"))).toBe("2026-10-01");
  });

  it("does date arithmetic on calendar days", () => {
    expect(addDays("2026-10-30", 3)).toBe("2026-11-02");
    expect(addDays("2026-12-31", 60)).toBe("2027-03-01");
    expect(addMonths("2026-12", 1)).toBe("2027-01");
    expect(addMonths("2026-01", -1)).toBe("2025-12");
  });

  it("lays out a month from the locale's first weekday", () => {
    // 1 Oct 2026 is a Thursday.
    const sunday = monthGrid("2026-10", 0);
    expect(sunday[0]).toEqual([null, null, null, null, "2026-10-01", "2026-10-02", "2026-10-03"]);
    const saturday = monthGrid("2026-10", firstDayOfWeek("ar"));
    expect(saturday[0]!.indexOf("2026-10-01")).toBe(5);
    expect(sunday.flat().filter(Boolean)).toHaveLength(31);
    expect(sunday.every((week) => week.length === 7)).toBe(true);
  });

  it("keeps only open, future departures, in time-of-day order", () => {
    const byDay = bookableByDay(
      [
        slot("2026-09-30", "MORNING"),
        slot("2026-10-02", "SUNSET"),
        slot("2026-10-02", "MORNING"),
        slot("2026-10-03", "MORNING", 0),
        slot("2026-10-04", "MORNING", 5, true),
      ],
      "2026-10-01",
    );
    expect([...byDay.keys()]).toEqual(["2026-10-02"]);
    expect(byDay.get("2026-10-02")!.map((s) => s.timeSlot)).toEqual(["MORNING", "SUNSET"]);
  });

  it("hides departures without room for a whole unit", () => {
    const slots = [slot("2026-10-02", "MORNING", 4), slot("2026-10-03", "MORNING", 5)];
    expect([...bookableByDay(slots, "2026-10-01", 5).keys()]).toEqual(["2026-10-03"]);
  });
});

describe("tour page copy", () => {
  it("has the same shape in every language", () => {
    const shape = (value: unknown): unknown =>
      typeof value === "function" ? "fn" : Array.isArray(value) ? value.length : value && typeof value === "object"
        ? Object.fromEntries(Object.entries(value).map(([k, v]) => [k, shape(v)]))
        : typeof value;
    for (const locale of ["ar", "ru", "it"] as const) expect(shape(tourPageCopy[locale])).toEqual(shape(tourPageCopy.en));
  });

  it("states the standard cancellation policy exactly", () => {
    expect(tourPageCopy.en.policy.STANDARD.join(" ")).toMatch(/48 hours.*50%.*24–48 hours.*No refund/);
  });
});
