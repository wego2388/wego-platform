<script setup lang="ts">
import { onMounted, ref } from "vue";
import { WegoAlert } from "@wego/ui";
import {
  clearAuthSession,
  readAuthSession,
  type AuthSession,
} from "../composables/useAuthSession";
import { ToursApiError } from "../composables/useToursApi";

useHead({ title: "Settings · Safari Tours Sharm" });

const router  = useRouter();
const session = ref<AuthSession | null>(null);
const state   = ref<"idle" | "loading" | "loaded" | "error">("idle");
const error   = ref("");

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

// Read-only for now (per Phase 4 spec)
const _canManage = ref(false); // future: hasPermission(session.value, "tours-operator.settings:manage")

function handleApiError(err: unknown) {
  if (err instanceof ToursApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
  }
}

async function load() {
  if (!session.value) return;
  state.value = "loading";
  error.value = "";
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
      error.value = err instanceof ToursApiError ? err.errorCode : "Failed to load settings.";
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
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">Settings</h1>
        </div>
      </header>

      <!-- Nav -->

      <WegoAlert v-if="state === 'error'" variant="danger" class="mt-6">{{ error }}</WegoAlert>
      <p v-else-if="state === 'loading'" class="mt-6 text-sm text-sts-muted">Loading…</p>

      <template v-else-if="state === 'loaded'">

        <!-- Read-only notice -->
        <div class="mt-6 rounded-2xl border border-sts-border bg-sts-gold-soft px-5 py-3 text-sm text-sts-warning">
          Settings are read-only in this release. Contact your system administrator to make changes.
        </div>

        <!-- No data yet -->
        <div v-if="!settings" class="mt-6 rounded-2xl border border-sts-border bg-sts-surface px-5 py-8 text-center text-sm text-sts-muted shadow-sm">
          No settings data available from the server yet.
        </div>

        <!-- Settings display -->
        <div v-else class="mt-6 space-y-4">

          <!-- Company info -->
          <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
            <h2 class="mb-3 text-sm font-semibold text-sts-muted uppercase tracking-wide">Company</h2>
            <dl class="space-y-2 text-sm">
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Name</dt>
                <dd class="font-medium text-end">{{ settings.companyName }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Phone</dt>
                <dd class="phone">{{ settings.companyPhone }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Email</dt>
                <dd class="break-all text-end">{{ settings.companyEmail }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Address</dt>
                <dd class="text-end">{{ settings.companyAddress }}</dd>
              </div>
              <div v-if="settings.whatsappNumber" class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">WhatsApp</dt>
                <dd class="phone">{{ settings.whatsappNumber }}</dd>
              </div>
            </dl>
          </div>

          <!-- System config -->
          <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
            <h2 class="mb-3 text-sm font-semibold text-sts-muted uppercase tracking-wide">System</h2>
            <dl class="space-y-2 text-sm">
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Currency</dt>
                <dd class="font-mono">{{ settings.currency }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Timezone</dt>
                <dd class="font-mono">{{ settings.timezone }}</dd>
              </div>
              <div class="flex justify-between gap-4">
                <dt class="text-sts-muted shrink-0">Booking expiry</dt>
                <dd class="tabular-nums">{{ settings.bookingExpiryMinutes }} minutes</dd>
              </div>
            </dl>
          </div>

          <!-- Session info -->
          <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
            <h2 class="mb-3 text-sm font-semibold text-sts-muted uppercase tracking-wide">Logged in as</h2>
            <p class="text-sm">{{ session?.email }}</p>
            <p class="mt-1 text-xs text-sts-muted">Permissions: {{ session?.permissions.join(", ") || "none" }}</p>
          </div>

        </div>
      </template>

    </div>
  </main>
</template>
