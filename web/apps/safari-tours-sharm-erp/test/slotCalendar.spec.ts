import { describe, expect, it } from "vitest";
import { addCalendarDays, calendarWeek, localCalendarDay, mondayForDay } from "../app/utils/slotCalendar";

describe("staff calendar uses date-only weeks, not midnight UTC conversions", () => {
  it.each(["2026-09-28", "2026-09-29", "2026-09-30", "2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04"])("%s belongs to the same Monday–Sunday week", (day) => {
    expect(mondayForDay(day)).toBe("2026-09-28");
    expect(calendarWeek(mondayForDay(day))).toEqual([
      "2026-09-28", "2026-09-29", "2026-09-30", "2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04",
    ]);
  });

  it.each([
    ["2026-12-28", 7, "2027-01-04"], ["2027-01-04", -7, "2026-12-28"],
    ["2028-02-28", 1, "2028-02-29"], ["2028-02-29", 1, "2028-03-01"],
    ["2026-04-20", 7, "2026-04-27"], ["2026-10-26", 7, "2026-11-02"],
    ["2026-03-02", 7, "2026-03-09"], ["2026-10-19", 7, "2026-10-26"],
  ] as const)("%s plus %s calendar days is %s across year/leap/DST boundaries", (day, offset, expected) => {
    expect(addCalendarDays(day, offset)).toBe(expected);
    expect(addCalendarDays(expected, -offset)).toBe(day);
  });

  it("extracts the browser's local day and does not mutate its date", () => {
    const value = new Date(2026, 8, 28, 0, 0, 0);
    const before = value.getTime();
    expect(localCalendarDay(value)).toBe("2026-09-28");
    expect(value.getTime()).toBe(before);
  });

  it.each(["", "bad", "2026-02-29", "2026-02-31", "2026-13-01", "2026-10-03T00:00:00Z"])("rejects malformed/impossible day %s instead of silently shifting it", (day) => {
    expect(() => mondayForDay(day)).toThrow(RangeError);
    expect(() => addCalendarDays(day, 7)).toThrow(RangeError);
  });

  it("rejects invalid instants and non-integer day offsets", () => {
    expect(() => localCalendarDay(new Date("bad"))).toThrow(RangeError);
    expect(() => addCalendarDays("2026-10-05", 0.5)).toThrow(RangeError);
    expect(() => addCalendarDays("2026-10-05", NaN)).toThrow(RangeError);
  });
});
