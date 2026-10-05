<script setup lang="ts">
/**
 * Brand logo: the owner's real logo (emblem + wordmark). The artwork reads on
 * light and dark backgrounds, so one image serves both themes; `inverse` is
 * kept for call sites over a dark hero and only adds a soft backdrop.
 * `variant="full"` shows the stacked logo (footer, about, documents).
 */
const props = withDefaults(defineProps<{ inverse?: boolean; height?: number; variant?: "lockup" | "full" }>(), {
  inverse: false,
  height: 40,
  variant: "lockup",
});
// Intrinsic ratios of the generated assets (see public/brand/README.md).
const ratio = computed(() => (props.variant === "full" ? 640 / 679 : 1220 / 300));
const src = computed(() => (props.variant === "full" ? "/brand/logo-full.webp" : "/brand/logo.webp"));
</script>

<template>
  <span class="sts-logo inline-block" :class="{ 'sts-logo--inverse': inverse }">
    <img
      :src="src"
      alt="Safari Tours Sharm"
      :height="height"
      :width="Math.round(height * ratio)"
      class="block"
      :style="{ height: `${height}px`, width: 'auto' }"
      decoding="async"
    >
  </span>
</template>

<style>
.sts-logo--inverse img { filter: drop-shadow(0 1px 2px rgb(0 0 0 / 0.35)); }
</style>
