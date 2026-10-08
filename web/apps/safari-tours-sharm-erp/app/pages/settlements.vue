<script setup lang="ts">
import CalendarDateField from "../components/CalendarDateField.vue";
import { isCalendarDate } from "../utils/calendarDate";
import { computed, onMounted, reactive, ref, watch } from "vue";
import { WegoAlert, WegoBadge } from "@wego/ui";
import { clearAuthSession, hasPermission, readAuthSession, type AuthSession } from "../composables/useAuthSession";
import {
  ToursApiError, adjustPayable, approveSettlement, getFxRateToday, getStatement, listPayables, paySettlement, reverseSettlementEntry,
  type PaidCurrency, type PartyStatement, type PartySummary, type SettlementMethod, type StatementMovement,
} from "../composables/useToursApi";
import { useErpLocale } from "../composables/useErpLocale";
import type { ErpMessageDescriptor } from "../utils/bookingMessages";
import type { ErpMessageKey } from "../utils/erpLocale";
import { documentPath } from "../utils/documentFormat";
import {
  MANAGER_LIMIT_EGP, financeErrorMessage, monthRange, needsApprovalToday, newRequestId, partyPath, statementSubject, toCents,
} from "../utils/financeOps";

/**
 * Supplier and driver settlements (OPS2-F). The server derives what is owed and enforces the
 * 5000 EGP manager limit, balances and one-time approvals; this page previews and records.
 */
const { t, count, signedMoney, dateLabel, instantLabel } = useErpLocale();
useHead(() => ({ title: `${t("fops.set.title")} · Safari Tours Sharm` }));

const router = useRouter();
const session = ref<AuthSession | null>(null);
const parties = ref<PartySummary[]>([]);
const selected = ref<PartySummary | null>(null);
const statement = ref<PartyStatement | null>(null);
const state = ref<"loading" | "loaded" | "error">("loading");
const range = monthRange();
const from = ref(range.from);
const to = ref(range.to);
const rate = ref<string | null>(null);
const busy = ref(false);
const done = ref(false);
const error = ref<ErpMessageDescriptor | null>(null);
const statementLoading = ref(false);
let statementSeq = 0;
const applied = computed(() => !statementLoading.value && !!statement.value && !!selected.value && statement.value.party.partyId === selected.value.partyId && statement.value.party.partyType === selected.value.partyType && statement.value.from === from.value && statement.value.to === to.value);
watch([from, to], () => { statementSeq++; statementLoading.value = false; done.value = false; });

const canPay = computed(() => hasPermission(session.value, "tours-operator.settlement:pay"));
const canApprove = computed(() => hasPermission(session.value, "tours-operator.settlement:approve"));
const canView = computed(() => canPay.value || canApprove.value || hasPermission(session.value, "tours-operator.payment:view"));
const METHODS: SettlementMethod[] = ["CASH", "INSTAPAY", "MOBILE_WALLET", "BANK_TRANSFER", "OTHER"];

const pay = reactive({ method: "CASH" as SettlementMethod, currency: "EGP" as PaidCurrency, amount: "", reference: "", note: "", approvalId: "", key: newRequestId() });
const approve = reactive({ currency: "EGP" as PaidCurrency, amount: "", note: "", key: newRequestId() });
const adjust = reactive({ kind: "CHARGE" as "CHARGE" | "DEDUCTION", currency: "EGP" as PaidCurrency, amount: "", slotId: "", serviceDate: "", reason: "", key: newRequestId() });

const payNeedsApproval = computed(() => needsApprovalToday(pay.amount, pay.currency, rate.value, statement.value ? statement.value.paidTodayEgp : "0.00"));
const usableApprovals = computed(() =>
  (statement.value?.approvals ?? []).filter((a) => !a.usedByPaymentId && a.amount.currencyCode === pay.currency
    && toCents(a.amount.amount) === toCents(pay.amount)),
);
const printLink = computed(() =>
  applied.value && selected.value && statement.value ? documentPath("settlement-statement", statementSubject(partyPath(selected.value.partyType), selected.value.partyId, statement.value.from, statement.value.to)) : "",
);

