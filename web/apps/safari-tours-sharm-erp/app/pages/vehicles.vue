<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { WegoAlert, WegoBadge } from "@wego/ui";
import type { Supplier, Vehicle, VehicleRequest } from "@wego/api-contract";
import { clearAuthSession, hasPermission, readAuthSession, type AuthSession } from "../composables/useAuthSession";
import { ToursApiError, listSuppliers, listVehicles, saveVehicle } from "../composables/useToursApi";
import { useErpLocale } from "../composables/useErpLocale";
import type { ErpMessageDescriptor } from "../utils/bookingMessages";
import type { ErpMessageKey } from "../utils/erpLocale";
import { registrySaveError } from "../utils/opsRegistry";

/** Vehicle registry (OPS2-E). It is empty until the owner provides vehicles, and everything keeps working while it is. */
const { t, count } = useErpLocale();
useHead(() => ({ title: `${t("ops.vehicles.title")} · Safari Tours Sharm` }));

const router = useRouter();
const session = ref<AuthSession | null>(null);
const rows = ref<Vehicle[]>([]);
const suppliers = ref<Supplier[]>([]);
const state = ref<"loading" | "loaded" | "error">("loading");
const error = ref<ErpMessageDescriptor | null>(null);
const showInactive = ref(false);
const editing = ref<Vehicle | "new" | null>(null);
const saving = ref(false);
const saved = ref(false);
const stale = ref(false);
const TYPES = ["SEDAN", "SUV", "JEEP", "VAN", "MINIBUS", "BUS", "OTHER"] as const;

const allowed = computed(() => hasPermission(session.value, "tours-operator.fleet:manage"));
const canSeeSuppliers = computed(() => hasPermission(session.value, "tours-operator.supplier:manage"));
const visible = computed(() => rows.value.filter((r) => showInactive.value || r.active));
const form = reactive({ label: "", plate: "", vehicleType: "VAN" as VehicleRequest["vehicleType"], seats: "" as string, ownership: "OWNED" as VehicleRequest["ownership"], hiredFromSupplierId: "", active: true });
const supplierName = (id: string | null | undefined) => suppliers.value.find((s) => s.id === id)?.name ?? "—";

function open(target: Vehicle | "new") {
  editing.value = target;
  saved.value = false;
  stale.value = false;
  error.value = null;
  const v = target === "new" ? null : target;
  Object.assign(form, { label: v?.label ?? "", plate: v?.plate ?? "", vehicleType: v?.vehicleType ?? "VAN", seats: v?.seats?.toString() ?? "", ownership: v?.ownership ?? "OWNED", hiredFromSupplierId: v?.hiredFromSupplierId ?? "", active: v?.active ?? true });
}

