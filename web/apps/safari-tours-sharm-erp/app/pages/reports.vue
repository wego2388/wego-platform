<script setup lang="ts">
import CalendarDateField from "../components/CalendarDateField.vue";
import { computed, onMounted, ref } from "vue";
import { WegoAlert, WegoBadge } from "@wego/ui";
import { clearAuthSession, hasPermission, readAuthSession, type AuthSession } from "../composables/useAuthSession";
import {
  ToursApiError, downloadReportCsv, getCashVarianceReport, getDepartureMoneyReport, getRequestConversionReport, getReversalReport,
  type FinanceReportName,
} from "../composables/useToursApi";
import type { CashVarianceReport, DepartureMoneyReport, RequestConversionReport, ReversalReport } from "@wego/api-contract";
import { useErpLocale } from "../composables/useErpLocale";
import type { ErpMessageKey } from "../utils/erpLocale";
import { monthRange } from "../utils/financeOps";
import { isCalendarDate } from "../utils/calendarDate";

/**
 * SAF-04 Slice B read-only reports. Each section is shown only when the account holds the permission
 * the server enforces for it; each documents its definition, period, currency and source.
 */
const { t, locale, count, signedMoney, dateLabel, instantLabel } = useErpLocale();
useHead(() => ({ title: `${t("rep.title")} · Safari Tours Sharm` }));

const router = useRouter();
const session = ref<AuthSession | null>(null);
const range = monthRange();
const from = ref(range.from);
const to = ref(range.to);
type Slot<T> = { state: "idle" | "loading" | "loaded" | "error"; data: T | null; csvFailed: boolean };
const fresh = <T,>(): Slot<T> => ({ state: "idle", data: null, csvFailed: false });
const reversals = ref(fresh<ReversalReport>());
const cash = ref(fresh<CashVarianceReport>());
const requests = ref(fresh<RequestConversionReport>());
const departures = ref(fresh<DepartureMoneyReport>());

const canFinance = computed(() => hasPermission(session.value, "tours-operator.payment:view"));
const canCash = computed(() => canFinance.value || hasPermission(session.value, "tours-operator.cash-box:confirm"));
const canRequests = computed(() => hasPermission(session.value, "tours-operator.booking:view"));
const canAny = computed(() => canFinance.value || canCash.value || canRequests.value);

async function run<T>(slot: { value: Slot<T> }, allowed: boolean, fetcher: (token: string) => Promise<T>) {
  if (!allowed || !session.value) return;
  slot.value = { state: "loading", data: null, csvFailed: false };
  try {
    slot.value = { state: "loaded", data: await fetcher(session.value.token), csvFailed: false };
  } catch (err) {
    if (err instanceof ToursApiError && err.status === 401) {
      clearAuthSession();
      void router.replace("/login");
      return;
    }
    slot.value = { state: "error", data: null, csvFailed: false };
  }
}

function load() {
  if (!isCalendarDate(from.value) || !isCalendarDate(to.value) || to.value < from.value) return;
  const [f, e] = [from.value, to.value];
  void run(reversals, canFinance.value, (token) => getReversalReport(token, f, e));
  void run(cash, canCash.value, (token) => getCashVarianceReport(token, f, e));
  void run(requests, canRequests.value, (token) => getRequestConversionReport(token, f, e));
  void run(departures, canFinance.value, (token) => getDepartureMoneyReport(token, f, e));
}

