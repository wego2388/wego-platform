<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert, WegoBadge } from "@wego/ui";
import { clearAuthSession, hasPermission, readAuthSession, type AuthSession } from "../composables/useAuthSession";
import {
  ToursApiError, confirmCash, countCash, getCashDay, listRecentCashDays, reopenCash,
  type CashBoxEvent, type CashDay, type PaidCurrency,
} from "../composables/useToursApi";
import { useErpLocale } from "../composables/useErpLocale";
import type { ErpMessageDescriptor } from "../utils/bookingMessages";
import type { ErpMessageKey } from "../utils/erpLocale";
import { financeErrorMessage, todayCairo, toCents } from "../utils/financeOps";

/**
 * Daily office cash box per currency (OPS2-F). Reception counts (cash-box:close); a different
 * person with cash-box:confirm confirms and closes the day, or reopens it with a reason.
 * The server computes the expected amount and refuses cash on a closed day.
 */
const { t, signedMoney, dateLabel, instantLabel } = useErpLocale();
useHead(() => ({ title: `${t("fops.cash.title")} · Safari Tours Sharm` }));

const router = useRouter();
const session = ref<AuthSession | null>(null);
const date = ref(todayCairo());
const currency = ref<PaidCurrency>("EUR");
const day = ref<CashDay | null>(null);
const recent = ref<CashBoxEvent[]>([]);
const state = ref<"loading" | "loaded" | "error">("loading");
const counted = ref("");
const note = ref("");
const reason = ref("");
const busy = ref(false);
const done = ref(false);
const error = ref<ErpMessageDescriptor | null>(null);

const canCount = computed(() => hasPermission(session.value, "tours-operator.cash-box:close"));
const canConfirm = computed(() => hasPermission(session.value, "tours-operator.cash-box:confirm"));
const canView = computed(() => canCount.value || canConfirm.value || hasPermission(session.value, "tours-operator.payment:view"));
const tone = computed(() => (day.value?.state === "CLOSED" ? "success" : day.value?.state === "COUNTED" ? "warning" : "neutral"));

async function load() {
  if (!session.value || !canView.value) return;
  state.value = "loading";
  try {
    const [d, r] = await Promise.all([getCashDay(session.value.token, date.value, currency.value), listRecentCashDays(session.value.token)]);
    day.value = d;
    recent.value = r;
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

async function act(action: (token: string) => Promise<CashDay>) {
  if (!session.value || busy.value) return;
  busy.value = true;
  error.value = null;
  done.value = false;
  try {
    day.value = await action(session.value.token);
    done.value = true;
    recent.value = await listRecentCashDays(session.value.token).catch(() => recent.value);
  } catch (err) {
    error.value = financeErrorMessage(err);
  } finally {
    busy.value = false;
  }
}

function count() {
  const zero = counted.value.trim() === "0" || counted.value.trim() === "0.00";
  if (!zero && toCents(counted.value) === null) {
    error.value = { key: "fops.amountInvalid" };
    return;
  }
  void act((token) => countCash(token, date.value, currency.value, Number(counted.value), note.value.trim() || undefined)).then(() => {
    counted.value = "";
    note.value = "";
  });
}

function reopen() {
  if (!reason.value.trim()) {
    error.value = { key: "fops.reasonRequired" };
    return;
  }
  void act((token) => reopenCash(token, date.value, currency.value, reason.value.trim())).then(() => (reason.value = ""));
}

onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) {
    void router.replace("/login");
    return;
  }
  void load();
});
const FIELD = "rounded-lg border border-sts-border bg-sts-surface px-3 py-2 text-sm";
const k = (key: string) => t(key as ErpMessageKey);
</script>

