<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { WegoAlert, WegoBadge } from "@wego/ui";
import { clearAuthSession, hasPermission, readAuthSession, type AuthSession } from "../composables/useAuthSession";
import {
  ToursApiError, createCost, endCost, listAllStaffTours, listCosts, listDrivers, listSuppliers, replaceCost,
  type CostBasis, type CostCategory, type CostComponent, type CostComponentRequest, type Driver, type PaidCurrency, type Supplier, type Tour,
} from "../composables/useToursApi";
import { useErpLocale } from "../composables/useErpLocale";
import type { ErpMessageDescriptor } from "../utils/bookingMessages";
import type { ErpMessageKey } from "../utils/erpLocale";
import { financeErrorMessage, todayCairo, toCents } from "../utils/financeOps";

/**
 * Tour costs and driver trip rates (OPS2-F). A cost is never edited: "change from a date"
 * ends it the day before and adds the new value; "end" stops it. Readable with the finance
 * permission; changing needs tours-operator.cost:manage.
 */
const { t, money, dateLabel } = useErpLocale();
useHead(() => ({ title: `${t("fops.costs.title")} · Safari Tours Sharm` }));

const router = useRouter();
const session = ref<AuthSession | null>(null);
const rows = ref<CostComponent[]>([]);
const tours = ref<Tour[]>([]);
const drivers = ref<Driver[]>([]);
const suppliers = ref<Supplier[]>([]);
const state = ref<"loading" | "loaded" | "error">("loading");
const showEnded = ref(false);
const editing = ref<"new" | CostComponent | null>(null);
const ending = ref<CostComponent | null>(null);
const lastDay = ref(todayCairo());
const saving = ref(false);
const saved = ref(false);
const error = ref<ErpMessageDescriptor | null>(null);
// One idempotency key per form attempt (review M3): a retried save never creates a second component.
let requestKey = crypto.randomUUID();

const CATEGORIES: CostCategory[] = ["SUPPLIER", "OWN_EXTRA", "FIXED", "DRIVER"];
const BASES: CostBasis[] = ["PER_PERSON", "PER_UNIT", "PER_DEPARTURE"];
const canManage = computed(() => hasPermission(session.value, "tours-operator.cost:manage"));
const canView = computed(() => canManage.value || hasPermission(session.value, "tours-operator.payment:view"));

const form = reactive({
  owner: "tour" as "tour" | "driver", tourId: "", driverId: "", category: "SUPPLIER" as CostCategory, label: "",
  basis: "PER_PERSON" as CostBasis, currency: "EGP" as PaidCurrency, amount: "", childAmount: "", supplierId: "", validFrom: todayCairo(),
  validUntil: "", note: "",
});
const restatesPast = computed(() => !!form.validFrom && form.validFrom < todayCairo());
const perDepartureOnly = computed(() => form.category === "FIXED" || form.category === "DRIVER");

const tourName = (id: string | null) => (id ? (tours.value.find((x) => x.id === id)?.nameEn ?? tours.value.find((x) => x.id === id)?.slug ?? id.slice(0, 8)) : "—");
const driverName = (id: string | null) => (id ? (drivers.value.find((x) => x.id === id)?.name ?? id.slice(0, 8)) : "—");
const supplierName = (id: string | null) => (id ? (suppliers.value.find((x) => x.id === id)?.name ?? id.slice(0, 8)) : t("fops.costs.anySupplier"));

const groups = computed(() => {
  const map = new Map<string, { title: string; items: CostComponent[] }>();
  for (const c of rows.value) {
    const key = c.tourId ?? `driver:${c.driverId}`;
    const title = c.tourId ? tourName(c.tourId) : `${t("fops.costs.driver")}: ${driverName(c.driverId)}`;
    if (!map.has(key)) map.set(key, { title, items: [] });
    map.get(key)!.items.push(c);
  }
  return [...map.values()].sort((a, b) => a.title.localeCompare(b.title));
});

