<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { WegoAlert, WegoBadge } from "@wego/ui";
import type { AssignmentOptions, AssignmentView } from "@wego/api-contract";
import { clearAssignment, saveAssignment, ToursApiError } from "../composables/useToursApi";
import { useErpLocale } from "../composables/useErpLocale";
import { assignmentFailure, issueMessage, supplierOrderSubject, type AssignmentFailure } from "../utils/opsRegistry";
import { docMessage } from "../utils/documentMessages";
import { documentPath } from "../utils/documentFormat";

/**
 * Daily assignment of a departure: driver, optional vehicle and suppliers. Conflicts (same driver or
 * vehicle on another departure in the same time window) are shown clearly and nothing is saved;
 * warnings (seats, licence, back-to-back windows) are listed but do not block. A driver, vehicle or
 * supplier that was deactivated after being assigned stays visible in the form, marked "inactive —
 * remove", so staff can see it and take it off (the server refuses to save it again).
 * The supplier note is staff-written text printed on the supplier orders; customers' own special
 * requests never go to suppliers.
 */
const props = defineProps<{
  token: string;
  slotId: string;
  view: AssignmentView | null;
  options: AssignmentOptions | null;
  canAssign: boolean;
  canPrint: boolean;
  /** Called when the server answered with a newer revision (so the day can refresh). */
  slotLabel?: (slotId: string) => string;
}>();
const emit = defineEmits<{ saved: [view: AssignmentView]; stale: [] }>();

const { t, locale, instantLabel } = useErpLocale();
const editing = ref(false);
const saving = ref(false);
const failure = ref<AssignmentFailure | null>(null);
const form = reactive({ driverId: "", vehicleId: "", supplierIds: [] as string[], supplierNote: "" });
const noteHintId = computed(() => `asg-note-hint-${props.slotId}`);
const headingId = computed(() => `asg-${props.slotId}`);

const assignment = computed(() => props.view?.assignment ?? null);
const issues = computed(() => props.view?.issues ?? []);
const driverOptions = computed(() => props.options?.drivers ?? []);
const vehicleOptions = computed(() => props.options?.vehicles ?? []);
const suggested = computed(() => new Set(props.view?.suggestedSuppliers.map((s) => s.id) ?? []));
const supplierOptions = computed(() => props.options?.suppliers ?? []);
const suggestedOptions = computed(() => supplierOptions.value.filter((s) => suggested.value.has(s.id)));
const otherOptions = computed(() => supplierOptions.value.filter((s) => !suggested.value.has(s.id)));
const driverLabel = (d: { name: string; licenceCoversDate: boolean }) => (d.licenceCoversDate ? d.name : `${d.name} — ${t("ops.licenceExpired")}`);
// Options carry active records only; an assigned one missing from them was deactivated since.
const inactiveDriver = computed(() => {
  const d = assignment.value?.driver;
  return d && !driverOptions.value.some((o) => o.id === d.id) ? d : null;
});
const inactiveVehicle = computed(() => {
  const v = assignment.value?.vehicle;
  return v && !vehicleOptions.value.some((o) => o.id === v.id) ? v : null;
});
const inactiveSuppliers = computed(() => (assignment.value?.suppliers ?? []).filter((s) => !supplierOptions.value.some((o) => o.id === s.id)));
const inactiveStillChosen = computed(() =>
  (inactiveDriver.value !== null && form.driverId === inactiveDriver.value.id)
  || (inactiveVehicle.value !== null && form.vehicleId === inactiveVehicle.value.id)
  || inactiveSuppliers.value.some((s) => form.supplierIds.includes(s.id)));

function startEdit() {
  const a = assignment.value;
  form.driverId = a?.driver?.id ?? "";
  form.vehicleId = a?.vehicle?.id ?? "";
  form.supplierIds = a?.suppliers.map((s) => s.id) ?? [];
  form.supplierNote = a?.supplierNote ?? "";
  failure.value = null;
  editing.value = true;
}

watch(() => props.slotId, () => { editing.value = false; failure.value = null; });

