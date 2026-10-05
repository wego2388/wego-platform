import type { Booking, Tour, TourSlot } from "@wego/api-contract";

/**
 * The day's run sheet: bookings grouped by tour and departure, with the
 * places each departure has sold. Pure so it can be unit-tested.
 */
export const SLOT_ORDER = ["SUNRISE", "MORNING", "AFTERNOON", "SUNSET"] as const;

export interface Departure {
  timeSlot: Booking["timeSlot"];
  slot: TourSlot | null;
  bookings: Booking[];
  guests: number;
  /** Units sold per option code for per-unit tours, e.g. { buggy: 3 }. */
  units: Record<string, number>;
  unpaid: number;
}

export interface TourRun {
  tourId: string;
  tourName: string;
  departures: Departure[];
  guests: number;
}

/** Bookings that still matter on the day: paid, completed or awaiting payment. */
export function isLive(booking: Booking, includeUnpaid: boolean): boolean {
  if (booking.status === "CONFIRMED" || booking.status === "COMPLETED") return true;
  // An office booking is live on the day once money has been taken (deposit or full); until then it is "unpaid".
  if (booking.status === "NEW" && booking.channel === "OFFICE" && booking.officePayment && booking.officePayment.state !== "UNPAID") return true;
  return includeUnpaid && booking.status === "NEW";
}

/** Still owes money on the day: NEW online (awaiting payment) or an office booking not paid in full. */
export function isUnpaid(booking: Booking): boolean {
  if (booking.status !== "NEW") return false;
  return booking.channel !== "OFFICE" || booking.officePayment?.state !== "PAID";
}

export function buildRunSheet(
  bookings: Booking[],
  tours: Record<string, Tour>,
  slots: Record<string, TourSlot[]>,
  includeUnpaid: boolean,
): TourRun[] {
  const byTour = new Map<string, Booking[]>();
  for (const booking of bookings) {
    if (!isLive(booking, includeUnpaid)) continue;
    const list = byTour.get(booking.tourId) ?? [];
    list.push(booking);
    byTour.set(booking.tourId, list);
  }
  const runs: TourRun[] = [];
  for (const [tourId, list] of byTour) {
    const tour = tours[tourId];
    const departures: Departure[] = [];
    for (const timeSlot of SLOT_ORDER) {
      const inSlot = list
        .filter((b) => b.timeSlot === timeSlot)
        .sort((a, b) => a.hotelName.localeCompare(b.hotelName) || a.reference.localeCompare(b.reference));
      if (!inSlot.length) continue;
      const units: Record<string, number> = {};
      for (const b of inSlot) if (b.unit) units[b.unit.optionCode] = (units[b.unit.optionCode] ?? 0) + b.unit.unitCount;
      departures.push({
        timeSlot,
        slot: (slots[tourId] ?? []).find((s) => s.timeSlot === timeSlot) ?? null,
        bookings: inSlot,
        guests: inSlot.reduce((sum, b) => sum + b.adultsCount + b.childrenCount, 0),
        units,
        unpaid: inSlot.filter(isUnpaid).length,
      });
    }
    runs.push({
      tourId,
      tourName: tour?.nameEn ?? tour?.slug ?? tourId,
      departures,
      guests: departures.reduce((sum, d) => sum + d.guests, 0),
    });
  }
  return runs.sort((a, b) => (tours[a.tourId]?.sortOrder ?? 0) - (tours[b.tourId]?.sortOrder ?? 0) || a.tourName.localeCompare(b.tourName));
}
