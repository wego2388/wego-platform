<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { WegoAlert } from "@wego/ui";
import { hasPermission, type AuthSession } from "../composables/useAuthSession";
import {
  getFxRateToday, listRefunds, recordRefund, reverseRefund,
  type Booking, type FxRate, type OfficeRefunds, type PaidCurrency, type RefundMethod,
} from "../composables/useToursApi";
import { useErpLocale } from "../composables/useErpLocale";
import type { ErpMessageDescriptor } from "../utils/bookingMessages";
import type { ErpMessageKey } from "../utils/erpLocale";
import { financeErrorMessage, newRequestId, toCents } from "../utils/financeOps";

/**
 * Money returned on a cancelled office booking (OPS2-F). Recording and reversing need the
 * manager permission tours-operator.booking:refund-office; the server caps the amount at what
 * was collected and never touches online card payments.
 */
const props = defineProps<{ booking: Booking; session: AuthSession }>();
const emit = defineEmits<{ changed: [] }>();
const { t, signedMoney, instantLabel } = useErpLocale();

const canRefund = computed(() => hasPermission(props.session, "tours-operator.booking:refund-office"));
const data = ref<OfficeRefunds | null>(null);
const rate = ref<FxRate | null>(null);
const error = ref<ErpMessageDescriptor | null>(null);
const done = ref(false);
const todayRateUsed = ref(false);
const busy = ref(false);
const METHODS: RefundMethod[] = ["CASH", "MOBILE_WALLET", "CARD_TERMINAL", "INSTAPAY", "FAWRY_OFFICE", "BANK_TRANSFER"];
const method = ref<RefundMethod>("CASH");
const currency = ref<PaidCurrency>("EUR");
const amount = ref("");
const reference = ref("");
const reason = ref("");
let key = newRequestId();
const reversedIds = computed(() => new Set((data.value?.entries ?? []).map((e) => e.reversesRefundId).filter(Boolean)));

async function load() {
  data.value = await listRefunds(props.session.token, props.booking.id);
  if (canRefund.value) rate.value = (await getFxRateToday(props.session.token).catch(() => null))?.rate ?? null;
}

async function submit() {
  if (busy.value) return;
  error.value = null;
  done.value = false;
  if (toCents(amount.value) === null) {
    error.value = { key: "fops.amountInvalid" };
    return;
  }
  if (!reason.value.trim()) {
    error.value = { key: "fops.reasonRequired" };
    return;
  }
  busy.value = true;
  try {
    const outcome = await recordRefund(props.session.token, props.booking.id, {
      clientRequestId: key, method: method.value, amount: Number(amount.value), currency: currency.value, reason: reason.value.trim(),
      ...(method.value !== "CASH" ? { reference: reference.value.trim() } : {}),
      ...(currency.value === "EGP" && rate.value ? { fxRateId: rate.value.id } : {}),
    });
    key = newRequestId();
    todayRateUsed.value = outcome.warning === "TODAY_RATE_USED";
    amount.value = "";
    reference.value = "";
    reason.value = "";
    done.value = true;
    await load();
    emit("changed");
  } catch (err) {
    error.value = financeErrorMessage(err);
  } finally {
    busy.value = false;
  }
}

async function reverse(id: string) {
  const why = window.prompt(t("fops.reason"))?.trim();
  if (!why || busy.value) return;
  busy.value = true;
  error.value = null;
  try {
    await reverseRefund(props.session.token, props.booking.id, id, { clientRequestId: newRequestId(), reason: why });
    await load();
    emit("changed");
  } catch (err) {
    error.value = financeErrorMessage(err);
  } finally {
    busy.value = false;
  }
}

onMounted(() => {
  void load().catch((err) => (error.value = financeErrorMessage(err)));
});
const FIELD = "w-full rounded-lg border border-sts-border bg-sts-surface px-3 py-2 text-sm";
const k = (s: string) => t(s as ErpMessageKey);
</script>

