<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert, WegoButton, WegoInput } from "@wego/ui";
import {
  readAuthSession,
  writeAuthSession,
} from "../composables/useAuthSession";
import { useErpLocale } from "../composables/useErpLocale";
import type { ErpMessageKey } from "../utils/erpLocale";
import ErpLanguageSwitch from "../components/ErpLanguageSwitch.vue";

// The sign-in screen is full-page and has no staff navigation.
definePageMeta({ layout: false });

const { t } = useErpLocale();
useHead(() => ({ title: `${t("login.title")} · Safari Tours Sharm` }));

const email = ref("");
const password = ref("");
const state = ref<"idle" | "submitting" | "error">("idle");
const errorKey = ref<ErpMessageKey | null>(null);
const errorCode = ref("");
const errorMsg = computed(() => errorKey.value ? t(errorKey.value, { code: errorCode.value }) : "");

const router = useRouter();

onMounted(() => {
  // Already signed in — go to overview. Use the shared parser so a corrupt
  // or foreign value under the storage key is not treated as a valid session.
  if (readAuthSession()) void router.replace("/");
});

interface LoginResponse {
  token: string;
}

interface MeResponse {
  email: string;
  roles: string[];
  permissions: string[];
}

async function revokeSessionBestEffort(token: string): Promise<void> {
  if (!token) return;
  await fetch("/api/v1/identity/logout", {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  }).catch(() => undefined);
}

async function submit() {
  state.value = "submitting";
  errorKey.value = null;
  errorCode.value = "";
  let issuedToken = "";
  try {
    const response = await fetch("/api/v1/identity/login", {
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
        errorKey.value = "login.incorrect";
      } else if (response.status === 429 || code === "rate_limited") {
        errorKey.value = "login.throttled";
      } else {
        errorKey.value = "login.failed";
        errorCode.value = code;
      }
      state.value = "error";
      return;
    }

    const login = (await response.json()) as LoginResponse;
    issuedToken = login.token;

    // Login only issues the opaque bearer token. Roles and permissions are
    // resolved by the authenticated /me endpoint; never infer them in the UI.
    const meResponse = await fetch("/api/v1/identity/me", {
      headers: { Authorization: `Bearer ${issuedToken}` },
    });
    if (!meResponse.ok) {
      await revokeSessionBestEffort(issuedToken);
      errorKey.value = "login.sessionInvalid";
      state.value = "error";
      return;
    }

    const me = (await meResponse.json()) as MeResponse;
    writeAuthSession({
      token: issuedToken,
      email: me.email,
      roles: me.roles,
      permissions: me.permissions,
    });
    void router.replace("/");
  } catch {
    // If login succeeded but /me or storage failed, revoke the otherwise
    // orphaned server-side session instead of leaving it valid until expiry.
    await revokeSessionBestEffort(issuedToken);
    errorKey.value = "login.connectionFailed";
    state.value = "error";
  }
}
</script>

<template>
  <main class="relative flex min-h-screen items-center justify-center bg-sts-ocean px-4 py-20 sm:px-6">
    <div class="absolute end-4 top-4 text-white"><ErpLanguageSwitch /></div>
    <div class="w-full max-w-sm">

      <div class="mb-10 text-center">
        <div class="inline-grid size-16 place-items-center rounded-3xl bg-sts-gold font-black text-sts-ocean text-2xl select-none">
          S
        </div>
        <p class="mt-4 font-semibold text-white">Safari Tours Sharm</p>
        <p class="mt-1 text-sm text-white/75">{{ t('login.dashboard') }}</p>
      </div>

      <div class="rounded-2xl bg-sts-surface p-8 shadow-xl">
        <h1 class="text-xl font-semibold text-sts-ink">{{ t('login.title') }}</h1>

        <WegoAlert v-if="state === 'error'" variant="danger" class="mt-5">
          {{ errorMsg }}
        </WegoAlert>

        <form class="mt-6 space-y-4" @submit.prevent="submit">
          <WegoInput
            id="email"
            v-model="email"
            :label="t('login.email')"
            type="email"
            dir="ltr"
            autocomplete="email"
            required
          />
          <WegoInput
            id="password"
            v-model="password"
            :label="t('login.password')"
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
            {{ state === "submitting" ? t('login.signingIn') : t('login.title') }}
          </WegoButton>
        </form>

        <p class="mt-5 text-center text-xs text-sts-muted">
          {{ t('login.invitedOnly') }}
        </p>
      </div>

    </div>
  </main>
</template>
