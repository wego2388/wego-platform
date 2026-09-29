<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert, WegoButton } from "@wego/ui";
import {
  clearAuthSession,
  logoutAuthSession,
  hasPermission,
  readAuthSession,
  type AuthSession,
} from "../composables/useAuthSession";
import {
  listPaymentLedger,
  listAllStaffTours,
  formatMoney,
  ToursApiError,
  type PaymentLedgerEntry,
  type Tour,
  PAGE_SIZE,
} from "../composables/useToursApi";
import {
  assertSingleCurrency,
  computeRevenueSummary,
  paymentLedgerEvents,
  uniqueByPaymentId,
  computeRevenueByTour,
  computePaymentStatusCounts,
  formatSignedMoney,
  type RevenueSummary,
  type RevenueByTourRow,
  type PaymentStatusCount,
} from "../composables/useFinanceAggregation";

useHead({ title: "Finance · Safari Tours Sharm" });

const router  = useRouter();
const session = ref<AuthSession | null>(null);
const payments = ref<PaymentLedgerEntry[]>([]);
const tours    = ref<Tour[]>([]);
const state    = ref<"idle" | "loading" | "loaded" | "error">("idle");
const error    = ref("");

// Date range filter — default: current month
function currentMonthRange() {
  const parts = new Intl.DateTimeFormat("en", {
    timeZone: "Africa/Cairo",
    year: "numeric",
    month: "2-digit",
  }).formatToParts(new Date());
  const year = parts.find((part) => part.type === "year")!.value;
  const month = parts.find((part) => part.type === "month")!.value;
  const lastDay = new Date(Date.UTC(Number(year), Number(month), 0)).getUTCDate();
  const from = `${year}-${month}-01`;
  const to = `${year}-${month}-${lastDay}`;
  return { from, to };
}

const { from: defaultFrom, to: defaultTo } = currentMonthRange();
const filterFrom = ref(defaultFrom);
const filterTo   = ref(defaultTo);
// Aggregations use the range the ledger was actually fetched for, so editing
// the date inputs never re-slices stale data before Apply reloads it.
const appliedFrom = ref(defaultFrom);
const appliedTo   = ref(defaultTo);

const canView = computed(() => hasPermission(session.value, "tours-operator.payment:view"));
const canViewTours = computed(() => hasPermission(session.value, "tours-operator.tour:view"));

// ── Computed finance aggregations (via pure composable) ───────────────────

const summary = computed<RevenueSummary>(() =>
  computeRevenueSummary(
    payments.value,
    appliedFrom.value,
    appliedTo.value,
  ),
);

const revenueByTour = computed<RevenueByTourRow[]>(() =>
  computeRevenueByTour(
    payments.value,
    tours.value,
    appliedFrom.value,
    appliedTo.value,
  ),
);

const statusCounts = computed<PaymentStatusCount[]>(() =>
  computePaymentStatusCounts(payments.value),
);

function handleApiError(err: unknown) {
  if (err instanceof ToursApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
  }
}

async function load() {
  if (!session.value) return;
  if (filterTo.value < filterFrom.value) {
    error.value = "End date must be on or after start date.";
    state.value = "error";
    return;
  }
  const from = filterFrom.value;
  const to = filterTo.value;
  state.value = "loading";
  error.value = "";
  try {
    const fetched: PaymentLedgerEntry[] = [];
    let after: string | undefined;
    for (;;) {
      const batch = await listPaymentLedger(session.value.token, {
        from,
        to,
        after,
        size: PAGE_SIZE,
      });
      fetched.push(...batch);
      if (batch.length < PAGE_SIZE) break;
      after = batch[batch.length - 1]!.paymentId;
    }
    const allPayments = uniqueByPaymentId(fetched);
    // Surface a currency mix as a load error instead of a render crash.
    assertSingleCurrency(paymentLedgerEvents(allPayments, from, to));
    // Tour names are a label only; finance must not depend on catalog access.
    const allTours = canViewTours.value ? await listAllStaffTours(session.value.token) : [];
    payments.value = allPayments;
    tours.value = allTours;
    appliedFrom.value = from;
    appliedTo.value = to;
    state.value = "loaded";
  } catch (err) {
    handleApiError(err);
    error.value = err instanceof ToursApiError
      ? err.errorCode
      : err instanceof Error ? err.message : "Failed to load finance data.";
    state.value = "error";
  }
}

function applyFilters() {
  void load();
}

async function logout() {
  await logoutAuthSession(session.value);
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
        You need payment-view permission to access finance data.
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
          <div class="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
            <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
              <p class="text-xs font-semibold text-sts-muted uppercase tracking-wide">Net revenue</p>
              <p class="mt-1 text-2xl font-black tabular-nums text-sts-ocean">{{ formatSignedMoney(summary.netRevenue) }}</p>
              <p class="mt-0.5 text-xs text-sts-muted">paid minus refunds</p>
            </div>
            <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
              <p class="text-xs font-semibold text-sts-muted uppercase tracking-wide">Gross paid</p>
              <p class="mt-1 text-2xl font-black tabular-nums text-sts-ocean">{{ formatMoney(summary.grossPaid) }}</p>
              <p class="mt-0.5 text-xs text-sts-muted">{{ summary.paidCount }} captured payments</p>
            </div>
            <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
              <p class="text-xs font-semibold text-sts-muted uppercase tracking-wide">Refunded</p>
              <p class="mt-1 text-2xl font-black tabular-nums text-rose-700">{{ formatMoney(summary.refunded) }}</p>
              <p class="mt-0.5 text-xs text-sts-muted">{{ summary.refundedCount }} refund events</p>
            </div>
            <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
              <p class="text-xs font-semibold text-sts-muted uppercase tracking-wide">Avg / booking</p>
              <p class="mt-1 text-2xl font-black tabular-nums text-sts-ocean">
                {{ summary.averagePaidBooking ? formatMoney(summary.averagePaidBooking) : '—' }}
              </p>
              <p class="mt-0.5 text-xs text-sts-muted">captured payments only</p>
            </div>
            <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
              <p class="text-xs font-semibold text-sts-muted uppercase tracking-wide">Net pax</p>
              <p class="mt-1 text-2xl font-black tabular-nums text-sts-ocean">{{ summary.paxTotal }}</p>
              <p class="mt-0.5 text-xs text-sts-muted">adults + children, minus refunds</p>
            </div>
          </div>

          <!-- Ledger status breakdown -->
          <div class="mt-6 grid gap-4 sm:grid-cols-2">

            <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
              <h2 class="mb-1 text-sm font-semibold text-sts-muted uppercase tracking-wide">Payment status</h2>
              <p class="mb-3 text-xs text-sts-muted">Pending and review captures never count as revenue, even after a refund.</p>
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
                    <span class="text-xs text-sts-muted">{{ row.paidCount }} paid</span>
                    <span v-if="row.refundedCount" class="text-xs text-rose-700">{{ row.refundedCount }} refunded</span>
                    <span class="text-xs text-sts-muted">{{ row.sharePercent }}% of gross</span>
                    <span class="money font-semibold">{{ formatSignedMoney(row.netRevenue) }}</span>
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
