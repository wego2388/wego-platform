<script setup lang="ts">
// The "Wego Platform" eyebrow + <h1> + optional description is
// hand-copied at the top of every one of the 17 ERP pages. This is the
// real component — deliberately still an <h1>, not a level chosen by
// the caller, since every page that uses this owns exactly one.
withDefaults(
  defineProps<{
    title: string;
    description?: string;
  }>(),
  {
    description: undefined,
  },
);
</script>

<template>
  <header>
    <p class="text-sm font-semibold tracking-[0.18em] text-wego-accent uppercase">Wego Platform</p>
    <h1 class="mt-4 text-3xl font-semibold tracking-tight">{{ title }}</h1>
    <p v-if="description" class="mt-2 text-wego-muted">{{ description }}</p>
  </header>
</template>
