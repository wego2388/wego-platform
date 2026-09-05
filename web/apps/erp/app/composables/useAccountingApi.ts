// Explicit imports/exports only — see useAuthSession.ts for why (no Nuxt
// auto-import runtime in the Vitest environment).

export type AccountType = "ASSET" | "LIABILITY" | "EQUITY" | "REVENUE" | "EXPENSE";
export type JournalLineDirection = "DEBIT" | "CREDIT";

// Not exported bare — useDiversApi.ts already exports a `PAGE_SIZE` of the
// same value, and a bare name here would hit the same Nuxt composable
// auto-import collision documented in useHrApi.ts.
export const ACCOUNTING_PAGE_SIZE = 50;

export interface Account {
  id: string;
  code: string;
  name: string;
  accountType: AccountType;
  normalBalance: JournalLineDirection;
  parentAccountId?: string;
  description?: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAccountBody {
  code: string;
  name: string;
  accountType: AccountType;
  parentAccountId?: string;
  description?: string;
}

export interface UpdateAccountBody {
  name: string;
  description?: string;
}

export interface JournalLine {
  id: string;
  accountId: string;
  direction: JournalLineDirection;
  amount: string;
}

export interface JournalLineInput {
  accountId: string;
  direction: JournalLineDirection;
  amount: string;
}

export interface JournalEntry {
  id: string;
  entryDate: string;
  description: string;
  reference?: string;
  currencyCode: string;
  lines: JournalLine[];
  debitTotal: string;
  creditTotal: string;
  reversalOfEntryId?: string;
  postedByUserId?: string;
  postedAt: string;
}

export interface PostJournalEntryBody {
  entryDate: string;
  description: string;
  reference?: string;
  currencyCode: string;
  lines: JournalLineInput[];
}

/** Carries the API's own `error` code (e.g. "unbalanced", "already_reversed") so callers can show a specific message. */
export class AccountingApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly errorCode: string,
  ) {
    super(errorCode);
  }
}

async function request<T>(path: string, token: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { ...(init.headers ?? {}), Authorization: `Bearer ${token}` },
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    const errorCode = (body && typeof body === "object" && "error" in body ? String(body.error) : null) ?? `http_${response.status}`;
    throw new AccountingApiError(response.status, errorCode);
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export function listAccounts(
  token: string,
  params: { accountType?: AccountType; activeOnly?: boolean; search?: string; page?: number; size?: number } = {},
): Promise<Account[]> {
  const query = new URLSearchParams();
  if (params.accountType) query.set("accountType", params.accountType);
  query.set("activeOnly", String(params.activeOnly ?? true));
  if (params.search) query.set("search", params.search);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? ACCOUNTING_PAGE_SIZE));
  return request<Account[]>(`/api/v1/accounting/accounts?${query.toString()}`, token);
}

export function createAccount(token: string, body: CreateAccountBody): Promise<Account> {
  return request<Account>("/api/v1/accounting/accounts", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function updateAccount(token: string, id: string, body: UpdateAccountBody): Promise<Account> {
  return request<Account>(`/api/v1/accounting/accounts/${id}`, token, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function deactivateAccount(token: string, id: string): Promise<Account> {
  return request<Account>(`/api/v1/accounting/accounts/${id}/deactivate`, token, { method: "POST" });
}

export function reactivateAccount(token: string, id: string): Promise<Account> {
  return request<Account>(`/api/v1/accounting/accounts/${id}/reactivate`, token, { method: "POST" });
}

export function listJournalEntries(
  token: string,
  params: { from?: string; to?: string; accountId?: string; reference?: string; page?: number; size?: number } = {},
): Promise<JournalEntry[]> {
  const query = new URLSearchParams();
  if (params.from) query.set("from", params.from);
  if (params.to) query.set("to", params.to);
  if (params.accountId) query.set("accountId", params.accountId);
  if (params.reference) query.set("reference", params.reference);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? ACCOUNTING_PAGE_SIZE));
  return request<JournalEntry[]>(`/api/v1/accounting/journal-entries?${query.toString()}`, token);
}

export function postJournalEntry(token: string, body: PostJournalEntryBody): Promise<JournalEntry> {
  return request<JournalEntry>("/api/v1/accounting/journal-entries", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function reverseJournalEntry(token: string, id: string, reason: string): Promise<JournalEntry> {
  return request<JournalEntry>(`/api/v1/accounting/journal-entries/${id}/reverse`, token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason }),
  });
}

export interface TrialBalanceLine {
  accountId: string;
  code: string;
  name: string;
  accountType: AccountType;
  debitBalance: string;
  creditBalance: string;
}

export interface TrialBalance {
  asOfDate: string;
  lines: TrialBalanceLine[];
  totalDebits: string;
  totalCredits: string;
}

export function getTrialBalance(token: string, asOfDate: string): Promise<TrialBalance> {
  const query = new URLSearchParams({ asOfDate });
  return request<TrialBalance>(`/api/v1/accounting/reports/trial-balance?${query.toString()}`, token);
}

export interface IncomeStatementLine {
  accountId: string;
  code: string;
  name: string;
  amount: string;
}

export interface IncomeStatement {
  from: string;
  to: string;
  revenueLines: IncomeStatementLine[];
  expenseLines: IncomeStatementLine[];
  totalRevenue: string;
  totalExpenses: string;
  netIncome: string;
}

export function getIncomeStatement(token: string, from: string, to: string): Promise<IncomeStatement> {
  const query = new URLSearchParams({ from, to });
  return request<IncomeStatement>(`/api/v1/accounting/reports/income-statement?${query.toString()}`, token);
}

/** `accountId`/`code` are absent on exactly one line — the synthesized "Retained Earnings (accumulated)" figure, which has no backing account row. */
export interface BalanceSheetLine {
  accountId?: string;
  code?: string;
  name: string;
  amount: string;
}

export interface BalanceSheet {
  asOfDate: string;
  assetLines: BalanceSheetLine[];
  liabilityLines: BalanceSheetLine[];
  equityLines: BalanceSheetLine[];
  totalAssets: string;
  totalLiabilities: string;
  totalEquity: string;
}

export function getBalanceSheet(token: string, asOfDate: string): Promise<BalanceSheet> {
  const query = new URLSearchParams({ asOfDate });
  return request<BalanceSheet>(`/api/v1/accounting/reports/balance-sheet?${query.toString()}`, token);
}
