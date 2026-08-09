<script setup lang="ts">
withDefaults(
  defineProps<{
    id: string;
    label: string;
    modelValue: string;
    type?: string;
    required?: boolean;
    autocomplete?: string;
  }>(),
  {
    type: "text",
    required: false,
    autocomplete: undefined,
  },
);

defineEmits<{
  "update:modelValue": [value: string];
}>();
</script>

<template>
  <div>
    <label :for="id" class="block text-sm font-medium text-wego-muted">{{ label }}</label>
    <input
      :id="id"
      :type="type"
      :value="modelValue"
      :required="required"
      :autocomplete="autocomplete"
      class="mt-2 w-full rounded-wego-control border border-wego-border bg-wego-surface px-4 py-2.5 text-wego-ink"
      @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    >
  </div>
</template>
