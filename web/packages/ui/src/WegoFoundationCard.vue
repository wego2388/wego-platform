<script setup lang="ts">
defineProps<{
  title: string;
  description: string;
  state: "ready" | "planned";
}>();
</script>

<template>
  <article
    :data-state="state"
    class="rounded-[var(--wego-radius-card)] border border-wego-border bg-wego-surface p-6 shadow-sm"
  >
    <div class="flex items-center justify-between gap-4">
      <h3 class="font-semibold">{{ title }}</h3>
      <span
        class="rounded-full bg-wego-accent-soft px-3 py-1 text-xs font-semibold tracking-wide text-wego-accent uppercase"
      >
        {{ state }}
      </span>
    </div>
    <p class="mt-4 leading-7 text-wego-muted">{{ description }}</p>
  </article>
</template>