async function load() {
  if (!session.value || !allowed.value) return;
  state.value = "loading";
  try {
    const token = session.value.token;
    const [list, sup] = await Promise.all([listVehicles(token), canSeeSuppliers.value ? listSuppliers(token) : Promise.resolve([] as Supplier[])]);
    rows.value = list;
    suppliers.value = sup;
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
  if (!session.value || saving.value || !editing.value) return;
  if (form.label.trim() === "" && form.plate.trim() === "") {
    error.value = { key: "ops.hint.vehicleIdentity" };
    return;
  }
  saving.value = true;
  saved.value = false;
  error.value = null;
  stale.value = false;
  const current = editing.value === "new" ? null : editing.value;
  const payload = {
    label: form.label.trim() === "" ? null : form.label.trim(), plate: form.plate.trim() === "" ? null : form.plate.trim(),
    vehicleType: form.vehicleType, seats: Number(form.seats), ownership: form.ownership,
    hiredFromSupplierId: form.ownership === "HIRED" && form.hiredFromSupplierId ? form.hiredFromSupplierId : null,
    active: form.active, ...(current ? { expectedRevision: current.revision } : {}),
  } satisfies VehicleRequest;
  try {
    await saveVehicle(session.value.token, payload, current?.id);
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
const FIELD = "w-full rounded-lg border border-sts-border bg-sts-surface px-3 py-2 text-sm";
const typeLabel = (v: string) => t(`ops.vt.${v}` as ErpMessageKey);
</script>

<template>
  <main class="px-4 py-8 sm:px-8">
    <div class="mx-auto max-w-5xl">
      <header class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 class="text-3xl font-semibold tracking-tight">{{ t('ops.vehicles.title') }}</h1>
          <p class="mt-1 max-w-2xl text-sm text-sts-muted">{{ t('ops.vehicles.intro') }} {{ t('ops.noDelete') }}</p>
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
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.label') }}<input v-model="form.label" maxlength="80" :class="FIELD" dir="auto" aria-describedby="vehicle-identity-hint"></label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.plate') }}<input v-model="form.plate" maxlength="24" :class="FIELD" dir="auto" aria-describedby="vehicle-identity-hint"></label>
          <p id="vehicle-identity-hint" class="text-xs text-sts-muted md:col-span-2">{{ t('ops.hint.vehicleIdentity') }}</p>
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.vehicleType') }}
            <select v-model="form.vehicleType" :class="FIELD"><option v-for="v in TYPES" :key="v" :value="v">{{ typeLabel(v) }}</option></select>
          </label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.seats') }}<input v-model="form.seats" type="number" min="1" max="80" required :class="FIELD" dir="ltr"></label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.ownership') }}
            <select v-model="form.ownership" :class="FIELD"><option value="OWNED">{{ t('ops.own.OWNED') }}</option><option value="HIRED">{{ t('ops.own.HIRED') }}</option></select>
          </label>
          <label v-if="form.ownership === 'HIRED' && canSeeSuppliers" class="grid gap-1 text-sm font-semibold">{{ t('ops.f.lender') }}
            <select v-model="form.hiredFromSupplierId" :class="FIELD"><option value="">{{ t('ops.f.none') }}</option><option v-for="s in suppliers" :key="s.id" :value="s.id">{{ s.name }}</option></select>
          </label>
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
        <p v-else-if="visible.length === 0" class="mt-8 rounded-2xl border border-dashed border-sts-border bg-sts-surface p-8 text-center text-sts-muted" data-testid="vehicles-empty">{{ t('ops.vehicles.empty') }}</p>
        <div v-else class="mt-6 overflow-x-auto rounded-2xl border border-sts-border bg-sts-surface">
          <table class="w-full text-sm">
            <caption class="sr-only">{{ t('ops.vehicles.title') }}</caption>
            <thead class="text-xs text-sts-muted">
              <tr>
                <th scope="col" class="px-4 py-2 text-start">{{ t('ops.f.label') }} / {{ t('ops.f.plate') }}</th>
                <th scope="col" class="px-4 py-2 text-start">{{ t('ops.f.vehicleType') }}</th>
                <th scope="col" class="px-4 py-2 text-start">{{ t('ops.f.seats') }}</th>
                <th scope="col" class="px-4 py-2 text-start">{{ t('ops.f.ownership') }}</th>
                <th scope="col" class="px-4 py-2 text-start">{{ t('ops.f.status') }}</th>
                <th scope="col" class="px-4 py-2"><span class="sr-only">{{ t('ops.edit') }}</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="v in visible" :key="v.id" class="border-t border-sts-border">
                <td class="px-4 py-2 font-semibold" dir="auto">{{ v.display }}</td>
                <td class="px-4 py-2">{{ typeLabel(v.vehicleType) }}</td>
                <td class="px-4 py-2 tabular-nums">{{ count(v.seats) }}</td>
                <td class="px-4 py-2">{{ t(`ops.own.${v.ownership}`) }}<span v-if="v.hiredFromSupplierId" class="block text-xs text-sts-muted" dir="auto">{{ supplierName(v.hiredFromSupplierId) }}</span></td>
                <td class="px-4 py-2"><WegoBadge :tone="v.active ? 'success' : 'neutral'">{{ v.active ? t('ops.active') : t('ops.inactive') }}</WegoBadge></td>
                <td class="px-4 py-2 text-end"><button type="button" class="font-semibold text-sts-ocean-mid hover:underline" @click="open(v)">{{ t('ops.edit') }}</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </div>
  </main>
</template>
