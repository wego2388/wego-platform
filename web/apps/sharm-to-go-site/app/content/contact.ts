// Sharm To Go's real customer contact channels, supplied by the owner
// (2026-09-20). Single source for every page — never hardcode these inline.
export const contact = {
  whatsappDigits: "201001413469",
  whatsappDisplay: "+20 10 0141 3469",
  email: "info@sharmtogo.com",
} as const;

export function whatsappLink(message: string): string {
  return `https://wa.me/${contact.whatsappDigits}?text=${encodeURIComponent(message)}`;
}

export function emailLink(subject: string): string {
  return `mailto:${contact.email}?subject=${encodeURIComponent(subject)}`;
}