async function save() {
  if (saving.value) return;
  if (!form.driverId && !form.vehicleId && form.supplierIds.length === 0) {
    failure.value = { message: { key: "asg.pickChoice" }, conflicts: [], revisionConflict: false };
    return;
  }
  saving.value = true;
  failure.value = null;
  try {
    const view = await saveAssignment(props.token, props.slotId, {
      driverId: form.driverId || null, vehicleId: form.vehicleId || null, supplierIds: form.supplierIds,
      supplierNote: form.supplierNote.trim() || null,
      expectedRevision: assignment.value?.revision ?? 0,
    });
    editing.value = false;
    emit("saved", view);
  } catch (err) {
    failure.value = assignmentFailure(err);
    if (failure.value.revisionConflict) emit("stale");
  } finally {
    saving.value = false;
  }
}

async function clear() {
  const current = assignment.value;
  if (!current || saving.value) return;
  saving.value = true;
  failure.value = null;
  try {
    emit("saved", await clearAssignment(props.token, props.slotId, current.revision));
    editing.value = false;
  } catch (err) {
    failure.value = assignmentFailure(err);
    if (err instanceof ToursApiError && err.errorCode === "revision_conflict") emit("stale");
  } finally {
    saving.value = false;
  }
}

const FIELD = "w-full rounded-lg border border-sts-border bg-sts-surface px-3 py-2 text-sm";
</script>

