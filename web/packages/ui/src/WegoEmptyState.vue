<script setup lang="ts">
withDefaults(
  defineProps<{
    message: string;
  }>(),
  {},
);
</script>

<template>
  <p class="mt-3 text-sm text-wego-muted">{{ message }}</p>
</template>
