<script setup lang="ts">
// The repeated shape across every ERP page: a rounded card with a
// border, surface background, and consistent padding — currently
// copy-pasted as raw utility classes 22+ times. `title`/`description`
// are optional since some panels (a create form, a filter bar) don't
// need a heading of their own — the caller's default slot always has
// the full content.
withDefaults(
  defineProps<{
    title?: string;
    description?: string;
  }>(),
  {
    title: undefined,
    description: undefined,
  },
);
</script>

<template>
  <section class="rounded-wego-card border border-wego-border bg-wego-surface p-6">
    <header v-if="title || description || $slots.actions" class="flex items-start justify-between gap-4">
      <div>
        <h2 v-if="title" class="text-xl font-semibold">{{ title }}</h2>
        <p v-if="description" class="mt-1 text-sm text-wego-muted">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="shrink-0">
        <slot name="actions" />
      </div>
    </header>
    <div :class="title || description || $slots.actions ? 'mt-6' : ''">
      <slot />
    </div>
  </section>
</template>