<template>
  <section class="border-b border-sts-border px-4 py-3 text-sm print:hidden" :aria-labelledby="headingId">
    <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
      <h3 :id="headingId" class="font-semibold">{{ t("asg.title") }}</h3>
      <template v-if="assignment">
        <span><span class="text-sts-muted">{{ t("asg.driver") }}:</span> <strong dir="auto">{{ assignment.driver?.name ?? t("asg.noDriver") }}</strong></span>
        <span><span class="text-sts-muted">{{ t("asg.vehicle") }}:</span> <strong dir="auto">{{ assignment.vehicle?.display ?? t("asg.noVehicle") }}</strong></span>
        <span v-if="assignment.suppliers.length"><span class="text-sts-muted">{{ t("asg.suppliers") }}:</span> <strong v-for="(s, i) in assignment.suppliers" :key="s.id" dir="auto">{{ i ? " · " : "" }}{{ s.name }}</strong></span>
      </template>
      <span v-else class="text-sts-muted">{{ t("asg.none") }}</span>
      <button v-if="canAssign && !editing" type="button" class="font-semibold text-sts-ocean-mid hover:underline" @click="startEdit">{{ assignment ? t("asg.change") : t("asg.assign") }}</button>
    </div>
    <p v-if="assignment?.supplierNote" class="mt-1"><span class="text-sts-muted">{{ t("asg.supplierNote") }}:</span> <span dir="auto">{{ assignment.supplierNote }}</span></p>
    <p v-if="assignment" class="mt-1 text-xs text-sts-muted">
      {{ t("asg.by", { who: assignment.assignedByEmail ?? "—" }) }} · {{ t("asg.updated", { who: assignment.updatedByEmail ?? "—", at: instantLabel(assignment.updatedAt) }) }} · {{ t("asg.rev", { n: assignment.revision }) }}
    </p>

    <ul v-if="issues.length" class="mt-2 grid gap-1" role="status">
      <li v-for="(issue, i) in issues" :key="i" class="flex items-start gap-2">
        <WegoBadge :tone="issue.blocking ? 'danger' : 'warning'">{{ issue.blocking ? t("asg.blocked") : t("asg.warning") }}</WegoBadge>
        <span>{{ t(issueMessage(issue).key, issueMessage(issue).params) }}</span>
      </li>
    </ul>

    <p v-if="assignment && canPrint" class="mt-2 flex flex-wrap gap-x-4 gap-y-1">
      <NuxtLink v-if="assignment.driver" :to="documentPath('driver-sheet', slotId)" class="font-semibold text-sts-ocean-mid hover:underline">{{ docMessage(locale, "doc.ui.printDriverSheet") }}</NuxtLink>
      <NuxtLink v-for="s in assignment.suppliers" :key="s.id" :to="documentPath('supplier-order', supplierOrderSubject(slotId, s.id))" class="font-semibold text-sts-ocean-mid hover:underline"><span dir="auto">{{ t("asg.supplierOrder", { name: s.name }) }}</span></NuxtLink>
    </p>

    <form v-if="editing" class="mt-3 grid gap-3 rounded-xl border border-sts-border bg-sts-canvas p-3 md:grid-cols-2" @submit.prevent="save">
      <label class="grid gap-1 font-semibold">{{ t("asg.driver") }}
        <select v-model="form.driverId" :class="FIELD">
          <option value="">{{ t("asg.noDriver") }}</option>
          <option v-for="d in driverOptions" :key="d.id" :value="d.id">{{ driverLabel(d) }}</option>
          <option v-if="inactiveDriver" :value="inactiveDriver.id">{{ t("asg.inactiveRemove", { name: inactiveDriver.name }) }}</option>
        </select>
      </label>
      <label class="grid gap-1 font-semibold">{{ t("asg.vehicle") }}
        <select v-model="form.vehicleId" :class="FIELD">
          <option value="">{{ t("asg.noVehicle") }}</option>
          <option v-for="v in vehicleOptions" :key="v.id" :value="v.id">{{ v.display }} ({{ v.seats }})</option>
          <option v-if="inactiveVehicle" :value="inactiveVehicle.id">{{ t("asg.inactiveRemove", { name: inactiveVehicle.display }) }}</option>
        </select>
      </label>
      <fieldset class="md:col-span-2">
        <legend class="font-semibold">{{ t("asg.suppliers") }}</legend>
        <p v-if="suggestedOptions.length" class="mt-1 text-xs text-sts-muted">{{ t("asg.suggested") }}</p>
        <div class="mt-1 flex flex-wrap gap-x-4 gap-y-1">
          <label v-for="s in suggestedOptions" :key="s.id" class="inline-flex items-center gap-2"><input v-model="form.supplierIds" type="checkbox" :value="s.id"> <span dir="auto">{{ s.name }}</span></label>
        </div>
        <p v-if="otherOptions.length" class="mt-2 text-xs text-sts-muted">{{ t("asg.otherSuppliers") }}</p>
        <div class="mt-1 flex flex-wrap gap-x-4 gap-y-1">
          <label v-for="s in otherOptions" :key="s.id" class="inline-flex items-center gap-2"><input v-model="form.supplierIds" type="checkbox" :value="s.id"> <span dir="auto">{{ s.name }}</span></label>
        </div>
        <div v-if="inactiveSuppliers.length" class="mt-2 flex flex-wrap gap-x-4 gap-y-1" data-test="inactive-suppliers">
          <label v-for="s in inactiveSuppliers" :key="s.id" class="inline-flex items-center gap-2 text-sts-danger"><input v-model="form.supplierIds" type="checkbox" :value="s.id"> <span dir="auto">{{ t("asg.inactiveRemove", { name: s.name }) }}</span></label>
        </div>
      </fieldset>
      <p v-if="inactiveStillChosen" class="font-semibold text-sts-danger md:col-span-2" role="note" data-test="inactive-notice">{{ t("asg.inactiveNotice") }}</p>
      <label class="grid gap-1 font-semibold md:col-span-2">{{ t("asg.supplierNote") }}
        <input v-model="form.supplierNote" type="text" maxlength="500" :class="FIELD" :aria-describedby="noteHintId" dir="auto">
        <span :id="noteHintId" class="text-xs font-normal text-sts-muted">{{ t("asg.supplierNoteHint") }}</span>
      </label>
      <div class="flex flex-wrap items-center gap-3 md:col-span-2">
        <button type="submit" class="rounded-lg bg-sts-ocean px-4 py-2 font-semibold text-white disabled:opacity-60" :disabled="saving">{{ saving ? t("ops.saving") : t("ops.save") }}</button>
        <button type="button" class="rounded-lg border border-sts-border px-4 py-2 font-semibold" @click="editing = false">{{ t("asg.close") }}</button>
        <button v-if="assignment" type="button" class="rounded-lg border border-sts-border px-4 py-2 font-semibold text-sts-danger" :disabled="saving" @click="clear">{{ t("asg.clear") }}</button>
      </div>
    </form>

    <WegoAlert v-if="failure" :variant="failure.conflicts.length ? 'danger' : 'warning'" class="mt-3" role="alert">
      <strong>{{ t(failure.message.key, failure.message.params) }}</strong>
      <ul v-if="failure.conflicts.length" class="mt-1 list-disc ps-5">
        <li v-for="(c, i) in failure.conflicts" :key="i">
          {{ t(`asg.conflict.${c.kind}`) }}
          <span v-if="slotLabel">{{ t("asg.conflictOther", { slot: slotLabel(c.otherSlotId) }) }}</span>
        </li>
      </ul>
    </WegoAlert>
  </section>
</template>
