<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { WegoAlert, WegoBadge, WegoButton, WegoInput, WegoSelect } from "@wego/ui";
import { hasPermission, type AuthSession } from "../composables/useAuthSession";
import {
  getFxRateToday, listOfficeCollections, quoteOfficeCollection, recordOfficeCollection, reverseOfficeCollection, setFxRate,
  type Booking, type CollectionMethod, type FxRate, type OfficeCollection, type OfficeCollectionQuote, type PaidCurrency,
} from "../composables/useToursApi";
import { useErpLocale } from "../composables/useErpLocale";
import type { ErpMessageDescriptor } from "../utils/bookingMessages";
import { COLLECTION_METHODS, methodNeedsReference, normalizeReference, officeErrorMessage, parseAmount } from "../utils/officeBooking";
import OfficePaymentBadge from "./OfficePaymentBadge.vue";

const props = defineProps<{ booking: Booking; session: AuthSession }>();
const emit = defineEmits<{ changed: [] }>();
const { t, money } = useErpLocale();

const canCollect = computed(() => hasPermission(props.session, "tours-operator.booking:collect-cash"));
const canManageFx = computed(() => hasPermission(props.session, "tours-operator.fx-rate:manage"));
const open = computed(() => props.booking.status === "CONFIRMED" || props.booking.status === "COMPLETED");
const pay = computed(() => props.booking.officePayment);

const entries = ref<OfficeCollection[]>([]);
const rate = ref<FxRate | null>(null);
const error = ref<ErpMessageDescriptor | null>(null);
const notice = ref("");
const busy = ref(false);

const method = ref<CollectionMethod>("CASH_AT_OFFICE");
const currency = ref<PaidCurrency>("EUR");
const amount = ref("");
const reference = ref("");
const touched = ref(false);
const quote = ref<OfficeCollectionQuote | null>(null);
let attemptKey: string = crypto.randomUUID();

const reversing = ref<string | null>(null);
const reverseReason = ref("");
const reverseTouched = ref(false);

const rateInput = ref("");
const rateTouched = ref(false);

const parsed = computed(() => parseAmount(amount.value));
const refCheck = computed(() => (methodNeedsReference(method.value) ? normalizeReference(reference.value) : null));
const amountError = computed(() => (touched.value && !parsed.value ? t("office.collect.amountInvalid") : undefined));
const refError = computed(() => (touched.value && refCheck.value && !refCheck.value.ok ? t(refCheck.value.key) : undefined));
const reversedIds = computed(() => new Set(entries.value.filter((e) => e.reversesCollectionId).map((e) => e.reversesCollectionId)));
const msg = (m: ErpMessageDescriptor | null) => (m ? t(m.key, m.params) : "");

const quoteText = computed(() => {
  const q = quote.value;
  if (!q) return "";
  if (q.status === "OK" && q.settledEur) {
    return q.fxRate ? t("office.collect.settles", { eur: money(q.settledEur), rate: q.fxRate }) : t("office.collect.settlesEur", { eur: money(q.settledEur) });
  }
  if (q.status === "RATE_MISSING") return t("office.collect.quoteRateMissing");
  if (q.status === "TOO_SMALL") return t("office.collect.quoteTooSmall");
  return t("office.collect.quoteExceeds", { outstanding: money(q.outstanding) });
});

async function refresh() {
  try {
    const [list, today] = await Promise.all([
      listOfficeCollections(props.session.token, props.booking.id),
      canCollect.value || canManageFx.value ? getFxRateToday(props.session.token) : Promise.resolve(null),
    ]);
    entries.value = list;
    rate.value = today?.rate ?? null;
  } catch (e) {
    error.value = officeErrorMessage(e);
  }
}

let quoteSeq = 0;
watch([amount, currency, () => props.booking.officePayment?.collected.amount], async () => {
  quote.value = null;
  const p = parsed.value;
  if (!p || !canCollect.value || !open.value) return;
  const seq = ++quoteSeq;
  try {
    const q = await quoteOfficeCollection(props.session.token, props.booking.id, currency.value, p.text);
    if (seq === quoteSeq) quote.value = q;
  } catch { /* the quote is a convenience; recording still validates on the server */ }
});

