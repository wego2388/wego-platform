<script setup lang="ts">
defineOptions({ inheritAttrs: false });

const props = withDefaults(
  defineProps<{
    id: string;
    label: string;
    modelValue: string;
    required?: boolean;
    help?: string;
    error?: string;
  }>(),
  {
    required: false,
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
    <textarea
      :id="id"
      v-bind="$attrs"
      :value="modelValue"
      :required="required"
      :aria-invalid="error ? 'true' : undefined"
      :aria-describedby="describedBy()"
      class="mt-2 w-full rounded-wego-control border border-wego-border bg-wego-surface px-4 py-2.5 text-wego-ink disabled:cursor-not-allowed disabled:opacity-60"
      @input="$emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
    />
    <p v-if="error" :id="`${id}-error`" class="mt-1.5 text-sm text-wego-danger">{{ error }}</p>
    <p v-else-if="help" :id="`${id}-help`" class="mt-1.5 text-sm text-wego-muted">{{ help }}</p>
  </div>
</template>
