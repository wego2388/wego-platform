<script setup lang="ts">
/** Native select on purpose: best keyboard, screen-reader and mobile picker support. */
defineOptions({ inheritAttrs: false });
const model = defineModel<string | null>();
defineProps<{ options: { value: string; label: string }[]; placeholder?: string; invalid?: boolean }>();
</script>

<template>
  <div class="relative">
    <select
      v-model="model"
      v-bind="$attrs"
      :aria-invalid="invalid || undefined"
      class="min-h-11 w-full appearance-none rounded-[var(--sts-radius-control)] border border-sts-border bg-sts-surface ps-3.5 pe-10 text-[0.95rem] text-sts-ink
             hover:border-sts-ocean-bright/50 focus:border-sts-ocean-bright aria-invalid:border-sts-danger"
    >
      <option v-if="placeholder" value="" disabled>{{ placeholder }}</option>
      <option v-for="option in options" :key="option.value" :value="option.value">{{ option.label }}</option>
    </select>
    <Icon name="lucide:chevron-down" class="pointer-events-none absolute end-3 top-1/2 size-4 -translate-y-1/2 text-sts-muted" aria-hidden="true" />
  </div>
</template>
