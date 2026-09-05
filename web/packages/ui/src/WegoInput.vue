<script setup lang="ts">
// inheritAttrs: false + `v-bind="$attrs"` on the real <input> — not a
// fixed list of forwarded props — so `disabled`, `placeholder`, `min`,
// `max`, `step`, or any other native input attribute a caller passes
// reaches the actual element instead of silently landing on the
// wrapper <div>. That was a real, shipped bug (WEGO-014 Phase 2/3
// planning): chart-of-accounts.vue's :disabled on an editing code
// field, equipment.vue's :disabled on an editing QR field, and two
// placeholder amounts never worked.
defineOptions({ inheritAttrs: false });

const props = withDefaults(
  defineProps<{
    id: string;
    label: string;
    modelValue: string;
    type?: string;
    required?: boolean;
    autocomplete?: string;
    help?: string;
    error?: string;
  }>(),
  {
    type: "text",
    required: false,
    autocomplete: undefined,
    help: undefined,
    error: undefined,
  },
);

defineEmits<{
  "update:modelValue": [value: string];
}>();

const describedBy = () => (props.error ? `${props.id}-error` : props.help ? `${props.id}-help` : undefined);
</script>

<template>
  <div>
    <label :for="id" class="block text-sm font-medium text-wego-muted">{{ label }}</label>
    <input
      :id="id"
      v-bind="$attrs"
      :type="type"
      :value="modelValue"
      :required="required"
      :autocomplete="autocomplete"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="describedBy()"
      class="mt-2 w-full rounded-wego-control border border-wego-border bg-wego-surface px-4 py-2.5 text-wego-ink disabled:cursor-not-allowed disabled:opacity-60"
      @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    >
    <p v-if="error" :id="`${id}-error`" class="mt-1.5 text-sm text-wego-danger">{{ error }}</p>
    <p v-else-if="help" :id="`${id}-help`" class="mt-1.5 text-sm text-wego-muted">{{ help }}</p>
  </div>
</template>
