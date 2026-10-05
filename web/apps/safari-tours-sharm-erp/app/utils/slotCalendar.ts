/** Tour dates are calendar days, never local-midnight instants sent through UTC. */
function parseDay(day: string): Date {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) throw new RangeError("Invalid calendar day");
  const value = new Date(`${day}T00:00:00Z`);
  if (!Number.isFinite(value.getTime()) || value.toISOString().slice(0, 10) !== day) {
    throw new RangeError("Invalid calendar day");
  }
  return value;
}

/** Preserve the existing browser-local choice of today, without shifting its date. */
export function localCalendarDay(value: Date): string {
  if (!Number.isFinite(value.getTime())) throw new RangeError("Invalid date");
  return `${String(value.getFullYear()).padStart(4, "0")}-${String(value.getMonth() + 1).padStart(2, "0")}-${String(value.getDate()).padStart(2, "0")}`;
}

export function addCalendarDays(day: string, days: number): string {
  if (!Number.isInteger(days)) throw new RangeError("Calendar offset must be an integer");
  const value = parseDay(day);
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
}

export function mondayForDay(day: string): string {
  const weekday = parseDay(day).getUTCDay();
  return addCalendarDays(day, weekday === 0 ? -6 : 1 - weekday);
}

export function calendarWeek(monday: string): string[] {
  return Array.from({ length: 7 }, (_, index) => addCalendarDays(monday, index));
}
