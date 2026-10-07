<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert, WegoBadge } from "@wego/ui";
import { clearAuthSession, hasPermission, readAuthSession, type AuthSession } from "../composables/useAuthSession";
import {
  ToursApiError, getProfitability, listAllStaffTours,
  type ProfitGroup, type ProfitGrouping, type ProfitReport, type ProfitTotals, type Tour,
} from "../composables/useToursApi";
import { useErpLocale } from "../composables/useErpLocale";
import type { ErpMessageKey } from "../utils/erpLocale";
import { monthRange } from "../utils/financeOps";

/**
 * Read-only profitability (OPS2-F, finance permission). Online card revenue and office
 * payments are separate columns; a row with any unknown cost shows no cost or profit.
 */
const { t, count, signedMoney, dateLabel } = useErpLocale();
useHead(() => ({ title: `${t("fops.profit.title")} · Safari Tours Sharm` }));

const router = useRouter();
const session = ref<AuthSession | null>(null);
const range = monthRange();
const from = ref(range.from);
const to = ref(range.to);
const groupBy = ref<ProfitGrouping>("DEPARTURE");
const report = ref<ProfitReport | null>(null);
const tours = ref<Tour[]>([]);
const state = ref<"idle" | "loading" | "loaded" | "error">("idle");
const GROUPS: ProfitGrouping[] = ["DEPARTURE", "BOOKING", "TOUR", "MONTH"];
const canView = computed(() => hasPermission(session.value, "tours-operator.payment:view"));

const tourName = (id: string | null) => (id ? (tours.value.find((x) => x.id === id)?.nameEn ?? tours.value.find((x) => x.id === id)?.slug ?? id.slice(0, 8)) : "");

function label(g: ProfitGroup): string {
  switch (report.value?.groupBy) {
    case "MONTH": return g.month ?? g.key;
    case "TOUR": return tourName(g.tourId);
    case "BOOKING": return `${g.reference ?? ""} · ${tourName(g.tourId)} · ${g.date ? dateLabel(g.date) : ""}`;
    default: return `${g.date ? dateLabel(g.date) : ""} · ${g.timeSlot ? t(`slot.${g.timeSlot}` as ErpMessageKey) : ""} · ${tourName(g.tourId)}`;
  }
}

function flags(totals: ProfitTotals, g?: ProfitGroup): string[] {
  const out = totals.gaps.map((gap) => t(`fops.gap.${gap}` as ErpMessageKey));
  if (g?.rateSource && g.rateSource !== "DEPARTURE_DATE") {
    out.push(t("fops.profit.rate", { rate: g.egpPerEur ?? "—", source: t(`fops.rate.${g.rateSource}` as ErpMessageKey) }));
  }
  return out;
}

async function load() {
  if (!session.value || !canView.value || to.value < from.value) return;
  state.value = "loading";
  try {
    report.value = await getProfitability(session.value.token, from.value, to.value, groupBy.value);
    if (!tours.value.length && hasPermission(session.value, "tours-operator.tour:view")) tours.value = await listAllStaffTours(session.value.token).catch(() => []);
    state.value = "loaded";
  } catch (err) {
    if (err instanceof ToursApiError && err.status === 401) {
      clearAuthSession();
      void router.replace("/login");
      return;
    }
    state.value = "error";
  }
}

onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) {
    void router.replace("/login");
    return;
  }
  void load();
});
const FIELD = "rounded-xl border border-sts-border bg-sts-surface px-4 py-2.5 text-sm";
</script>

