<script setup lang="ts">
/** Bare layout for printable documents: no staff navigation, so the paper is the whole page. */
</script>

<template>
  <div class="min-h-screen bg-sts-canvas text-sts-ink print:bg-white">
    <slot />
  </div>
</template>
