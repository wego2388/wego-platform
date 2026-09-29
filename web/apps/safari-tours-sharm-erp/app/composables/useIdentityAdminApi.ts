// Explicit imports/exports only — see useAuthSession.ts for why (no Nuxt
// auto-import runtime in the Vitest environment).

export type UserStatus = "ACTIVE" | "DISABLED";

export interface StaffUser {
  id: string;
  email: string;
  status: UserStatus;
  roles: string[];
  createdAt: string;
}

export interface Role {
  code: string;
  description: string;
  permissions: string[];
}

export interface Permission {
  code: string;
  description: string;
}

/** Carries the API's own `error` code (e.g. "email_already_in_use") so callers can show a specific message. */
export class IdentityAdminApiError extends Error {
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
    throw new IdentityAdminApiError(response.status, errorCode);
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export function listUsers(token: string): Promise<StaffUser[]> {
  return request<StaffUser[]>("/api/v1/identity/users", token);
}

export function createUser(
  token: string,
  input: { email: string; password: string; roleCodes: string[] },
): Promise<StaffUser> {
  return request<StaffUser>("/api/v1/identity/users", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

export function disableUser(token: string, id: string): Promise<StaffUser> {
  return request<StaffUser>(`/api/v1/identity/users/${id}/disable`, token, { method: "POST" });
}

export function enableUser(token: string, id: string): Promise<StaffUser> {
  return request<StaffUser>(`/api/v1/identity/users/${id}/enable`, token, { method: "POST" });
}

export function resetUserPassword(token: string, id: string, newPassword: string): Promise<StaffUser> {
  return request<StaffUser>(`/api/v1/identity/users/${id}/reset-password`, token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ newPassword }),
  });
}

export function assignUserRoles(token: string, id: string, roleCodes: string[]): Promise<StaffUser> {
  return request<StaffUser>(`/api/v1/identity/users/${id}/roles`, token, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ roleCodes }),
  });
}

export function listRoles(token: string): Promise<Role[]> {
  return request<Role[]>("/api/v1/identity/roles", token);
}

export function listPermissions(token: string): Promise<Permission[]> {
  return request<Permission[]>("/api/v1/identity/permissions", token);
}

export function createRole(
  token: string,
  input: { code: string; description: string; permissionCodes: string[] },
): Promise<Role> {
  return request<Role>("/api/v1/identity/roles", token, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
}

export function updateRolePermissions(token: string, code: string, permissionCodes: string[]): Promise<Role> {
  return request<Role>(`/api/v1/identity/roles/${code}/permissions`, token, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ permissionCodes }),
  });
}