<template>
  <main class="px-4 py-8 sm:px-8">
    <div class="mx-auto max-w-7xl">
      <h1 class="text-3xl font-semibold tracking-tight">{{ t('fops.profit.title') }}</h1>
      <p class="mt-1 max-w-3xl text-sm text-sts-muted">{{ t('fops.profit.intro') }}</p>
      <WegoAlert v-if="session && !canView" variant="danger" class="mt-6" role="alert">{{ t('common.forbidden') }}</WegoAlert>
      <template v-else-if="canView">
        <form class="mt-6 flex flex-wrap items-end gap-3" @submit.prevent="load">
          <label class="grid gap-1 text-xs font-semibold text-sts-muted">{{ t('fops.from') }}<input id="profit-from" v-model="from" type="date" :class="FIELD" dir="ltr"></label>
          <label class="grid gap-1 text-xs font-semibold text-sts-muted">{{ t('fops.to') }}<input id="profit-to" v-model="to" type="date" :class="FIELD" dir="ltr"></label>
          <label class="grid gap-1 text-xs font-semibold text-sts-muted">{{ t('fops.profit.groupBy') }}
            <select id="profit-group" v-model="groupBy" :class="FIELD"><option v-for="g in GROUPS" :key="g" :value="g">{{ t(`fops.group.${g}`) }}</option></select>
          </label>
          <button type="submit" class="rounded-lg bg-sts-ocean px-4 py-2.5 text-sm font-semibold text-white">{{ t('fops.apply') }}</button>
        </form>

        <p v-if="state === 'loading'" class="mt-6 text-sts-muted" role="status">{{ t('common.loading') }}</p>
        <WegoAlert v-else-if="state === 'error'" variant="danger" class="mt-6" role="alert">{{ t('fops.loadFailed') }}</WegoAlert>
        <template v-else-if="state === 'loaded' && report">
          <WegoAlert v-if="report.totals.incompleteBookings > 0" variant="warning" class="mt-6" role="status">
            {{ t('fops.profit.incomplete', { count: count(report.totals.incompleteBookings) }) }}
          </WegoAlert>
          <p v-if="report.groups.length === 0" class="mt-6 rounded-2xl border border-dashed border-sts-border bg-sts-surface p-8 text-center text-sts-muted">{{ t('fops.profit.empty') }}</p>
          <div v-else class="mt-6 overflow-x-auto rounded-2xl border border-sts-border bg-sts-surface">
            <table class="w-full text-sm">
              <caption class="sr-only">{{ t('fops.profit.title') }}</caption>
              <thead class="text-xs text-sts-muted">
                <tr>
                  <th scope="col" class="px-3 py-2 text-start">{{ t('fops.profit.row') }}</th>
                  <th scope="col" class="px-3 py-2 text-end">{{ t('fops.profit.guests') }}</th>
                  <th scope="col" class="px-3 py-2 text-end">{{ t('fops.profit.online') }}</th>
                  <th scope="col" class="px-3 py-2 text-end">{{ t('fops.profit.office') }}</th>
                  <th scope="col" class="px-3 py-2 text-end">{{ t('fops.profit.revenue') }}</th>
                  <th scope="col" class="px-3 py-2 text-end">{{ t('fops.profit.cost') }}</th>
                  <th scope="col" class="px-3 py-2 text-end">{{ t('fops.profit.profit') }}</th>
                  <th scope="col" class="px-3 py-2 text-end">{{ t('fops.profit.margin') }}</th>
                  <th scope="col" class="px-3 py-2 text-start">{{ t('fops.profit.flags') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="g in report.groups" :key="g.key" class="border-t border-sts-border">
                  <th scope="row" class="px-3 py-2 text-start font-semibold" dir="auto">{{ label(g) }}</th>
                  <td class="px-3 py-2 text-end tabular-nums">{{ count(g.totals.guests) }}</td>
                  <td class="px-3 py-2 text-end tabular-nums">{{ signedMoney(g.totals.onlineRevenue) }}</td>
                  <td class="px-3 py-2 text-end tabular-nums">{{ signedMoney(g.totals.officeRevenue) }}</td>
                  <td class="px-3 py-2 text-end tabular-nums font-semibold">{{ signedMoney(g.totals.revenue) }}</td>
                  <td class="px-3 py-2 text-end tabular-nums">{{ g.totals.cost ? signedMoney(g.totals.cost) : '—' }}</td>
                  <td class="px-3 py-2 text-end tabular-nums font-semibold" :class="g.totals.profit?.amount.startsWith('-') ? 'text-rose-700' : ''">{{ g.totals.profit ? signedMoney(g.totals.profit) : '—' }}</td>
                  <td class="px-3 py-2 text-end tabular-nums">{{ g.totals.marginPercent !== null ? `${g.totals.marginPercent}%` : '—' }}</td>
                  <td class="px-3 py-2"><WegoBadge v-for="f in flags(g.totals, g)" :key="f" tone="warning" class="me-1">{{ f }}</WegoBadge></td>
                </tr>
              </tbody>
              <tfoot class="border-t-2 border-sts-border font-semibold">
                <tr>
                  <th scope="row" class="px-3 py-2 text-start">{{ t('fops.profit.total') }}</th>
                  <td class="px-3 py-2 text-end tabular-nums">{{ count(report.totals.guests) }}</td>
                  <td class="px-3 py-2 text-end tabular-nums">{{ signedMoney(report.totals.onlineRevenue) }}</td>
                  <td class="px-3 py-2 text-end tabular-nums">{{ signedMoney(report.totals.officeRevenue) }}</td>
                  <td class="px-3 py-2 text-end tabular-nums">{{ signedMoney(report.totals.revenue) }}</td>
                  <td class="px-3 py-2 text-end tabular-nums">{{ report.totals.cost ? signedMoney(report.totals.cost) : '—' }}</td>
                  <td class="px-3 py-2 text-end tabular-nums">{{ report.totals.profit ? signedMoney(report.totals.profit) : '—' }}</td>
                  <td class="px-3 py-2 text-end tabular-nums">{{ report.totals.marginPercent !== null ? `${report.totals.marginPercent}%` : '—' }}</td>
                  <td class="px-3 py-2" />
                </tr>
              </tfoot>
            </table>
          </div>
        </template>
      </template>
    </div>
  </main>
</template>
