<script setup lang="ts">
import { ref } from "vue";
import { WegoAlert, WegoButton, WegoInput } from "@wego/ui";

useHead({ title: "Sign in · Wego Platform" });

type LoginState = "idle" | "submitting" | "error" | "success";

interface LoginResponse {
  token: string;
}

interface MeResponse {
  email: string;
  roles: string[];
  permissions: string[];
}

const email = ref("");
const password = ref("");
const state = ref<LoginState>("idle");
const errorMessage = ref("");
const retryAfterSeconds = ref<number | null>(null);
const token = ref("");
const authenticatedEmail = ref("");
const roles = ref<string[]>([]);
const permissions = ref<string[]>([]);
const logoutWarning = ref(false);

// Shared by logout() and submit()'s post-login /me failure path: a login
// response and its session both exist server-side the moment /login
// succeeds, before this tab has confirmed anything about it via /me. Any
// path that stops trusting a token from here on must try to revoke it
// server-side too, not just forget it locally — otherwise the session sits
// valid for its full lifetime with no way for this tab to cancel it.
async function revokeSessionBestEffort(rawToken: string): Promise<boolean> {
  if (!rawToken) return true;

  try {
    const response = await fetch("/api/v1/identity/logout", {
      method: "POST",
      headers: { Authorization: `Bearer ${rawToken}` },
    });
    return response.ok;
  } catch {
    return false;
  }
}

async function submit() {
  state.value = "submitting";
  errorMessage.value = "";
  retryAfterSeconds.value = null;
  logoutWarning.value = false;

  // Hoisted out of the try block: a session can exist server-side (login
  // already succeeded) before /me is ever reached, so the catch block below
  // needs to know whether there's a token to worry about too — not just the
  // explicit "meResponse.ok is false" branch inside the try.
  let issuedToken = "";

  try {
    const loginResponse = await fetch("/api/v1/identity/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: email.value, password: password.value }),
    });

    if (!loginResponse.ok) {
      const body = await loginResponse.json().catch(() => ({ error: "invalid_credentials" }));
      errorMessage.value = body.error ?? "invalid_credentials";
      // The wait time genuinely varies (the account throttle escalates it
      // per failure; nginx's own edge limit is fixed) — read the real
      // value rather than showing a message that implies retrying
      // immediately is reasonable when the server will reject it again.
      retryAfterSeconds.value =
        errorMessage.value === "rate_limited" ? Number(loginResponse.headers.get("Retry-After")) || null : null;
      state.value = "error";
      return;
    }

    const loginBody = (await loginResponse.json()) as LoginResponse;
    issuedToken = loginBody.token;

    const meResponse = await fetch("/api/v1/identity/me", {
      headers: { Authorization: `Bearer ${issuedToken}` },
    });

    if (!meResponse.ok) {
      // A session now exists server-side even though this tab never got to
      // confirm it. Best-effort cancel it immediately rather than leaving it
      // valid for its full lifetime with no way for this tab to revoke it —
      // and don't keep it in `token`, so a retry can't accidentally reuse an
      // orphaned session instead of asking for a fresh one.
      const revoked = await revokeSessionBestEffort(issuedToken);
      logoutWarning.value = !revoked;
      errorMessage.value = "session_not_recognized";
      state.value = "error";
      return;
    }

    const me = (await meResponse.json()) as MeResponse;
    token.value = issuedToken;
    authenticatedEmail.value = me.email;
    roles.value = me.roles;
    permissions.value = me.permissions;
    state.value = "success";
  } catch {
    // A thrown fetch (network failure, DNS, CORS) must still leave the form
    // usable — without this, "submitting" was the last state ever set and
    // the button stayed disabled forever. If /login itself threw, no token
    // was ever issued and there's nothing to clean up. But if /login already
    // succeeded and /me is what threw, a session exists server-side with
    // this tab holding the only reference to it — the same orphaned-session
    // risk as an explicit /me failure above, just via a thrown exception
    // instead of a non-2xx status, so it gets the same best-effort revoke.
    if (issuedToken) {
      const revoked = await revokeSessionBestEffort(issuedToken);
      logoutWarning.value = !revoked;
    }
    errorMessage.value = "network_error";
    state.value = "error";
  }
}

async function logout() {
  const serverConfirmedRevocation = await revokeSessionBestEffort(token.value);

  // Always clear local state — this browser tab shouldn't go on presenting
  // the token regardless of whether the server confirmed revocation. But if
  // it didn't, say so: the session is still valid server-side until it
  // expires on its own, which a silent "you're signed out" would hide.
  logoutWarning.value = token.value !== "" && !serverConfirmedRevocation;
  token.value = "";
  authenticatedEmail.value = "";
  roles.value = [];
  permissions.value = [];
  state.value = "idle";
}

function formatWait(totalSeconds: number): string {
  if (totalSeconds < 60) {
    return `${totalSeconds} second${totalSeconds === 1 ? "" : "s"}`;
  }
  const minutes = Math.ceil(totalSeconds / 60);
  return `${minutes} minute${minutes === 1 ? "" : "s"}`;
}

function errorText(reason: string): string {
  if (reason === "invalid_credentials") return "Incorrect email or password.";
  if (reason === "rate_limited") {
    return retryAfterSeconds.value
      ? `Too many attempts. Try again in ${formatWait(retryAfterSeconds.value)}.`
      : "Too many attempts. Please wait a moment before trying again.";
  }
  if (reason === "network_error") return "Could not reach the server. Check your connection and try again.";
  return "Something went wrong. Please try again.";
}
</script>

<template>
  <main class="min-h-screen bg-wego-canvas px-6 py-12 text-wego-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-md">
      <p class="text-sm font-semibold tracking-[0.18em] text-wego-accent uppercase">Wego Platform</p>
      <h1 class="mt-4 text-3xl font-semibold tracking-tight">Sign in</h1>

      <WegoAlert v-if="logoutWarning" variant="warning" role="status" class="mt-6">
        Signed out on this device, but the server didn't confirm the session was revoked — it
        may still be valid elsewhere until it expires on its own.
      </WegoAlert>

      <form v-if="state !== 'success'" class="mt-8 space-y-5" @submit.prevent="submit">
        <WegoInput id="email" v-model="email" label="Email" type="email" required autocomplete="username" />
        <WegoInput
          id="password"
          v-model="password"
          label="Password"
          type="password"
          required
          autocomplete="current-password"
        />

        <WegoAlert v-if="state === 'error'" variant="danger">
          {{ errorText(errorMessage) }}
        </WegoAlert>

        <WegoButton type="submit" class="w-full" :disabled="state === 'submitting'" :loading="state === 'submitting'">
          {{ state === "submitting" ? "Signing in…" : "Sign in" }}
        </WegoButton>
      </form>

      <div v-else class="mt-8 rounded-wego-card border border-wego-border bg-wego-surface p-6">
        <p class="font-semibold">Signed in as {{ authenticatedEmail }}</p>
        <p class="mt-2 text-sm text-wego-muted">
          Roles: {{ roles.length ? roles.join(", ") : "none" }}
        </p>
        <p class="mt-1 text-sm text-wego-muted">
          Permissions: {{ permissions.length ? permissions.join(", ") : "none" }}
        </p>
        <WegoButton type="button" variant="secondary" class="mt-4" @click="logout">Sign out</WegoButton>
      </div>
    </div>
  </main>
</template>