function handle(err: unknown) {
  if (err instanceof ToursApiError && err.status === 401) {
    clearAuthSession();
    void router.replace("/login");
    return;
  }
  error.value = financeErrorMessage(err);
}

async function load() {
  if (!session.value || !canView.value) return;
  state.value = "loading";
  try {
    parties.value = await listPayables(session.value.token);
    if (canPay.value) rate.value = (await getFxRateToday(session.value.token).catch(() => null))?.rate?.egpPerEur ?? null;
    state.value = "loaded";
  } catch (err) {
    handle(err);
    state.value = "error";
  }
}

async function open(party: PartySummary) {
  if (busy.value) return;
  selected.value = party;
  statement.value = null;
  error.value = null;
  done.value = false;
  await loadStatement();
}

async function loadStatement() {
  const seq = ++statementSeq;
  if (!session.value || !selected.value || !isCalendarDate(from.value) || !isCalendarDate(to.value) || to.value < from.value) { statementLoading.value = false; error.value = { key: "date.invalid" }; return; }
  const target = selected.value; const start = from.value; const end = to.value;
  statementLoading.value = true;
  try {
    const result = await getStatement(session.value.token, partyPath(target.partyType), target.partyId, start, end);
    if (seq !== statementSeq) return;
    statement.value = result;
  } catch (err) {
    if (seq !== statementSeq) return;
    handle(err);
  } finally { if (seq === statementSeq) statementLoading.value = false; }
}

async function run(action: () => Promise<unknown>, reset: () => void) {
  if (busy.value) return;
  busy.value = true;
  error.value = null;
  done.value = false;
  try {
    await action();
    reset();
    done.value = true;
    await Promise.all([load(), loadStatement()]);
  } catch (err) {
    handle(err);
  } finally {
    busy.value = false;
  }
}

function amountOk(text: string): boolean {
  if (toCents(text) !== null) return true;
  error.value = { key: "fops.amountInvalid" };
  return false;
}

async function submitPay() {
  const p = selected.value;
  if (!session.value || !canPay.value || !applied.value || !p || !amountOk(pay.amount)) return;
  const token = session.value.token;
  await run(
    () => paySettlement(token, partyPath(p.partyType), p.partyId, {
      clientRequestId: pay.key, method: pay.method, currency: pay.currency, amount: Number(pay.amount),
      ...(pay.reference.trim() ? { reference: pay.reference.trim() } : {}), ...(pay.note.trim() ? { note: pay.note.trim() } : {}),
      ...(pay.approvalId ? { approvalId: pay.approvalId } : {}),
    }),
    () => Object.assign(pay, { amount: "", reference: "", note: "", approvalId: "", key: newRequestId() }),
  );
}

async function submitApprove() {
  const p = selected.value;
  if (!session.value || !canApprove.value || !applied.value || !p || !amountOk(approve.amount)) return;
  const token = session.value.token;
  await run(
    () => approveSettlement(token, partyPath(p.partyType), p.partyId, {
      clientRequestId: approve.key, currency: approve.currency, amount: Number(approve.amount), ...(approve.note.trim() ? { note: approve.note.trim() } : {}),
    }),
    () => Object.assign(approve, { amount: "", note: "", key: newRequestId() }),
  );
}

async function submitAdjust() {
  const p = selected.value;
  if (!session.value || !canPay.value || !applied.value || !p || !amountOk(adjust.amount)) return;
  if (!adjust.slotId.trim() && !isCalendarDate(adjust.serviceDate)) { error.value = { key: "date.invalid" }; return; }
  if (!adjust.reason.trim()) {
    error.value = { key: "fops.reasonRequired" };
    return;
  }
  const token = session.value.token;
  await run(
    () => adjustPayable(token, partyPath(p.partyType), p.partyId, {
      clientRequestId: adjust.key, kind: adjust.kind, currency: adjust.currency, amount: Number(adjust.amount), reason: adjust.reason.trim(),
      ...(adjust.slotId.trim() ? { slotId: adjust.slotId.trim() } : { serviceDate: adjust.serviceDate }),
    }),
    () => Object.assign(adjust, { amount: "", slotId: "", reason: "", key: newRequestId() }),
  );
}

