<script setup lang="ts">
/** The "horizon" line: a sunset arc meeting a wave. Decorative only. */
defineProps<{ tone?: "sand" | "ocean" }>();
</script>

<template>
  <svg viewBox="0 0 1200 40" preserveAspectRatio="none" class="block h-6 w-full" aria-hidden="true" focusable="false">
    <path
      d="M0 28 C 150 10, 300 10, 450 28 S 750 46, 900 28 S 1100 12, 1200 24"
      fill="none"
      :stroke="tone === 'ocean' ? 'var(--sts-color-ocean-bright)' : 'var(--sts-color-sand)'"
      stroke-width="2"
      stroke-linecap="round"
      opacity="0.8"
    />
    <circle cx="600" cy="20" r="6" fill="var(--sts-color-sunset)" />
  </svg>
</template>
