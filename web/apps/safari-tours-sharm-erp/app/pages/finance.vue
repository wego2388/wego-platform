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
  listPaymentLedger,
  listAllStaffTours,
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
  type RevenueSummary,
  type RevenueByTourRow,
  type PaymentStatusCount,
} from "../composables/useFinanceAggregation";
import { useErpLocale } from "../composables/useErpLocale";
import { formatErpDate } from "../utils/erpLocale";
import { operationsErrorMessage } from "../utils/operationsMessages";
import type { ErpMessageDescriptor } from "../utils/bookingMessages";

const { t, count, money, signedMoney } = useErpLocale();
useHead(() => ({ title: `${t("finance.title")} · Safari Tours Sharm` }));

const router  = useRouter();
const session = ref<AuthSession | null>(null);
const payments = ref<PaymentLedgerEntry[]>([]);
const tours    = ref<Tour[]>([]);
const state    = ref<"idle" | "loading" | "loaded" | "error">("idle");
const error    = ref<ErpMessageDescriptor | null>(null);
let loadVersion = 0;

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
  const version = ++loadVersion;
  if (formatErpDate(filterFrom.value, "en") === "—" || formatErpDate(filterTo.value, "en") === "—" || filterTo.value < filterFrom.value) {
    error.value = { key: "finance.invalidRange" };
    state.value = "error";
    return;
  }
  const from = filterFrom.value;
  const to = filterTo.value;
  state.value = "loading";
  error.value = null;
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
      if (version !== loadVersion) return;
      fetched.push(...batch);
      if (batch.length < PAGE_SIZE) break;
      after = batch[batch.length - 1]!.paymentId;
    }
    const allPayments = uniqueByPaymentId(fetched);
    // Surface a currency mix as a load error instead of a render crash.
    assertSingleCurrency(paymentLedgerEvents(allPayments, from, to));
    // Tour names are a label only; finance must not depend on catalog access.
    const allTours = canViewTours.value ? await listAllStaffTours(session.value.token) : [];
    if (version !== loadVersion) return;
    payments.value = allPayments;
    tours.value = allTours;
    appliedFrom.value = from;
    appliedTo.value = to;
    state.value = "loaded";
  } catch (err) {
    handleApiError(err);
    if (version !== loadVersion) return;
    error.value = err instanceof Error && err.message === "Finance report cannot mix currencies"
      ? { key: "finance.currencyMix" } : operationsErrorMessage(err);
    state.value = "error";
  }
}

function applyFilters() {
  void load();
}


onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) { void router.replace("/login"); return; }
  if (!canView.value) { void router.replace("/"); return; }
  void load();
});
</script>

