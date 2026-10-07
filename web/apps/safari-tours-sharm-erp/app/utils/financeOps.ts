import type { PaidCurrency } from "@wego/api-contract";
import { ToursApiError, type PartyPath } from "../composables/useToursApi";
import type { ErpMessageDescriptor } from "./bookingMessages";
import type { ErpMessageKey } from "./erpLocale";

/**
 * Pure helpers for the OPS2-F pages. The server decides every money rule
 * (limits, balances, refundable, cash-box state); these only preview and
 * translate, never compute a figure that is saved.
 */

/** Owner-delegated default (hub, marked for review): a manager pays up to this many EGP per payment. */
export const MANAGER_LIMIT_EGP = "5000.00";

export function todayCairo(now: Date = new Date()): string {
  return now.toLocaleDateString("sv-SE", { timeZone: "Africa/Cairo" });
}

export function monthRange(now: Date = new Date()): { from: string; to: string } {
  const today = todayCairo(now);
  const [y, m] = today.split("-").map(Number) as [number, number];
  const last = new Date(Date.UTC(y, m, 0)).getUTCDate();
  return { from: `${today.slice(0, 7)}-01`, to: `${today.slice(0, 7)}-${String(last).padStart(2, "0")}` };
}

/** Exact cents from a typed amount ("12", "12.5", "12.50", Arabic-Indic digits too); null when invalid or not positive. */
export function toCents(raw: string): bigint | null {
  const ascii = raw.trim().replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660)).replace(/٫/g, ".");
  if (!/^\d{1,7}(\.\d{1,2})?$/.test(ascii)) return null;
  const [whole, fraction = ""] = ascii.split(".") as [string, string?];
  const cents = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0") || "0");
  return cents > 0n ? cents : null;
}

export function centsToText(cents: bigint): string {
  const negative = cents < 0n;
  const abs = negative ? -cents : cents;
  return `${negative ? "-" : ""}${abs / 100n}.${(abs % 100n).toString().padStart(2, "0")}`;
}

/**
 * Preview of the manager limit: EGP as typed, EUR at today's rate (rounded half-up to piastres).
 * Exactly 5000.00 EGP needs no approval; EUR without a rate always needs one (the server says the same).
 */
export function needsApproval(amount: string, currency: PaidCurrency, egpPerEur: string | null): boolean {
  const cents = toCents(amount);
  if (cents === null) return false;
  const limit = toCents(MANAGER_LIMIT_EGP)!;
  if (currency === "EGP") return cents > limit;
  if (!egpPerEur || !/^\d+(\.\d{1,4})?$/.test(egpPerEur)) return true;
  const [w, f = ""] = egpPerEur.split(".") as [string, string?];
  const rate10k = BigInt(w) * 10000n + BigInt(f.padEnd(4, "0") || "0");
  const egpCents = (cents * rate10k + 5000n) / 10000n;
  return egpCents > limit;
}

export function partyPath(type: "SUPPLIER" | "DRIVER"): PartyPath {
  return type === "SUPPLIER" ? "suppliers" : "drivers";
}

/** The print link subject of a statement: "<suppliers|drivers>_<id>_<from>_<to>" (ids and dates only). */
export function statementSubject(party: PartyPath, id: string, from: string, to: string): string {
  return `${party}_${id}_${from}_${to}`;
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const DAY = /^\d{4}-\d{2}-\d{2}$/;

export function parseStatementSubject(subject: string): { party: PartyPath; id: string; from: string; to: string } | null {
  const parts = subject.split("_");
  if (parts.length !== 4) return null;
  const [party, id, from, to] = parts as [string, string, string, string];
  if ((party !== "suppliers" && party !== "drivers") || !UUID.test(id) || !DAY.test(from) || !DAY.test(to) || to < from) return null;
  return { party, id, from, to };
}

const CODES = new Set<string>([
  "approval_required", "approval_mismatch", "approval_already_used", "approval_not_found", "amount_exceeds_balance",
  "amount_exceeds_refundable", "nothing_to_refund", "booking_not_cancelled", "cash_day_closed", "cash_day_not_counted",
  "cash_day_not_closed", "cash_day_in_future", "cash_expected_changed", "cannot_confirm_own_count", "cannot_reverse_own_refund",
  "entry_already_reversed", "refund_already_reversed", "reference_required", "reference_not_allowed", "fx_rate_not_set",
  "fx_rate_changed", "cost_already_ended", "effective_before_current", "basis_must_be_per_departure", "child_amount_per_person_only",
  "idempotency_key_reused",
]);

/** Plain-language message for an OPS2-F refusal, with the server's details (balance, refundable, expected) as parameters. */
export function financeErrorMessage(error: unknown): ErpMessageDescriptor {
  if (!(error instanceof ToursApiError)) return { key: "common.connectionFailed" };
  if (error.status === 401) return { key: "common.sessionExpired" };
  if (error.status === 403 && !CODES.has(error.errorCode)) return { key: "fops.err.forbidden" };
  const details = (error.body as { details?: Record<string, string> } | null)?.details ?? {};
  if (CODES.has(error.errorCode)) return { key: `fops.err.${error.errorCode}` as ErpMessageKey, params: details };
  if (error.status === 400 || error.errorCode === "validation_failed") return { key: "fops.err.invalid" };
  return { key: "fops.err.generic", params: { code: error.errorCode } };
}

export function newRequestId(): string {
  return globalThis.crypto.randomUUID();
}