async function csv(report: FinanceReportName, slot: Slot<unknown>) {
  if (!session.value) return;
  slot.csvFailed = false;
  try {
    const blob = await downloadReportCsv(session.value.token, report, from.value, to.value, locale.value);
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `${report}-${from.value}-${to.value}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  } catch {
    slot.csvFailed = true;
  }
}

onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) {
    void router.replace("/login");
    return;
  }
  load();
});
const doc = (prefix: "rev" | "cash" | "req" | "dep") => [
  ["rep.definition", `rep.${prefix}.definition`], ["rep.period", `rep.${prefix}.period`],
  ["rep.currency", `rep.${prefix}.currency`], ["rep.source", `rep.${prefix}.source`],
] as [ErpMessageKey, ErpMessageKey][];
const th = "px-3 py-2 text-start";
const thEnd = "px-3 py-2 text-end";
const td = "px-3 py-2 align-top";
const tdEnd = "px-3 py-2 text-end tabular-nums";
const BTN = "min-h-11 rounded-lg border border-sts-border bg-sts-surface px-4 py-2 text-sm font-semibold";
</script>

<template>
  <main class="px-4 py-8 sm:px-8">
    <div class="mx-auto max-w-7xl">
      <h1 class="text-3xl font-semibold tracking-tight">{{ t('rep.title') }}</h1>
      <p class="mt-1 max-w-3xl text-sm text-sts-muted">{{ t('rep.intro') }}</p>
      <WegoAlert v-if="session && !canAny" variant="danger" class="mt-6" role="alert">{{ t('rep.noAccess') }}</WegoAlert>
      <template v-else-if="canAny">
        <form class="mt-6 flex flex-wrap items-end gap-3" @submit.prevent="load">
          <CalendarDateField id="rep-from" v-model="from" :label="t('rep.from')" required class="w-full sm:w-80" />
          <CalendarDateField id="rep-to" v-model="to" :label="t('rep.to')" required class="w-full sm:w-80" />
          <button type="submit" class="min-h-11 rounded-lg bg-sts-ocean px-4 py-2.5 text-sm font-semibold text-white">{{ t('rep.apply') }}</button>
        </form>

        <!-- Reversals -->
        <section v-if="canFinance" id="report-reversals" class="mt-10" aria-labelledby="rep-rev-h">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <h2 id="rep-rev-h" class="text-xl font-semibold">{{ t('rep.rev.title') }}</h2>
            <button type="button" :class="BTN" :disabled="reversals.state !== 'loaded'" @click="csv('reversals', reversals)">{{ t('rep.csv') }}</button>
          </div>
          <dl class="mt-3 grid gap-2 rounded-2xl border border-sts-border bg-sts-surface p-4 text-sm sm:grid-cols-2">
            <div v-for="[label, text] in doc('rev')" :key="text"><dt class="font-semibold">{{ t(label) }}</dt><dd class="text-sts-muted">{{ t(text) }}</dd></div>
          </dl>
          <p v-if="reversals.state === 'loading'" class="mt-4 text-sts-muted" role="status">{{ t('rep.loading') }}</p>
          <WegoAlert v-else-if="reversals.state === 'error'" variant="danger" class="mt-4" role="alert">{{ t('rep.failed') }}</WegoAlert>
          <template v-else-if="reversals.data">
            <WegoAlert v-if="reversals.csvFailed" variant="danger" class="mt-4" role="alert">{{ t('rep.csvFailed') }}</WegoAlert>
            <p v-if="reversals.data.lines.length === 0" class="mt-4 rounded-2xl border border-dashed border-sts-border bg-sts-surface p-6 text-center text-sts-muted">{{ t('rep.empty') }}</p>
            <template v-else>
              <ul class="mt-4 flex flex-wrap gap-2 text-sm">
                <li v-for="x in reversals.data.totals" :key="x.kind + x.currency"><WegoBadge tone="neutral">{{ t(`rep.kind.${x.kind}` as ErpMessageKey) }} · {{ count(x.count) }} · {{ signedMoney(x.amount) }}</WegoBadge></li>
              </ul>
              <div class="mt-3 overflow-x-auto rounded-2xl border border-sts-border bg-sts-surface">
                <table class="w-full text-sm">
                  <caption class="sr-only">{{ t('rep.rev.title') }}</caption>
                  <thead class="text-xs text-sts-muted"><tr>
                    <th scope="col" :class="th">{{ t('rep.rev.kind') }}</th><th scope="col" :class="th">{{ t('rep.rev.when') }}</th><th scope="col" :class="th">{{ t('rep.rev.who') }}</th>
                    <th scope="col" :class="th">{{ t('rep.rev.original') }}</th><th scope="col" :class="thEnd">{{ t('rep.rev.lag') }}</th><th scope="col" :class="thEnd">{{ t('rep.rev.amount') }}</th>
                    <th scope="col" :class="th">{{ t('rep.rev.reason') }}</th><th scope="col" :class="th">{{ t('rep.rev.ref') }}</th>
                  </tr></thead>
                  <tbody>
                    <tr v-for="l in reversals.data.lines" :key="l.id" class="border-t border-sts-border">
                      <td :class="td">{{ t(`rep.kind.${l.kind}` as ErpMessageKey) }}<WegoBadge v-if="l.crossedClosedDay" tone="warning" class="ms-1">{{ t('rep.rev.crossed') }}</WegoBadge></td>
                      <td :class="td">{{ instantLabel(l.reversedAt) }}</td>
                      <td :class="td" dir="auto">{{ l.reversedBy ?? '—' }}</td>
                      <td :class="td">{{ instantLabel(l.originalAt) }}<span v-if="l.originalBy" class="block text-xs text-sts-muted" dir="auto">{{ t('rep.by') }} {{ l.originalBy }}</span></td>
                      <td :class="tdEnd">{{ count(l.lagDays) }}</td>
                      <td :class="tdEnd">{{ signedMoney(l.amount) }}<span v-if="l.amountEur && l.amount.currencyCode !== 'EUR'" class="block text-xs text-sts-muted">{{ signedMoney(l.amountEur) }}</span></td>
                      <td :class="td" dir="auto">{{ l.reason }}</td>
                      <td :class="td" dir="auto">{{ l.bookingReference ?? l.partyName ?? '—' }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </template>
          </template>
        </section>

        <!-- Cash variances -->
        <section v-if="canCash" id="report-cash" class="mt-10" aria-labelledby="rep-cash-h">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <h2 id="rep-cash-h" class="text-xl font-semibold">{{ t('rep.cash.title') }}</h2>
            <button type="button" :class="BTN" :disabled="cash.state !== 'loaded'" @click="csv('cash-variances', cash)">{{ t('rep.csv') }}</button>
          </div>
          <dl class="mt-3 grid gap-2 rounded-2xl border border-sts-border bg-sts-surface p-4 text-sm sm:grid-cols-2">
            <div v-for="[label, text] in doc('cash')" :key="text"><dt class="font-semibold">{{ t(label) }}</dt><dd class="text-sts-muted">{{ t(text) }}</dd></div>
          </dl>
          <p v-if="cash.state === 'loading'" class="mt-4 text-sts-muted" role="status">{{ t('rep.loading') }}</p>
          <WegoAlert v-else-if="cash.state === 'error'" variant="danger" class="mt-4" role="alert">{{ t('rep.failed') }}</WegoAlert>
          <template v-else-if="cash.data">
            <WegoAlert v-if="cash.csvFailed" variant="danger" class="mt-4" role="alert">{{ t('rep.csvFailed') }}</WegoAlert>
            <p v-if="cash.data.days.length === 0" class="mt-4 rounded-2xl border border-dashed border-sts-border bg-sts-surface p-6 text-center text-sts-muted">{{ t('rep.empty') }}</p>
            <template v-else>
              <ul class="mt-4 grid gap-1 text-sm">
                <li v-for="x in cash.data.totals" :key="x.currency"><strong>{{ x.currency }}</strong> · {{ t('rep.cash.totals', { days: count(x.closedDays), withDiff: count(x.daysWithVariance), diff: signedMoney(x.difference) }) }}</li>
              </ul>
              <div class="mt-3 overflow-x-auto rounded-2xl border border-sts-border bg-sts-surface">
                <table class="w-full text-sm">
                  <caption class="sr-only">{{ t('rep.cash.title') }}</caption>
                  <thead class="text-xs text-sts-muted"><tr>
                    <th scope="col" :class="th">{{ t('rep.cash.date') }}</th><th scope="col" :class="th">{{ t('rep.currency') }}</th><th scope="col" :class="th">&nbsp;</th>
                    <th scope="col" :class="thEnd">{{ t('rep.cash.expected') }}</th><th scope="col" :class="thEnd">{{ t('rep.cash.counted') }}</th><th scope="col" :class="thEnd">{{ t('rep.cash.diff') }}</th>
                    <th scope="col" :class="th">{{ t('rep.cash.countedBy') }}</th><th scope="col" :class="th">{{ t('rep.cash.confirmedBy') }}</th>
                    <th scope="col" :class="thEnd">{{ t('rep.cash.counts') }}</th><th scope="col" :class="thEnd">{{ t('rep.cash.reopens') }}</th>
                  </tr></thead>
                  <tbody>
                    <tr v-for="d in cash.data.days" :key="d.date + d.currency" class="border-t border-sts-border">
                      <th scope="row" :class="td + ' text-start font-semibold'">{{ dateLabel(d.date) }}</th>
                      <td :class="td">{{ d.currency }}</td>
                      <td :class="td"><WegoBadge :tone="d.state === 'CLOSED' ? 'success' : 'warning'">{{ t(`rep.state.${d.state}` as ErpMessageKey) }}</WegoBadge></td>
                      <td :class="tdEnd">{{ signedMoney(d.expected) }}</td>
                      <td :class="tdEnd">{{ signedMoney(d.counted) }}</td>
                      <td :class="tdEnd + (d.difference.amount !== '0.00' ? ' font-semibold text-rose-700' : '')">{{ signedMoney(d.difference) }}</td>
                      <td :class="td" dir="auto">{{ d.countedBy ?? '—' }}</td>
                      <td :class="td" dir="auto">{{ d.confirmedBy ?? '—' }}</td>
                      <td :class="tdEnd">{{ count(d.countEvents) }}</td>
                      <td :class="tdEnd">{{ count(d.reopenCount) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </template>
          </template>
        </section>

        <!-- Request conversion -->
        <section v-if="canRequests" id="report-requests" class="mt-10" aria-labelledby="rep-req-h">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <h2 id="rep-req-h" class="text-xl font-semibold">{{ t('rep.req.title') }}</h2>
            <button type="button" :class="BTN" :disabled="requests.state !== 'loaded'" @click="csv('request-conversion', requests)">{{ t('rep.csv') }}</button>
          </div>
          <dl class="mt-3 grid gap-2 rounded-2xl border border-sts-border bg-sts-surface p-4 text-sm sm:grid-cols-2">
            <div v-for="[label, text] in doc('req')" :key="text"><dt class="font-semibold">{{ t(label) }}</dt><dd class="text-sts-muted">{{ t(text) }}</dd></div>
          </dl>
          <p v-if="requests.state === 'loading'" class="mt-4 text-sts-muted" role="status">{{ t('rep.loading') }}</p>
          <WegoAlert v-else-if="requests.state === 'error'" variant="danger" class="mt-4" role="alert">{{ t('rep.failed') }}</WegoAlert>
          <template v-else-if="requests.data">
            <WegoAlert v-if="requests.csvFailed" variant="danger" class="mt-4" role="alert">{{ t('rep.csvFailed') }}</WegoAlert>
            <p v-if="requests.data.total === 0" class="mt-4 rounded-2xl border border-dashed border-sts-border bg-sts-surface p-6 text-center text-sts-muted">{{ t('rep.empty') }}</p>
            <dl v-else class="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
              <div class="rounded-2xl border border-sts-border bg-sts-surface p-3"><dt class="text-xs text-sts-muted">{{ t('rep.req.total') }}</dt><dd class="text-2xl font-semibold tabular-nums">{{ count(requests.data.total) }}</dd></div>
              <div class="rounded-2xl border border-sts-border bg-sts-surface p-3"><dt class="text-xs text-sts-muted">{{ t('rep.req.NEW') }}</dt><dd class="text-2xl font-semibold tabular-nums">{{ count(requests.data.new) }}</dd></div>
              <div class="rounded-2xl border border-sts-border bg-sts-surface p-3"><dt class="text-xs text-sts-muted">{{ t('rep.req.IN_PROGRESS') }}</dt><dd class="text-2xl font-semibold tabular-nums">{{ count(requests.data.inProgress) }}</dd></div>
              <div class="rounded-2xl border border-sts-border bg-sts-surface p-3"><dt class="text-xs text-sts-muted">{{ t('rep.req.CLOSED') }}</dt><dd class="text-2xl font-semibold tabular-nums">{{ count(requests.data.closed) }}</dd></div>
              <div class="rounded-2xl border border-sts-border bg-sts-surface p-3"><dt class="text-xs text-sts-muted">{{ t('rep.req.CONVERTED') }}</dt><dd class="text-2xl font-semibold tabular-nums">{{ count(requests.data.converted) }}</dd></div>
              <div class="rounded-2xl border border-sts-border bg-sts-surface p-3"><dt class="text-xs text-sts-muted">{{ t('rep.req.rate') }}</dt><dd class="text-2xl font-semibold tabular-nums">{{ requests.data.conversionPercent ?? '—' }}%</dd></div>
            </dl>
          </template>
        </section>

        <!-- Departure-day money -->
        <section v-if="canFinance" id="report-departures" class="mt-10" aria-labelledby="rep-dep-h">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <h2 id="rep-dep-h" class="text-xl font-semibold">{{ t('rep.dep.title') }}</h2>
            <button type="button" :class="BTN" :disabled="departures.state !== 'loaded'" @click="csv('departure-money', departures)">{{ t('rep.csv') }}</button>
          </div>
          <dl class="mt-3 grid gap-2 rounded-2xl border border-sts-border bg-sts-surface p-4 text-sm sm:grid-cols-2">
            <div v-for="[label, text] in doc('dep')" :key="text"><dt class="font-semibold">{{ t(label) }}</dt><dd class="text-sts-muted">{{ t(text) }}</dd></div>
          </dl>
          <p v-if="departures.state === 'loading'" class="mt-4 text-sts-muted" role="status">{{ t('rep.loading') }}</p>
          <WegoAlert v-else-if="departures.state === 'error'" variant="danger" class="mt-4" role="alert">{{ t('rep.failed') }}</WegoAlert>
          <template v-else-if="departures.data">
            <WegoAlert v-if="departures.csvFailed" variant="danger" class="mt-4" role="alert">{{ t('rep.csvFailed') }}</WegoAlert>
            <p v-if="departures.data.days.length === 0" class="mt-4 rounded-2xl border border-dashed border-sts-border bg-sts-surface p-6 text-center text-sts-muted">{{ t('rep.empty') }}</p>
            <div v-else class="mt-4 overflow-x-auto rounded-2xl border border-sts-border bg-sts-surface">
              <table class="w-full text-sm">
                <caption class="sr-only">{{ t('rep.dep.title') }}</caption>
                <thead class="text-xs text-sts-muted"><tr>
                  <th scope="col" :class="th">{{ t('rep.dep.date') }}</th><th scope="col" :class="thEnd">{{ t('rep.dep.bookings') }}</th><th scope="col" :class="thEnd">{{ t('rep.dep.cancelled') }}</th>
                  <th scope="col" :class="thEnd">{{ t('rep.dep.booked') }}</th><th scope="col" :class="thEnd">{{ t('rep.dep.collected') }}</th><th scope="col" :class="thEnd">{{ t('rep.dep.outstanding') }}</th>
                  <th scope="col" :class="thEnd">{{ t('rep.dep.kept') }}</th><th scope="col" :class="thEnd">{{ t('rep.dep.receivedEur') }}</th><th scope="col" :class="thEnd">{{ t('rep.dep.receivedEgp') }}</th>
                  <th scope="col" :class="thEnd">{{ t('rep.dep.egpAsEur') }}</th><th scope="col" :class="thEnd">{{ t('rep.dep.flags') }}</th>
                </tr></thead>
                <tbody>
                  <tr v-for="d in departures.data.days" :key="d.date" class="border-t border-sts-border">
                    <th scope="row" :class="td + ' text-start font-semibold'">{{ dateLabel(d.date) }}</th>
                    <td :class="tdEnd">{{ count(d.liveBookings) }}</td><td :class="tdEnd">{{ count(d.cancelledBookings) }}</td>
                    <td :class="tdEnd">{{ signedMoney(d.booked) }}</td><td :class="tdEnd">{{ signedMoney(d.collected) }}</td><td :class="tdEnd">{{ signedMoney(d.outstanding) }}</td>
                    <td :class="tdEnd">{{ signedMoney(d.retainedOnCancelled) }}</td>
                    <td :class="tdEnd">{{ signedMoney(d.received.find((r) => r.currency === 'EUR')!.amount) }}</td>
                    <td :class="tdEnd">{{ signedMoney(d.received.find((r) => r.currency === 'EGP')!.amount) }}</td>
                    <td :class="tdEnd">{{ signedMoney(d.received.find((r) => r.currency === 'EGP')!.settledEur) }}</td>
                    <td :class="tdEnd">{{ count(d.paymentsUnderReview) }} / {{ count(d.refundsPending) }}</td>
                  </tr>
                </tbody>
                <tfoot v-if="departures.data.totals" class="border-t-2 border-sts-border font-semibold">
                  <tr>
                    <th scope="row" :class="th">{{ t('rep.total') }}</th>
                    <td :class="tdEnd">{{ count(departures.data.totals.liveBookings) }}</td><td :class="tdEnd">{{ count(departures.data.totals.cancelledBookings) }}</td>
                    <td :class="tdEnd">{{ signedMoney(departures.data.totals.booked) }}</td><td :class="tdEnd">{{ signedMoney(departures.data.totals.collected) }}</td><td :class="tdEnd">{{ signedMoney(departures.data.totals.outstanding) }}</td>
                    <td :class="tdEnd">{{ signedMoney(departures.data.totals.retainedOnCancelled) }}</td>
                    <td :class="tdEnd">{{ signedMoney(departures.data.totals.received.find((r) => r.currency === 'EUR')!.amount) }}</td>
                    <td :class="tdEnd">{{ signedMoney(departures.data.totals.received.find((r) => r.currency === 'EGP')!.amount) }}</td>
                    <td :class="tdEnd">{{ signedMoney(departures.data.totals.received.find((r) => r.currency === 'EGP')!.settledEur) }}</td>
                    <td :class="tdEnd">{{ count(departures.data.totals.paymentsUnderReview) }} / {{ count(departures.data.totals.refundsPending) }}</td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </template>
        </section>
      </template>
    </div>
  </main>
</template>
