<script setup lang="ts">
defineOptions({ inheritAttrs: false });

withDefaults(
  defineProps<{
    id: string;
    modelValue: boolean;
    disabled?: boolean;
  }>(),
  {
    disabled: false,
  },
);

defineEmits<{
  "update:modelValue": [value: boolean];
}>();
</script>

<template>
  <label :for="id" class="flex items-center gap-2 text-sm" :class="disabled ? 'cursor-not-allowed opacity-60' : 'cursor-pointer'">
    <input
      :id="id"
      v-bind="$attrs"
      type="checkbox"
      :checked="modelValue"
      :disabled="disabled"
      class="h-4 w-4 rounded border-wego-border-strong text-wego-accent accent-wego-accent disabled:cursor-not-allowed"
      @change="$emit('update:modelValue', ($event.target as HTMLInputElement).checked)"
    >
    <slot />
  </label>
</template>