<template>
  <main class="px-6 py-8 text-sts-ink sm:px-10 lg:px-16">
    <div class="mx-auto max-w-6xl">

      <!-- Header -->
      <header class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 class="mt-1 text-2xl font-semibold tracking-tight">{{ t("finance.title") }}</h1>
        </div>
      </header>

      <!-- Nav -->

      <!-- Permission check -->
      <WegoAlert v-if="!canView" variant="danger" class="mt-6">
        {{ t("finance.permission") }}
      </WegoAlert>

      <template v-else>
        <!-- Date range filter -->
        <div class="mt-6 flex flex-wrap items-end gap-3">
          <div class="flex flex-col gap-1">
            <label class="text-xs font-semibold text-sts-muted" for="fin-from">{{ t("finance.from") }}</label>
            <input
              id="fin-from"
              v-model="filterFrom"
              type="date"
              class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm focus:outline-sts-gold"
            >
          </div>
          <div class="flex flex-col gap-1">
            <label class="text-xs font-semibold text-sts-muted" for="fin-to">{{ t("finance.to") }}</label>
            <input
              id="fin-to"
              v-model="filterTo"
              type="date"
              class="rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm focus:outline-sts-gold"
            >
          </div>
          <WegoButton type="button" variant="primary" size="sm" @click="applyFilters">
            {{ t("finance.apply") }}
          </WegoButton>
        </div>

        <WegoAlert v-if="state === 'error' && error" variant="danger" class="mt-6">{{ t(error.key, error.params) }}</WegoAlert>
        <p v-else-if="state === 'loading'" class="mt-6 text-sm text-sts-muted" role="status">{{ t("common.loading") }}</p>

        <template v-else-if="state === 'loaded'">

          <!-- KPI cards -->
          <div class="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
            <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
              <p class="text-xs font-semibold text-sts-muted uppercase tracking-wide">{{ t("finance.net") }}</p>
              <p class="money mt-1 text-2xl font-black tabular-nums text-sts-ocean">{{ signedMoney(summary.netRevenue) }}</p>
              <p class="mt-0.5 text-xs text-sts-muted">{{ t("finance.netHelp") }}</p>
            </div>
            <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
              <p class="text-xs font-semibold text-sts-muted uppercase tracking-wide">{{ t("finance.gross") }}</p>
              <p class="money mt-1 text-2xl font-black tabular-nums text-sts-ocean">{{ money(summary.grossPaid) }}</p>
              <p class="mt-0.5 text-xs text-sts-muted">{{ t("finance.capturedCount", { count: count(summary.paidCount) }) }}</p>
            </div>
            <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
              <p class="text-xs font-semibold text-sts-muted uppercase tracking-wide">{{ t("finance.refunded") }}</p>
              <p class="money mt-1 text-2xl font-black tabular-nums text-rose-700">{{ money(summary.refunded) }}</p>
              <p class="mt-0.5 text-xs text-sts-muted">{{ t("finance.refundCount", { count: count(summary.refundedCount) }) }}</p>
            </div>
            <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
              <p class="text-xs font-semibold text-sts-muted uppercase tracking-wide">{{ t("finance.average") }}</p>
              <p class="money mt-1 text-2xl font-black tabular-nums text-sts-ocean">
                {{ summary.averagePaidBooking ? money(summary.averagePaidBooking) : '—' }}
              </p>
              <p class="mt-0.5 text-xs text-sts-muted">{{ t("finance.averageHelp") }}</p>
            </div>
            <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
              <p class="text-xs font-semibold text-sts-muted uppercase tracking-wide">{{ t("finance.netPax") }}</p>
              <p class="mt-1 text-2xl font-black tabular-nums text-sts-ocean">{{ count(summary.paxTotal) }}</p>
              <p class="mt-0.5 text-xs text-sts-muted">{{ t("finance.paxHelp") }}</p>
            </div>
          </div>

          <!-- Ledger status breakdown -->
          <div class="mt-6 grid gap-4 sm:grid-cols-2">

            <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
              <h2 class="mb-1 text-sm font-semibold text-sts-muted uppercase tracking-wide">{{ t("finance.status") }}</h2>
              <p class="mb-3 text-xs text-sts-muted">{{ t("finance.statusHelp") }}</p>
              <dl class="space-y-2">
                <div
                  v-for="row in statusCounts"
                  :key="row.status"
                  class="flex items-center justify-between text-sm"
                >
                  <dt>
                    <span :class="`badge badge-${row.status}`">{{ t(`payment.${row.status}`) }}</span>
                  </dt>
                  <dd class="tabular-nums font-semibold">{{ count(row.count) }}</dd>
                </div>
              </dl>
            </div>

            <!-- Revenue by tour -->
            <div class="rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm">
              <h2 class="mb-3 text-sm font-semibold text-sts-muted uppercase tracking-wide">{{ t("finance.byTour") }}</h2>
              <p v-if="revenueByTour.length === 0" class="text-sm text-sts-muted">{{ t("finance.empty") }}</p>
              <dl v-else class="space-y-2">
                <div
                  v-for="row in revenueByTour"
                  :key="row.tourId"
                  class="flex flex-wrap items-center justify-between text-sm gap-4"
                >
                  <dt class="min-w-0 break-all font-mono text-xs text-sts-muted">
                    {{ row.tourSlug ?? t('finance.unknownTour') }}
                  </dt>
                  <dd class="flex flex-wrap items-center gap-3">
                    <span class="text-xs text-sts-muted">{{ t("finance.paidCount", { count: count(row.paidCount) }) }}</span>
                    <span v-if="row.refundedCount" class="text-xs text-rose-700">{{ t("finance.refundedCount", { count: count(row.refundedCount) }) }}</span>
                    <span class="text-xs text-sts-muted">{{ t("finance.grossShare", { percent: count(row.sharePercent) }) }}</span>
                    <span class="money font-semibold">{{ signedMoney(row.netRevenue) }}</span>
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
