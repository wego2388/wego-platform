// Explicit imports/exports only — see useAuthSession.ts for why (no Nuxt
// auto-import runtime in the Vitest environment).

export type PayrollRunStatus = "DRAFT" | "POSTED";

// Not exported bare — useDiversApi.ts already exports a `PAGE_SIZE` of the
// same value, and a bare name here would hit the same Nuxt composable
// auto-import collision documented in useHrApi.ts/useAccountingApi.ts.
export const PAYROLL_PAGE_SIZE = 50;

export interface PayrollLine {
  employeeId: string;
  amount: string;
}

export interface PayrollRun {
  id: string;
  payPeriodStart: string;
  payPeriodEnd: string;
  currencyCode: string;
  totalAmount: string;
  status: PayrollRunStatus;
  lines: PayrollLine[];
  createdByUserId?: string;
  createdAt: string;
  postedByUserId?: string;
  postedAt?: string;
  journalEntryId?: string;
}

export interface CreatePayrollRunBody {
  payPeriodStart: string;
  payPeriodEnd: string;
}

/** Carries the API's own `error` code (e.g. "overlaps_existing_run", "not_draft") so callers can show a specific message. */
export class PayrollApiError extends Error {
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
    throw new PayrollApiError(response.status, errorCode);
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export function listPayrollRuns(
  token: string,
  params: { status?: PayrollRunStatus; page?: number; size?: number } = {},
): Promise<PayrollRun[]> {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? PAYROLL_PAGE_SIZE));
  return request<PayrollRun[]>(`/api/v1/payroll/runs?${query.toString()}`, token);
}

export function createPayrollRun(token: string, body: CreatePayrollRunBody): Promise<PayrollRun> {
  return request<PayrollRun>("/api/v1/payroll/runs", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function postPayrollRun(token: string, id: string): Promise<PayrollRun> {
  return request<PayrollRun>(`/api/v1/payroll/runs/${id}/post`, token, { method: "POST" });
}

export async function discardPayrollRun(token: string, id: string): Promise<void> {
  await request<unknown>(`/api/v1/payroll/runs/${id}/discard`, token, { method: "POST" });
}
