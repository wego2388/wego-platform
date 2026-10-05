<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert } from "@wego/ui";
import {
  clearAuthSession,
  hasPermission,
  readAuthSession,
  type AuthSession,
} from "../composables/useAuthSession";
import {
  listBookings,
  ToursApiError,
} from "../composables/useToursApi";
import { useErpLocale } from "../composables/useErpLocale";
import { summarizeBookingCustomers, type CustomerSummary } from "../utils/customerSummaries";
import { operationsErrorMessage } from "../utils/operationsMessages";
import type { ErpMessageDescriptor } from "../utils/bookingMessages";

const { t, count, money, dateLabel } = useErpLocale();
useHead(() => ({ title: `${t("customers.title")} · Safari Tours Sharm` }));

const router  = useRouter();
const session = ref<AuthSession | null>(null);
const state   = ref<"idle" | "loading" | "loaded" | "error">("idle");
const error   = ref<ErpMessageDescriptor | null>(null);
const customers   = ref<CustomerSummary[]>([]);
const searchQuery = ref("");
const filterNat   = ref("");

const canView = computed(() => hasPermission(session.value, "tours-operator.booking:view"));

const filteredCustomers = computed(() => {
  let list = customers.value;
  if (searchQuery.value.trim()) {
    const q = searchQuery.value.trim().toLowerCase();
    list = list.filter(
      (c) =>
        c.fullName.toLowerCase().includes(q) ||
        c.phone.includes(q) ||
        (c.email ?? "").toLowerCase().includes(q),
    );
  }
  if (filterNat.value) {
    list = list.filter((c) => c.nationality === filterNat.value);
  }
  return list;
});

const nationalities = computed(() => [
  ...new Set(customers.value.map((c) => c.nationality).filter(Boolean)),
].sort());

function handleApiError(err: unknown) {
  if (err instanceof ToursApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
  }
}

async function load() {
  if (!session.value || !canView.value) return;
  state.value = "loading";
  error.value = null;
  try {
    // Deliberately bounded: existing API maximum, not full CRM or extra PII pages.
    const bookings = await listBookings(session.value.token, { size: 200 });
    customers.value = summarizeBookingCustomers(bookings);
    state.value = "loaded";
  } catch (err) {
    handleApiError(err);
    error.value = operationsErrorMessage(err);
    state.value = "error";
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
    <div class="mx-auto max-w-6xl">

      <!-- Header -->
      <header class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">{{ t("customers.title") }}</h1>
        </div>
      </header>

      <!-- Nav -->

      <!-- Permission check -->
      <WegoAlert v-if="!canView" variant="danger" class="mt-6">
        {{ t("common.forbidden") }} <code>tours-operator.booking:view</code>
      </WegoAlert>

      <template v-else>
        <p class="mt-4 text-xs text-sts-muted">{{ t("customers.scope") }}</p>
        <!-- Filters -->
        <div class="mt-6 flex flex-wrap items-center gap-3">
          <input
            v-model="searchQuery"
            type="search"
            :placeholder="t('customers.search')"
            :aria-label="t('customers.search')"
            class="w-full min-w-0 rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm focus:outline-sts-gold sm:w-auto sm:min-w-64"
          >
          <select
            v-model="filterNat"
            :aria-label="t('customers.filterNationality')"
            class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm focus:outline-sts-gold"
          >
            <option value="">{{ t("customers.allNationalities") }}</option>
            <option v-for="nat in nationalities" :key="nat" :value="nat">{{ nat }}</option>
          </select>
          <span v-if="state === 'loaded'" class="text-sm text-sts-muted">
            {{ t("customers.count", { count: count(filteredCustomers.length) }) }}
          </span>
        </div>

        <!-- Error -->
        <WegoAlert v-if="state === 'error' && error" variant="danger" class="mt-6">{{ t(error.key, error.params) }}</WegoAlert>

        <!-- Loading -->
        <p v-else-if="state === 'loading'" class="mt-6 text-sm text-sts-muted" role="status">{{ t("common.loading") }}</p>

        <!-- Empty -->
        <p v-else-if="state === 'loaded' && filteredCustomers.length === 0" class="mt-6 text-sm text-sts-muted">
          {{ t("customers.empty") }}
        </p>

        <!-- Table -->
        <div v-else-if="filteredCustomers.length > 0" class="mt-6 overflow-hidden rounded-2xl border border-sts-border bg-sts-surface shadow-sm">
          <div class="overflow-x-auto" tabindex="0" role="region" :aria-label="t('customers.title')">
            <table class="w-full text-sm" :aria-label="t('customers.title')">
              <thead>
                <tr class="border-b border-sts-border bg-sts-canvas/60">
                  <th scope="col" class="px-5 py-3 text-start text-xs font-semibold text-sts-muted">{{ t("settings.name") }}</th>
                  <th scope="col" class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">{{ t("settings.phone") }}</th>
                  <th scope="col" class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">{{ t("customers.nationality") }}</th>
                  <th scope="col" class="px-4 py-3 text-start text-xs font-semibold text-sts-muted">{{ t("settings.email") }}</th>
                  <th scope="col" class="px-4 py-3 text-center text-xs font-semibold text-sts-muted">{{ t("customers.bookings") }}</th>
                  <th scope="col" class="px-4 py-3 text-end text-xs font-semibold text-sts-muted">{{ t("customers.bookedValue") }}</th>
                  <th scope="col" class="px-5 py-3 text-start text-xs font-semibold text-sts-muted">{{ t("customers.latestTourDate") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="c in filteredCustomers"
                  :key="c.key"
                  class="border-b border-sts-border/50 last:border-0 hover:bg-sts-canvas/50"
                >
                  <td class="px-5 py-3.5 font-medium">{{ c.fullName }}</td>
                  <td class="px-4 py-3.5">
                    <a v-if="c.phone" :href="`tel:${c.phone}`" class="phone text-sts-ocean underline underline-offset-2 text-xs">
                      {{ c.phone }}
                    </a>
                  </td>
                  <td class="px-4 py-3.5 text-sts-muted">{{ c.nationality }}</td>
                  <td class="px-4 py-3.5 text-sts-muted text-xs">{{ c.email ?? '—' }}</td>
                  <td class="px-4 py-3.5 text-center tabular-nums font-semibold text-sts-ocean">{{ count(c.bookingCount) }}</td>
                  <td class="px-4 py-3.5 text-end font-semibold"><span v-for="value in c.bookedValues" :key="value.currencyCode" class="money block whitespace-nowrap">{{ money(value) }}</span></td>
                  <td class="px-5 py-3.5 text-sts-muted tabular-nums">{{ dateLabel(c.latestTourDate) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

      </template>

    </div>
  </main>
</template>