async function record() {
  touched.value = true;
  error.value = null; notice.value = "";
  const p = parsed.value;
  if (!p || (refCheck.value && !refCheck.value.ok)) return;
  busy.value = true;
  try {
    await recordOfficeCollection(props.session.token, props.booking.id, {
      clientRequestId: attemptKey, method: method.value, amount: p.amount, currency: currency.value,
      ...(refCheck.value?.ok ? { reference: refCheck.value.value } : {}),
    });
    attemptKey = crypto.randomUUID();
    amount.value = ""; reference.value = ""; touched.value = false; quote.value = null;
    notice.value = t("office.collect.recorded");
    await refresh();
    emit("changed");
  } catch (e) {
    error.value = officeErrorMessage(e);
    if (e instanceof Error && "status" in e) attemptKey = crypto.randomUUID();
  } finally { busy.value = false; }
}

async function reverse(id: string) {
  reverseTouched.value = true;
  error.value = null; notice.value = "";
  if (!reverseReason.value.trim()) return;
  busy.value = true;
  try {
    await reverseOfficeCollection(props.session.token, props.booking.id, id, { clientRequestId: crypto.randomUUID(), reason: reverseReason.value.trim() });
    reversing.value = null; reverseReason.value = ""; reverseTouched.value = false;
    await refresh();
    emit("changed");
  } catch (e) { error.value = officeErrorMessage(e); } finally { busy.value = false; }
}

async function saveRate() {
  rateTouched.value = true;
  error.value = null; notice.value = "";
  const v = rateInput.value.trim();
  if (!/^\d{1,4}(\.\d{1,4})?$/.test(v) || Number(v) < 1 || Number(v) > 1000) return;
  busy.value = true;
  try {
    await setFxRate(props.session.token, Number(v));
    rateInput.value = ""; rateTouched.value = false;
    notice.value = t("office.fx.saved");
    await refresh();
  } catch (e) { error.value = officeErrorMessage(e); } finally { busy.value = false; }
}

onMounted(refresh);
watch(() => props.booking.id, refresh);
</script>

