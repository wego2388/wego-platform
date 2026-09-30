/**
 * Click-to-chat WhatsApp links for staff (owner decision 2026-09-30: no
 * WhatsApp Business API). Opens the staff member's own WhatsApp with a
 * prefilled message; nothing is sent automatically.
 *
 * DRAFT wording — awaiting owner approval, same as the email templates. It
 * states only booking facts.
 */
import type { Booking } from "@wego/api-contract";

const MESSAGES: Record<string, (b: Booking) => string> = {
  en: (b) => `Hello ${b.customer.fullName}, this is Safari Tours Sharm about your booking ${b.reference} on ${b.tourDate}.`,
  ar: (b) => `مرحبًا ${b.customer.fullName}، معك Safari Tours Sharm بخصوص حجزك رقم ${b.reference} بتاريخ ${b.tourDate}.`,
  ru: (b) => `Здравствуйте, ${b.customer.fullName}! Это Safari Tours Sharm по поводу вашего бронирования ${b.reference} на ${b.tourDate}.`,
  it: (b) => `Buongiorno ${b.customer.fullName}, siamo Safari Tours Sharm per la sua prenotazione ${b.reference} del ${b.tourDate}.`,
};

/**
 * wa.me wants the international number as digits only, without "+" or "00".
 * A local-format number ("010 …") has no country code and would open the
 * wrong chat, so it gets no link.
 */
export function whatsappNumber(phone: string): string | null {
  const trimmed = phone.trim();
  if (!trimmed.startsWith("+") && !trimmed.startsWith("00")) return null;
  const digits = trimmed.replace(/\D/g, "").replace(/^00/, "");
  return digits.length >= 8 && digits.length <= 15 ? digits : null;
}

export function whatsappLink(booking: Booking): string | null {
  const number = whatsappNumber(booking.customer.phone);
  if (!number) return null;
  const message = (MESSAGES[booking.locale] ?? MESSAGES.en!)(booking);
  return `https://wa.me/${number}?text=${encodeURIComponent(message)}`;
}
