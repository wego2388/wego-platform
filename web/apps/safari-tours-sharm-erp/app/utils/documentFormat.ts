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

export type DocumentKind = "voucher" | "receipt" | "cancellation" | "run-sheet" | "pickup" | "driver-sheet" | "supplier-order" | "settlement-statement";
export const DOCUMENT_KINDS: readonly DocumentKind[] = ["voucher", "receipt", "cancellation", "run-sheet", "pickup", "driver-sheet", "supplier-order", "settlement-statement"];

export const DOCUMENT_PERMISSION: Record<DocumentKind, string> = {
  voucher: "tours-operator.document:print",
  receipt: "tours-operator.document:print",
  cancellation: "tours-operator.document:print",
  "run-sheet": "tours-operator.document:print-ops",
  pickup: "tours-operator.document:print-ops",
  "driver-sheet": "tours-operator.document:print-ops",
  "supplier-order": "tours-operator.document:print-ops",
  "settlement-statement": "tours-operator.settlement:pay",
};

/** The settlement statement prints with either settlement permission (manager or owner). */
export function canPrintDocument(kind: DocumentKind, has: (permission: string) => boolean): boolean {
  if (kind === "settlement-statement") return has("tours-operator.settlement:pay") || has("tours-operator.settlement:approve");
  return has(DOCUMENT_PERMISSION[kind] ?? "");
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ISO_DAY = /^\d{4}-\d{2}-\d{2}$/;

/** A document link carries only an internal id (or a date for the run sheet), never customer data. */
export function isValidSubject(kind: DocumentKind, id: string): boolean {
  if (kind === "run-sheet") return ISO_DAY.test(id);
  // A settlement statement is "<suppliers|drivers>_<party id>_<from>_<to>".
  if (kind === "settlement-statement") {
    const [party, partyId, from, to, ...rest] = id.split("_");
    return rest.length === 0 && (party === "suppliers" || party === "drivers") && UUID.test(partyId ?? "")
      && ISO_DAY.test(from ?? "") && ISO_DAY.test(to ?? "") && (to ?? "") >= (from ?? "");
  }
  // A supplier order is "<slot id>_<supplier id>".
  if (kind === "supplier-order") return id.split("_").length === 2 && id.split("_").every((part) => UUID.test(part));
  return UUID.test(id);
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