<template>
  <section class="mt-6 rounded-2xl border border-sts-border bg-sts-surface px-5 py-4 shadow-sm" aria-labelledby="office-pay-heading">
    <h2 id="office-pay-heading" class="text-sm font-semibold text-sts-muted uppercase tracking-wide">{{ t('office.collect.title') }}</h2>
    <p class="mt-1 text-xs text-sts-muted">{{ t('office.collect.intro') }}</p>

    <div v-if="pay" class="mt-3 flex flex-wrap items-center gap-x-6 gap-y-2 text-sm">
      <OfficePaymentBadge :booking="booking" />
      <span>{{ t('office.collect.total') }}: <strong class="money tabular-nums">{{ money(booking.totalPrice) }}</strong></span>
      <span>{{ t('office.collect.collected') }}: <strong class="money tabular-nums">{{ money(pay.collected) }}</strong></span>
      <span>{{ t('office.collect.outstanding') }}: <strong class="money tabular-nums">{{ money(pay.outstanding) }}</strong></span>
    </div>
    <WegoAlert v-if="pay?.cashToReturn" variant="warning" class="mt-3">{{ t('office.collect.cashToReturnNote') }}</WegoAlert>
    <WegoAlert v-if="error" variant="danger" class="mt-3" role="alert">{{ msg(error) }}</WegoAlert>
    <WegoAlert v-if="notice" variant="success" class="mt-3" role="status">{{ notice }}</WegoAlert>

    <p v-if="entries.length === 0" class="mt-4 text-sm text-sts-muted">{{ t('office.collect.empty') }}</p>
    <ol v-else class="mt-4 space-y-3">
      <li v-for="e in entries" :key="e.id" class="rounded-xl border border-sts-border/60 px-3 py-2 text-sm">
        <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
          <strong>{{ t(`office.collect.method.${e.method}`) }}</strong>
          <span class="tabular-nums">{{ e.kind === 'REVERSAL' ? '−' : '' }}{{ money(e.amount) }}</span>
          <WegoBadge v-if="e.kind === 'REVERSAL'" tone="danger">{{ t('office.collect.reversed') }}</WegoBadge>
          <WegoBadge v-else-if="reversedIds.has(e.id)" tone="neutral">{{ t('office.collect.reversed') }}</WegoBadge>
        </div>
        <p v-if="e.amountPaid.currencyCode !== 'EUR'" class="text-xs text-sts-muted tabular-nums">
          {{ t('office.collect.paidAs', { paid: money(e.amountPaid) }) }}<span v-if="e.fxRate"> · {{ e.fxRate }} EGP/EUR</span>
        </p>
        <p v-if="e.reference" class="text-xs text-sts-muted" dir="ltr">{{ t('office.collect.refLabel', { ref: e.reference }) }}</p>
        <p v-if="e.reason" class="text-xs">{{ t('office.collect.reversalOf', { reason: e.reason }) }}</p>
        <div v-if="canCollect && e.kind === 'COLLECTION' && !reversedIds.has(e.id)" class="mt-2">
          <WegoButton v-if="reversing !== e.id" type="button" variant="secondary" size="sm" @click="reversing = e.id; reverseReason = ''; reverseTouched = false">{{ t('office.collect.reverse') }}</WegoButton>
          <div v-else class="flex flex-wrap items-end gap-2">
            <WegoInput :id="`reverse-${e.id}`" v-model="reverseReason" :label="t('office.collect.reverseReason')" class="min-w-[14rem] flex-1" :error="reverseTouched && !reverseReason.trim() ? t('office.collect.reverseNeedsReason') : undefined" />
            <WegoButton type="button" variant="secondary" size="sm" :disabled="busy" @click="reverse(e.id)">{{ t('office.collect.reverseConfirm') }}</WegoButton>
          </div>
        </div>
      </li>
    </ol>

    <form v-if="canCollect && open && pay && pay.state !== 'PAID'" class="mt-5 grid gap-4 sm:grid-cols-2" novalidate @submit.prevent="record">
      <WegoSelect id="collect-method" v-model="method" :label="t('office.collect.method')">
        <option v-for="m in COLLECTION_METHODS" :key="m" :value="m">{{ t(`office.collect.method.${m}`) }}</option>
      </WegoSelect>
      <WegoSelect id="collect-currency" v-model="currency" :label="t('office.collect.currency')">
        <option value="EUR">EUR</option>
        <option value="EGP">EGP</option>
      </WegoSelect>
      <WegoInput id="collect-amount" v-model="amount" inputmode="decimal" dir="ltr" :label="t('office.collect.amount')" required :error="amountError" />
      <WegoInput v-if="methodNeedsReference(method)" id="collect-reference" v-model="reference" dir="ltr" maxlength="64" autocomplete="off" :label="t('office.collect.reference')" :help="t('office.collect.referenceHelp')" required :error="refError" />
      <p v-if="quoteText" class="text-sm sm:col-span-2" :class="quote?.status === 'OK' ? 'text-sts-muted' : 'font-semibold text-sts-danger'" aria-live="polite">{{ quoteText }}</p>
      <div class="sm:col-span-2">
        <WegoButton type="submit" variant="primary" :disabled="busy">{{ busy ? t('office.collect.recording') : t('office.collect.record') }}</WegoButton>
      </div>
    </form>

    <div v-if="canCollect || canManageFx" class="mt-5 border-t border-sts-border pt-4 text-sm">
      <h3 class="font-semibold">{{ t('office.fx.title') }}</h3>
      <p class="mt-1" :class="rate ? '' : 'text-sts-danger font-semibold'">{{ rate ? t('office.fx.value', { rate: rate.egpPerEur }) : t('office.fx.notSet') }}</p>
      <form v-if="canManageFx" class="mt-2 flex flex-wrap items-end gap-2" novalidate @submit.prevent="saveRate">
        <WegoInput id="fx-rate" v-model="rateInput" inputmode="decimal" dir="ltr" :label="t('office.fx.setLabel')" :error="rateTouched && !/^\d{1,4}(\.\d{1,4})?$/.test(rateInput.trim()) ? t('office.fx.invalid') : undefined" />
        <WegoButton type="submit" variant="secondary" size="sm" :disabled="busy">{{ t('office.fx.set') }}</WegoButton>
      </form>
      <p class="mt-2 text-xs text-sts-muted">{{ t('office.fx.policy') }}</p>
    </div>
  </section>
</template>