async function load() {
  if (!session.value || !canView.value) return;
  state.value = "loading";
  try {
    rows.value = await listCosts(session.value.token, showEnded.value);
    // Names are labels only: missing permissions just show short ids.
    if (hasPermission(session.value, "tours-operator.tour:view")) tours.value = await listAllStaffTours(session.value.token).catch(() => []);
    if (hasPermission(session.value, "tours-operator.fleet:manage")) drivers.value = await listDrivers(session.value.token).catch(() => []);
    if (hasPermission(session.value, "tours-operator.supplier:manage")) suppliers.value = await listSuppliers(session.value.token).catch(() => []);
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

function open(target: "new" | CostComponent) {
  editing.value = target;
  requestKey = crypto.randomUUID();
  ending.value = null;
  error.value = null;
  saved.value = false;
  const c = target === "new" ? null : target;
  Object.assign(form, {
    owner: c?.driverId ? "driver" : "tour", tourId: c?.tourId ?? "", driverId: c?.driverId ?? "", category: c?.category ?? "SUPPLIER",
    label: c?.label ?? "", basis: c?.basis ?? "PER_PERSON", currency: (c?.amount.currencyCode ?? "EGP") as PaidCurrency,
    amount: c?.amount.amount ?? "", childAmount: c?.childAmount?.amount ?? "", supplierId: c?.supplierId ?? "", validFrom: todayCairo(), validUntil: "", note: "",
  });
}

function payload(): CostComponentRequest | null {
  const amountOk = form.amount.trim() === "0" || form.amount.trim() === "0.00" || toCents(form.amount) !== null;
  const childOk = form.childAmount.trim() === "" || form.childAmount.trim() === "0" || toCents(form.childAmount) !== null;
  if (!amountOk || !childOk || !form.label.trim()) {
    error.value = { key: "fops.amountInvalid" };
    return null;
  }
  const driver = form.category === "DRIVER";
  return {
    clientRequestId: requestKey,
    tourId: driver ? null : form.tourId || null,
    driverId: driver ? form.driverId || null : null,
    category: form.category,
    label: form.label.trim(),
    basis: perDepartureOnly.value ? "PER_DEPARTURE" : form.basis,
    currency: form.currency,
    amount: Number(form.amount),
    childAmount: form.basis === "PER_PERSON" && !perDepartureOnly.value && form.childAmount.trim() ? Number(form.childAmount) : null,
    supplierId: form.category === "SUPPLIER" && form.supplierId ? form.supplierId : null,
    validFrom: form.validFrom,
    validUntil: form.validUntil || null,
    note: form.note.trim() || null,
  };
}

async function save() {
  if (!session.value || saving.value || !editing.value) return;
  error.value = null;
  const body = payload();
  if (!body) return;
  saving.value = true;
  try {
    if (editing.value === "new") await createCost(session.value.token, body);
    else await replaceCost(session.value.token, editing.value.id, body);
    editing.value = null;
    saved.value = true;
    await load();
  } catch (err) {
    error.value = financeErrorMessage(err);
  } finally {
    saving.value = false;
  }
}

async function confirmEnd() {
  if (!session.value || !ending.value || saving.value) return;
  saving.value = true;
  error.value = null;
  try {
    await endCost(session.value.token, ending.value.id, lastDay.value);
    ending.value = null;
    saved.value = true;
    await load();
  } catch (err) {
    error.value = financeErrorMessage(err);
  } finally {
    saving.value = false;
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
const FIELD = "w-full rounded-lg border border-sts-border bg-sts-surface px-3 py-2 text-sm";
const cat = (v: string) => t(`fops.cat.${v}` as ErpMessageKey);
const basis = (v: string) => t(`fops.basis.${v}` as ErpMessageKey);
</script>

<template>
  <main class="px-4 py-8 sm:px-8">
    <div class="mx-auto max-w-6xl">
      <header class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 class="text-3xl font-semibold tracking-tight">{{ t('fops.costs.title') }}</h1>
          <p class="mt-1 max-w-3xl text-sm text-sts-muted">{{ t('fops.costs.intro') }}</p>
        </div>
        <div v-if="canView" class="flex items-center gap-3">
          <label class="inline-flex items-center gap-2 text-sm"><input v-model="showEnded" type="checkbox" @change="load"> {{ t('fops.costs.showEnded') }}</label>
          <button v-if="canManage" type="button" class="rounded-lg bg-sts-ocean px-4 py-2 text-sm font-semibold text-white" @click="open('new')">{{ t('fops.costs.add') }}</button>
        </div>
      </header>

      <WegoAlert v-if="session && !canView" variant="danger" class="mt-6" role="alert">{{ t('common.forbidden') }}</WegoAlert>
      <template v-else-if="canView">
        <p v-if="!canManage" class="mt-4 text-sm text-sts-muted">{{ t('fops.readOnly') }}</p>
        <p v-if="saved" class="mt-4 text-sm font-semibold text-sts-success" role="status">{{ t('fops.saved') }}</p>
        <WegoAlert v-if="error && !editing && !ending" variant="danger" class="mt-4" role="alert">{{ t(error.key, error.params) }}</WegoAlert>

        <form v-if="editing" id="cost-form" class="mt-6 grid gap-4 rounded-2xl border border-sts-border bg-sts-surface p-5 md:grid-cols-3" @submit.prevent="save">
          <label class="grid gap-1 text-sm font-semibold">{{ t('fops.costs.category') }}
            <select id="cost-category" v-model="form.category" :class="FIELD" :disabled="editing !== 'new'"><option v-for="c in CATEGORIES" :key="c" :value="c">{{ cat(c) }}</option></select>
          </label>
          <label v-if="form.category !== 'DRIVER'" class="grid gap-1 text-sm font-semibold">{{ t('fops.costs.tour') }}
            <select id="cost-tour" v-model="form.tourId" required :class="FIELD" :disabled="editing !== 'new'">
              <option value="" disabled>—</option>
              <option v-for="x in tours" :key="x.id" :value="x.id">{{ x.nameEn ?? x.slug }}</option>
            </select>
          </label>
          <label v-else class="grid gap-1 text-sm font-semibold">{{ t('fops.costs.driver') }}
            <select id="cost-driver" v-model="form.driverId" required :class="FIELD" :disabled="editing !== 'new'">
              <option value="" disabled>—</option>
              <option v-for="d in drivers" :key="d.id" :value="d.id">{{ d.name }}</option>
            </select>
          </label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('fops.costs.label') }}<input id="cost-label" v-model="form.label" required maxlength="80" :class="FIELD" dir="auto"></label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('fops.costs.basis') }}
            <select v-model="form.basis" :class="FIELD" :disabled="perDepartureOnly"><option v-for="b in BASES" :key="b" :value="b">{{ basis(b) }}</option></select>
          </label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('fops.currency') }}
            <select id="cost-currency" v-model="form.currency" :class="FIELD"><option value="EGP">{{ t('fops.cur.EGP') }}</option><option value="EUR">{{ t('fops.cur.EUR') }}</option></select>
          </label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('fops.amount') }}<input id="cost-amount" v-model="form.amount" required inputmode="decimal" :class="FIELD" dir="ltr"></label>
          <label v-if="form.basis === 'PER_PERSON' && !perDepartureOnly" class="grid gap-1 text-sm font-semibold">{{ t('fops.costs.child') }}
            <input v-model="form.childAmount" inputmode="decimal" :class="FIELD" dir="ltr">
            <span class="text-xs font-normal text-sts-muted">{{ t('fops.costs.childHint') }}</span>
          </label>
          <label v-if="form.category === 'SUPPLIER'" class="grid gap-1 text-sm font-semibold">{{ t('fops.costs.supplier') }}
            <select v-model="form.supplierId" :class="FIELD"><option value="">{{ t('fops.costs.anySupplier') }}</option><option v-for="s in suppliers" :key="s.id" :value="s.id">{{ s.name }}</option></select>
          </label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('fops.costs.validFrom') }}<input id="cost-from" v-model="form.validFrom" type="date" required :class="FIELD" dir="ltr"></label>
          <p v-if="restatesPast" id="cost-past-warning" class="text-sm font-semibold text-sts-warning md:col-span-3" role="alert">{{ t('fops.costs.pastWarning') }}</p>
          <label v-if="editing === 'new'" class="grid gap-1 text-sm font-semibold">{{ t('fops.costs.validUntil') }}<input v-model="form.validUntil" type="date" :class="FIELD" dir="ltr"></label>
          <label class="grid gap-1 text-sm font-semibold md:col-span-3">{{ t('fops.note') }}<input v-model="form.note" maxlength="500" :class="FIELD" dir="auto"></label>
          <div class="flex flex-wrap gap-3 md:col-span-3">
            <button type="submit" class="rounded-lg bg-sts-ocean px-4 py-2 text-sm font-semibold text-white disabled:opacity-60" :disabled="saving">{{ saving ? t('fops.saving') : t('fops.save') }}</button>
            <button type="button" class="rounded-lg border border-sts-border px-4 py-2 text-sm font-semibold" @click="editing = null">{{ t('fops.cancel') }}</button>
          </div>
          <WegoAlert v-if="error" variant="danger" class="md:col-span-3" role="alert">{{ t(error.key, error.params) }}</WegoAlert>
        </form>

        <form v-if="ending" class="mt-6 flex flex-wrap items-end gap-3 rounded-2xl border border-sts-border bg-sts-surface p-5" @submit.prevent="confirmEnd">
          <p class="w-full text-sm font-semibold" dir="auto">{{ ending.label }}</p>
          <label class="grid gap-1 text-sm font-semibold">{{ t('fops.costs.lastDay') }}<input v-model="lastDay" type="date" required :class="FIELD" dir="ltr"></label>
          <button type="submit" class="rounded-lg bg-sts-ocean px-4 py-2 text-sm font-semibold text-white disabled:opacity-60" :disabled="saving">{{ t('fops.costs.end') }}</button>
          <button type="button" class="rounded-lg border border-sts-border px-4 py-2 text-sm font-semibold" @click="ending = null">{{ t('fops.cancel') }}</button>
          <WegoAlert v-if="error" variant="danger" class="w-full" role="alert">{{ t(error.key, error.params) }}</WegoAlert>
        </form>

        <p v-if="state === 'loading'" class="mt-8 text-sts-muted" role="status">{{ t('common.loading') }}</p>
        <WegoAlert v-else-if="state === 'error'" variant="danger" class="mt-6" role="alert">{{ t('fops.loadFailed') }}</WegoAlert>
        <p v-else-if="groups.length === 0" class="mt-8 rounded-2xl border border-dashed border-sts-border bg-sts-surface p-8 text-center text-sts-muted">{{ t('fops.costs.empty') }}</p>
        <section v-for="g in groups" v-else :key="g.title" class="mt-6 overflow-x-auto rounded-2xl border border-sts-border bg-sts-surface">
          <h2 class="px-4 pt-4 text-lg font-semibold" dir="auto">{{ g.title }}</h2>
          <table class="mt-2 w-full text-sm">
            <thead class="text-xs text-sts-muted">
              <tr>
                <th scope="col" class="px-4 py-2 text-start">{{ t('fops.costs.category') }}</th>
                <th scope="col" class="px-4 py-2 text-start">{{ t('fops.costs.label') }}</th>
                <th scope="col" class="px-4 py-2 text-end">{{ t('fops.amount') }}</th>
                <th scope="col" class="px-4 py-2 text-start">{{ t('fops.costs.supplier') }}</th>
                <th scope="col" class="px-4 py-2 text-start">{{ t('fops.costs.validFrom') }} – {{ t('fops.costs.validUntil') }}</th>
                <th scope="col" class="px-4 py-2"><span class="sr-only">{{ t('fops.costs.change') }}</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="c in g.items" :key="c.id" class="border-t border-sts-border" :class="c.ended ? 'text-sts-muted' : ''">
                <td class="px-4 py-2">{{ cat(c.category) }}</td>
                <td class="px-4 py-2" dir="auto">{{ c.label }}</td>
                <td class="px-4 py-2 text-end tabular-nums">
                  {{ money(c.amount) }} <span class="text-xs text-sts-muted">{{ basis(c.basis) }}</span>
                  <span v-if="c.childAmount" class="block text-xs text-sts-muted">{{ t('fops.costs.child') }}: {{ money(c.childAmount) }}</span>
                </td>
                <td class="px-4 py-2" dir="auto">{{ c.category === 'SUPPLIER' ? supplierName(c.supplierId) : '—' }}</td>
                <td class="px-4 py-2">
                  {{ dateLabel(c.validFrom) }} – {{ c.validUntil ? dateLabel(c.validUntil) : t('fops.costs.open') }}
                  <WegoBadge :tone="c.ended ? 'neutral' : 'success'" class="ms-2">{{ c.ended ? t('fops.costs.ended') : t('fops.costs.active') }}</WegoBadge>
                </td>
                <td class="px-4 py-2 text-end whitespace-nowrap">
                  <template v-if="canManage && !c.ended">
                    <button type="button" class="font-semibold text-sts-ocean-mid hover:underline" @click="open(c)">{{ t('fops.costs.change') }}</button>
                    <button type="button" class="ms-3 font-semibold text-sts-danger hover:underline" @click="ending = c; editing = null; error = null">{{ t('fops.costs.end') }}</button>
                  </template>
                </td>
              </tr>
            </tbody>
          </table>
        </section>
      </template>
    </div>
  </main>
</template>
