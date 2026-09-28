<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert, WegoButton } from "@wego/ui";
import {
  clearAuthSession,
  hasPermission,
  readAuthSession,
  type AuthSession,
} from "../composables/useAuthSession";
import {
  listBookings,
  listTours,
  formatMoney,
  ToursApiError,
  type Booking,
  type Tour,
  PAGE_SIZE,
} from "../composables/useToursApi";
import {
  filterByDateRange,
  computeRevenueSummary,
  computeRevenueByTour,
  computeStatusCounts,
  type RevenueSummary,
  type RevenueByTourRow,
  type BookingStatusCount,
} from "../composables/useFinanceAggregation";

useHead({ title: "Finance · Safari Tours Sharm" });

const router  = useRouter();
const session = ref<AuthSession | null>(null);
const bookings = ref<Booking[]>([]);
const tours    = ref<Tour[]>([]);
const state    = ref<"idle" | "loading" | "loaded" | "error">("idle");
const error    = ref("");

// Date range filter — default: current month
function currentMonthRange() {
  const now = new Date();
  const from = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-01`;
  const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
  const to = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${lastDay}`;
  return { from, to };
}

const { from: defaultFrom, to: defaultTo } = currentMonthRange();
const filterFrom = ref(defaultFrom);
const filterTo   = ref(defaultTo);

const canView = computed(() => hasPermission(session.value, "tours-operator.booking:view"));

// ── Computed finance aggregations (via pure composable) ───────────────────

const filteredBookings = computed(() =>
  filterByDateRange(bookings.value, filterFrom.value, filterTo.value),
);

const summary = computed<RevenueSummary>(() =>
  computeRevenueSummary(filteredBookings.value),
);

const revenueByTour = computed<RevenueByTourRow[]>(() =>
  computeRevenueByTour(filteredBookings.value, tours.value),
);

const statusCounts = computed<BookingStatusCount[]>(() =>
  computeStatusCounts(filteredBookings.value),
);

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
    // Fetch all bookings for the selected month (paginate if needed)
    const allBookings: Booking[] = [];
    let page = 0;
    let hasMore = true;
    while (hasMore) {
      const batch = await listBookings(session.value.token, {
        page,
        size: PAGE_SIZE,
      });
      allBookings.push(...batch);
      hasMore = batch.length === PAGE_SIZE;
      page++;
    }
    bookings.value = allBookings;
    tours.value = await listTours(session.value.token, { activeOnly: false });
    state.value = "loaded";
  } catch (err) {
    handleApiError(err);
    error.value = err instanceof ToursApiError ? err.errorCode : "Failed to load finance data.";
    state.value = "error";
  }
}

function applyFilters() {
  // Filtering is computed client-side from already-loaded data
  // Re-fetch only if data not yet loaded
  if (state.value !== "loaded") void load();
}

function logout() {
  clearAuthSession();
  void router.replace("/login");
}

onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) { void router.replace("/login"); return; }
  if (!canView.value) { void router.replace("/"); return; }
  void load();
});
</script>