<template>
  <section class="mt-6 rounded-2xl border border-sts-border bg-sts-surface p-5" :aria-label="t('fops.refund.title')">
    <h2 class="text-lg font-semibold">{{ t('fops.refund.title') }}</h2>
    <p class="mt-1 text-sm text-sts-muted">{{ t('fops.refund.intro') }}</p>
    <dl v-if="data" class="mt-3 grid gap-2 text-sm sm:grid-cols-3">
      <div><dt class="text-sts-muted">{{ t('fops.refund.collected') }}</dt><dd class="font-semibold tabular-nums">{{ signedMoney(data.position.collected) }}</dd></div>
      <div><dt class="text-sts-muted">{{ t('fops.refund.refunded') }}</dt><dd class="font-semibold tabular-nums">{{ signedMoney(data.position.refunded) }}</dd></div>
      <div><dt class="text-sts-muted">{{ t('fops.refund.refundable') }}</dt><dd id="refund-refundable" class="font-semibold tabular-nums">{{ signedMoney(data.position.refundable) }}</dd></div>
    </dl>
    <p v-if="data && !data.entries.length" class="mt-3 text-sm text-sts-muted">{{ t('fops.refund.empty') }}</p>
    <ul v-else-if="data" class="mt-3 space-y-1 text-sm">
      <li v-for="e in data.entries" :key="e.id">
        {{ instantLabel(e.recordedAt) }} · {{ e.kind === 'REVERSAL' ? t('fops.reversal') : k(`fops.rm.${e.method}`) }} · {{ signedMoney(e.amountPaid) }}
        <span class="text-sts-muted" dir="auto"> · {{ e.reason }}</span>
        <span v-if="e.recordedByEmail" class="text-sts-muted"> · {{ t('fops.by', { email: e.recordedByEmail }) }}</span>
        <template v-if="e.kind === 'REFUND'">
          <span v-if="reversedIds.has(e.id)" class="ms-2 text-xs text-sts-muted">{{ t('fops.reversed') }}</span>
          <button v-else-if="canRefund" type="button" class="ms-2 text-xs font-semibold text-sts-danger hover:underline" @click="reverse(e.id)">{{ t('fops.reverse') }}</button>
        </template>
      </li>
    </ul>
    <p v-if="done" class="mt-3 text-sm font-semibold text-sts-success" role="status">{{ t('fops.saved') }}</p>
    <p v-if="todayRateUsed" id="refund-today-rate" class="mt-1 text-sm font-semibold text-sts-warning" role="status">{{ t('fops.refund.todayRateUsed') }}</p>
    <WegoAlert v-if="error" variant="danger" class="mt-3" role="alert">{{ t(error.key, error.params) }}</WegoAlert>
    <form v-if="canRefund && data && data.position.refundable.amount !== '0.00'" id="refund-form" class="mt-4 grid gap-3 md:grid-cols-3" @submit.prevent="submit">
      <label class="grid gap-1 text-sm font-semibold">{{ t('fops.method') }}<select id="refund-method" v-model="method" :class="FIELD"><option v-for="m in METHODS" :key="m" :value="m">{{ k(`fops.rm.${m}`) }}</option></select></label>
      <label class="grid gap-1 text-sm font-semibold">{{ t('fops.currency') }}
        <select id="refund-currency" v-model="currency" :class="FIELD"><option value="EUR">{{ t('fops.cur.EUR') }}</option><option value="EGP">{{ t('fops.cur.EGP') }}</option></select>
        <span v-if="currency === 'EGP'" class="text-xs font-normal text-sts-muted">{{ rate ? t('fops.refund.egpRate', { rate: rate.egpPerEur }) : t('fops.refund.noRate') }}</span>
      </label>
      <label class="grid gap-1 text-sm font-semibold">{{ t('fops.amount') }}<input id="refund-amount" v-model="amount" inputmode="decimal" required :class="FIELD" dir="ltr"></label>
      <label v-if="method !== 'CASH'" class="grid gap-1 text-sm font-semibold">{{ t('fops.reference') }}<input id="refund-reference" v-model="reference" maxlength="64" required :class="FIELD" dir="ltr"></label>
      <label class="grid gap-1 text-sm font-semibold md:col-span-2">{{ t('fops.reason') }}<input id="refund-reason" v-model="reason" maxlength="500" required :class="FIELD" dir="auto"></label>
      <div class="md:col-span-3"><button type="submit" class="rounded-lg bg-sts-ocean px-4 py-2 text-sm font-semibold text-white disabled:opacity-60" :disabled="busy">{{ t('fops.refund.record') }}</button></div>
    </form>
  </section>
</template>
