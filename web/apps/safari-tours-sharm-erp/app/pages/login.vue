<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert, WegoButton, WegoInput } from "@wego/ui";
import {
  clearAuthSession,
  type AuthSession,
  writeAuthSession,
} from "../composables/useAuthSession";

useHead({ title: "Sign in · Safari Tours Sharm" });

const email = ref("");
const password = ref("");
const state = ref<"idle" | "submitting" | "error">("idle");
const errorMsg = ref("");

const router = useRouter();

onMounted(() => {
  // Already signed in — go to overview
  if (typeof sessionStorage !== "undefined") {
    const raw = sessionStorage.getItem("wego_auth_session");
    if (raw) {
      try {
        const parsed = JSON.parse(raw) as Partial<AuthSession>;
        if (typeof parsed.token === "string" && parsed.token) {
          void router.replace("/");
        }
      } catch { /* ignore */ }
    }
  }
});

async function submit() {
  state.value = "submitting";
  errorMsg.value = "";
  try {
    const response = await fetch("/api/v1/identity/sessions", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: email.value, password: password.value }),
    });

    if (!response.ok) {
      const body = await response.json().catch(() => null);
      const code =
        body && typeof body === "object" && "error" in body
          ? String(body.error)
          : `http_${response.status}`;
      if (response.status === 401 || code === "invalid_credentials") {
        errorMsg.value = "Incorrect email or password.";
      } else if (response.status === 403 || code === "account_locked") {
        errorMsg.value = "Account locked — too many failed attempts. Try again later.";
      } else {
        errorMsg.value = `Sign in failed (${code}).`;
      }
      state.value = "error";
      return;
    }

    const session = (await response.json()) as AuthSession;
    writeAuthSession(session);
    void router.replace("/");
  } catch {
    errorMsg.value = "Could not reach the server. Check your connection and try again.";
    state.value = "error";
  }
}
</script>

<template>
  <main class="flex min-h-screen items-center justify-center bg-sts-ocean px-6 py-12">
    <div class="w-full max-w-sm">

      <div class="mb-10 text-center">
        <div class="inline-grid size-16 place-items-center rounded-3xl bg-sts-gold font-black text-sts-ocean text-2xl select-none">
          S
        </div>
        <p class="mt-4 font-semibold text-white">Safari Tours Sharm</p>
        <p class="mt-1 text-sm text-white/55">Operations Dashboard</p>
      </div>

      <div class="rounded-2xl bg-sts-surface p-8 shadow-xl">
        <h1 class="text-xl font-semibold text-sts-ink">Sign in</h1>

        <WegoAlert v-if="state === 'error'" variant="danger" class="mt-5">
          {{ errorMsg }}
        </WegoAlert>

        <form class="mt-6 space-y-4" @submit.prevent="submit">
          <WegoInput
            id="email"
            v-model="email"
            label="Email"
            type="email"
            autocomplete="email"
            required
          />
          <WegoInput
            id="password"
            v-model="password"
            label="Password"
            type="password"
            autocomplete="current-password"
            required
          />
          <WegoButton
            type="submit"
            class="w-full"
            :disabled="state === 'submitting'"
            :loading="state === 'submitting'"
          >
            {{ state === "submitting" ? "Signing in…" : "Sign in" }}
          </WegoButton>
        </form>

        <p class="mt-5 text-center text-xs text-sts-muted">
          Access for invited staff only.
        </p>
      </div>

    </div>
  </main>
</template>
