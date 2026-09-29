// Explicit imports only — no Nuxt auto-import, so this works identically
// in Vitest (happy-dom, no Nuxt runtime) and in the real app.
// Follows the exact same contract as erp/app/composables/useAuthSession.ts.

export interface AuthSession {
  token: string;
  email: string;
  roles: string[];
  permissions: string[];
}

const STORAGE_KEY = "wego_auth_session";

/** sessionStorage, not localStorage — bearer tokens must not outlive the tab. */
export function readAuthSession(): AuthSession | null {
  if (typeof sessionStorage === "undefined") return null;
  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<AuthSession>;
    if (typeof parsed.token !== "string" || !parsed.token) return null;
    return {
      token: parsed.token,
      email: typeof parsed.email === "string" ? parsed.email : "",
      roles: Array.isArray(parsed.roles) ? parsed.roles : [],
      permissions: Array.isArray(parsed.permissions) ? parsed.permissions : [],
    };
  } catch {
    return null;
  }
}

export function writeAuthSession(session: AuthSession): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function clearAuthSession(): void {
  sessionStorage.removeItem(STORAGE_KEY);
}

/** Revoke the opaque backend session before clearing this tab's bearer token. */
export async function logoutAuthSession(session: AuthSession | null): Promise<void> {
  if (!session?.token) {
    clearAuthSession();
    return;
  }

  const response = await fetch("/api/v1/identity/logout", {
    method: "POST",
    headers: { Authorization: `Bearer ${session.token}` },
  });
  if (!response.ok && response.status !== 401) {
    // Keep the local token when revocation could not be verified. Callers do
    // not navigate because this rejection propagates, so the failure cannot
    // be silently presented as a successful sign-out.
    throw new Error("Unable to revoke the staff session");
  }
  clearAuthSession();
}

export function hasPermission(session: AuthSession | null, permission: string): boolean {
  return session !== null && session.permissions.includes(permission);
}
