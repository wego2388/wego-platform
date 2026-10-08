<script setup lang="ts">
withDefaults(defineProps<{ tone?: "sand" | "sea" | "night" }>(), { tone: "sand" });
</script>

<template>
  <div class="sts-section-backdrop" :data-tone="tone" aria-hidden="true">
    <svg viewBox="0 0 1440 500" preserveAspectRatio="xMidYMid slice" focusable="false" class="h-full w-full">
      <circle cx="1190" cy="120" r="72" fill="currentColor" opacity=".11" />
      <circle cx="1190" cy="120" r="104" fill="none" stroke="currentColor" opacity=".1" />
      <g fill="none" stroke="currentColor" stroke-width="1.25" opacity=".22">
        <path d="M-80 370 C230 170 430 550 750 320 S1240 80 1520 270" />
        <path d="M-80 392 C230 192 430 572 750 342 S1240 102 1520 292" />
        <path d="M-80 414 C230 214 430 594 750 364 S1240 124 1520 314" />
        <path d="M-80 436 C230 236 430 616 750 386 S1240 146 1520 336" />
        <path d="M-80 458 C230 258 430 638 750 408 S1240 168 1520 358" />
      </g>
    </svg>
  </div>
</template>

<style scoped>
.sts-section-backdrop { position: absolute; inset: 0; z-index: 0; overflow: hidden; pointer-events: none; color: var(--sts-color-sand); }
.sts-section-backdrop[data-tone="sea"] { color: #0e7c86; }
.sts-section-backdrop[data-tone="night"] { color: #c8a97e; opacity: .6; }
</style>
