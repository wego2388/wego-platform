import { erpIntlLocale, formatErpCount, formatErpDate, formatErpMoney } from "./erpLocale";
import type { DocumentLanguage } from "./documentMessages";
import type { Money } from "@wego/api-contract";

export function docDir(lang: DocumentLanguage): "rtl" | "ltr" {
  return lang === "ar" ? "rtl" : "ltr";
}

export const docMoney = (money: Money, lang: DocumentLanguage) => formatErpMoney(money, lang);
export const docCount = (value: number, lang: DocumentLanguage) => formatErpCount(value, lang);
export const docDate = (day: string, lang: DocumentLanguage, long = false) => formatErpDate(day, lang, long);

/** Operations run on Cairo time, so every printed date-time is shown in it whatever the browser's zone is. */
export function docInstant(value: string, lang: DocumentLanguage): string {
  const instant = new Date(value);
  if (!Number.isFinite(instant.getTime())) return "—";
  return new Intl.DateTimeFormat(erpIntlLocale(lang), { dateStyle: "medium", timeStyle: "short", timeZone: "Africa/Cairo" }).format(instant);
}

export function docDay(value: string, lang: DocumentLanguage): string {
  const instant = new Date(value);
  if (!Number.isFinite(instant.getTime())) return "—";
  return new Intl.DateTimeFormat(erpIntlLocale(lang), { dateStyle: "medium", timeZone: "Africa/Cairo" }).format(instant);
}

/** The tour name in the document language, falling back to the English name. */
export function docTourName(doc: { tourNameEn: string; tourNameAr: string | null }, lang: DocumentLanguage): string {
  return lang === "ar" && doc.tourNameAr ? doc.tourNameAr : doc.tourNameEn;
}

export type DocumentKind = "voucher" | "receipt" | "cancellation" | "run-sheet" | "pickup";
export const DOCUMENT_KINDS: readonly DocumentKind[] = ["voucher", "receipt", "cancellation", "run-sheet", "pickup"];

export const DOCUMENT_PERMISSION: Record<DocumentKind, string> = {
  voucher: "tours-operator.document:print",
  receipt: "tours-operator.document:print",
  cancellation: "tours-operator.document:print",
  "run-sheet": "tours-operator.document:print-ops",
  pickup: "tours-operator.document:print-ops",
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ISO_DAY = /^\d{4}-\d{2}-\d{2}$/;

/** A document link carries only an internal id (or a date for the run sheet), never customer data. */
export function isValidSubject(kind: DocumentKind, id: string): boolean {
  return kind === "run-sheet" ? ISO_DAY.test(id) : UUID.test(id);
}

export function documentPath(kind: DocumentKind, id: string): string {
  return `/documents/${kind}/${id}`;
}

/** Customer paper never carries a staff email: only the local part's initials, e.g. mona.ali@… → MA. */
export function staffInitials(email: string | null | undefined): string {
  const local = (email ?? "").split("@")[0] ?? "";
  const letters = local.split(/[^\p{L}]+/u).filter(Boolean).slice(0, 3).map((part) => part.charAt(0).toUpperCase()).join("");
  return letters || "—";
}
