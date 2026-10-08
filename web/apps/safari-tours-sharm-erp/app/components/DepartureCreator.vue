<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { WegoAlert, WegoButton, WegoInput, WegoSelect } from "@wego/ui";
import CalendarDateField from "./CalendarDateField.vue";
import { createSlot, ToursApiError, type Tour, type TourSlot, type TimeSlot } from "../composables/useToursApi";
import { useErpLocale } from "../composables/useErpLocale";
import { isCalendarDate, operatorCalendarDay } from "../utils/calendarDate";
import { tourErrorMessage } from "../utils/tourMessages";
import type { ErpMessageDescriptor } from "../utils/bookingMessages";

const props = defineProps<{ token: string; tour: Tour; date?: string; disabled?: boolean }>();
const emit = defineEmits<{ saved: [slot: TourSlot]; conflict: []; expired: [] }>();
const { t, count } = useErpLocale();
const date = ref(props.date ?? operatorCalendarDay());
const time = ref(""); const capacity = ref(""); const confirmed = ref(false);
const busy = ref(false); const error = ref<ErpMessageDescriptor | null>(null);
const success = ref(false);
let revision = 0;
const today = operatorCalendarDay();
const times = computed(() => props.tour.availableTimeSlots as TimeSlot[]);
watch(() => props.date, (value) => { if (value !== undefined) date.value = value; });
watch([date, time, capacity, () => props.tour.id], () => { revision++; confirmed.value = false; success.value = false; error.value = null; });
async function save() {
  if (busy.value || props.disabled) return;
  error.value = null; success.value = false;
  if (!isCalendarDate(date.value) || date.value < today || !times.value.includes(time.value as TimeSlot) || !/^\d+$/.test(capacity.value) || Number(capacity.value) < 1 || Number(capacity.value) > 1000 || !confirmed.value) {
    error.value = { key: "inventory.invalidSlot" }; return;
  }
  busy.value = true;
  const submittedRevision = revision;
  try {
    const slot = await createSlot(props.token, props.tour.id, { date: date.value, timeSlot: time.value, capacity: Number(capacity.value) });
    if (submittedRevision === revision) { success.value = true; confirmed.value = false; }
    emit("saved", slot);
  } catch (e) {
    if (e instanceof ToursApiError && e.status === 401) emit("expired");
    if (submittedRevision !== revision) return;
    if (e instanceof ToursApiError && e.errorCode === "slot_already_exists") { error.value = { key: "inventory.slotExists" }; emit("conflict"); }
    else error.value = tourErrorMessage(e);
  } finally { busy.value = false; }
}
</script>

<template>
  <details class="rounded-2xl border border-sts-border bg-sts-surface p-4">
    <summary class="cursor-pointer text-sm font-semibold text-sts-ocean">{{ t('inventory.createSlot') }}</summary>
    <p class="mt-3 text-sm text-sts-muted">{{ t('inventory.slotIntro') }}</p>
    <WegoAlert v-if="error" variant="danger" class="mt-3" role="alert">{{ t(error.key, error.params) }}</WegoAlert>
    <WegoAlert v-if="success" variant="success" class="mt-3" role="status">{{ t('inventory.slotSaved') }}</WegoAlert>
    <fieldset class="mt-3 space-y-4" :disabled="busy || disabled">
      <div class="grid gap-4 sm:grid-cols-2">
        <CalendarDateField v-if="props.date === undefined" id="departure-date" v-model="date" :label="t('common.date')" :min="today" required />
        <WegoSelect id="departure-time" v-model="time" :label="t('office.new.slot')" required>
          <option value="">{{ t('office.new.chooseSlot') }}</option><option v-for="s in times" :key="s" :value="s">{{ t(`slot.${s}`) }}</option>
        </WegoSelect>
        <WegoInput id="departure-capacity" v-model="capacity" type="number" min="1" max="1000" required :label="t('inventory.capacity')" :help="t('inventory.capacityHint', { capacity: count(tour.capacity) })" />
      </div>
      <label class="flex items-start gap-2 text-sm"><input v-model="confirmed" type="checkbox" class="mt-1" required> {{ t('inventory.confirm') }}</label>
      <WegoButton type="button" :disabled="busy || disabled" @click="save">{{ t(busy ? 'inventory.saving' : 'inventory.saveSlot') }}</WegoButton>
    </fieldset>
  </details>
</template>
