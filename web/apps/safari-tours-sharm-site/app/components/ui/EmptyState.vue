<script setup lang="ts">
defineProps<{ icon?: string; title: string; body?: string }>();
</script>

<template>
  <div class="flex flex-col items-center gap-3 rounded-[var(--sts-radius-card)] border border-dashed border-sts-border px-6 py-12 text-center">
    <span class="grid size-12 place-items-center rounded-full bg-sts-sand-soft text-sts-ocean-bright">
      <Icon :name="icon ?? 'lucide:compass'" class="size-6" aria-hidden="true" />
    </span>
    <p class="text-base font-semibold text-sts-ink">{{ title }}</p>
    <p v-if="body" class="max-w-md text-sm text-sts-muted">{{ body }}</p>
    <slot />
  </div>
</template>