function reversible(m: StatementMovement): { entry: "payments" | "adjustments"; id: string } | null {
  if (m.kind === "PAYMENT" && m.payment) return { entry: "payments", id: m.payment.id };
  if ((m.kind === "CHARGE" || m.kind === "DEDUCTION") && m.adjustment) return { entry: "adjustments", id: m.adjustment.id };
  return null;
}
const reversedIds = computed(() => new Set((statement.value?.movements ?? []).flatMap((m) => [m.payment?.reversesPaymentId, m.adjustment?.reversesAdjustmentId]).filter(Boolean)));

async function reverse(m: StatementMovement) {
  const p = selected.value;
  const target = reversible(m);
  if (!session.value || !canApprove.value || !applied.value || !p || !target) return;
  const reason = window.prompt(t("fops.reason"))?.trim();
  if (!reason) return;
  const token = session.value.token;
  await run(() => reverseSettlementEntry(token, partyPath(p.partyType), p.partyId, target.entry, target.id, { clientRequestId: newRequestId(), reason }), () => {});
}

onMounted(() => {
  session.value = readAuthSession();
  if (!session.value) {
    void router.replace("/login");
    return;
  }
  void load();
});
const FIELD = "w-full rounded-lg border border-sts-border bg-sts-surface px-3 py-2 text-sm";
const k = (key: string) => t(key as ErpMessageKey);
</script>

