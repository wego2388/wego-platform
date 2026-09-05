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

export type AttendanceStatus = "PRESENT" | "ABSENT" | "LATE" | "HALF_DAY";

export interface AttendanceRecord {
  id: string;
  employeeId: string;
  attendanceDate: string;
  status: AttendanceStatus;
  clockIn?: string;
  clockOut?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface RecordAttendanceBody {
  employeeId: string;
  attendanceDate: string;
  status: AttendanceStatus;
  clockIn?: string;
  clockOut?: string;
  notes?: string;
}

/** Recording again for the same employee/date corrects that day's record, so this always returns 200 — never a 201. */
export function recordAttendance(token: string, body: RecordAttendanceBody): Promise<AttendanceRecord> {
  return request<AttendanceRecord>("/api/v1/hr/attendance", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function listAttendance(
  token: string,
  params: { employeeId?: string; from?: string; to?: string; page?: number; size?: number } = {},
): Promise<AttendanceRecord[]> {
  const query = new URLSearchParams();
  if (params.employeeId) query.set("employeeId", params.employeeId);
  if (params.from) query.set("from", params.from);
  if (params.to) query.set("to", params.to);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? HR_PAGE_SIZE));
  return request<AttendanceRecord[]>(`/api/v1/hr/attendance?${query.toString()}`, token);
}

export type LeaveType = "ANNUAL" | "SICK" | "UNPAID" | "OTHER";
export type LeaveRequestStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";

export interface LeaveRequest {
  id: string;
  employeeId: string;
  leaveType: LeaveType;
  startDate: string;
  endDate: string;
  reason?: string;
  status: LeaveRequestStatus;
  requestedByUserId?: string;
  requestedAt: string;
  decidedByUserId?: string;
  decidedAt?: string;
  decisionNotes?: string;
  cancelledAt?: string;
}

export interface SubmitLeaveRequestBody {
  employeeId: string;
  leaveType: LeaveType;
  startDate: string;
  endDate: string;
  reason?: string;
}

export function submitLeaveRequest(token: string, body: SubmitLeaveRequestBody): Promise<LeaveRequest> {
  return request<LeaveRequest>("/api/v1/hr/leave-requests", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function listLeaveRequests(
  token: string,
  params: { employeeId?: string; status?: LeaveRequestStatus; page?: number; size?: number } = {},
): Promise<LeaveRequest[]> {
  const query = new URLSearchParams();
  if (params.employeeId) query.set("employeeId", params.employeeId);
  if (params.status) query.set("status", params.status);
  query.set("page", String(params.page ?? 0));
  query.set("size", String(params.size ?? HR_PAGE_SIZE));
  return request<LeaveRequest[]>(`/api/v1/hr/leave-requests?${query.toString()}`, token);
}

export function approveLeaveRequest(token: string, id: string, notes?: string): Promise<LeaveRequest> {
  return request<LeaveRequest>(`/api/v1/hr/leave-requests/${id}/approve`, token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ notes: notes || undefined }),
  });
}

export function rejectLeaveRequest(token: string, id: string, notes?: string): Promise<LeaveRequest> {
  return request<LeaveRequest>(`/api/v1/hr/leave-requests/${id}/reject`, token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ notes: notes || undefined }),
  });
}

export function cancelLeaveRequest(token: string, id: string): Promise<LeaveRequest> {
  return request<LeaveRequest>(`/api/v1/hr/leave-requests/${id}/cancel`, token, { method: "POST" });
}
