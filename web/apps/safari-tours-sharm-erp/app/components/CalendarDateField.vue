<script setup lang="ts">
import { computed, ref, watch, watchEffect } from "vue";
import { useErpLocale } from "../composables/useErpLocale";
import { dateFromParts, isCalendarDate } from "../utils/calendarDate";

const props = defineProps<{ id: string; modelValue: string; label: string; min?: string; max?: string; required?: boolean; disabled?: boolean; error?: string }>();
const emit = defineEmits<{ "update:modelValue": [value: string]; cleared: [] }>();
const { t, locale, dateLabel } = useErpLocale();
const day = ref(""); const month = ref(""); const year = ref("");
const yearControl = ref<HTMLInputElement | null>(null);
let lastEmitted: string | undefined;
watch(() => props.modelValue, (value) => {
  // Keep invalid/partial parts visible while the parent receives no usable ISO date.
  if (value === lastEmitted) return;
  const parts = isCalendarDate(value) ? value.split("-") : [];
  year.value = parts[0] ?? ""; month.value = parts[1] ? String(Number(parts[1])) : ""; day.value = parts[2] ? String(Number(parts[2])) : "";
}, { immediate: true });
const value = computed(() => dateFromParts(day.value, month.value, year.value));
const validation = computed(() => {
  if (!day.value && !month.value && !year.value) return props.required ? t("date.invalid") : "";
  if (!value.value) return t("date.invalid");
  if (props.min && value.value < props.min) return t("date.min", { date: dateLabel(props.min) });
  if (props.max && value.value > props.max) return t("date.max", { date: dateLabel(props.max) });
  return "";
});
const months = computed(() => Array.from({ length: 12 }, (_, index) => ({
  value: String(index + 1), label: new Intl.DateTimeFormat(locale.value === "ar" ? "ar-EG" : "en-GB", { month: "long", timeZone: "UTC", calendar: "gregory" }).format(new Date(Date.UTC(2024, index, 1))),
})));
function update() { lastEmitted = validation.value ? "" : value.value; emit("update:modelValue", lastEmitted); }
function clear() { day.value = ""; month.value = ""; year.value = ""; update(); emit("cleared"); }
watchEffect(() => yearControl.value?.setCustomValidity(validation.value));
const field = "w-full min-w-0 rounded-xl border border-sts-border bg-sts-surface px-2 py-2.5 text-sm text-sts-ink focus:outline-sts-gold disabled:opacity-60";
</script>

<template>
  <fieldset :id="id" class="min-w-0" :disabled="disabled" :aria-describedby="`${id}-help`">
    <legend class="mb-1 text-sm font-semibold">{{ label }}<span v-if="required" aria-hidden="true"> *</span></legend>
    <div class="grid grid-cols-[minmax(0,1fr)_minmax(0,1.6fr)_minmax(0,1fr)] gap-2">
      <label class="grid min-w-0 gap-1 text-xs text-sts-muted" :for="`${id}-day`">{{ t('date.day') }}
        <select :id="`${id}-day`" v-model="day" :class="field" :required="required" :aria-invalid="!!(error || validation)" @change="update"><option value="">{{ t('date.choose') }}</option><option v-for="d in 31" :key="d" :value="String(d)">{{ d }}</option></select>
      </label>
      <label class="grid min-w-0 gap-1 text-xs text-sts-muted" :for="`${id}-month`">{{ t('date.month') }}
        <select :id="`${id}-month`" v-model="month" :class="field" :required="required" :aria-invalid="!!(error || validation)" @change="update"><option value="">{{ t('date.choose') }}</option><option v-for="m in months" :key="m.value" :value="m.value">{{ m.label }}</option></select>
      </label>
      <label class="grid min-w-0 gap-1 text-xs text-sts-muted" :for="`${id}-year`">{{ t('date.year') }}
        <input :id="`${id}-year`" ref="yearControl" v-model="year" :class="field" type="text" inputmode="numeric" maxlength="4" dir="ltr" :required="required" :aria-invalid="!!(error || validation)" @input="update">
      </label>
    </div>
    <p :id="`${id}-help`" class="mt-1 text-xs" :class="error || validation ? 'text-sts-danger' : 'text-sts-muted'">{{ error || validation || (value ? dateLabel(value, true) : t('date.hint')) }}</p>
    <button v-if="!required && (day || month || year)" type="button" class="mt-1 text-xs font-semibold text-sts-ocean hover:underline" @click="clear">{{ t('date.clear') }}</button>
  </fieldset>
</template>
