<script setup lang="ts">
defineOptions({ inheritAttrs: false });
const model = defineModel<string | null>();
defineProps<{ invalid?: boolean }>();
</script>

<template>
  <textarea
    v-model="model"
    v-bind="$attrs"
    :aria-invalid="invalid || undefined"
    class="min-h-24 w-full rounded-[var(--sts-radius-control)] border border-sts-border bg-sts-surface px-3.5 py-2.5 text-[0.95rem] text-sts-ink
           placeholder:text-sts-muted/70 hover:border-sts-ocean-bright/50 focus:border-sts-ocean-bright aria-invalid:border-sts-danger"
  />
</template>
