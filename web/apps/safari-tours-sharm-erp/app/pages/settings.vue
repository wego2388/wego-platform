<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert } from "@wego/ui";
import {
  clearAuthSession,
  readAuthSession,
  type AuthSession,
} from "../composables/useAuthSession";
import { ToursApiError } from "../composables/useToursApi";
import { useErpLocale } from "../composables/useErpLocale";
import type { ErpMessageDescriptor } from "../utils/bookingMessages";
import { operationsErrorMessage } from "../utils/operationsMessages";

const { t, count } = useErpLocale();
useHead(() => ({ title: `${t("settings.title")} · Safari Tours Sharm` }));

const router  = useRouter();
const session = ref<AuthSession | null>(null);
const state   = ref<"idle" | "loading" | "loaded" | "error">("idle");
const error   = ref<ErpMessageDescriptor | null>(null);

interface SettingsData {
  companyName: string;
  companyPhone: string;
  companyEmail: string;
  companyAddress: string;
  currency: string;
  timezone: string;
  bookingExpiryMinutes: number;
  whatsappNumber: string;
}

const settings = ref<SettingsData | null>(null);

function handleApiError(err: unknown) {
  if (err instanceof ToursApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
  }
}

async function load() {
  if (!session.value) return;
  state.value = "loading";
  error.value = null;
  try {
    const response = await fetch("/api/v1/tours-operator/settings", {
      headers: { Authorization: `Bearer ${session.value.token}` },
    });
    if (!response.ok) {
      const body = await response.json().catch(() => null);
      const code = body?.error ?? `http_${response.status}`;
      throw new ToursApiError(response.status, code);
    }
    settings.value = await response.json() as SettingsData;
    state.value    = "loaded";
  } catch (err) {
    handleApiError(err);
    // Settings endpoint may not exist yet — show empty state gracefully
    if (err instanceof ToursApiError && err.status === 404) {
      settings.value = null;
      state.value    = "loaded";
    } else {
      error.value = operationsErrorMessage(err);
      state.value = "error";
    }
  }
}


onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) { void router.replace("/login"); return; }
  void load();
});
</script>

<template>
  <main class="px-6 py-8 text-sts-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-2xl">

      <!-- Header -->
      <header class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">{{ t("settings.title") }}</h1>
        </div>
      </header>

      <!-- Nav -->

      <WegoAlert v-if="state === 'error' && error" variant="danger" class="mt-6 break-all">{{ t(error.key, error.params) }}</WegoAlert>
      <p v-else-if="state === 'loading'" class="mt-6 text-sm text-sts-muted" role="status">{{ t("common.loading") }}</p>

      <template v-else-if="state === 'loaded'">

        <!-- Read-only notice -->
        <div class="mt-6 rounded-2xl border border-sts-border bg-sts-gold-soft px-5 py-3 text-sm text-sts-warning">
          {{ t("settings.readOnly") }}
        </div>

        <!-- No data yet -->
        <div v-if="!settings" class="mt-6 rounded-2xl border border-sts-border bg-sts-surface px-5 py-8 text-center text-sm text-sts-muted shadow-sm">
          {{ t("settings.noData") }}
        </div>

        <!-- Settings display -->
        <div v-else class="mt-6 space-y-4">

          <!-- Company info -->
          <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
            <h2 class="mb-3 text-sm font-semibold text-sts-muted uppercase tracking-wide">{{ t("settings.company") }}</h2>
            <dl class="space-y-2 text-sm">
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t("settings.name") }}</dt>
                <dd class="min-w-0 break-words font-medium text-end">{{ settings.companyName }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t("settings.phone") }}</dt>
                <dd class="phone min-w-0 break-all">{{ settings.companyPhone }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t("settings.email") }}</dt>
                <dd class="break-all text-end">{{ settings.companyEmail }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t("settings.address") }}</dt>
                <dd class="min-w-0 break-words text-end">{{ settings.companyAddress }}</dd>
              </div>
              <div v-if="settings.whatsappNumber" class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">WhatsApp</dt>
                <dd class="phone min-w-0 break-all">{{ settings.whatsappNumber }}</dd>
              </div>
            </dl>
          </div>

          <!-- System config -->
          <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
            <h2 class="mb-3 text-sm font-semibold text-sts-muted uppercase tracking-wide">{{ t("settings.system") }}</h2>
            <dl class="space-y-2 text-sm">
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t("settings.currency") }}</dt>
                <dd class="font-mono">{{ settings.currency }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t("settings.timezone") }}</dt>
                <dd class="min-w-0 break-all font-mono" dir="ltr">{{ settings.timezone }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">{{ t("settings.expiry") }}</dt>
                <dd class="tabular-nums">{{ t("settings.minutes", { count: count(settings.bookingExpiryMinutes) }) }}</dd>
              </div>
            </dl>
          </div>

          <!-- Session info -->
          <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
            <h2 class="mb-3 text-sm font-semibold text-sts-muted uppercase tracking-wide">{{ t("settings.loggedIn") }}</h2>
            <p class="break-all text-sm" dir="ltr">{{ session?.email }}</p>
            <p class="mt-1 break-all text-xs text-sts-muted">{{ t("settings.permissions", { permissions: session?.permissions.join(", ") || t("settings.none") }) }}</p>
          </div>

        </div>
      </template>

    </div>
  </main>
</template>
