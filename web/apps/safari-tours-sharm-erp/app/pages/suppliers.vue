<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { WegoAlert, WegoBadge } from "@wego/ui";
import type { Supplier, SupplierRequest, Tour } from "@wego/api-contract";
import { clearAuthSession, hasPermission, readAuthSession, type AuthSession } from "../composables/useAuthSession";
import { ToursApiError, listAllStaffTours, listSuppliers, saveSupplier } from "../composables/useToursApi";
import { useErpLocale } from "../composables/useErpLocale";
import type { ErpMessageDescriptor } from "../utils/bookingMessages";
import type { ErpMessageKey } from "../utils/erpLocale";
import { registrySaveError } from "../utils/opsRegistry";

/** Supplier registry (OPS2-E). Records are never deleted; switch one to inactive instead. Prices come with costs (OPS2-F). */
const { t, count } = useErpLocale();
useHead(() => ({ title: `${t("ops.suppliers.title")} · Safari Tours Sharm` }));

const router = useRouter();
const session = ref<AuthSession | null>(null);
const rows = ref<Supplier[]>([]);
const tours = ref<Tour[]>([]);
const state = ref<"loading" | "loaded" | "error">("loading");
const error = ref<ErpMessageDescriptor | null>(null);
const showInactive = ref(false);
const editing = ref<Supplier | "new" | null>(null);
const saving = ref(false);
const saved = ref(false);
const stale = ref(false);

const SERVICE = ["DIVING_SNORKELING", "QUAD_BUGGY_SAFARI", "BOAT", "TRANSPORT", "ATTRACTION", "OTHER"] as const;
const CHANNEL = ["WHATSAPP", "PHONE", "EMAIL", "OTHER"] as const;
const PRICING = ["PER_PERSON", "PER_UNIT", "PER_TRIP", "PERCENT_OF_SALE", "OTHER"] as const;
const CADENCE = ["AFTER_EACH_TRIP", "WEEKLY", "MONTHLY", "OTHER"] as const;
const METHOD = ["CASH", "INSTAPAY", "MOBILE_WALLET", "BANK_TRANSFER", "OTHER"] as const;

const allowed = computed(() => hasPermission(session.value, "tours-operator.supplier:manage"));
const visible = computed(() => rows.value.filter((r) => showInactive.value || r.active));
const tourName = (id: string) => tours.value.find((x) => x.id === id)?.nameEn ?? tours.value.find((x) => x.id === id)?.slug ?? id;

const form = reactive({
  code: "", name: "", serviceType: "OTHER" as SupplierRequest["serviceType"], contactPerson: "", businessPhone: "",
  // Native number inputs emit numbers when populated and a string when cleared.
  confirmationChannel: "" as string, noticeHours: "" as string | number, pricingBasis: "" as string, currency: "" as string,
  settlementCadence: "" as string, paymentMethod: "" as string, cancellationTerms: "", active: true, tourIds: [] as string[],
});

function open(target: Supplier | "new") {
  editing.value = target;
  saved.value = false;
  stale.value = false;
  error.value = null;
  const s = target === "new" ? null : target;
  Object.assign(form, {
    code: s?.code ?? "", name: s?.name ?? "", serviceType: s?.serviceType ?? "OTHER", contactPerson: s?.contactPerson ?? "",
    businessPhone: s?.businessPhone ?? "", confirmationChannel: s?.confirmationChannel ?? "", noticeHours: s?.noticeHours?.toString() ?? "",
    pricingBasis: s?.pricingBasis ?? "", currency: s?.currency ?? "", settlementCadence: s?.settlementCadence ?? "",
    paymentMethod: s?.paymentMethod ?? "", cancellationTerms: s?.cancellationTerms ?? "", active: s?.active ?? true, tourIds: [...(s?.tourIds ?? [])],
  });
}

const orNull = (v: string) => (v.trim() === "" ? null : v.trim());

