<script setup lang="ts">
import { computed, useId } from "vue";

/**
 * Label + control + hint + error, wired for assistive technology: the slot
 * receives the ids to put on the control so the label, hint and error are
 * announced with it.
 */
const props = defineProps<{
  label: string;
  hint?: string;
  error?: string | null;
  required?: boolean;
  id?: string;
}>();

const generated = useId();
const controlId = computed(() => props.id ?? `f-${generated}`);
const hintId = computed(() => (props.hint ? `${controlId.value}-hint` : undefined));
const errorId = computed(() => (props.error ? `${controlId.value}-error` : undefined));
const describedBy = computed(() => [hintId.value, errorId.value].filter(Boolean).join(" ") || undefined);
</script>

<template>
  <div class="flex flex-col gap-1.5">
    <label :for="controlId" class="text-sm font-semibold text-sts-ink">
      {{ label }}<span v-if="required" class="text-sts-danger" aria-hidden="true"> *</span>
    </label>
    <slot
      :id="controlId"
      :described-by="describedBy"
      :invalid="Boolean(error)"
      :required="required"
    />
    <p v-if="hint" :id="hintId" class="text-xs text-sts-muted">{{ hint }}</p>
    <p v-if="error" :id="errorId" class="flex items-center gap-1 text-xs font-medium text-sts-danger">
      <Icon name="lucide:circle-alert" class="size-3.5 shrink-0" aria-hidden="true" />{{ error }}
    </p>
  </div>
</template>
