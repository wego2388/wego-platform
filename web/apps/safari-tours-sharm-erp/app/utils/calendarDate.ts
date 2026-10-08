/** A date is a Gregorian calendar day, not an instant. No UTC/local conversion. */
export function isCalendarDate(value: string): boolean {
  if (!/^[1-9]\d{3}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

export function operatorCalendarDay(value = new Date()): string {
  return value.toLocaleDateString("sv-SE", { timeZone: "Africa/Cairo" });
}

export function asciiDigits(value: string): string {
  return value.replace(/[٠-٩۰-۹]/g, (digit) => String(digit.charCodeAt(0) - (digit >= "۰" ? 0x6f0 : 0x660)));
}

export function dateFromParts(day: string, month: string, year: string): string {
  const y = asciiDigits(year.trim());
  if (!/^[1-9]\d{3}$/.test(y) || !/^(?:[1-9]|[12]\d|3[01])$/.test(day) || !/^(?:[1-9]|1[0-2])$/.test(month)) return "";
  const value = `${y}-${month.padStart(2, "0")}-${day.padStart(2, "0")}`;
  return isCalendarDate(value) ? value : "";
}