async function load() {
  if (!session.value || !allowed.value) return;
  state.value = "loading";
  try {
    const token = session.value.token;
    const [list, allTours] = await Promise.all([
      listSuppliers(token),
      hasPermission(session.value, "tours-operator.tour:view") ? listAllStaffTours(token) : Promise.resolve([] as Tour[]),
    ]);
    rows.value = list;
    tours.value = allTours;
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

async function save() {
  if (!session.value || !allowed.value || saving.value || !editing.value) return;
  saving.value = true;
  saved.value = false;
  error.value = null;
  stale.value = false;
  const current = editing.value === "new" ? null : editing.value;
  try {
    const noticeText = String(form.noticeHours).trim();
    const noticeHours = noticeText === "" ? null : Number(noticeText);
    if (noticeHours !== null && (!Number.isInteger(noticeHours) || noticeHours < 0 || noticeHours > 720)) {
      error.value = { key: "ops.invalid" };
      return;
    }
    const payload = {
      code: form.code.trim(), name: form.name.trim(), serviceType: form.serviceType, contactPerson: orNull(form.contactPerson),
      businessPhone: orNull(form.businessPhone), confirmationChannel: orNull(form.confirmationChannel) as SupplierRequest["confirmationChannel"],
      noticeHours, pricingBasis: orNull(form.pricingBasis) as SupplierRequest["pricingBasis"],
      currency: orNull(form.currency) as SupplierRequest["currency"], settlementCadence: orNull(form.settlementCadence) as SupplierRequest["settlementCadence"],
      paymentMethod: orNull(form.paymentMethod) as SupplierRequest["paymentMethod"], cancellationTerms: orNull(form.cancellationTerms),
      active: form.active, tourIds: form.tourIds, ...(current ? { expectedRevision: current.revision } : {}),
    } satisfies SupplierRequest;
    await saveSupplier(session.value.token, payload, current?.id);
    saved.value = true;
    editing.value = null;
    await load();
  } catch (err) {
    error.value = registrySaveError(err);
    stale.value = err instanceof ToursApiError && err.errorCode === "revision_conflict";
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

const key = (prefix: string, value: string) => t(`ops.${prefix}.${value}` as ErpMessageKey);
const FIELD = "w-full rounded-lg border border-sts-border bg-sts-surface px-3 py-2 text-sm";
</script>

<template>
  <main class="px-4 py-8 sm:px-8">
    <div class="mx-auto max-w-6xl">
      <header class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 class="text-3xl font-semibold tracking-tight">{{ t('ops.suppliers.title') }}</h1>
          <p class="mt-1 max-w-2xl text-sm text-sts-muted">{{ t('ops.suppliers.intro') }} {{ t('ops.noDelete') }}</p>
        </div>
        <div v-if="allowed" class="flex items-center gap-3">
          <label class="inline-flex items-center gap-2 text-sm"><input v-model="showInactive" type="checkbox"> {{ t('ops.showInactive') }}</label>
          <button type="button" class="rounded-lg bg-sts-ocean px-4 py-2 text-sm font-semibold text-white" @click="open('new')">{{ t('ops.add') }}</button>
        </div>
      </header>

      <WegoAlert v-if="session && !allowed" variant="danger" class="mt-6" role="alert">{{ t('common.forbidden') }}</WegoAlert>
      <template v-else-if="allowed">
        <p v-if="saved" class="mt-4 text-sm font-semibold text-sts-success" role="status">{{ t('ops.saved') }}</p>

        <form v-if="editing" class="mt-6 grid gap-4 rounded-2xl border border-sts-border bg-sts-surface p-5 md:grid-cols-2" @submit.prevent="save">
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.code') }}
            <input v-model="form.code" required maxlength="24" :class="FIELD" autocomplete="off" dir="ltr">
          </label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.name') }}
            <input v-model="form.name" required maxlength="120" :class="FIELD" dir="auto">
          </label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.serviceType') }}
            <select v-model="form.serviceType" :class="FIELD"><option v-for="v in SERVICE" :key="v" :value="v">{{ key('st', v) }}</option></select>
          </label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.contact') }}
            <input v-model="form.contactPerson" maxlength="120" :class="FIELD" dir="auto">
          </label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.phone') }}
            <input v-model="form.businessPhone" type="tel" maxlength="32" :class="FIELD" dir="ltr" aria-describedby="supplier-phone-hint">
            <span id="supplier-phone-hint" class="text-xs font-normal text-sts-muted">{{ t('ops.hint.phone') }}</span>
          </label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.channel') }}
            <select v-model="form.confirmationChannel" :class="FIELD"><option value="">{{ t('ops.f.none') }}</option><option v-for="v in CHANNEL" :key="v" :value="v">{{ key('ch', v) }}</option></select>
          </label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.notice') }}
            <input v-model="form.noticeHours" type="number" min="0" max="720" step="1" :class="FIELD" dir="ltr">
          </label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.pricing') }}
            <select v-model="form.pricingBasis" :class="FIELD"><option value="">{{ t('ops.f.none') }}</option><option v-for="v in PRICING" :key="v" :value="v">{{ key('pb', v) }}</option></select>
          </label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.currency') }}
            <select v-model="form.currency" :class="FIELD"><option value="">{{ t('ops.f.none') }}</option><option value="EGP">EGP</option><option value="EUR">EUR</option></select>
          </label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.cadence') }}
            <select v-model="form.settlementCadence" :class="FIELD"><option value="">{{ t('ops.f.none') }}</option><option v-for="v in CADENCE" :key="v" :value="v">{{ key('sc', v) }}</option></select>
          </label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.payMethod') }}
            <select v-model="form.paymentMethod" :class="FIELD"><option value="">{{ t('ops.f.none') }}</option><option v-for="v in METHOD" :key="v" :value="v">{{ key('pm', v) }}</option></select>
          </label>
          <label class="grid gap-1 text-sm font-semibold md:col-span-2">{{ t('ops.f.terms') }}
            <textarea v-model="form.cancellationTerms" rows="2" maxlength="1000" :class="FIELD" dir="auto" />
          </label>
          <fieldset v-if="tours.length" class="md:col-span-2">
            <legend class="text-sm font-semibold">{{ t('ops.f.tours') }}</legend>
            <div class="mt-2 grid gap-1 sm:grid-cols-2 lg:grid-cols-3">
              <label v-for="tour in tours" :key="tour.id" class="inline-flex items-center gap-2 text-sm"><input v-model="form.tourIds" type="checkbox" :value="tour.id"> <span dir="auto">{{ tour.nameEn ?? tour.slug }}</span></label>
            </div>
          </fieldset>
          <label class="inline-flex items-center gap-2 text-sm font-semibold"><input v-model="form.active" type="checkbox"> {{ t('ops.active') }}</label>
          <div class="flex flex-wrap items-center gap-3 md:col-span-2">
            <button type="submit" class="rounded-lg bg-sts-ocean px-4 py-2 text-sm font-semibold text-white disabled:opacity-60" :disabled="saving">{{ saving ? t('ops.saving') : t('ops.save') }}</button>
            <button type="button" class="rounded-lg border border-sts-border px-4 py-2 text-sm font-semibold" @click="editing = null">{{ t('ops.cancel') }}</button>
            <button v-if="stale" type="button" class="rounded-lg border border-sts-border px-4 py-2 text-sm font-semibold" @click="editing = null; load()">{{ t('ops.reload') }}</button>
          </div>
          <WegoAlert v-if="error" variant="danger" class="md:col-span-2" role="alert">{{ t(error.key, error.params) }}</WegoAlert>
        </form>

        <p v-if="state === 'loading'" class="mt-8 text-sts-muted" role="status">{{ t('common.loading') }}</p>
        <WegoAlert v-else-if="state === 'error'" variant="danger" class="mt-6" role="alert">{{ t('ops.loadFailed') }}</WegoAlert>
        <p v-else-if="visible.length === 0" class="mt-8 rounded-2xl border border-dashed border-sts-border bg-sts-surface p-8 text-center text-sts-muted">{{ t('ops.suppliers.empty') }}</p>
        <div v-else class="mt-6 overflow-x-auto rounded-2xl border border-sts-border bg-sts-surface">
          <table class="w-full text-sm">
            <caption class="sr-only">{{ t('ops.suppliers.title') }}</caption>
            <thead class="text-xs text-sts-muted">
              <tr>
                <th scope="col" class="px-4 py-2 text-start">{{ t('ops.f.code') }}</th>
                <th scope="col" class="px-4 py-2 text-start">{{ t('ops.f.name') }}</th>
                <th scope="col" class="px-4 py-2 text-start">{{ t('ops.f.serviceType') }}</th>
                <th scope="col" class="px-4 py-2 text-start">{{ t('ops.f.contact') }}</th>
                <th scope="col" class="px-4 py-2 text-start">{{ t('ops.f.channel') }}</th>
                <th scope="col" class="px-4 py-2 text-start">{{ t('ops.f.tours') }}</th>
                <th scope="col" class="px-4 py-2 text-start">{{ t('ops.f.status') }}</th>
                <th scope="col" class="px-4 py-2"><span class="sr-only">{{ t('ops.edit') }}</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in visible" :key="s.id" class="border-t border-sts-border align-top">
                <td class="px-4 py-2 font-mono" dir="ltr">{{ s.code }}</td>
                <td class="px-4 py-2 font-semibold" dir="auto">{{ s.name }}</td>
                <td class="px-4 py-2">{{ key('st', s.serviceType) }}</td>
                <td class="px-4 py-2"><span dir="auto">{{ s.contactPerson ?? '—' }}</span><div v-if="s.businessPhone" dir="ltr"><a :href="`tel:${s.businessPhone}`" class="hover:underline">{{ s.businessPhone }}</a></div></td>
                <td class="px-4 py-2">{{ s.confirmationChannel ? key('ch', s.confirmationChannel) : '—' }}<span v-if="s.noticeHours !== null" class="block text-xs text-sts-muted">{{ count(s.noticeHours) }} h</span></td>
                <td class="px-4 py-2 text-xs"><span v-if="s.tourIds.length === 0" class="text-sts-muted">—</span><span v-for="id in s.tourIds" :key="id" class="me-2 inline-block" dir="auto">{{ tourName(id) }}</span></td>
                <td class="px-4 py-2"><WegoBadge :tone="s.active ? 'success' : 'neutral'">{{ s.active ? t('ops.active') : t('ops.inactive') }}</WegoBadge></td>
                <td class="px-4 py-2 text-end"><button type="button" class="font-semibold text-sts-ocean-mid hover:underline" @click="open(s)">{{ t('ops.edit') }}</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </div>
  </main>
</template>
