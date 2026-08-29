// Explicit imports only (no reliance on Nuxt auto-import) so this module
// behaves identically under the plain @vitejs/plugin-vue + happy-dom Vitest
// environment (no Nuxt runtime) and under the real Nuxt app — matching how
// login.vue already imports @wego/ui explicitly rather than auto-importing it.

export interface AuthSession {
  token: string;
  email: string;
  roles: string[];
  permissions: string[];
}

const STORAGE_KEY = "wego_auth_session";

/** `sessionStorage` (not `localStorage`): a bearer token should not outlive the browser tab. */
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
    // Corrupt/foreign value under our key — treat as no session rather than throwing.
    return null;
  }
}

export function writeAuthSession(session: AuthSession): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function clearAuthSession(): void {
  sessionStorage.removeItem(STORAGE_KEY);
}

export function hasPermission(session: AuthSession | null, permission: string): boolean {
  return session !== null && session.permissions.includes(permission);
}
