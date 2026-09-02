<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoButton, WegoInput } from "@wego/ui";
import { clearAuthSession, readAuthSession, writeAuthSession } from "../composables/useAuthSession";

useHead({ title: "Sign in · Sharm To Go" });

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

// Same orphaned-session discipline as web/apps/erp/app/pages/login.vue — see
// that file's own comment for the full rationale.
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
    writeAuthSession({ token: issuedToken, email: me.email, roles: me.roles, permissions: me.permissions });
  } catch {
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
  logoutWarning.value = token.value !== "" && !serverConfirmedRevocation;
  token.value = "";
  authenticatedEmail.value = "";
  roles.value = [];
  permissions.value = [];
  state.value = "idle";
  clearAuthSession();
}

onMounted(() => {
  const session = readAuthSession();
  if (!session) return;
  token.value = session.token;
  authenticatedEmail.value = session.email;
  roles.value = session.roles;
  permissions.value = session.permissions;
  state.value = "success";
});

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
      <p class="text-sm font-semibold tracking-[0.18em] text-wego-accent uppercase">Sharm To Go</p>
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
        <p class="mt-2 text-sm text-wego-muted">Roles: {{ roles.length ? roles.join(", ") : "none" }}</p>
        <p class="mt-1 text-sm text-wego-muted">Permissions: {{ permissions.length ? permissions.join(", ") : "none" }}</p>
        <div class="mt-4 flex flex-wrap items-center gap-4">
          <WegoButton type="button" variant="secondary" @click="logout">Sign out</WegoButton>
          <NuxtLink to="/providers" class="text-sm font-semibold text-wego-accent underline">Providers</NuxtLink>
          <NuxtLink to="/categories" class="text-sm font-semibold text-wego-accent underline">Categories</NuxtLink>
          <NuxtLink to="/services" class="text-sm font-semibold text-wego-accent underline">Services</NuxtLink>
        </div>
      </div>
    </div>
  </main>
</template>
