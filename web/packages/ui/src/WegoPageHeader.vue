<script setup lang="ts">
// The eyebrow + <h1> + optional description is hand-copied at the top
// of every one of the 17 ERP pages. This is the real component —
// deliberately still an <h1>, not a level chosen by the caller, since
// every page that uses this owns exactly one. `eyebrow` defaults to
// "Wego Platform" (web/apps/erp's own brand) but is overridable — a
// second client's own ERP (see web/apps/sharm-to-go-erp) reuses this
// exact component with its own client name instead of literally
// showing "Wego Platform" on someone else's dashboard.
withDefaults(
  defineProps<{
    title: string;
    description?: string;
    eyebrow?: string;
  }>(),
  {
    description: undefined,
    eyebrow: "Wego Platform",
  },
);
</script>

<template>
  <header>
    <p class="text-sm font-semibold tracking-[0.18em] text-wego-accent uppercase">{{ eyebrow }}</p>
    <h1 class="mt-4 text-3xl font-semibold tracking-tight">{{ title }}</h1>
    <p v-if="description" class="mt-2 text-wego-muted">{{ description }}</p>
  </header>
</template>
