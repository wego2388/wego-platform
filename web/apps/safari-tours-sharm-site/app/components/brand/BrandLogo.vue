<script setup lang="ts">
/**
 * Brand logo. The SVGs in /public/brand are mockups until the real logo is
 * delivered. By default it follows the theme (light logo on dark theme);
 * `inverse` forces the light logo, e.g. over a dark hero.
 */
withDefaults(defineProps<{ inverse?: boolean; height?: number }>(), { inverse: false, height: 40 });
</script>

<template>
  <span class="sts-logo inline-block" :class="{ 'sts-logo--inverse': inverse }">
    <img
      src="/brand/logo.svg"
      alt="Safari Tours Sharm"
      :height="height"
      :width="Math.round(height * (240 / 56))"
      class="sts-logo__light block"
      :style="{ height: `${height}px`, width: 'auto' }"
      decoding="async"
    >
    <img
      src="/brand/logo-inverse.svg"
      alt=""
      aria-hidden="true"
      :height="height"
      :width="Math.round(height * (240 / 56))"
      class="sts-logo__dark block"
      :style="{ height: `${height}px`, width: 'auto' }"
      decoding="async"
    >
  </span>
</template>

<style>
.sts-logo__dark { display: none; }
.sts-logo--inverse .sts-logo__light,
:root[data-theme="dark"] .sts-logo .sts-logo__light { display: none; }
.sts-logo--inverse .sts-logo__dark,
:root[data-theme="dark"] .sts-logo .sts-logo__dark { display: block; }
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) .sts-logo .sts-logo__light { display: none; }
  :root:not([data-theme="light"]) .sts-logo .sts-logo__dark { display: block; }
}
</style>