<template>
  <main class="px-4 py-8 sm:px-8">
    <div class="mx-auto max-w-7xl">
      <h1 class="text-3xl font-semibold tracking-tight">{{ t('fops.set.title') }}</h1>
      <p class="mt-1 max-w-3xl text-sm text-sts-muted">{{ t('fops.set.intro', { limit: MANAGER_LIMIT_EGP }) }}</p>
      <WegoAlert v-if="session && !canView" variant="danger" class="mt-6" role="alert">{{ t('common.forbidden') }}</WegoAlert>
      <template v-else-if="canView">
        <p v-if="state === 'loading'" class="mt-6 text-sts-muted" role="status">{{ t('common.loading') }}</p>
        <WegoAlert v-else-if="state === 'error'" variant="danger" class="mt-6" role="alert">{{ t('fops.loadFailed') }}</WegoAlert>
        <p v-else-if="parties.length === 0" class="mt-6 rounded-2xl border border-dashed border-sts-border bg-sts-surface p-8 text-center text-sts-muted">{{ t('fops.set.empty') }}</p>
        <div v-else class="mt-6 overflow-x-auto rounded-2xl border border-sts-border bg-sts-surface">
          <table class="w-full text-sm">
            <caption class="sr-only">{{ t('fops.set.title') }}</caption>
            <thead class="text-xs text-sts-muted">
              <tr>
                <th scope="col" class="px-4 py-2 text-start">{{ t('fops.set.party') }}</th>
                <th scope="col" class="px-4 py-2 text-start">{{ t('fops.set.type') }}</th>
                <th scope="col" class="px-4 py-2 text-end">{{ t('fops.set.balance') }}</th>
                <th scope="col" class="px-4 py-2 text-end">{{ t('fops.set.issues') }}</th>
                <th scope="col" class="px-4 py-2 text-end">{{ t('fops.set.pending') }}</th>
                <th scope="col" class="px-4 py-2"><span class="sr-only">{{ t('fops.set.open') }}</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in parties" :key="p.partyId" class="border-t border-sts-border" :class="selected?.partyId === p.partyId ? 'bg-sts-canvas' : ''">
                <td class="px-4 py-2 font-semibold" dir="auto">{{ p.name }} <bdi v-if="p.code" dir="ltr" class="text-xs text-sts-muted">{{ p.code }}</bdi></td>
                <td class="px-4 py-2">{{ k(`fops.set.${p.partyType}`) }}</td>
                <td class="px-4 py-2 text-end tabular-nums">
                  <span v-for="b in p.balances" :key="b.currency" class="block">{{ signedMoney(b.closing) }}</span>
                  <span v-if="!p.balances.length">—</span>
                </td>
                <td class="px-4 py-2 text-end"><WegoBadge v-if="p.openIssues" tone="warning">{{ count(p.openIssues) }}</WegoBadge></td>
                <td class="px-4 py-2 text-end">{{ p.pendingApprovals ? count(p.pendingApprovals) : '' }}</td>
                <td class="px-4 py-2 text-end"><button type="button" class="font-semibold text-sts-ocean-mid hover:underline" @click="open(p)">{{ t('fops.set.statement') }}</button></td>
              </tr>
            </tbody>
          </table>
        </div>

        <section v-if="selected" class="mt-8 rounded-2xl border border-sts-border bg-sts-surface p-5" :aria-label="t('fops.set.statement')">
          <div class="flex flex-wrap items-end gap-3">
            <h2 class="me-auto text-xl font-semibold" dir="auto">{{ t('fops.set.statement') }} · {{ selected.name }}</h2>
            <CalendarDateField id="settlement-from" v-model="from" :label="t('fops.from')" required :disabled="busy" class="w-full sm:w-80" />
            <CalendarDateField id="settlement-to" v-model="to" :label="t('fops.to')" required :disabled="busy" class="w-full sm:w-80" />
            <button type="button" :disabled="busy" class="rounded-lg border border-sts-border px-3 py-2 text-sm font-semibold" @click="loadStatement">{{ t('fops.apply') }}</button>
            <NuxtLink v-if="applied && (canPay || canApprove)" :to="printLink" class="rounded-lg bg-sts-ocean px-3 py-2 text-sm font-semibold text-white">{{ t('fops.set.print') }}</NuxtLink>
          </div>
          <p v-if="done" class="mt-3 text-sm font-semibold text-sts-success" role="status">{{ t('fops.saved') }}</p>
          <WegoAlert v-if="error" variant="danger" class="mt-3" role="alert">{{ t(error.key, error.params) }}</WegoAlert>

          <p v-if="statementLoading" class="mt-3 text-sm text-sts-muted" role="status">{{ t('common.loading') }}</p>
          <WegoAlert v-else-if="statement && !applied" variant="warning" class="mt-3" role="status">{{ t('inventory.applyDates') }}</WegoAlert>
          <template v-if="applied && statement">
            <dl class="mt-4 grid gap-3 sm:grid-cols-2">
              <div v-for="b in statement.balances" :key="b.currency" class="rounded-xl border border-sts-border p-3 text-sm">
                <dt class="font-semibold">{{ k(`fops.cur.${b.currency}`) }}</dt>
                <dd class="mt-1 grid grid-cols-2 gap-1 tabular-nums">
                  <span>{{ t('fops.set.opening') }}</span><span class="text-end">{{ signedMoney(b.opening) }}</span>
                  <span>{{ t('fops.set.owed') }}</span><span class="text-end">{{ signedMoney(b.owed) }}</span>
                  <span>{{ t('fops.set.paid') }}</span><span class="text-end">{{ signedMoney(b.paid) }}</span>
                  <span class="font-semibold">{{ t('fops.set.closing') }}</span><span class="text-end font-semibold">{{ signedMoney(b.closing) }}</span>
                </dd>
              </div>
            </dl>
            <ul v-if="statement.issues.length" class="mt-4 space-y-1 text-sm">
              <li v-for="i in statement.issues" :key="`${i.slotId}-${i.code}`"><WegoBadge tone="warning">{{ dateLabel(i.date) }}</WegoBadge> {{ k(`fops.issue.${i.code}`) }} <bdi dir="ltr" class="text-xs text-sts-muted">{{ i.slotId }}</bdi></li>
            </ul>
            <h3 class="mt-5 text-sm font-semibold">{{ t('fops.set.movements') }}</h3>
            <p v-if="!statement.movements.length" class="mt-2 text-sm text-sts-muted">{{ t('fops.set.noMovements') }}</p>
            <table v-else class="mt-2 w-full text-sm">
              <tbody>
                <tr v-for="(m, i) in statement.movements" :key="i" class="border-t border-sts-border">
                  <td class="px-2 py-1.5">{{ dateLabel(m.date) }}</td>
                  <td class="px-2 py-1.5">{{ k(`fops.mv.${m.kind}`) }} <span v-if="m.guests" class="text-xs text-sts-muted">· {{ t('common.guests', { count: count(m.guests) }) }}</span></td>
                  <td class="px-2 py-1.5 text-sts-muted" dir="auto">{{ m.adjustment?.reason ?? m.payment?.reason ?? m.payment?.note ?? m.labels.join(', ') }}</td>
                  <td class="px-2 py-1.5 text-end tabular-nums">{{ signedMoney(m.amount) }}</td>
                  <td class="px-2 py-1.5 text-end">
                    <template v-if="reversible(m)">
                      <span v-if="reversedIds.has(reversible(m)!.id)" class="text-xs text-sts-muted">{{ t('fops.reversed') }}</span>
                      <button v-else-if="canApprove" type="button" class="text-xs font-semibold text-sts-danger hover:underline" @click="reverse(m)">{{ t('fops.reverse') }}</button>
                    </template>
                  </td>
                </tr>
              </tbody>
            </table>
            <template v-if="statement.approvals.length">
              <h3 class="mt-5 text-sm font-semibold">{{ t('fops.set.approvals') }}</h3>
              <ul class="mt-2 space-y-1 text-sm">
                <li v-for="a in statement.approvals" :key="a.id">{{ signedMoney(a.amount) }} · {{ instantLabel(a.approvedAt) }} · {{ a.usedByPaymentId ? t('fops.set.used') : t('fops.set.unused') }}</li>
              </ul>
            </template>
          </template>

          <div v-if="applied" class="mt-6 grid gap-4 lg:grid-cols-3">
            <form v-if="canPay" id="pay-form" class="grid gap-2 rounded-xl border border-sts-border p-4" @submit.prevent="submitPay">
              <h3 class="font-semibold">{{ t('fops.set.pay') }}</h3>
              <p v-if="statement" id="paid-today" class="text-xs text-sts-muted">{{ t('fops.set.paidToday', { amount: statement.paidTodayEgp ?? '?', limit: MANAGER_LIMIT_EGP }) }}</p>
              <label class="grid gap-1 text-sm">{{ t('fops.method') }}<select v-model="pay.method" :class="FIELD"><option v-for="m in METHODS" :key="m" :value="m">{{ k(`fops.sm.${m}`) }}</option></select></label>
              <label class="grid gap-1 text-sm">{{ t('fops.currency') }}<select v-model="pay.currency" :class="FIELD"><option value="EGP">{{ t('fops.cur.EGP') }}</option><option value="EUR">{{ t('fops.cur.EUR') }}</option></select></label>
              <label class="grid gap-1 text-sm">{{ t('fops.amount') }}<input id="pay-amount" v-model="pay.amount" inputmode="decimal" required :class="FIELD" dir="ltr"></label>
              <label v-if="pay.method !== 'CASH'" class="grid gap-1 text-sm">{{ t('fops.reference') }}<input v-model="pay.reference" maxlength="64" required :class="FIELD" dir="ltr"></label>
              <p v-if="payNeedsApproval" id="pay-needs-approval" class="text-xs font-semibold text-sts-warning" role="status">{{ t('fops.set.needsApproval', { limit: MANAGER_LIMIT_EGP }) }}</p>
              <label v-if="payNeedsApproval" class="grid gap-1 text-sm">{{ t('fops.set.approval') }}
                <select v-model="pay.approvalId" :class="FIELD">
                  <option value="">{{ t('fops.set.noApproval') }}</option>
                  <option v-for="a in usableApprovals" :key="a.id" :value="a.id">{{ signedMoney(a.amount) }} · {{ instantLabel(a.approvedAt) }}</option>
                </select>
              </label>
              <label class="grid gap-1 text-sm">{{ t('fops.note') }}<input v-model="pay.note" maxlength="500" :class="FIELD" dir="auto"></label>
              <button type="submit" class="rounded-lg bg-sts-ocean px-4 py-2 text-sm font-semibold text-white disabled:opacity-60" :disabled="busy">{{ t('fops.set.pay') }}</button>
            </form>
            <form v-if="canApprove" id="approve-form" class="grid gap-2 rounded-xl border border-sts-border p-4" @submit.prevent="submitApprove">
              <h3 class="font-semibold">{{ t('fops.set.approve') }}</h3>
              <label class="grid gap-1 text-sm">{{ t('fops.currency') }}<select v-model="approve.currency" :class="FIELD"><option value="EGP">{{ t('fops.cur.EGP') }}</option><option value="EUR">{{ t('fops.cur.EUR') }}</option></select></label>
              <label class="grid gap-1 text-sm">{{ t('fops.amount') }}<input v-model="approve.amount" inputmode="decimal" required :class="FIELD" dir="ltr"></label>
              <label class="grid gap-1 text-sm">{{ t('fops.note') }}<input v-model="approve.note" maxlength="500" :class="FIELD" dir="auto"></label>
              <button type="submit" class="rounded-lg bg-sts-ocean px-4 py-2 text-sm font-semibold text-white disabled:opacity-60" :disabled="busy">{{ t('fops.set.approve') }}</button>
            </form>
            <form v-if="canPay" id="adjust-form" class="grid gap-2 rounded-xl border border-sts-border p-4" @submit.prevent="submitAdjust">
              <h3 class="font-semibold">{{ t('fops.set.adjust') }}</h3>
              <label class="grid gap-1 text-sm">{{ t('fops.set.kind') }}<select v-model="adjust.kind" :class="FIELD"><option value="CHARGE">{{ t('fops.mv.CHARGE') }}</option><option value="DEDUCTION">{{ t('fops.mv.DEDUCTION') }}</option></select></label>
              <label class="grid gap-1 text-sm">{{ t('fops.currency') }}<select v-model="adjust.currency" :class="FIELD"><option value="EGP">{{ t('fops.cur.EGP') }}</option><option value="EUR">{{ t('fops.cur.EUR') }}</option></select></label>
              <label class="grid gap-1 text-sm">{{ t('fops.amount') }}<input v-model="adjust.amount" inputmode="decimal" required :class="FIELD" dir="ltr"></label>
              <label class="grid gap-1 text-sm">{{ t('fops.set.slot') }}<input v-model="adjust.slotId" :class="FIELD" dir="ltr"></label>
              <CalendarDateField v-if="!adjust.slotId" id="adjustment-service-date" v-model="adjust.serviceDate" :label="t('fops.date')" required />
              <label class="grid gap-1 text-sm">{{ t('fops.reason') }}<input v-model="adjust.reason" maxlength="500" required :class="FIELD" dir="auto"></label>
              <button type="submit" class="rounded-lg bg-sts-ocean px-4 py-2 text-sm font-semibold text-white disabled:opacity-60" :disabled="busy">{{ t('fops.set.adjust') }}</button>
            </form>
          </div>
        </section>
      </template>
    </div>
  </main>
</template>