<template>
  <main class="px-4 py-8 sm:px-8">
    <div class="mx-auto max-w-5xl">
      <h1 class="text-3xl font-semibold tracking-tight">{{ t('fops.cash.title') }}</h1>
      <p class="mt-1 max-w-3xl text-sm text-sts-muted">{{ t('fops.cash.intro') }}</p>
      <WegoAlert v-if="session && !canView" variant="danger" class="mt-6" role="alert">{{ t('common.forbidden') }}</WegoAlert>
      <template v-else-if="canView">
        <form class="mt-6 flex flex-wrap items-end gap-3" @submit.prevent="load">
          <label class="grid gap-1 text-xs font-semibold text-sts-muted">{{ t('fops.date') }}<input id="cash-date" v-model="date" type="date" :class="FIELD" dir="ltr"></label>
          <label class="grid gap-1 text-xs font-semibold text-sts-muted">{{ t('fops.currency') }}
            <select id="cash-currency" v-model="currency" :class="FIELD"><option value="EUR">{{ t('fops.cur.EUR') }}</option><option value="EGP">{{ t('fops.cur.EGP') }}</option></select>
          </label>
          <button type="submit" class="rounded-lg bg-sts-ocean px-4 py-2 text-sm font-semibold text-white">{{ t('fops.apply') }}</button>
        </form>
        <p v-if="state === 'loading'" class="mt-6 text-sts-muted" role="status">{{ t('common.loading') }}</p>
        <WegoAlert v-else-if="state === 'error'" variant="danger" class="mt-6" role="alert">{{ t('fops.loadFailed') }}</WegoAlert>
        <template v-else-if="day">
          <section class="mt-6 rounded-2xl border border-sts-border bg-sts-surface p-5">
            <div class="flex flex-wrap items-center gap-3">
              <h2 class="text-xl font-semibold">{{ dateLabel(day.date, true) }} · {{ k(`fops.cur.${day.currency}`) }}</h2>
              <WegoBadge id="cash-state" :tone="tone">{{ k(`fops.cash.state.${day.state}`) }}</WegoBadge>
            </div>
            <dl class="mt-4 grid gap-2 text-sm sm:grid-cols-2">
              <div class="flex justify-between gap-4"><dt>{{ t('fops.cash.collected') }}</dt><dd class="tabular-nums">{{ signedMoney(day.cashCollected) }}</dd></div>
              <div class="flex justify-between gap-4"><dt>{{ t('fops.cash.collectionsReversed') }}</dt><dd class="tabular-nums">{{ signedMoney(day.collectionsReversed) }}</dd></div>
              <div class="flex justify-between gap-4"><dt>{{ t('fops.cash.refunded') }}</dt><dd class="tabular-nums">{{ signedMoney(day.cashRefunded) }}</dd></div>
              <div class="flex justify-between gap-4"><dt>{{ t('fops.cash.refundsReversed') }}</dt><dd class="tabular-nums">{{ signedMoney(day.refundsReversed) }}</dd></div>
              <div class="flex justify-between gap-4"><dt>{{ t('fops.cash.settlements') }}</dt><dd class="tabular-nums">{{ signedMoney(day.cashSettlementsPaid) }}</dd></div>
              <div class="flex justify-between gap-4"><dt>{{ t('fops.cash.settlementsReversed') }}</dt><dd class="tabular-nums">{{ signedMoney(day.settlementsReversed) }}</dd></div>
              <div class="flex justify-between gap-4 text-base font-semibold sm:col-span-2"><dt>{{ t('fops.cash.expected') }}</dt><dd id="cash-expected" class="tabular-nums">{{ signedMoney(day.expected) }}</dd></div>
            </dl>
            <p v-if="done" class="mt-3 text-sm font-semibold text-sts-success" role="status">{{ t('fops.saved') }}</p>
            <WegoAlert v-if="error" variant="danger" class="mt-3" role="alert">{{ t(error.key, error.params) }}</WegoAlert>
            <div class="mt-4 flex flex-wrap items-end gap-3">
              <form v-if="canCount && day.state !== 'CLOSED'" id="cash-count" class="flex flex-wrap items-end gap-2" @submit.prevent="count">
                <label class="grid gap-1 text-xs font-semibold">{{ t('fops.cash.counted') }}<input v-model="counted" inputmode="decimal" required :class="FIELD" dir="ltr"></label>
                <label class="grid gap-1 text-xs font-semibold">{{ t('fops.note') }}<input v-model="note" maxlength="500" :class="FIELD" dir="auto"></label>
                <button type="submit" class="rounded-lg bg-sts-ocean px-4 py-2 text-sm font-semibold text-white disabled:opacity-60" :disabled="busy">{{ t('fops.cash.count') }}</button>
              </form>
              <button
                v-if="canConfirm && day.state === 'COUNTED'" id="cash-confirm" type="button"
                class="rounded-lg bg-sts-ocean px-4 py-2 text-sm font-semibold text-white disabled:opacity-60" :disabled="busy"
                @click="act((token) => confirmCash(token, date, currency))"
              >{{ t('fops.cash.confirm') }}</button>
              <form v-if="canConfirm && day.state === 'CLOSED'" id="cash-reopen" class="flex flex-wrap items-end gap-2" @submit.prevent="reopen">
                <label class="grid gap-1 text-xs font-semibold">{{ t('fops.reason') }}<input v-model="reason" maxlength="500" required :class="FIELD" dir="auto"></label>
                <button type="submit" class="rounded-lg border border-sts-border px-4 py-2 text-sm font-semibold" :disabled="busy">{{ t('fops.cash.reopen') }}</button>
              </form>
            </div>
            <h3 class="mt-6 text-sm font-semibold">{{ t('fops.cash.events') }}</h3>
            <p v-if="!day.events.length" class="mt-1 text-sm text-sts-muted">{{ t('fops.none') }}</p>
            <ol v-else class="mt-2 space-y-1 text-sm">
              <li v-for="e in day.events" :key="e.id">
                {{ instantLabel(e.occurredAt) }} · {{ k(`fops.cash.ev.${e.kind}`) }}
                <template v-if="e.kind === 'COUNT' && e.counted && e.difference"> · {{ signedMoney(e.counted) }} ({{ t('fops.cash.difference') }} {{ signedMoney(e.difference) }})</template>
                <span v-if="e.note" class="text-sts-muted" dir="auto"> · {{ e.note }}</span>
                <span v-if="e.actorEmail" class="text-sts-muted"> · {{ t('fops.by', { email: e.actorEmail }) }}</span>
              </li>
            </ol>
          </section>
          <section v-if="recent.length" class="mt-6">
            <h2 class="text-sm font-semibold">{{ t('fops.cash.recent') }}</h2>
            <ul class="mt-2 grid gap-1 text-sm sm:grid-cols-2">
              <li v-for="e in recent" :key="e.id">{{ dateLabel(e.date) }} · {{ k(`fops.cur.${e.currency}`) }} · {{ k(`fops.cash.ev.${e.kind}`) }}</li>
            </ul>
          </section>
        </template>
      </template>
    </div>
  </main>
</template>
