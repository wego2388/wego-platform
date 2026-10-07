<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { WegoAlert, WegoBadge } from "@wego/ui";
import type { Driver, DriverRequest } from "@wego/api-contract";
import { clearAuthSession, hasPermission, readAuthSession, type AuthSession } from "../composables/useAuthSession";
import { ToursApiError, listDrivers, saveDriver } from "../composables/useToursApi";
import { useErpLocale } from "../composables/useErpLocale";
import type { ErpMessageDescriptor } from "../utils/bookingMessages";
import type { ErpMessageKey } from "../utils/erpLocale";
import { licenceState, registrySaveError } from "../utils/opsRegistry";

/** Driver registry (OPS2-E): name, work phone, engagement and licence date only. A driver whose licence has expired cannot be assigned. */
const { t, dateLabel } = useErpLocale();
useHead(() => ({ title: `${t("ops.drivers.title")} · Safari Tours Sharm` }));

const router = useRouter();
const session = ref<AuthSession | null>(null);
const rows = ref<Driver[]>([]);
const state = ref<"loading" | "loaded" | "error">("loading");
const error = ref<ErpMessageDescriptor | null>(null);
const showInactive = ref(false);
const editing = ref<Driver | "new" | null>(null);
const saving = ref(false);
const saved = ref(false);
const stale = ref(false);
const today = new Date().toLocaleDateString("sv-SE", { timeZone: "Africa/Cairo" });
const ENGAGEMENT = ["PER_TRIP", "MONTHLY", "DAILY", "OTHER"] as const;

const allowed = computed(() => hasPermission(session.value, "tours-operator.fleet:manage"));
const visible = computed(() => rows.value.filter((r) => showInactive.value || r.active));
const form = reactive({ name: "", workPhone: "", engagementType: "PER_TRIP" as DriverRequest["engagementType"], licenceValidUntil: "", active: true });

function open(target: Driver | "new") {
  editing.value = target;
  saved.value = false;
  stale.value = false;
  error.value = null;
  const d = target === "new" ? null : target;
  Object.assign(form, { name: d?.name ?? "", workPhone: d?.workPhone ?? "", engagementType: d?.engagementType ?? "PER_TRIP", licenceValidUntil: d?.licenceValidUntil ?? "", active: d?.active ?? true });
}

async function load() {
  if (!session.value || !allowed.value) return;
  state.value = "loading";
  try {
    rows.value = await listDrivers(session.value.token);
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
  saving.value = true;
  saved.value = false;
  error.value = null;
  stale.value = false;
  const current = editing.value === "new" ? null : editing.value;
  const payload = {
    name: form.name.trim(), workPhone: form.workPhone.trim() === "" ? null : form.workPhone.trim(), engagementType: form.engagementType,
    licenceValidUntil: form.licenceValidUntil, active: form.active, ...(current ? { expectedRevision: current.revision } : {}),
  } satisfies DriverRequest;
  try {
    await saveDriver(session.value.token, payload, current?.id);
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
const engagement = (v: string) => t(`ops.eng.${v}` as ErpMessageKey);
</script>

<template>
  <main class="px-4 py-8 sm:px-8">
    <div class="mx-auto max-w-5xl">
      <header class="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 class="text-3xl font-semibold tracking-tight">{{ t('ops.drivers.title') }}</h1>
          <p class="mt-1 max-w-2xl text-sm text-sts-muted">{{ t('ops.drivers.intro') }} {{ t('ops.noDelete') }}</p>
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
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.name') }}<input v-model="form.name" required maxlength="120" :class="FIELD" dir="auto"></label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.phone') }}
            <input v-model="form.workPhone" type="tel" maxlength="32" :class="FIELD" dir="ltr" aria-describedby="driver-phone-hint">
            <span id="driver-phone-hint" class="text-xs font-normal text-sts-muted">{{ t('ops.hint.phone') }}</span>
          </label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.engagement') }}
            <select v-model="form.engagementType" :class="FIELD"><option v-for="v in ENGAGEMENT" :key="v" :value="v">{{ engagement(v) }}</option></select>
          </label>
          <label class="grid gap-1 text-sm font-semibold">{{ t('ops.f.licence') }}<input v-model="form.licenceValidUntil" type="date" required :class="FIELD" dir="ltr"></label>
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
        <p v-else-if="visible.length === 0" class="mt-8 rounded-2xl border border-dashed border-sts-border bg-sts-surface p-8 text-center text-sts-muted">{{ t('ops.drivers.empty') }}</p>
        <div v-else class="mt-6 overflow-x-auto rounded-2xl border border-sts-border bg-sts-surface">
          <table class="w-full text-sm">
            <caption class="sr-only">{{ t('ops.drivers.title') }}</caption>
            <thead class="text-xs text-sts-muted">
              <tr>
                <th scope="col" class="px-4 py-2 text-start">{{ t('ops.f.name') }}</th>
                <th scope="col" class="px-4 py-2 text-start">{{ t('ops.f.phone') }}</th>
                <th scope="col" class="px-4 py-2 text-start">{{ t('ops.f.engagement') }}</th>
                <th scope="col" class="px-4 py-2 text-start">{{ t('ops.f.licence') }}</th>
                <th scope="col" class="px-4 py-2 text-start">{{ t('ops.f.status') }}</th>
                <th scope="col" class="px-4 py-2"><span class="sr-only">{{ t('ops.edit') }}</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="d in visible" :key="d.id" class="border-t border-sts-border">
                <td class="px-4 py-2 font-semibold" dir="auto">{{ d.name }}</td>
                <td class="px-4 py-2" dir="ltr"><a v-if="d.workPhone" :href="`tel:${d.workPhone}`" class="hover:underline">{{ d.workPhone }}</a><span v-else>—</span></td>
                <td class="px-4 py-2">{{ engagement(d.engagementType) }}</td>
                <td class="px-4 py-2">
                  {{ dateLabel(d.licenceValidUntil) }}
                  <WegoBadge v-if="licenceState(d.licenceValidUntil, today) === 'expired'" tone="danger" class="ms-2">{{ t('ops.licenceExpired') }}</WegoBadge>
                  <WegoBadge v-else-if="licenceState(d.licenceValidUntil, today) === 'soon'" tone="warning" class="ms-2">{{ t('ops.licenceSoon') }}</WegoBadge>
                </td>
                <td class="px-4 py-2"><WegoBadge :tone="d.active ? 'success' : 'neutral'">{{ d.active ? t('ops.active') : t('ops.inactive') }}</WegoBadge></td>
                <td class="px-4 py-2 text-end"><button type="button" class="font-semibold text-sts-ocean-mid hover:underline" @click="open(d)">{{ t('ops.edit') }}</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </div>
  </main>
</template>
