// Explicit imports/exports only — see useAuthSession.ts for why (no Nuxt
// auto-import runtime in the Vitest environment).

// Not exported: `Money` is already a top-level export of useDiversApi.ts,
// and Nuxt's composable auto-import registrar silently drops one of two
// same-named exports across files — same collision class as the backend's
// JooqStaffUserLookup bean-name clash, avoided here the same way: keep the
// duplicate module-local instead of sharing the name across composables.
interface HrMoney {
  amount: string;
  currencyCode: string;
}

export type EmployeeStatus = "ACTIVE" | "TERMINATED";

// Exported under an HR-prefixed name — useDiversApi.ts already exports a
// `PAGE_SIZE` of the same value, and a bare `PAGE_SIZE` here would hit the
// same auto-import collision documented above for HrMoney.
export const HR_PAGE_SIZE = 50;

/**
 * The roster/list projection returned by GET /hr/employees — deliberately
 * omits salary, email and phone (the same PII-minimization discipline
 * `DiverSummaryResponse`, products/divers, already established for a bulk
 * read under one broad `hr:employee-view` permission). Fetch a single
 * employee by id (getEmployee) for the full record.
 */
export interface EmployeeSummary {
  id: string;
  fullName: string;
  position: string;
  department?: string;
  status: EmployeeStatus;
}

export interface Employee {
  id: string;
  fullName: string;
  position: string;
  department?: string;
  hireDate: string;
  email?: string;
  phone?: string;
  baseSalary?: HrMoney;
  linkedUserId?: string;
  status: EmployeeStatus;
  createdAt: string;
  terminatedAt?: string;
}

export interface UpsertEmployeeBody {
  fullName: string;
  position: string;
  department?: string;
  hireDate: string;
  email?: string;
  phone?: string;
  baseSalary?: HrMoney;
  linkedUserId?: string;
}

/** Carries the API's own `error` code (e.g. "already_terminated") so callers can show a specific message. */
export class HrApiError extends Error {
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
    throw new HrApiError(response.status, errorCode);
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export function listEmployees(
  token: string,
  params: { status?: EmployeeStatus; search?: string; page?: number; size?: number } = {},
): Promise<EmployeeSummary[]> {
  const query = new URLSearchParams();
  if (params.status) query.set("status", params.status);
  if (params.search) query.set("search", params.search);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? HR_PAGE_SIZE));
  return request<EmployeeSummary[]>(`/api/v1/hr/employees?${query.toString()}`, token);
}

export function getEmployee(token: string, id: string): Promise<Employee> {
  return request<Employee>(`/api/v1/hr/employees/${id}`, token);
}

export function createEmployee(token: string, body: UpsertEmployeeBody): Promise<Employee> {
  return request<Employee>("/api/v1/hr/employees", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function updateEmployee(token: string, id: string, body: UpsertEmployeeBody): Promise<Employee> {
  return request<Employee>(`/api/v1/hr/employees/${id}`, token, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function terminateEmployee(token: string, id: string, reason?: string): Promise<Employee> {
  return request<Employee>(`/api/v1/hr/employees/${id}/terminate`, token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason: reason || undefined }),
  });
}