<template>
  <main class="min-h-screen bg-sts-canvas px-6 py-8 text-sts-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-6xl">

      <!-- Header -->
      <header class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <NuxtLink to="/" class="text-sm text-sts-muted hover:text-sts-ocean">← Overview</NuxtLink>
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">Finance</h1>
        </div>
        <WegoButton type="button" variant="secondary" size="sm" class="text-sts-muted" @click="logout">
          Sign out
        </WegoButton>
      </header>

      <!-- Nav -->
      <nav class="mt-4 flex gap-4 text-sm" aria-label="Section navigation">
        <NuxtLink to="/"         class="text-sts-muted hover:text-sts-ocean">Overview</NuxtLink>
        <NuxtLink to="/bookings" class="text-sts-muted hover:text-sts-ocean">Bookings</NuxtLink>
        <NuxtLink to="/tours"    class="text-sts-muted hover:text-sts-ocean">Tours</NuxtLink>
        <NuxtLink to="/finance"  class="font-semibold text-sts-ocean border-b-2 border-sts-ocean pb-0.5">Finance</NuxtLink>
      </nav>

      <!-- Permission check -->
      <WegoAlert v-if="!canView" variant="danger" class="mt-6">
        You don't have permission to view finance data.
      </WegoAlert>

      <template v-else>
        <!-- Date range filter -->
        <div class="mt-6 flex flex-wrap items-end gap-3">
          <div class="flex flex-col gap-1">
            <label class="text-xs font-semibold text-sts-muted" for="fin-from">From</label>
            <input
              id="fin-from"
              v-model="filterFrom"
              type="date"
              class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm focus:outline-sts-gold"
            >
          </div>
          <div class="flex flex-col gap-1">
            <label class="text-xs font-semibold text-sts-muted" for="fin-to">To</label>
            <input
              id="fin-to"
              v-model="filterTo"
              type="date"
              class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm focus:outline-sts-gold"
            >
          </div>
          <WegoButton type="button" variant="primary" size="sm" @click="applyFilters">
            Apply
          </WegoButton>
        </div>

        <WegoAlert v-if="state === 'error'" variant="danger" class="mt-6">{{ error }}</WegoAlert>
        <p v-else-if="state === 'loading'" class="mt-6 text-sm text-sts-muted">Loading…</p>

        <template v-else-if="state === 'loaded'">

          <!-- KPI cards -->
          <div class="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
              <p class="text-xs font-semibold text-sts-muted uppercase tracking-wide">Revenue</p>
              <p class="mt-1 text-2xl font-black tabular-nums text-sts-ocean">{{ formatMoney(summary.totalRevenue) }}</p>
              <p class="mt-0.5 text-xs text-sts-muted">confirmed + completed</p>
            </div>
            <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
              <p class="text-xs font-semibold text-sts-muted uppercase tracking-wide">Bookings</p>
              <p class="mt-1 text-2xl font-black tabular-nums text-sts-ocean">{{ summary.confirmedCount }}</p>
              <p class="mt-0.5 text-xs text-sts-muted">of {{ summary.totalCount }} total</p>
            </div>
            <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
              <p class="text-xs font-semibold text-sts-muted uppercase tracking-wide">Pax</p>
              <p class="mt-1 text-2xl font-black tabular-nums text-sts-ocean">{{ summary.paxTotal }}</p>
              <p class="mt-0.5 text-xs text-sts-muted">adults + children</p>
            </div>
            <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
              <p class="text-xs font-semibold text-sts-muted uppercase tracking-wide">Avg / booking</p>
              <p class="mt-1 text-2xl font-black tabular-nums text-sts-ocean">
                {{ summary.averagePerBooking ? formatMoney(summary.averagePerBooking) : '—' }}
              </p>
              <p class="mt-0.5 text-xs text-sts-muted">confirmed + completed</p>
            </div>
          </div>

          <!-- Status breakdown -->
          <div class="mt-6 grid gap-4 sm:grid-cols-2">

            <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
              <h2 class="mb-3 text-sm font-semibold text-sts-muted uppercase tracking-wide">Bookings by status</h2>
              <dl class="space-y-2">
                <div
                  v-for="row in statusCounts"
                  :key="row.status"
                  class="flex items-center justify-between text-sm"
                >
                  <dt>
                    <span :class="`badge badge-${row.status}`">{{ row.status }}</span>
                  </dt>
                  <dd class="tabular-nums font-semibold">{{ row.count }}</dd>
                </div>
              </dl>
            </div>

            <!-- Revenue by tour -->
            <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
              <h2 class="mb-3 text-sm font-semibold text-sts-muted uppercase tracking-wide">Revenue by tour</h2>
              <p v-if="revenueByTour.length === 0" class="text-sm text-sts-muted">No data for this period.</p>
              <dl v-else class="space-y-2">
                <div
                  v-for="row in revenueByTour"
                  :key="row.tourId"
                  class="flex items-center justify-between text-sm gap-4"
                >
                  <dt class="font-mono text-xs text-sts-muted truncate">
                    {{ row.tourSlug ?? 'Unknown tour' }}
                  </dt>
                  <dd class="flex items-center gap-3 shrink-0">
                    <span class="text-xs text-sts-muted">{{ row.bookingCount }} bookings</span>
                    <span class="text-xs text-sts-muted">{{ row.sharePercent }}%</span>
                    <span class="money font-semibold">{{ formatMoney(row.total) }}</span>
                  </dd>
                </div>
              </dl>
            </div>

          </div>

        </template>
      </template>

    </div>
  </main>
</template>
