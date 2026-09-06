<script setup lang="ts">
// Shared header for every non-landing page (experiences list/detail,
// design system, booking prototype) — was hand-duplicated with slightly
// different markup in each of those four pages before this Phase 2 pass.
// The landing page (`pages/index.vue`) keeps its own richer header (full
// nav + logo mark) since it's structurally different, not just styled
// differently.
defineProps<{
  direction: "ltr" | "rtl";
  backTo: string;
  backLabel: string;
  localeLabel: string;
}>();

defineEmits<{ toggleLocale: [] }>();
</script>

<template>
  <header class="flex items-center justify-between gap-4">
    <NuxtLink :to="backTo" class="flex items-center gap-2 font-semibold text-sharm-sea transition-transform hover:-translate-x-0.5 rtl:hover:translate-x-0.5">
      <span aria-hidden="true">{{ direction === "rtl" ? "→" : "←" }}</span>
      {{ backLabel }}
    </NuxtLink>
    <button
      type="button"
      class="min-h-11 rounded-full border border-sharm-sea/20 bg-white px-4 text-sm font-semibold text-sharm-sea transition-transform hover:scale-105"
      @click="$emit('toggleLocale')"
    >
      {{ localeLabel }}
    </button>
  </header>
</template>
