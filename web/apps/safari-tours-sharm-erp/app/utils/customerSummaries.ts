import { addMoney, type Booking, type Money } from "@wego/api-contract";

export interface CustomerSummary {
  key: string;
  fullName: string;
  phone: string;
  nationality: string;
  email: string | null;
  bookingCount: number;
  latestTourDate: string;
  bookedValues: Money[];
}

/** A bounded contact grouping, not a person registry or a paid/lifetime metric. */
export function summarizeBookingCustomers(bookings: Booking[]): CustomerSummary[] {
  const contacts = new Map<string, CustomerSummary>();
  for (const booking of bookings) {
    const key = booking.customer.phone || booking.id;
    let contact = contacts.get(key);
    if (!contact) {
      contact = { key, ...booking.customer, bookingCount: 0, latestTourDate: booking.tourDate, bookedValues: [] };
      contacts.set(key, contact);
    }
    contact.bookingCount++;
    if (booking.tourDate > contact.latestTourDate) contact.latestTourDate = booking.tourDate;
    const index = contact.bookedValues.findIndex((value) => value.currencyCode === booking.totalPrice.currencyCode);
    if (index >= 0) contact.bookedValues[index] = addMoney([contact.bookedValues[index]!, booking.totalPrice]);
    else contact.bookedValues.push({ ...booking.totalPrice });
  }
  return [...contacts.values()].sort((a, b) => b.latestTourDate.localeCompare(a.latestTourDate));
}
